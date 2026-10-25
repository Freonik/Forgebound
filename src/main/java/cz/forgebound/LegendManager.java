package cz.forgebound;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Legends: every forged weapon / relic copy gets a hidden serial number, so the plugin always knows which copy
 * is the real one. Abandoned legends (holder offline for too long) return to the forge, personal stats per
 * legend are tracked, and /legends shows the whole server's legends plus your own stats.
 */
public final class LegendManager implements Listener {

    private static final String[] STATS = {"kills", "mob-kills", "damage", "ability-f", "ability-shift-f", "seconds"};

    private final Forgebound plugin;
    private final ItemManager items;
    private final NamespacedKey instanceKey;
    private final NamespacedKey weaponKey;
    private final File legendsFile;
    private final File statsFile;
    private final YamlConfiguration legends;
    private final YamlConfiguration stats;
    private boolean statsDirty;

    public LegendManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.instanceKey = Forgebound.key("legend_instance");
        this.weaponKey = Forgebound.key("weapon");
        this.legendsFile = new File(plugin.getDataFolder(), "legends.yml");
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        this.legends = YamlConfiguration.loadConfiguration(legendsFile);
        this.stats = YamlConfiguration.loadConfiguration(statsFile);
    }

    private static String key(String ref) {
        return ref.replace(':', '_');
    }

    private boolean isLegend(ItemManager.Entry entry) {
        return entry != null && entry.kind != ItemManager.Kind.MATERIAL && !entry.mythic;
    }

    public void start() {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks += 40;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    scan(player);
                }
                if (ticks % 6000 == 0) {                         // every 5 minutes
                    checkAbandoned();
                    saveStats();
                    saveLegends();
                }
                if (ticks == 2400 || ticks % 216_000 == 0) {     // 2 minutes after start, then every 3 hours
                    logHolders();
                }
            }
        }.runTaskTimer(plugin, 60L, 40L);
    }

    public void shutdown() {
        saveStats();
        saveLegends();
    }

    private void saveLegends() {
        plugin.saveYaml(legends, legendsFile);
    }

    private void saveStats() {
        if (!statsDirty) {
            return;
        }
        statsDirty = false;
        plugin.saveYaml(stats, statsFile);
    }

    // ================================================================ serial numbers

    /** A new real copy of a legend was made: give it a fresh serial number (older copies stop being valid). */
    public void stamp(ItemStack stack, String ref) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        String id = UUID.randomUUID().toString();
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        legends.set("current." + key(ref), id);
        saveLegends();
    }

    /** No copy is valid any more (the legend went back to the forge). */
    public void revoke(String ref) {
        legends.set("current." + key(ref), "revoked-" + UUID.randomUUID());
        legends.set("holder." + key(ref), null);
        saveLegends();
    }

    /** true if this copy of the legend is still the real one (stamps old, unstamped copies on first sight). */
    private boolean valid(ItemStack stack, ItemManager.Entry entry) {
        String current = legends.getString("current." + key(entry.ref()));
        String mine = stack.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        if (mine == null) {
            if (current == null) {
                stamp(stack, entry.ref());                       // a copy from before serial numbers: adopt it
                return true;
            }
            return !current.startsWith("revoked-");
        }
        return mine.equals(current);
    }

    private void crumble(Player player, Inventory inventory, int slot, ItemManager.Entry entry) {
        inventory.setItem(slot, null);
        player.sendMessage(plugin.msg("legend-crumbled", "&7Your {item} &7crumbled to dust - it went back to the forge.",
                "item", ItemManager.nameOf(entry.item)));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SAND_BREAK, 1.2f, 0.6f);
        player.getWorld().spawnParticle(Particle.ASH, player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
    }

    /** Checks a whole inventory for stale copies; returns nothing, removes them in place. */
    private void sweep(Inventory inventory, Player viewer) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemManager.Entry entry = items.customOf(contents[i]);
            if (isLegend(entry) && !valid(contents[i], entry)) {
                crumble(viewer, inventory, i, entry);
            }
        }
    }

    // ================================================================ holders and abandoned legends

    /** Every 2 seconds: stale copies, who holds what, time held. */
    private void scan(Player player) {
        long now = System.currentTimeMillis();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemManager.Entry entry = items.customOf(contents[i]);
            if (!isLegend(entry)) {
                continue;
            }
            if (!valid(contents[i], entry)) {
                crumble(player, player.getInventory(), i, entry);
                continue;
            }
            legends.set("holder." + key(entry.ref()) + ".uuid", player.getUniqueId().toString());
            legends.set("holder." + key(entry.ref()) + ".name", player.getName());
            legends.set("holder." + key(entry.ref()) + ".seen", now);
            if (entry.kind == ItemManager.Kind.WEAPON && plugin.stars() != null && plugin.stars().enabled()) {
                legends.set("holder." + key(entry.ref()) + ".stars", plugin.stars().halvesOf(contents[i]));
            }
        }
        // time with the legend in use: weapon in the main hand, relic in its slot
        ItemManager.Entry hand = items.weaponOf(player.getInventory().getItemInMainHand());
        if (hand != null) {
            add(player, hand.ref(), "seconds", 2);
        }
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemManager.Entry relic = items.relicOf(player.getInventory().getItem(slot.equipment));
            if (relic != null && relic.relic.slot() == slot) {
                add(player, relic.ref(), "seconds", 2);
            }
        }
    }

    /**
     * One console line with who holds which legend (for the logs / the morning check):
     * "[Legends] holders: weapon:echo_blade=Steve (3/5, online), ... | forging: ... | free: ...".
     */
    public void logHolders() {
        // who carries what right now (online players), so copies held by two players at once show up
        java.util.Map<String, List<String>> carriedBy = new java.util.HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            java.util.Set<String> mine = new java.util.HashSet<>();
            for (ItemStack stack : p.getInventory().getContents()) {
                ItemManager.Entry entry = items.customOf(stack);
                if (isLegend(entry) && mine.add(entry.ref())) {
                    carriedBy.computeIfAbsent(entry.ref(), k -> new ArrayList<>()).add(p.getName());
                }
            }
        }
        java.util.Map<String, String> stolen = plugin.ghost() != null ? plugin.ghost().stolenRefs() : java.util.Map.of();
        List<String> held = new ArrayList<>();
        List<String> forging = new ArrayList<>();
        List<String> free = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ItemManager.Entry entry : loggedLegends(carriedBy.keySet())) {
            ForgeData.Claim claim = plugin.rituals().claim(entry);
            if (claim != null && claim.state() == ForgeData.State.RITUAL) {
                forging.add(entry.ref() + " (" + claim.name() + ")");
                continue;
            }
            String base = "holder." + key(entry.ref());
            List<String> online = carriedBy.getOrDefault(entry.ref(), List.of());
            if (stolen.containsKey(entry.ref()) && online.isEmpty()) {
                held.add(entry.ref() + "=the copy (stolen from " + stolen.get(entry.ref()) + ")");
                continue;
            }
            boolean known = legends.isSet(base + ".name");
            if (claim == null && online.isEmpty() && !known) {
                free.add(entry.ref());
                continue;
            }
            StringBuilder sb = new StringBuilder(entry.ref()).append('=');
            if (online.size() > 1) {
                sb.append(String.join(" + ", online)).append(" (COPIES: ").append(online.size()).append(" players have one");
            } else {
                String name = !online.isEmpty() ? online.getFirst() : legends.getString(base + ".name", claim == null ? "?" : claim.name());
                sb.append(name).append(" (");
                if (legends.isSet(base + ".stars")) {
                    sb.append(Stars.starCount(legends.getInt(base + ".stars"))).append("/5, ");
                }
                long seen = legends.getLong(base + ".seen", claim == null ? now : claim.time());
                sb.append(!online.isEmpty() ? "online" : "seen " + RitualManager.format(Math.max(0, now - seen)) + " ago");
            }
            if (claim == null) {
                sb.append(", given by an admin");                   // nobody forged it: /forge give
            }
            held.add(sb.append(')').toString());
        }
        plugin.getLogger().info("[Legends] holders: " + (held.isEmpty() ? "nobody" : String.join(", ", held))
                + " | forging: " + (forging.isEmpty() ? "-" : String.join(", ", forging))
                + " | free: " + (free.isEmpty() ? "-" : String.join(", ", free)));
    }

    /** The legend is gone (destroyed, released): nobody holds it any more. */
    public void forgetHolder(String ref) {
        legends.set("holder." + key(ref), null);
        saveLegends();
    }

    /** Legends whose holder hasn't been online for a long time go back to the forge. */
    private void checkAbandoned() {
        int days = plugin.getConfig().getInt("legends.inactive-days", 7);
        if (days <= 0) {
            return;
        }
        long limit = days * 86_400_000L;
        long now = System.currentTimeMillis();
        for (ItemManager.Entry entry : allLegends()) {
            ForgeData.Claim claim = plugin.rituals().claim(entry);
            if (claim == null || claim.state() != ForgeData.State.FORGED) {
                continue;
            }
            String uuid = legends.getString("holder." + key(entry.ref()) + ".uuid", claim.player().toString());
            OfflinePlayer holder = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            if (holder.isOnline()) {
                continue;
            }
            long seen = Math.max(holder.getLastSeen(), legends.getLong("holder." + key(entry.ref()) + ".seen", 0));
            if (seen <= 0) {
                seen = claim.time();
            }
            if (now - seen < limit) {
                continue;
            }
            plugin.rituals().release(entry.ref());
            revoke(entry.ref());
            Bukkit.broadcast(plugin.msg("legend-abandoned",
                    "&7{item} &7was abandoned for {days} days and returned to the forge. It can be forged again!",
                    "item", ItemManager.nameOf(entry.item), "days", String.valueOf(days)));
            plugin.getLogger().info("[Rituals] " + entry.ref() + " returned to the forge (holder " + holder.getName() + " inactive)");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !(event.getInventory().getHolder() instanceof LegendsHolder)) {
            Bukkit.getScheduler().runTask(plugin, () -> sweep(event.getInventory(), player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        ItemManager.Entry entry = items.customOf(stack);
        if (event.getEntity() instanceof Player player && isLegend(entry) && !valid(stack, entry)) {
            event.setCancelled(true);
            event.getItem().remove();
            player.sendMessage(plugin.msg("legend-crumbled", "&7Your {item} &7crumbled to dust - it went back to the forge.",
                    "item", ItemManager.nameOf(entry.item)));
        }
    }

    /** Legends made with a vanilla crafting recipe also get a serial number. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemManager.Entry entry = items.customOf(event.getCurrentItem());
        if (isLegend(entry)) {
            ItemStack result = event.getCurrentItem().clone();
            stamp(result, entry.ref());
            event.setCurrentItem(result);
        }
    }

    // ================================================================ stats

    /** One stat (kills, mob-kills, damage...) summed over all legends, per player. */
    public java.util.Map<java.util.UUID, Double> totals(String stat) {
        java.util.Map<java.util.UUID, Double> map = new java.util.HashMap<>();
        for (String uuid : stats.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection mine = stats.getConfigurationSection(uuid);
            if (mine == null) {
                continue;
            }
            double sum = 0;
            for (String ref : mine.getKeys(false)) {
                sum += mine.getDouble(ref + "." + stat, 0);
            }
            if (sum > 0) {
                try {
                    map.put(java.util.UUID.fromString(uuid), sum);
                } catch (IllegalArgumentException ignored) {
                    // not a player entry
                }
            }
        }
        return map;
    }

    private void add(Player player, String ref, String stat, double amount) {
        String path = player.getUniqueId() + "." + key(ref) + "." + stat;
        stats.set(path, stats.getDouble(path, 0) + amount);
        statsDirty = true;
    }

    /** Called by AbilityManager when an ability fires. */
    public void ability(Player player, String ref, boolean shift) {
        add(player, ref, shift ? "ability-shift-f" : "ability-f", 1);
    }

    private ItemManager.Entry weaponOfDamager(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return items.weaponOf(player.getInventory().getItemInMainHand());
        }
        if (damager instanceof Projectile projectile) {
            String ref = projectile.getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
            return ref == null ? null : items.get(ref);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        if (attacker == null || attacker.equals(event.getEntity()) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        ItemManager.Entry weapon = weaponOfDamager(event.getDamager());
        if (weapon != null) {
            add(attacker, weapon.ref(), "damage", event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) {
            return;
        }
        ItemManager.Entry weapon = items.weaponOf(killer.getInventory().getItemInMainHand());
        if (weapon == null) {
            return;
        }
        add(killer, weapon.ref(), event.getEntity() instanceof Player ? "kills" : "mob-kills", 1);
        if (plugin.getConfig().getBoolean("kill-effects", true)) {
            KillEffects.play(weapon.id, event.getEntity());
        }
    }

    // ================================================================ /legends

    private static final class LegendsHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** For the console's holder list: the legends of the game + any that someone has (e.g. given by an admin). */
    private List<ItemManager.Entry> loggedLegends(java.util.Set<String> carried) {
        List<ItemManager.Entry> list = new ArrayList<>(allLegends());
        for (ItemManager.Kind kind : new ItemManager.Kind[] {ItemManager.Kind.WEAPON, ItemManager.Kind.RELIC}) {
            for (ItemManager.Entry entry : items.all(kind)) {
                if (!list.contains(entry) && (carried.contains(entry.ref())
                        || legends.isSet("holder." + key(entry.ref()) + ".name"))) {
                    list.add(entry);
                }
            }
        }
        return list;
    }

    /** Legends that are part of the game right now (forgeable, or already out there). */
    private List<ItemManager.Entry> allLegends() {
        List<ItemManager.Entry> list = new ArrayList<>();
        for (ItemManager.Kind kind : new ItemManager.Kind[] {ItemManager.Kind.WEAPON, ItemManager.Kind.RELIC}) {
            for (ItemManager.Entry entry : items.all(kind)) {
                if (items.craftable(entry) || plugin.rituals().claim(entry) != null) {
                    list.add(entry);
                }
            }
        }
        return list;
    }

    public boolean onCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("player-only", "&cOnly players can use this command."));
            return true;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        LegendsHolder holder = new LegendsHolder();
        holder.inventory = Bukkit.createInventory(holder, 54,
                ItemManager.color(plugin.getConfig().getString("legends.title", "&8Legends")));
        Inventory inv = holder.inventory;
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }
        List<ItemManager.Entry> list = allLegends();
        int weapons = (int) list.stream().filter(e -> e.kind == ItemManager.Kind.WEAPON).count();
        int slot = 0;
        for (int i = 0; i < list.size() && slot < 45; i++) {
            if (i == weapons) {
                slot = ((slot + 8) / 9) * 9;                     // relics start on a new row
            }
            inv.setItem(slot++, icon(player, list.get(i)));
        }
        inv.setItem(49, summary(player));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    private ItemStack icon(Player player, ItemManager.Entry entry) {
        ItemStack icon = entry.item.clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        ForgeData.Claim claim = plugin.rituals().claim(entry);
        long ritual = plugin.rituals().remaining(entry.ref());
        if (ritual >= 0) {
            lore.add(ItemManager.color("&5⚒ Being forged &8(" + RitualManager.format(ritual) + " left)"));
        } else if (claim != null) {
            lore.add(ItemManager.color("&c🔒 Owned by &f" + plugin.rituals().publicName(claim.name())));
        } else {
            lore.add(ItemManager.color("&a✔ Can be forged"));
        }
        ConfigurationSection mine = stats.getConfigurationSection(player.getUniqueId() + "." + key(entry.ref()));
        if (mine != null) {
            lore.add(Component.empty());
            lore.add(ItemManager.color("&6&lYour stats"));
            if (entry.kind == ItemManager.Kind.WEAPON) {
                lore.add(ItemManager.color("&7Player kills: &f" + (long) mine.getDouble("kills")));
                lore.add(ItemManager.color("&7Mob kills: &f" + (long) mine.getDouble("mob-kills")));
                lore.add(ItemManager.color("&7Damage dealt: &f" + Math.round(mine.getDouble("damage") / 2) + " ❤"));
                lore.add(ItemManager.color("&7Abilities used: &f" + (long) mine.getDouble("ability-f") + " &8(F) &7· &f"
                        + (long) mine.getDouble("ability-shift-f") + " &8(Shift+F)"));
                lore.add(ItemManager.color("&7Time in hand: &f" + duration(mine.getDouble("seconds"))));
            } else {
                lore.add(ItemManager.color("&7Time worn: &f" + duration(mine.getDouble("seconds"))));
            }
        } else {
            lore.add(Component.empty());
            lore.add(ItemManager.color("&8You haven't used this one yet."));
        }
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack summary(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(ItemManager.color("&6&l" + player.getName()));
        double kills = 0;
        double damage = 0;
        double seconds = 0;
        int used = 0;
        ConfigurationSection all = stats.getConfigurationSection(player.getUniqueId().toString());
        if (all != null) {
            for (String ref : all.getKeys(false)) {
                ConfigurationSection s = all.getConfigurationSection(ref);
                if (s == null) {
                    continue;
                }
                used++;
                kills += s.getDouble("kills");
                damage += s.getDouble("damage");
                seconds += s.getDouble("seconds");
            }
        }
        meta.lore(List.of(
                ItemManager.color("&7Legends used: &f" + used),
                ItemManager.color("&7Player kills with legends: &f" + (long) kills),
                ItemManager.color("&7Damage with legends: &f" + Math.round(damage / 2) + " ❤"),
                ItemManager.color("&7Time with legends: &f" + duration(seconds))));
        head.setItemMeta(meta);
        return head;
    }

    private static String duration(double seconds) {
        long s = (long) seconds;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LegendsHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LegendsHolder) {
            event.setCancelled(true);
        }
    }
}
