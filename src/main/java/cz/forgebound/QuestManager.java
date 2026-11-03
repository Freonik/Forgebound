package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Questmaster: an NPC (placed with /forge world npc set) with daily and weekly quests - the same for everyone,
 * picked from the pools in the config. Progress counts anywhere, the reward is claimed at the NPC.
 */
public final class QuestManager implements Listener {

    enum Type { KILL, KILL_PLAYER, MINE, FISH, TRAVEL, DELIVER }

    record Quest(String key, boolean weekly, Type type, Set<String> targets, int amount, String name,
                 int tokens, int materials, int xp, int totems) { }

    private final Forgebound plugin;
    private final ItemManager items;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey npcKey;
    /** Blocks players placed recently (so placing and breaking doesn't count as mining). */
    private final Set<Location> placed = new LinkedHashSet<>();
    private boolean dirty;

    public QuestManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.file = new File(plugin.getDataFolder(), "quests.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.npcKey = Forgebound.key("questmaster");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("quests.enabled", true);
    }

    public void start() {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                keepNpc();
                if (ticks % 2 == 0) {
                    travelTick();
                }
                if (dirty) {
                    dirty = false;
                    plugin.saveYaml(data, file);
                }
            }
        }.runTaskTimer(plugin, 100L, 200L);
    }

    public void shutdown() {
        plugin.saveYaml(data, file);
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("big-events.timezone", "Europe/Prague"));
        } catch (RuntimeException ex) {
            return ZoneId.systemDefault();
        }
    }

    private LocalDate today() {
        return ZonedDateTime.now(zone()).toLocalDate();
    }

    private String dayKey() {
        return today().toString();
    }

    private String weekKey() {
        LocalDate d = today();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    // ================================================================ the quests of today / this week

    private List<Quest> pool(boolean weekly) {
        List<Quest> list = new ArrayList<>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection(weekly ? "quests.weekly" : "quests.daily");
        if (root == null) {
            return list;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null || !s.getBoolean("enabled", true)) {
                continue;
            }
            try {
                Type type = Type.valueOf(s.getString("type", "KILL").toUpperCase(Locale.ROOT));
                Set<String> targets = new LinkedHashSet<>();
                for (String t : s.getString("target", "").split(",")) {
                    if (!t.isBlank()) {
                        targets.add(t.trim().toUpperCase(Locale.ROOT));
                    }
                }
                list.add(new Quest(key, weekly, type, targets, Math.max(1, s.getInt("amount", 1)),
                        s.getString("name", key), s.getInt("tokens", 0), s.getInt("materials", 0), s.getInt("xp", 0),
                        s.getInt("totems", 0)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("quests." + (weekly ? "weekly." : "daily.") + key + ": " + ex.getMessage());
            }
        }
        return list;
    }

    /** Today's / this week's quests: the same for everybody, picked by the date. */
    List<Quest> current(boolean weekly) {
        List<Quest> pool = new ArrayList<>(pool(weekly));
        pool.sort((a, b) -> a.key().compareTo(b.key()));
        int count = Math.min(pool.size(), plugin.getConfig().getInt(weekly ? "quests.weekly-count" : "quests.daily-count", weekly ? 1 : 3));
        long seed = (weekly ? weekKey() : dayKey()).hashCode() * 31L + (weekly ? 7 : 1);
        java.util.Collections.shuffle(pool, new Random(seed));
        return pool.subList(0, count);
    }

    private List<Quest> active() {
        List<Quest> all = new ArrayList<>(current(false));
        all.addAll(current(true));
        return all;
    }

    // ================================================================ progress

    private String base(UUID id, Quest q) {
        return "players." + id + "." + (q.weekly() ? "weekly" : "daily");
    }

    /** The player's section for this period (emptied when a new day / week starts). */
    private String period(UUID id, Quest q) {
        String base = base(id, q);
        String key = q.weekly() ? weekKey() : dayKey();
        if (!key.equals(data.getString(base + ".period"))) {
            data.set(base, null);
            data.set(base + ".period", key);
            dirty = true;
        }
        return base;
    }

    int progress(Player p, Quest q) {
        String base = period(p.getUniqueId(), q);
        if (q.type() == Type.TRAVEL) {
            String startPath = base + ".travel-start." + q.key();
            if (!data.isSet(startPath)) {
                data.set(startPath, travelled(p));
                dirty = true;
            }
            return (int) Math.min(q.amount(), (travelled(p) - data.getLong(startPath)) / 100);
        }
        return data.getInt(base + ".progress." + q.key());
    }

    boolean claimed(Player p, Quest q) {
        return data.getStringList(period(p.getUniqueId(), q) + ".claimed").contains(q.key());
    }

    private static long travelled(Player p) {
        return (long) p.getStatistic(Statistic.WALK_ONE_CM) + p.getStatistic(Statistic.SPRINT_ONE_CM)
                + p.getStatistic(Statistic.CROUCH_ONE_CM) + p.getStatistic(Statistic.SWIM_ONE_CM);
    }

    private void add(Player p, Type type, String target, int amount) {
        if (!enabled() || amount <= 0 || (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)) {
            return;
        }
        for (Quest q : active()) {
            if (q.type() != type || !matches(q, target) || claimed(p, q)) {
                continue;
            }
            String path = period(p.getUniqueId(), q) + ".progress." + q.key();
            int before = data.getInt(path);
            if (before >= q.amount()) {
                continue;
            }
            int now = Math.min(q.amount(), before + amount);
            data.set(path, now);
            dirty = true;
            if (now >= q.amount()) {
                done(p, q);
            } else {
                p.sendActionBar(ItemManager.color("&6📜 " + q.name() + " &f" + now + "&7/&f" + q.amount()));
            }
        }
    }

    private void done(Player p, Quest q) {
        p.sendMessage(plugin.msg("quest-done", "&6📜 Quest done: &f{quest}&7. Go to the &6Questmaster &7for the reward.",
                "quest", q.name()));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
    }

    private boolean matches(Quest q, String target) {
        if (q.targets().isEmpty()) {
            return true;
        }
        for (String t : q.targets()) {
            if (t.equals(target)) {
                return true;
            }
            if (t.startsWith("#") && target != null) {
                Material m = Material.matchMaterial(target);
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(t.substring(1).toLowerCase(Locale.ROOT)),
                        Material.class);
                if (m != null && tag != null && tag.isTagged(m)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void travelTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Quest q : active()) {
                if (q.type() == Type.TRAVEL && !claimed(p, q)) {
                    String flag = period(p.getUniqueId(), q) + ".told." + q.key();
                    if (progress(p, q) >= q.amount() && !data.getBoolean(flag)) {
                        data.set(flag, true);
                        dirty = true;
                        done(p, q);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) {
            return;
        }
        Entity e = event.getEntity();
        add(killer, Type.KILL, e.getType().name(), 1);
        if (e instanceof Enemy) {
            add(killer, Type.KILL, "ANY_MONSTER", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || (plugin.teams() != null && plugin.teams().allies(killer, victim))) {
            return;
        }
        // each victim counts once per quest period
        for (Quest q : active()) {
            if (q.type() == Type.KILL_PLAYER) {
                String path = period(killer.getUniqueId(), q) + ".victims." + q.key();
                List<String> victims = new ArrayList<>(data.getStringList(path));
                if (victims.contains(victim.getUniqueId().toString())) {
                    killer.sendActionBar(ItemManager.color("&7The same player doesn't count twice for &f" + q.name()));
                    return;
                }
                victims.add(victim.getUniqueId().toString());
                data.set(path, victims);
            }
        }
        add(killer, Type.KILL_PLAYER, null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        placed.add(event.getBlock().getLocation());
        if (placed.size() > 20_000) {
            placed.remove(placed.iterator().next());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (placed.remove(b.getLocation())) {
            return;
        }
        add(event.getPlayer(), Type.MINE, b.getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            add(event.getPlayer(), Type.FISH, null, 1);
        }
    }

    // ================================================================ the NPC

    private Location npcLocation() {
        ConfigurationSection s = data.getConfigurationSection("npc");
        if (s == null) {
            return null;
        }
        World w = Bukkit.getWorld(s.getString("world", ""));
        return w == null ? null : new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw"), 0);
    }

    public void setNpc(Player admin) {
        removeNpcs();
        Location at = admin.getLocation();
        data.set("npc.world", at.getWorld().getName());
        data.set("npc.x", at.getX());
        data.set("npc.y", at.getY());
        data.set("npc.z", at.getZ());
        data.set("npc.yaw", at.getYaw());
        plugin.saveYaml(data, file);
        spawnNpc(npcLocation());
        admin.sendMessage(ItemManager.color("&aThe Questmaster stands here now."));
    }

    public void removeNpc(CommandSender sender) {
        removeNpcs();
        data.set("npc", null);
        plugin.saveYaml(data, file);
        sender.sendMessage(ItemManager.color("&7The Questmaster is gone."));
    }

    private void removeNpcs() {
        for (World w : Bukkit.getWorlds()) {
            for (Villager v : w.getEntitiesByClass(Villager.class)) {
                if (isNpc(v)) {
                    v.remove();
                }
            }
        }
    }

    private boolean isNpc(Entity e) {
        return e instanceof Villager && e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    /** The NPC is always there where it was placed (respawned if something removed it, never twice). */
    private void keepNpc() {
        Location at = npcLocation();
        if (at == null || !enabled() || !at.isChunkLoaded()) {
            return;
        }
        Chunk chunk = at.getChunk();
        if (!chunk.isEntitiesLoaded()) {
            return;
        }
        List<Villager> found = new ArrayList<>();
        for (Villager v : at.getWorld().getNearbyEntitiesByType(Villager.class, at, 4)) {
            if (isNpc(v)) {
                found.add(v);
            }
        }
        if (found.isEmpty()) {
            spawnNpc(at);
            return;
        }
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
        if (found.getFirst().getLocation().distanceSquared(at) > 0.25) {
            found.getFirst().teleport(at);
        }
    }

    private void spawnNpc(Location at) {
        if (at == null) {
            return;
        }
        at.getWorld().spawn(at, Villager.class, v -> {
            v.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
            v.customName(ItemManager.color(plugin.getConfig().getString("quests.npc-name", "&6&lQuestmaster")));
            v.setCustomNameVisible(true);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setProfession(Villager.Profession.LIBRARIAN);
            v.setVillagerLevel(5);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTalk(PlayerInteractEntityEvent event) {
        if (isNpc(event.getRightClicked())) {
            event.setCancelled(true);
            if (enabled()) {
                open(event.getPlayer(), true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcHurt(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ================================================================ the menu

    private static final int[] DAILY_SLOTS = {10, 11, 12};
    private static final int INFO_SLOT = 14;
    private static final int WEEKLY_SLOT = 16;

    private static final class QuestHolder implements InventoryHolder {
        final List<Quest> slots = new ArrayList<>();
        boolean atNpc;
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** atNpc = rewards can be claimed (otherwise /quests only shows progress). */
    public void open(Player p, boolean atNpc) {
        if (!enabled()) {
            p.sendMessage(plugin.msg("quests-off", "&7Quests are switched off."));
            return;
        }
        QuestHolder holder = new QuestHolder();
        holder.atNpc = atNpc;
        Inventory inv = Bukkit.createInventory(holder, 27, ItemManager.color("&8Questmaster"));
        holder.inventory = inv;
        for (int i = 0; i < 27; i++) {
            holder.slots.add(null);
            inv.setItem(i, pane());
        }
        List<Quest> daily = current(false);
        for (int i = 0; i < Math.min(DAILY_SLOTS.length, daily.size()); i++) {
            inv.setItem(DAILY_SLOTS[i], icon(p, daily.get(i), atNpc));
            holder.slots.set(DAILY_SLOTS[i], daily.get(i));
        }
        List<Quest> weekly = current(true);
        if (!weekly.isEmpty()) {
            inv.setItem(WEEKLY_SLOT, icon(p, weekly.getFirst(), atNpc));
            holder.slots.set(WEEKLY_SLOT, weekly.getFirst());
        }
        inv.setItem(INFO_SLOT, info(atNpc));
        p.openInventory(inv);
    }

    private static ItemStack pane() {
        ItemStack s = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        m.setHideTooltip(true);
        s.setItemMeta(m);
        return s;
    }

    private ItemStack info(boolean atNpc) {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone());
        ZonedDateTime monday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone());
        ItemStack s = new ItemStack(Material.CLOCK);
        ItemMeta m = s.getItemMeta();
        m.displayName(ItemManager.color("&6&lQuests"));
        List<Component> lore = new ArrayList<>();
        lore.add(ItemManager.color("&7Left: &fdaily quests &7(new in &f"
                + MythicManager.format(midnight.toInstant().toEpochMilli() - System.currentTimeMillis()) + "&7)"));
        lore.add(ItemManager.color("&7Right: &5weekly quest &7(new in &f"
                + MythicManager.format(monday.toInstant().toEpochMilli() - System.currentTimeMillis()) + "&7)"));
        lore.add(Component.empty());
        lore.add(ItemManager.color(atNpc ? "&7Progress counts anywhere." : "&7Rewards are claimed at the &6Questmaster&7."));
        m.lore(lore);
        s.setItemMeta(m);
        return s;
    }

    private ItemStack icon(Player p, Quest q, boolean atNpc) {
        Material icon = switch (q.type()) {
            case KILL -> Material.IRON_SWORD;
            case KILL_PLAYER -> Material.NETHERITE_SWORD;
            case MINE -> Material.IRON_PICKAXE;
            case FISH -> Material.FISHING_ROD;
            case TRAVEL -> Material.LEATHER_BOOTS;
            case DELIVER -> Material.CHEST;
        };
        String custom = plugin.getConfig().getString("quests." + (q.weekly() ? "weekly." : "daily.") + q.key() + ".icon");
        if (custom != null && Material.matchMaterial(custom) != null) {
            icon = Material.matchMaterial(custom);
        }
        boolean claimed = claimed(p, q);
        int have = q.type() == Type.DELIVER ? count(p, q) : progress(p, q);
        boolean complete = have >= q.amount();
        ItemStack s = new ItemStack(claimed ? Material.LIME_DYE : icon);
        ItemMeta m = s.getItemMeta();
        m.displayName(ItemManager.color((q.weekly() ? "&5&l" : "&6&l") + q.name()));
        List<Component> lore = new ArrayList<>();
        lore.add(ItemManager.color(q.weekly() ? "&5Weekly quest" : "&6Daily quest"));
        int shown = Math.min(have, q.amount());
        int filled = (int) Math.round(20.0 * shown / q.amount());
        lore.add(ItemManager.color("&a" + "|".repeat(filled) + "&8" + "|".repeat(20 - filled) + " &f" + shown + "&7/&f" + q.amount()));
        lore.add(Component.empty());
        lore.add(ItemManager.color("&7Reward: " + rewardText(q)));
        lore.add(Component.empty());
        if (claimed) {
            lore.add(ItemManager.color("&a✔ Done"));
        } else if (q.type() == Type.DELIVER) {
            lore.add(ItemManager.color(complete ? (atNpc ? "&eClick to hand it over" : "&7Hand it over at the &6Questmaster")
                    : "&7Bring it to the &6Questmaster"));
        } else if (complete) {
            lore.add(ItemManager.color(atNpc ? "&eClick to claim the reward" : "&eClaim it at the &6Questmaster"));
        }
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (complete && !claimed) {
            m.setEnchantmentGlintOverride(true);
        }
        s.setItemMeta(m);
        return s;
    }

    private String rewardText(Quest q) {
        List<String> parts = new ArrayList<>();
        if (q.tokens() > 0) {
            parts.add("&e" + q.tokens() + " Bounty Token" + (q.tokens() == 1 ? "" : "s"));
        }
        if (q.materials() > 0) {
            parts.add("&d" + q.materials() + " legendary material" + (q.materials() == 1 ? "" : "s"));
        }
        if (q.xp() > 0) {
            parts.add("&a" + q.xp() + " XP");
        }
        if (q.totems() > 0) {
            parts.add("&6" + (q.totems() == 1 ? "a Totem of the Forge" : q.totems() + " Totems of the Forge"));
        }
        return parts.isEmpty() ? "&7-" : String.join("&7, ", parts);
    }

    private int count(Player p, Quest q) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && q.targets().contains(s.getType().name()) && items.customOf(s) == null) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private void take(Player p, Quest q, int amount) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack s = contents[i];
            if (s != null && q.targets().contains(s.getType().name()) && items.customOf(s) == null) {
                int use = Math.min(amount, s.getAmount());
                s.setAmount(s.getAmount() - use);
                amount -= use;
                contents[i] = s.getAmount() <= 0 ? null : s;
            }
        }
        p.getInventory().setStorageContents(contents);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof QuestHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        Quest q = slot >= 0 && slot < holder.slots.size() ? holder.slots.get(slot) : null;
        if (q == null || !holder.atNpc || claimed(p, q)) {
            return;
        }
        if (q.type() == Type.DELIVER) {
            if (count(p, q) < q.amount()) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            take(p, q, q.amount());
        } else if (progress(p, q) < q.amount()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        String path = period(p.getUniqueId(), q) + ".claimed";
        List<String> claimed = new ArrayList<>(data.getStringList(path));
        claimed.add(q.key());
        data.set(path, claimed);
        dirty = true;
        reward(p, q);
        event.getInventory().setItem(slot, icon(p, q, true));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof QuestHolder) {
            event.setCancelled(true);
        }
    }

    private void reward(Player p, Quest q) {
        if (q.tokens() > 0 && plugin.bounty() != null) {
            plugin.bounty().give(p, plugin.bounty().token(q.tokens()));
        }
        for (int i = 0; i < q.materials() && plugin.bounty() != null; i++) {
            List<ItemManager.Entry> mats = plugin.bounty().enabledMaterials();
            if (!mats.isEmpty()) {
                plugin.bounty().give(p, mats.get(ThreadLocalRandom.current().nextInt(mats.size())).item.clone());
            }
        }
        if (q.xp() > 0) {
            p.giveExp(q.xp());
        }
        for (int i = 0; i < q.totems() && plugin.totems() != null && plugin.bounty() != null; i++) {
            plugin.bounty().give(p, plugin.totems().item());
        }
        p.sendMessage(plugin.msg("quest-claimed", "&6📜 &f{quest} &7- reward: {reward}", "quest", q.name(),
                "reward", ItemManager.color(rewardText(q))));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0);
        plugin.award(p, q.weekly() ? "weekly_quest" : "odd_jobs");
        if (plugin.top() != null) {
            plugin.top().add(p, "quests");
        }
        plugin.getLogger().info("[Quests] " + p.getName() + " finished the " + (q.weekly() ? "weekly" : "daily")
                + " quest '" + q.key() + "'");
        // all three dailies of a day
        if (!q.weekly()) {
            int doneToday = 0;
            List<Quest> daily = current(false);
            for (Quest d : daily) {
                if (claimed(p, d)) {
                    doneToday++;
                }
            }
            if (doneToday == daily.size() && plugin.getConfig().getInt("quests.all-daily-bonus-tokens", 2) > 0
                    && plugin.bounty() != null) {
                int bonus = plugin.getConfig().getInt("quests.all-daily-bonus-tokens", 2);
                plugin.bounty().give(p, plugin.bounty().token(bonus));
                p.sendMessage(plugin.msg("quest-all-daily", "&6📜 All daily quests done! &7Bonus: &e{tokens} Bounty Tokens&7.",
                        "tokens", bonus));
            }
        }
    }

    /** For /forge legend status and the codex: how many players finished something today. */
    public int finishedToday() {
        Set<String> who = new HashSet<>();
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players != null) {
            for (String id : players.getKeys(false)) {
                if (dayKey().equals(players.getString(id + ".daily.period")) && !players.getStringList(id + ".daily.claimed").isEmpty()) {
                    who.add(id);
                }
            }
        }
        return who.size();
    }
}
