package cz.forgebound;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Weapon stars (★ to ★★★★★, kept in half steps) and the Ancient Forges where they are earned.
 * <ul>
 *   <li>A freshly forged legend has ★ (weaker than before stars existed), ★★★ is the old strength, ★★★★★ the top.
 *       Stars change the weapon's damage (melee, projectiles and abilities) and its ability cooldowns.</li>
 *   <li>Weapons forged before stars existed start at ★★★.</li>
 *   <li>Killed by a player: every legend weapon you carry loses 2 stars. Any other death: ½ star. Never below ★.</li>
 *   <li>An Ancient Forge (rare: in the Scorched Lands and in the Nether) raises a weapon by one star with a ritual.
 *       The weapon stays in the forge until the ritual ends and then drops there. Afterwards the forge cools down.</li>
 * </ul>
 */
public final class StarManager implements Listener {

    private static final Particle.DustOptions SOUL = new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.4f);
    private static final Particle.DustOptions RITUAL = new Particle.DustOptions(Color.fromRGB(255, 190, 60), 1.4f);

    private final Forgebound plugin;
    private final ItemManager items;
    private final NamespacedKey starsKey = Forgebound.key("weapon_stars");
    private final NamespacedKey killsKey = Forgebound.key("weapon_kills");
    private final File file;
    /** Ancient Forges found by world generation (on other threads), waiting to be registered on the main thread. */
    private final Queue<Site> found = new ConcurrentLinkedQueue<>();
    private final Map<String, Site> sites = new LinkedHashMap<>();
    private final Map<String, Running> running = new LinkedHashMap<>();
    private int ticks;

    static final class Site {
        final String world;
        final int x;
        final int y;
        final int z;
        long readyAt;

        Site(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        String id() {
            return world + ";" + x + ";" + y + ";" + z;
        }

        Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private static final class Running {
        Site site;
        ItemStack weapon;
        int target;
        long endsAt;
        long total;
        UUID player;
        String name;
        BossBar bar;
        ItemDisplay display;
    }

    public StarManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.file = new File(plugin.getDataFolder(), "ancient-forges.yml");
    }

    // ================================================================ settings and pure helpers

    public boolean enabled() {
        return plugin.getConfig().getBoolean("stars.enabled", true);
    }

    private double perStar() {
        return plugin.getConfig().getDouble("stars.percent-per-star", 7.5);
    }

    // ================================================================ stars on items

    /** A weapon that has stars (mythic event weapons don't). */
    private ItemManager.Entry starWeapon(ItemStack stack) {
        ItemManager.Entry entry = items.weaponOf(stack);
        return entry == null || entry.mythic ? null : entry;
    }

    /** The weapon's stars in halves (2 = ★, 10 = ★★★★★). Weapons from before stars count as legacy-stars. */
    public int halvesOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 6;
        }
        Integer h = stack.getPersistentDataContainer().get(starsKey, PersistentDataType.INTEGER);
        return h != null ? Stars.clamp(h) : Stars.clamp((int) Math.round(plugin.getConfig().getDouble("stars.legacy-stars", 3) * 2));
    }

    /** Sets the stars and rewrites the name and lore. */
    public void setHalves(ItemStack stack, ItemManager.Entry entry, int halves) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(starsKey, PersistentDataType.INTEGER, Stars.clamp(halves));
        stack.setItemMeta(meta);
        decorate(stack, entry);
    }

    /** A weapon that just came out of the forge ritual. */
    public void initForged(ItemStack stack, ItemManager.Entry entry) {
        if (enabled() && entry.kind == ItemManager.Kind.WEAPON) {
            setHalves(stack, entry, (int) Math.round(plugin.getConfig().getDouble("stars.new-stars", 1) * 2));
        }
    }

    /** Gives an old weapon its stars (once). Returns true if the stack changed. */
    public boolean ensure(ItemStack stack) {
        if (!enabled() || stack == null || !stack.hasItemMeta()
                || stack.getPersistentDataContainer().has(starsKey, PersistentDataType.INTEGER)) {
            return false;
        }
        ItemManager.Entry entry = starWeapon(stack);
        if (entry == null) {
            return false;
        }
        setHalves(stack, entry, halvesOf(stack));
        return true;
    }

    /** Name with the stars after it; lore = the weapon's lore + kills (if counted) + stars and what they do. */
    public void decorate(ItemStack stack, ItemManager.Entry entry) {
        ItemMeta meta = stack.getItemMeta();
        ItemMeta template = entry.item.getItemMeta();
        int halves = halvesOf(stack);
        Component name = template.hasDisplayName() ? template.displayName() : ItemManager.nameOf(entry.item);
        if (enabled()) {
            name = name.append(ItemManager.color(" " + Stars.starText(halves)));
        }
        meta.displayName(name);
        List<Component> lore = new ArrayList<>(template.lore() == null ? List.of() : template.lore());
        int kills = meta.getPersistentDataContainer().getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
        if (kills > 0) {
            lore.add(ItemManager.color("&6⚔ Kills: &f" + kills));
        }
        if (enabled()) {
            double dmg = (Stars.damageMultiplier(halves, perStar()) - 1) * 100;
            double cd = (Stars.cooldownMultiplier(halves, perStar()) - 1) * 100;
            lore.add(ItemManager.color("&8Stars: " + Stars.starText(halves) + " &8(" + Stars.starCount(halves) + "/5)"));
            lore.add(ItemManager.color("&7Damage " + signed(dmg) + " &8· &7Cooldowns " + signed(cd)));
            if (halves < 10) {
                lore.add(ItemManager.color("&8More stars at an Ancient Forge."));
            }
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
    }

    private static String signed(double percent) {
        long p = Math.round(percent);
        return p == 0 ? "&f±0%" : p > 0 ? "&a+" + p + "%" : "&c" + p + "%";
    }

    public double cooldownMultiplier(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!enabled() || starWeapon(hand) == null) {
            return 1;
        }
        return Stars.cooldownMultiplier(halvesOf(hand), perStar());
    }

    // ================================================================ old weapons get their stars when seen

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ItemStack[] contents = event.getPlayer().getInventory().getContents();
        boolean changed = false;
        for (ItemStack stack : contents) {
            changed |= ensure(stack);
        }
        if (changed) {
            event.getPlayer().getInventory().setContents(contents);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack stack = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (ensure(stack)) {
            event.getPlayer().getInventory().setItem(event.getNewSlot(), stack);
        }
    }

    // ================================================================ damage

    /** All damage a player deals while holding a legend (hits, arrows, abilities) scales with its stars. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled()) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!(damager instanceof Player player) || player.equals(event.getEntity())) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (starWeapon(hand) == null) {
            return;
        }
        event.setDamage(event.getDamage() * Stars.damageMultiplier(halvesOf(hand), perStar()));
    }

    // ================================================================ dying costs stars

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled()) {
            return;
        }
        Player player = event.getEntity();
        Player killer = player.getKiller();
        boolean pvp = killer != null && !killer.equals(player);
        int loss = (int) Math.round(plugin.getConfig().getDouble(pvp ? "stars.lose-on-player-kill" : "stars.lose-on-other-death",
                pvp ? 2 : 0.5) * 2);
        if (loss <= 0) {
            return;
        }
        List<Lost> lost = new ArrayList<>();
        lostFromFive = false;
        for (ItemStack stack : event.getDrops()) {
            lose(stack, loss, lost);
        }
        if (event.getKeepInventory()) {
            ItemStack[] contents = player.getInventory().getContents();
            for (ItemStack stack : contents) {
                lose(stack, loss, lost);
            }
            player.getInventory().setContents(contents);
        }
        if (lost.isEmpty()) {
            return;
        }
        if (lostFromFive && plugin.advancements() != null) {
            if (pvp) {
                plugin.award(killer, "starfall");
            } else {
                plugin.award(player, "hubris");
            }
        }
        String amount = Stars.starCount(loss) + (loss == 2 ? " star" : " stars");
        for (Lost l : lost) {
            plugin.getLogger().info("[Stars] " + player.getName() + " lost " + amount + " on " + l.ref() + " (now "
                    + Stars.starCount(l.halves()) + "/5) - " + (pvp ? "killed by " + killer.getName() : "died"));
            if (pvp) {
                Bukkit.broadcast(plugin.msg("stars-lost-pvp", "&c☠ {item} &7of &c{player} &7lost &c{amount} &7to &c{killer}&7: {stars}",
                        "item", l.name(), "player", player.getName(), "amount", amount,
                        "killer", killer.getName(), "stars", ItemManager.color(Stars.starText(l.halves()))));
            } else {
                player.sendMessage(plugin.msg("stars-lost", "&7Your {item} &7lost &c{amount}&7: {stars}",
                        "item", l.name(), "amount", amount, "stars", ItemManager.color(Stars.starText(l.halves()))));
            }
        }
    }

    private record Lost(Component name, String ref, int halves) { }

    /** Set while a death is processed: one of the weapons had five stars before it lost some. */
    private boolean lostFromFive;

    private void lose(ItemStack stack, int loss, List<Lost> lost) {
        ItemManager.Entry entry = starWeapon(stack);
        if (entry == null) {
            return;
        }
        int before = halvesOf(stack);
        int after = Stars.clamp(before - loss);
        if (before >= 10 && after < before) {
            lostFromFive = true;
        }
        if (after != before) {
            setHalves(stack, entry, after);
            lost.add(new Lost(ItemManager.nameOf(entry.item), entry.ref(), after));
        } else {
            setHalves(stack, entry, before);
        }
    }

    // ================================================================ Ancient Forges: registry

    /** The anvils of all known Ancient Forges in this world (x, y, z): their area is protected. */
    public java.util.List<int[]> forgeAnvils(World world) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (Site s : sites.values()) {
            if (s.world.equals(world.getName())) {
                out.add(new int[]{s.x, s.y, s.z});
            }
        }
        return out;
    }

    /** Is this block the anvil of an Ancient Forge? */
    public boolean isForgeAnvil(org.bukkit.block.Block block) {
        return sites.containsKey(block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ());
    }

    /** Called by world generation (any thread). */
    public void found(String world, int x, int y, int z) {
        found.add(new Site(world, x, y, z));
    }

    public void start() {
        load();
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 40L, 10L);
    }

    public void shutdown() {
        drainFound();
        save();
        for (Running r : running.values()) {
            removeVisuals(r);
        }
        running.clear();
    }

    private void drainFound() {
        boolean changed = false;
        Site s;
        while ((s = found.poll()) != null) {
            if (sites.putIfAbsent(s.id(), s) == null) {
                changed = true;
                plugin.getLogger().info("[Stars] An Ancient Forge generated at " + s.world + " " + s.x + " " + s.y + " " + s.z + ".");
            }
        }
        if (changed) {
            save();
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("forges");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection f = sec.getConfigurationSection(key);
                if (f == null) {
                    continue;
                }
                Site site = new Site(f.getString("world", "world"), f.getInt("x"), f.getInt("y"), f.getInt("z"));
                site.readyAt = f.getLong("ready-at", 0);
                sites.put(site.id(), site);
            }
        }
        ConfigurationSection rit = yaml.getConfigurationSection("rituals");
        if (rit != null) {
            for (String key : rit.getKeys(false)) {
                ConfigurationSection f = rit.getConfigurationSection(key);
                Site site = f == null ? null : sites.get(f.getString("site", ""));
                ItemStack weapon = f == null ? null : f.getItemStack("weapon");
                if (site == null || weapon == null) {
                    continue;
                }
                Running r = new Running();
                r.site = site;
                r.weapon = weapon;
                r.target = f.getInt("target");
                r.total = f.getLong("total");
                r.endsAt = System.currentTimeMillis() + f.getLong("remaining");
                r.player = UUID.fromString(f.getString("player", new UUID(0, 0).toString()));
                r.name = f.getString("name", "?");
                running.put(site.id(), r);
                spawnVisuals(r);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (Site s : sites.values()) {
            String p = "forges.f" + (i++);
            yaml.set(p + ".world", s.world);
            yaml.set(p + ".x", s.x);
            yaml.set(p + ".y", s.y);
            yaml.set(p + ".z", s.z);
            yaml.set(p + ".ready-at", s.readyAt);
        }
        i = 0;
        long now = System.currentTimeMillis();
        for (Running r : running.values()) {
            String p = "rituals.r" + (i++);
            yaml.set(p + ".site", r.site.id());
            yaml.set(p + ".weapon", r.weapon);
            yaml.set(p + ".target", r.target);
            yaml.set(p + ".total", r.total);
            yaml.set(p + ".remaining", Math.max(0, r.endsAt - now));
            yaml.set(p + ".player", r.player.toString());
            yaml.set(p + ".name", r.name);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save ancient-forges.yml: " + ex.getMessage());
        }
    }

    private Site siteAt(Block block) {
        return sites.get(block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ());
    }

    // ================================================================ Ancient Forges: using one

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Site site = siteAt(event.getClickedBlock());
        if (site == null) {
            return;
        }
        event.setCancelled(true);                              // never the vanilla anvil screen
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!enabled()) {
            player.sendMessage(plugin.msg("stars-disabled", "&7The Ancient Forge is cold. (Stars are turned off.)"));
            return;
        }
        Running r = running.get(site.id());
        if (r != null) {
            player.sendMessage(plugin.msg("ancient-busy", "&6✦ {item} &7is ascending here. &e{time} &7left.",
                    "item", ItemManager.nameOf(r.weapon), "time", RitualManager.format(r.endsAt - System.currentTimeMillis())));
            return;
        }
        long cooling = site.readyAt - System.currentTimeMillis();
        if (cooling > 0) {
            player.sendMessage(plugin.msg("ancient-cooling", "&7The Ancient Forge is still cooling down: &c{time}&7.",
                    "time", RitualManager.format(cooling)));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemManager.Entry weapon = starWeapon(hand);
        if (weapon == null) {
            player.sendMessage(plugin.msg("ancient-hint", "&6✦ Ancient Forge &7- use the anvil while holding a legend weapon to give it another star."));
            return;
        }
        ensure(hand);
        int halves = halvesOf(hand);
        if (halves >= 10) {
            player.sendMessage(plugin.msg("ancient-max", "&7{item} &7already has all five stars.", "item", ItemManager.nameOf(weapon.item)));
            return;
        }
        int cap = maxHalves(site);
        if (halves >= cap) {
            player.sendMessage(plugin.msg("ancient-cap", "&7This forge can't make a weapon stronger than {stars}&7. {next}",
                    "stars", ItemManager.color(Stars.starText(cap)), "next", ItemManager.color(nextForge(cap))));
            return;
        }
        Offer offer = offer(weapon, halves, cap);
        if (offer == null) {
            player.sendMessage(ItemManager.color("&cThe star costs in config.yml (stars.costs) are broken - ask an admin."));
            return;
        }
        List<io.papermc.paper.registry.data.dialog.body.DialogBody> body = new ArrayList<>();
        body.add(Dialogs.item(hand));
        body.add(Dialogs.text("&7" + Stars.starText(halves) + " &7→ " + Stars.starText(offer.target)
                + "\n&8This forge goes up to " + Stars.starCount(cap) + " stars."));
        Component cost = ItemManager.color("&7Costs:");
        for (ItemManager.Cost c : offer.costs) {
            int have = items.count(player.getInventory(), c);
            cost = cost.append(Component.newline())
                    .append(ItemManager.color((have >= c.amount() ? "&a✔ " : "&c✘ ") + "&f" + c.amount() + "x "))
                    .append(c.display().effectiveName())
                    .append(ItemManager.color(" &8(" + have + ")"));
        }
        if (offer.tokens > 0 && plugin.bounty() != null) {
            int have = plugin.bounty().countTokens(player);
            cost = cost.append(Component.newline()).append(ItemManager.color((have >= offer.tokens ? "&a✔ " : "&c✘ ")
                    + "&f" + offer.tokens + "x &6Bounty Token &8(" + have + ")"));
        }
        body.add(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(cost, 260));
        body.add(Dialogs.text("&7The ritual takes &e" + RitualManager.format(offer.millis) + "&7 (only while the server runs). "
                + "The weapon stays in the forge until it ends, then it drops here &c- whoever is here can take it&7. "
                + "Afterwards this forge cools down for &e" + RitualManager.format(cooldownMs()) + "&7."));
        Dialogs.confirm(plugin, player, "&6&lAncient Forge", body, "&6Ascend", "&7Not now", () -> begin(player, site, weapon));
    }

    private record Offer(int target, List<ItemManager.Cost> costs, int tokens, long millis) { }

    /** What the next star costs: stars.costs.<star the ritual reaches>. "material:own" = the weapon's own material. */
    private Offer offer(ItemManager.Entry weapon, int halves, int cap) {
        int target = Math.min(cap, Stars.clamp(halves + 2));
        int star = (target + 1) / 2;
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("stars.costs." + star);
        if (sec == null) {
            return null;
        }
        String own = null;
        for (ItemManager.Cost c : weapon.cost) {
            if (c.ref().toLowerCase(java.util.Locale.ROOT).startsWith("material:")) {
                own = c.ref();
            }
        }
        List<Map<?, ?>> maps = new ArrayList<>();
        for (Map<?, ?> m : sec.getMapList("items")) {
            if ("material:own".equalsIgnoreCase(String.valueOf(m.get("item")))) {
                if (own == null) {
                    continue;
                }
                Map<Object, Object> copy = new LinkedHashMap<>(m);
                copy.put("item", own);
                maps.add(copy);
            } else {
                maps.add(m);
            }
        }
        try {
            return new Offer(target, items.costs(maps), sec.getInt("tokens", 0),
                    (long) (sec.getDouble("minutes", 10) * 60_000));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("stars.costs." + star + ": " + ex.getMessage());
            return null;
        }
    }

    private long cooldownMs() {
        return (long) (plugin.getConfig().getDouble("ancient-forge.cooldown-hours", 24) * 3_600_000);
    }

    private void begin(Player player, Site site, ItemManager.Entry expected) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemManager.Entry weapon = starWeapon(hand);
        Location core = site.location();
        if (weapon == null || weapon != expected || core == null || running.containsKey(site.id())
                || site.readyAt > System.currentTimeMillis() || !player.getWorld().equals(core.getWorld())
                || player.getLocation().distanceSquared(core) > 64) {
            player.sendMessage(plugin.msg("ancient-cancelled", "&7The forge didn't take it. Stand at the anvil with the weapon in your hand."));
            return;
        }
        Offer offer = offer(weapon, halvesOf(hand), maxHalves(site));
        if (offer == null || halvesOf(hand) >= maxHalves(site)) {
            return;
        }
        boolean tokensOk = offer.tokens <= 0 || (plugin.bounty() != null && plugin.bounty().countTokens(player) >= offer.tokens);
        if (!items.canAfford(player.getInventory(), offer.costs) || !tokensOk) {
            player.sendMessage(plugin.msg("ancient-cant-afford", "&cYou don't have everything the forge asks for."));
            return;
        }
        items.take(player.getInventory(), offer.costs);
        if (offer.tokens > 0) {
            plugin.bounty().takeTokens(player, offer.tokens);
        }
        player.getInventory().setItemInMainHand(null);
        Running r = new Running();
        r.site = site;
        r.weapon = hand;
        r.target = offer.target;
        r.total = offer.millis;
        r.endsAt = System.currentTimeMillis() + offer.millis;
        r.player = player.getUniqueId();
        r.name = player.getName();
        running.put(site.id(), r);
        spawnVisuals(r);
        save();
        World world = core.getWorld();
        Location c = core.clone().add(0.5, 1, 0.5);
        world.strikeLightningEffect(c);
        world.playSound(c, Sound.BLOCK_ANVIL_USE, 1.5f, 0.5f);
        world.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.7f);
        Bukkit.broadcast(plugin.msg("ancient-started",
                "&6✦ An Ancient Forge {where} &6has awakened: &f{item} &7is ascending to {stars}&7. Ready in &e{time}&7.",
                "where", ItemManager.color(where(site)), "item", ItemManager.nameOf(weapon.item), "stars", ItemManager.color(Stars.starText(r.target)),
                "time", RitualManager.format(r.total)));
        plugin.getLogger().info("[Stars] " + player.getName() + " started an ascension of " + weapon.ref() + " ("
                + Stars.starCount(halvesOf(hand)) + " -> " + Stars.starCount(r.target) + " stars) at " + site.id()
                + ", ready in " + RitualManager.format(r.total));
    }

    private String where(Site site) {
        World w = Bukkit.getWorld(site.world);
        World.Environment env = w == null ? World.Environment.NORMAL : w.getEnvironment();
        return env == World.Environment.NETHER ? "&cin the Nether" : env == World.Environment.THE_END ? "&5in the End"
                : "&6in the Scorched Lands";
    }

    /** How far this forge can raise a weapon (halves): overworld ★★, Nether ★★★, End ★★★★★ by default. */
    private int maxHalves(Site site) {
        World w = Bukkit.getWorld(site.world);
        World.Environment env = w == null ? World.Environment.NORMAL : w.getEnvironment();
        String key = env == World.Environment.NETHER ? "nether" : env == World.Environment.THE_END ? "end" : "overworld";
        double def = env == World.Environment.NETHER ? 3 : env == World.Environment.THE_END ? 5 : 2;
        return Stars.clamp((int) Math.round(plugin.getConfig().getDouble("ancient-forge.max-stars." + key, def) * 2));
    }

    private String nextForge(int cap) {
        double nether = plugin.getConfig().getDouble("ancient-forge.max-stars.nether", 3) * 2;
        return cap < nether ? "&7Find the Ancient Forge &cin the Nether&7." : "&7Only the Ancient Forge &5in the End &7goes higher.";
    }

    private void complete(Running r) {
        running.remove(r.site.id());
        removeVisuals(r);
        Location core = r.site.location();
        ItemManager.Entry entry = starWeapon(r.weapon);
        if (entry != null) {
            setHalves(r.weapon, entry, r.target);
        }
        r.site.readyAt = System.currentTimeMillis() + cooldownMs();
        save();
        plugin.getLogger().info("[Stars] " + (entry == null ? "a weapon" : entry.ref()) + " ascended to "
                + Stars.starCount(r.target) + "/5 at " + r.site.id() + " (started by " + r.name + ")");
        if (core == null) {
            plugin.getLogger().warning("Ascension finished but the world " + r.site.world + " is gone; the weapon is in ancient-forges.yml.");
            return;
        }
        World world = core.getWorld();
        Location c = core.clone().add(0.5, 1.3, 0.5);
        world.dropItem(c, r.weapon, item -> {
            item.setVelocity(new org.bukkit.util.Vector(0, 0.2, 0));
            item.setUnlimitedLifetime(true);
            item.setInvulnerable(true);
            item.setGlowing(true);
            item.setCanMobPickup(false);
        });
        world.strikeLightningEffect(c);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, c, 80, 0.4, 0.8, 0.4, 0.4);
        world.spawnParticle(Particle.DUST, c, 60, 0.8, 1, 0.8, 0, RITUAL);
        world.playSound(c, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 0.9f);
        Bukkit.broadcast(plugin.msg("ancient-done", "&6✦ {item} &7has ascended to {stars} &7at an Ancient Forge {where}&7!",
                "item", ItemManager.nameOf(r.weapon), "stars", ItemManager.color(Stars.starText(r.target)), "where", ItemManager.color(where(r.site))));
        if (plugin.advancements() != null) {
            plugin.advancements().awardLater(r.player, "ascension");
            World fw = Bukkit.getWorld(r.site.world);
            plugin.advancements().progress(r.player, "pilgrim",
                    fw == null ? "overworld" : fw.getEnvironment().name().toLowerCase(java.util.Locale.ROOT), 3);
            if (r.target >= 10) {
                plugin.advancements().awardLater(r.player, "five_stars");
            }
        }
    }

    // ================================================================ the End forge rises when the dragon dies

    private boolean hasEndForge() {
        for (Site s : sites.values()) {
            World w = Bukkit.getWorld(s.world);
            if (w != null && w.getEnvironment() == World.Environment.THE_END) {
                return true;
            }
        }
        return raising;
    }

    private boolean raising;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.EnderDragon
                && event.getEntity().getWorld().getEnvironment() == World.Environment.THE_END) {
            World end = event.getEntity().getWorld();
            Bukkit.getScheduler().runTaskLater(plugin, () -> raiseEnd(end, null), 20L * 12);   // after the death animation
        }
    }

    /** Servers where the dragon died before this version: the forge rises when someone comes to the End. */
    @EventHandler
    public void onEnterEnd(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        World end = event.getPlayer().getWorld();
        if (end.getEnvironment() == World.Environment.THE_END && end.getEnderDragonBattle() != null
                && end.getEnderDragonBattle().hasBeenPreviouslyKilled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> raiseEnd(end, null), 20L * 5);
        }
    }

    /** Raises the End's Ancient Forge on a floating island near the main island (once). */
    public void raiseEnd(World end, CommandSender admin) {
        if (!enabled() || !plugin.getConfig().getBoolean("ancient-forge.end.enabled", true) || hasEndForge()) {
            if (admin != null) {
                admin.sendMessage(ItemManager.color("&7The End already has its Ancient Forge (or it's turned off)."));
            }
            return;
        }
        raising = true;
        java.util.SplittableRandom r = new java.util.SplittableRandom(end.getSeed() ^ 0xE4DF0L);
        double angle = r.nextDouble() * Math.PI * 2;
        double dist = plugin.getConfig().getDouble("ancient-forge.end.distance", 110);
        int cx = (int) Math.round(Math.cos(angle) * dist);
        int cz = (int) Math.round(Math.sin(angle) * dist);
        int y0 = plugin.getConfig().getInt("ancient-forge.end.height", 70);
        int x0 = cx - 12;
        int z0 = cz - 12;
        for (int x = x0 - 16; x <= x0 + 40; x += 16) {
            for (int z = z0 - 16; z <= z0 + 40; z += 16) {
                end.getChunkAt(x >> 4, z >> 4).load(true);
            }
        }
        // plan every block first, then let it rise out of the void layer by layer
        Map<Long, String> plan = new LinkedHashMap<>();
        List<int[]> order = new ArrayList<>();
        ForgeRuins.Canvas canvas = new ForgeRuins.Canvas() {
            @Override
            public void set(int x, int y, int z, String block) {
                long k = ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
                if (plan.put(k, block) == null) {
                    order.add(new int[] {x, y, z});
                }
            }

            @Override
            public String get(int x, int y, int z) {
                long k = ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
                String planned = plan.get(k);
                return planned != null ? planned.replaceAll("\\[.*", "") : end.getBlockAt(x, y, z).getType().getKey().toString();
            }
        };
        ForgeRuins.Result result = ForgeRuins.build(ForgeRuins.Type.ANCIENT_END, canvas, x0, y0, z0, r);
        order.sort(java.util.Comparator.comparingInt(p -> p[1]));
        Location center = new Location(end, cx + 0.5, y0 + 4, cz + 0.5);
        Bukkit.broadcast(plugin.msg("ancient-end-rising",
                "&5✦ The dragon has fallen. &dSomething ancient rises out of the void in the End..."));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 0.6f);
        }
        int perTick = Math.max(40, order.size() / 80);
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                for (int n = 0; n < perTick && i < order.size(); n++, i++) {
                    int[] p = order.get(i);
                    long k = ((long) p[0] & 0x3FFFFFF) << 38 | ((long) p[2] & 0x3FFFFFF) << 12 | (p[1] & 0xFFF);
                    end.getBlockAt(p[0], p[1], p[2]).setBlockData(Bukkit.createBlockData(plan.get(k)), false);
                }
                if (i % (perTick * 6) < perTick) {
                    end.spawnParticle(Particle.REVERSE_PORTAL, center, 60, 6, 3, 6, 0.1);
                    end.playSound(center, Sound.BLOCK_STONE_PLACE, 2f, 0.5f);
                }
                if (i < order.size()) {
                    return;
                }
                cancel();
                for (int[] chest : result.bigChests) {
                    if (end.getBlockAt(chest[0], chest[1], chest[2]).getState() instanceof org.bukkit.block.Chest c) {
                        c.setLootTable(Bukkit.getLootTable(ScorchedLands.LOOT_BIG), r.nextLong());
                        c.update(true, false);
                    }
                }
                Site site = new Site(end.getName(), result.core[0], result.core[1], result.core[2]);
                sites.put(site.id(), site);
                raising = false;
                save();
                end.strikeLightningEffect(center);
                end.spawnParticle(Particle.REVERSE_PORTAL, center, 300, 5, 3, 5, 0.3);
                end.spawnParticle(Particle.END_ROD, center, 80, 4, 3, 4, 0.05);
                for (Player p : end.getPlayers()) {
                    plugin.award(p, "out_of_the_void");
                }
                Bukkit.broadcast(plugin.msg("ancient-end-risen",
                        "&5✦ The &dAncient Forge of the End &5has risen! &7Only it can give a weapon its last stars."));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.7f);
                }
                plugin.getLogger().info("[Stars] The End's Ancient Forge rose at " + site.id());
            }
        }.runTaskTimer(plugin, 20L, 1L);
        if (admin != null) {
            admin.sendMessage(ItemManager.color("&7Raising the End forge at &f" + cx + " " + y0 + " " + cz + "&7."));
        }
    }

    // ================================================================ visuals

    private void spawnVisuals(Running r) {
        Location core = r.site.location();
        if (core == null) {
            return;
        }
        r.bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        if (core.isChunkLoaded()) {
            spawnDisplay(r, core);
        }
    }

    private void spawnDisplay(Running r, Location core) {
        r.display = core.getWorld().spawn(core.clone().add(0.5, 2.1, 0.5), ItemDisplay.class, d -> {
            d.setItemStack(r.weapon.clone());
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(255, 190, 60));
        });
    }

    private void removeVisuals(Running r) {
        if (r.bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(r.bar);
            }
        }
        if (r.display != null) {
            r.display.remove();
            r.display = null;
        }
    }

    private void tick() {
        ticks++;
        drainFound();
        if (ticks % 4 == 0) {
            buildMissing();
        }
        long now = System.currentTimeMillis();
        for (Running r : new ArrayList<>(running.values())) {
            if (now >= r.endsAt) {
                complete(r);
                continue;
            }
            Location core = r.site.location();
            if (core == null) {
                continue;
            }
            float progress = (float) Math.max(0, Math.min(1, 1 - (double) (r.endsAt - now) / Math.max(1, r.total)));
            r.bar.progress(progress);
            r.bar.name(ItemManager.color("&6✦ ").append(ItemManager.nameOf(r.weapon))
                    .append(ItemManager.color(" &7→ " + Stars.starText(r.target) + " &8· &e" + RitualManager.format(r.endsAt - now))));
            boolean loaded = core.isChunkLoaded();
            if (loaded && (r.display == null || !r.display.isValid())) {
                spawnDisplay(r, core);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean near = p.getWorld().equals(core.getWorld()) && p.getLocation().distanceSquared(core) < 48 * 48;
                if (near) {
                    p.showBossBar(r.bar);
                } else {
                    p.hideBossBar(r.bar);
                }
            }
            if (loaded) {
                World w = core.getWorld();
                Location c = core.clone().add(0.5, 2.1, 0.5);
                double a = ticks * 0.4;
                for (int i = 0; i < 3; i++) {
                    double b = a + i * 2.094;
                    w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(b) * 1.2, Math.sin(a) * 0.3, Math.sin(b) * 1.2), 1, 0, 0, 0, 0, RITUAL);
                }
                if (ticks % 4 == 0) {
                    w.playSound(c, Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
                }
            }
        }
        // a pillar of soul fire above every forge near a player: you can spot it from far away
        if (ticks % 2 == 0) {
            for (Site s : sites.values()) {
                Location core = s.location();
                if (core == null || !core.isChunkLoaded() || !anyoneNear(core, 160)) {
                    continue;
                }
                boolean busy = running.containsKey(s.id());
                boolean cold = s.readyAt > now;
                World w = core.getWorld();
                for (int y = 3; y <= 40; y += 3) {
                    Location p = core.clone().add(0.5, y, 0.5);
                    if (cold && !busy) {
                        w.spawnParticle(Particle.LARGE_SMOKE, p, 1, 0.1, 0.4, 0.1, 0.01, null, true);
                    } else {
                        w.spawnParticle(Particle.DUST, p, 1, 0.08, 0.4, 0.08, 0, busy ? RITUAL : SOUL, true);
                        if (y % 9 == 0) {
                            w.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0.1, 0.3, 0.1, 0.01, null, true);
                        }
                    }
                }
            }
        }
        if (ticks % 120 == 0 && !running.isEmpty()) {
            save();
        }
    }

    private static boolean anyoneNear(Location at, double radius) {
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(at) < radius * radius) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ the forge can't be broken

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Site site = siteAt(event.getBlock());
        if (site == null) {
            return;
        }
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE && p.hasPermission("forgebound.admin") && p.isSneaking()
                && !running.containsKey(site.id())) {
            sites.remove(site.id());
            save();
            p.sendMessage(ItemManager.color("&7Ancient Forge removed."));
            return;
        }
        event.setCancelled(true);
        p.sendActionBar(ItemManager.color("&7The Ancient Forge can't be broken."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> siteAt(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> siteAt(b) != null);
    }

    // ================================================================ admin

    /** /forge ancient: the known forges, and the guaranteed ones that haven't generated yet. */
    public void list(CommandSender sender) {
        drainFound();
        if (plugin.scorched() != null) {
            for (World w : Bukkit.getWorlds()) {
                for (int[] p : plugin.scorched().plannedForges(w)) {
                    boolean built = sites.values().stream().anyMatch(s -> s.world.equals(w.getName())
                            && Math.abs(s.x - p[0]) < 24 && Math.abs(s.z - p[1]) < 24);
                    if (!built) {
                        sender.sendMessage(ItemManager.color("&8- planned: &7" + w.getName() + " " + p[0] + " ~ " + p[1]
                                + " &8(generates when someone gets there) &e[tp]")
                                .clickEvent(ClickEvent.suggestCommand("/execute in " + w.getKey().asString() + " run tp @s "
                                        + p[0] + " " + (w.getEnvironment() == World.Environment.NETHER ? 70 : 200) + " " + p[1])));
                    }
                }
            }
        }
        if (!hasEndForge()) {
            sender.sendMessage(ItemManager.color("&8- End: &7rises when the dragon dies &8(or &e/forge ancient end&8)"));
        }
        if (sites.isEmpty()) {
            sender.sendMessage(ItemManager.color("&7No Ancient Forge has generated yet. They appear in NEW chunks (Scorched Lands, Nether). "
                    + "&e/forge ancient here &7builds one where you stand."));
            return;
        }
        long now = System.currentTimeMillis();
        sender.sendMessage(ItemManager.color("&6✦ Ancient Forges &7(" + sites.size() + ")"));
        for (Site s : sites.values()) {
            String state = running.containsKey(s.id()) ? "&eritual" : s.readyAt > now ? "&ccooling " + RitualManager.format(s.readyAt - now) : "&aready";
            sender.sendMessage(ItemManager.color("&7- &f" + s.world + " " + s.x + " " + s.y + " " + s.z + " &8· " + state + " &e[tp]")
                    .clickEvent(ClickEvent.runCommand("/execute in " + worldKey(s.world) + " run tp @s " + s.x + " " + (s.y + 1) + " " + (s.z + 2))));
        }
    }

    private static String worldKey(String world) {
        World w = Bukkit.getWorld(world);
        return w == null ? world : w.getKey().asString();
    }

    /** /forge ancient here: builds an Ancient Forge in front of the admin (the anvil 6 blocks ahead). */
    public void buildHere(Player player) {
        Location at = player.getLocation();
        Site site = build(at.getWorld(), at.getBlockX(), at.getBlockY() - 1, at.getBlockZ());
        player.teleport(new Location(at.getWorld(), site.x + 0.5, site.y - 3, site.z - 13.5, 0, 0));
        player.sendMessage(ItemManager.color("&6✦ &7Ancient Forge built. &8(sneak-break the anvil in creative to remove it)"));
    }

    /** Builds an Ancient Forge with its anvil above (cx, floor, cz) in a world that's already generated. */
    private Site build(World world, int cx, int floor, int cz) {
        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        boolean endWorld = world.getEnvironment() == World.Environment.THE_END;
        int x0 = cx - 12;
        int y0 = floor;
        int z0 = cz - 12;
        ForgeRuins.Canvas canvas = new ForgeRuins.Canvas() {
            @Override
            public void set(int x, int y, int z, String block) {
                world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(block), false);
            }

            @Override
            public String get(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType().getKey().toString();
            }
        };
        ForgeRuins.Result result = ForgeRuins.build(endWorld ? ForgeRuins.Type.ANCIENT_END
                        : nether ? ForgeRuins.Type.ANCIENT_NETHER : ForgeRuins.Type.ANCIENT,
                canvas, x0, y0, z0, new java.util.SplittableRandom(cx * 31L + cz));
        for (int[] chest : result.bigChests) {
            if (world.getBlockAt(chest[0], chest[1], chest[2]).getState() instanceof org.bukkit.block.Chest c) {
                c.setLootTable(Bukkit.getLootTable(ScorchedLands.LOOT_BIG), cx * 7919L + cz);
                c.update(true, false);
            }
        }
        Site site = new Site(world.getName(), result.core[0], result.core[1], result.core[2]);
        sites.put(site.id(), site);
        save();
        return site;
    }

    // ================================================================ guaranteed forges in land that was already explored

    /** planned spot -> the check it was first seen loaded without a forge */
    private final Map<String, Integer> missing = new java.util.HashMap<>();

    /**
     * A guaranteed forge only generates with NEW chunks. If its spot was explored before (e.g. the Nether around a
     * portal near spawn), it's built the moment a player comes close instead.
     */
    private void buildMissing() {
        if (plugin.scorched() == null) {
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            for (int[] p : plugin.scorched().plannedForges(w)) {
                boolean have = sites.values().stream().anyMatch(s -> s.world.equals(w.getName())
                        && Math.abs(s.x - p[0]) < 24 && Math.abs(s.z - p[1]) < 24);
                String key = w.getName() + ";" + p[0] + ";" + p[1];
                if (have || !w.isChunkLoaded(p[0] >> 4, p[1] >> 4)
                        || !anyoneNear(new Location(w, p[0], w.getEnvironment() == World.Environment.NETHER ? 64 : 80, p[1]), 96)) {
                    missing.remove(key);
                    continue;
                }
                // wait a moment: a chunk that's generating right now registers its forge itself
                int first = missing.computeIfAbsent(key, k -> ticks);
                if (ticks - first < 6) {
                    continue;
                }
                missing.remove(key);
                int floor = floorAt(w, p[0], p[1]);
                Site site = build(w, p[0], floor, p[1]);
                plugin.getLogger().info("[Stars] An Ancient Forge generated at " + w.getName() + " " + site.x + " " + site.y + " "
                        + site.z + " (built late: that land was explored before)");
            }
        }
    }

    /** Where the forge's floor goes: the surface in the overworld, an open cave floor (or y 64) in the Nether. */
    private static int floorAt(World w, int x, int z) {
        if (w.getEnvironment() == World.Environment.NETHER) {
            for (int y = 32; y <= 100; y++) {
                if (!w.getBlockAt(x, y, z).getType().isSolid()) {
                    continue;
                }
                boolean open = true;
                for (int up = 1; up <= 8 && open; up++) {
                    open = w.getBlockAt(x, y + up, z).getType().isAir();
                }
                if (open) {
                    return y;
                }
            }
            return 64;
        }
        return w.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    /** /forge stars <player> <stars>: sets the stars of the weapon in the player's hand. */
    public void setCommand(CommandSender sender, Player target, double stars) {
        ItemStack hand = target.getInventory().getItemInMainHand();
        ItemManager.Entry entry = starWeapon(hand);
        if (entry == null) {
            sender.sendMessage(ItemManager.color("&c" + target.getName() + " isn't holding a legend weapon."));
            return;
        }
        setHalves(hand, entry, (int) Math.round(stars * 2));
        target.getInventory().setItemInMainHand(hand);
        plugin.getLogger().info("[Stars] " + sender.getName() + " set " + target.getName() + "'s " + entry.ref() + " to "
                + Stars.starCount(halvesOf(hand)) + "/5 (admin command)");
        sender.sendMessage(ItemManager.color("&7" + target.getName() + "'s ").append(ItemManager.nameOf(hand)));
    }

    /** /forge ancient finish: ends every running ascension now. */
    public void finishAll(CommandSender sender) {
        for (Running r : running.values()) {
            r.endsAt = System.currentTimeMillis();
        }
        sender.sendMessage(ItemManager.color("&7" + running.size() + " ascension(s) will finish now."));
    }

    /** /forge ancient cool: resets every forge's cooldown. */
    public void resetCooldowns(CommandSender sender) {
        sites.values().forEach(s -> s.readyAt = 0);
        save();
        sender.sendMessage(ItemManager.color("&7Every Ancient Forge is ready again."));
    }
}
