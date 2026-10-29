package cz.forgebound;

import cz.forgebound.ForgeData.Claim;
import cz.forgebound.ForgeData.SavedRitual;
import cz.forgebound.ForgeData.State;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Forge rituals: pressing FORGE on a weapon starts a long ritual on the crafting table.
 * When it finishes the weapon drops on the table. Every weapon exists only once on the server.
 */
public final class RitualManager implements Listener {

    private static final Particle.DustOptions RUNE = new Particle.DustOptions(Color.fromRGB(170, 90, 255), 1.3f);
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 150, 50), 1.1f);

    private final Forgebound plugin;
    private final ItemManager items;
    private final ForgeData data;
    private final Map<String, Running> running = new LinkedHashMap<>();
    private int ticks;

    private static final class Running {
        String weapon;
        Location table;     // block location of the crafting table
        long endsAt;
        long total;
        UUID player;
        String name;
        int stage = -1;      // current location-reveal stage
        BossBar bar;
        ItemDisplay display;
    }

    /** Ritual timers stand still while too few players are online. */
    private boolean paused;
    private long lastTick;

    public RitualManager(Forgebound plugin, ItemManager items, ForgeData data) {
        this.plugin = plugin;
        this.items = items;
        this.data = data;
    }

    // ---------------------------------------------------------------- settings

    public boolean ritualsEnabled() {
        return plugin.getConfig().getBoolean("ritual.enabled", true);
    }

    public boolean uniqueEnabled() {
        return plugin.getConfig().getBoolean("unique-weapons.enabled", true);
    }

    /** Ritual length for a weapon (per-weapon "ritual-minutes" overrides the global value). */
    public long durationMs(ItemManager.Entry weapon) {
        double global = plugin.getConfig().getDouble("ritual.duration-minutes", 15);
        double minutes = plugin.getConfig().getDouble(ItemManager.configPath(weapon) + ".ritual-minutes", global);
        return Math.max(1000L, (long) (minutes * 60_000));
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        for (SavedRitual saved : data.savedRituals()) {
            World world = Bukkit.getWorld(saved.world());
            ItemManager.Entry weapon = items.get(saved.weapon());
            if (world == null || weapon == null) {
                plugin.getLogger().warning("Can't resume ritual for " + saved.weapon() + " (world or weapon missing).");
                continue;
            }
            Running r = new Running();
            r.weapon = saved.weapon();
            r.table = new Location(world, saved.x(), saved.y(), saved.z());
            r.endsAt = System.currentTimeMillis() + saved.remaining();
            r.total = Math.max(saved.total(), saved.remaining());
            r.player = saved.player();
            r.name = saved.name();
            r.stage = stageIndex(progress(r));
            spawnVisuals(r, weapon);
            running.put(r.weapon, r);
            plugin.getLogger().info("Resumed ritual for " + r.weapon + " (" + format(saved.remaining()) + " left).");
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    /** Plugin disable: save remaining time and remove visuals. */
    public void shutdown() {
        save();
        for (Running r : running.values()) {
            removeVisuals(r);
        }
        running.clear();
    }

    public void save() {
        long now = System.currentTimeMillis();
        List<SavedRitual> list = new ArrayList<>();
        for (Running r : running.values()) {
            list.add(new SavedRitual(r.weapon, r.table.getWorld().getName(), r.table.getBlockX(), r.table.getBlockY(),
                    r.table.getBlockZ(), Math.max(0, r.endsAt - now), r.total, r.player, r.name));
        }
        data.save(list);
    }

    // ---------------------------------------------------------------- queries

    /** Why a weapon can't be forged right now (as a message), or null if it can. */
    public Component blocked(ItemManager.Entry weapon) {
        if (!uniqueEnabled() || !isUnique(weapon)) {
            return null;
        }
        Claim claim = data.claim(weapon.ref());
        if (claim == null) {
            return null;
        }
        if (claim.state() == State.RITUAL) {
            return plugin.msg("weapon-in-ritual", "&c{item} &7is being forged by &c{player} &7right now.",
                    "item", ItemManager.nameOf(weapon.item), "player", publicName(claim.name()));
        }
        return plugin.msg("weapon-claimed", "&c{item} &7already exists. It was claimed by &c{player}&7.",
                "item", ItemManager.nameOf(weapon.item), "player", publicName(claim.name()));
    }

    public boolean isUnique(ItemManager.Entry weapon) {
        return !weapon.mythic && plugin.getConfig().getBoolean(ItemManager.configPath(weapon) + ".unique", true);
    }

    public Claim claim(ItemManager.Entry weapon) {
        return uniqueEnabled() && isUnique(weapon) ? data.claim(weapon.ref()) : null;
    }

    /** Remaining ritual time for a weapon in ms, or -1 if none is running. */
    public long remaining(String weapon) {
        Running r = running.get(weapon);
        return r == null ? -1 : Math.max(0, r.endsAt - System.currentTimeMillis());
    }

    public boolean isRitualTable(Block block) {
        for (Running r : running.values()) {
            if (same(r.table, block)) {
                return true;
            }
        }
        return false;
    }

    /** A weapon was made without a ritual (rituals turned off): it now exists.
     *  Returns false if it was already claimed. */
    public boolean claimInstant(ItemManager.Entry weapon, Player player, String source) {
        if (!uniqueEnabled() || !isUnique(weapon)) {
            return true;
        }
        if (data.claim(weapon.ref()) != null) {
            return false;
        }
        data.setClaim(weapon.ref(), new Claim(State.FORGED, player.getUniqueId(), player.getName(),
                System.currentTimeMillis(), source));
        save();
        return true;
    }

    // ---------------------------------------------------------------- start / finish

    /** Starts a ritual. The caller already checked materials and availability and took the materials. */
    public void begin(Player player, ItemManager.Entry weapon, Block table) {
        long duration = durationMs(weapon);
        Running r = new Running();
        r.weapon = weapon.ref();
        r.table = table.getLocation();
        r.endsAt = System.currentTimeMillis() + duration;
        r.total = duration;
        r.player = player.getUniqueId();
        r.name = player.getName();
        running.put(r.weapon, r);
        if (uniqueEnabled() && isUnique(weapon)) {
            data.setClaim(weapon.ref(), new Claim(State.RITUAL, r.player, r.name, System.currentTimeMillis(), "ritual"));
        }
        r.stage = stageIndex(0);
        spawnVisuals(r, weapon);
        save();
        plugin.getLogger().info("[Rituals] " + player.getName() + " started forging " + weapon.ref() + " at "
                + table.getWorld().getName() + " " + table.getX() + " " + table.getY() + " " + table.getZ()
                + ", ready in " + format(duration));

        Bukkit.broadcast(plugin.msg("ritual-started",
                "&5⚒ &d{player} &7started forging {item}&7{where}&7! Ready in &d{time}&7.",
                "player", publicName(r.name), "item", ItemManager.nameOf(weapon.item), "where", where(r), "time", format(duration)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 0.6f);
        }
        World world = table.getWorld();
        Location c = center(r);
        world.strikeLightningEffect(c);
        world.spawnParticle(Particle.REVERSE_PORTAL, c, 120, 0.4, 0.6, 0.4, 0.4);
    }

    private void complete(Running r) {
        running.remove(r.weapon);
        removeVisuals(r);
        ItemManager.Entry weapon = items.get(r.weapon);
        if (weapon == null) {
            plugin.getLogger().warning("Ritual for " + r.weapon + " finished, but the weapon no longer exists in the config.");
            data.release(r.weapon);
            save();
            return;
        }
        Location c = center(r);
        World world = c.getWorld();
        ItemStack forged = weapon.item.clone();
        plugin.stamp(forged, weapon.ref());
        if (plugin.stars() != null) {
            plugin.stars().initForged(forged, weapon);
        }
        if (plugin.advancements() != null) {
            plugin.advancements().markForged(forged, r.player);
        }
        Item drop = world.dropItem(c.clone().add(0, 0.7, 0), forged, item -> {
            item.setVelocity(new org.bukkit.util.Vector(0, 0.15, 0));
            item.setUnlimitedLifetime(true);
            item.setInvulnerable(true);
            item.setGlowing(true);
            item.setCanMobPickup(false);
        });
        world.strikeLightningEffect(c);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), 120, 0.3, 1.5, 0.3, 0.15);
        world.spawnParticle(Particle.DUST, c.clone().add(0, 1, 0), 80, 1.2, 1.2, 1.2, 0, RUNE);

        if (uniqueEnabled() && isUnique(weapon)) {
            data.setClaim(r.weapon, new Claim(State.FORGED, r.player, r.name, System.currentTimeMillis(), "ritual"));
        }
        save();
        if (plugin.customEnchants() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, plugin.customEnchants()::checkUnlock, 100L);
        }
        Bukkit.broadcast(plugin.msg("ritual-done", "&6⚒ {item} &7has been forged by &6{player}&7{where}&7!",
                "item", ItemManager.nameOf(weapon.item), "player", publicName(r.name), "where", where(r)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        plugin.getLogger().info("[Rituals] " + r.weapon + " was forged by " + r.name + " (item " + drop.getUniqueId() + ")");
        if (plugin.advancements() != null) {
            plugin.advancements().awardLater(r.player, "forgemaster");
        }
    }

    /** Admin: finish a ritual right now. */
    public boolean finishNow(String weapon) {
        Running r = running.get(weapon);
        if (r == null) {
            return false;
        }
        r.endsAt = System.currentTimeMillis();
        return true;
    }

    /** Admin: make a weapon available again (also stops its ritual, nothing drops). */
    public boolean release(String weapon) {
        Running r = running.remove(weapon);
        if (r != null) {
            removeVisuals(r);
        }
        boolean had = data.release(weapon) != null || r != null;
        save();
        if (plugin.legends() != null) {
            plugin.legends().forgetHolder(weapon);
        }
        return had;
    }

    // ---------------------------------------------------------------- visuals

    private void spawnVisuals(Running r, ItemManager.Entry weapon) {
        r.bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_20);
        updateBar(r);
        if (bossbarMode().equals("ALL")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(r.bar);
            }
        }
        spawnDisplay(r, weapon);
    }

    private void spawnDisplay(Running r, ItemManager.Entry weapon) {
        Location at = center(r).add(0, 1.1, 0);
        r.display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(weapon.item.clone());
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(170, 90, 255));
            d.setTeleportDuration(2);
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
        }
    }

    private String bossbarMode() {
        return plugin.getConfig().getString("ritual.bossbar", "ALL").toUpperCase(Locale.ROOT);
    }

    private void updateBar(Running r) {
        ItemManager.Entry weapon = items.get(r.weapon);
        long left = Math.max(0, r.endsAt - System.currentTimeMillis());
        if (paused) {
            r.bar.name(plugin.raw("ritual-bossbar-paused",
                    "&d⚒ {item} &8• &cpaused &7- waiting for {min} players online &8({online}/{min})",
                    "item", weapon == null ? Component.text(r.weapon) : ItemManager.nameOf(weapon.item),
                    "min", String.valueOf(minPlayers()), "online", String.valueOf(Bukkit.getOnlinePlayers().size())));
            return;
        }
        r.bar.name(plugin.raw("ritual-bossbar", "&d⚒ {player} &7is forging {item} &8• &f{time}{where}",
                "player", publicName(r.name), "item", weapon == null ? Component.text(r.weapon) : ItemManager.nameOf(weapon.item),
                "time", format(left), "where", where(r)));
        r.bar.progress((float) Math.max(0, Math.min(1, 1 - (double) left / Math.max(1, r.total))));
    }

    /** Players needed online to start a ritual (and to keep its timer running). 0 = no limit. */
    public int minPlayers() {
        return Math.max(0, plugin.getConfig().getInt("ritual.min-players", 3));
    }

    public boolean enoughPlayers() {
        return Bukkit.getOnlinePlayers().size() >= minPlayers();
    }

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        long elapsed = lastTick == 0 ? 0 : now - lastTick;
        lastTick = now;
        boolean wasPaused = paused;
        paused = !running.isEmpty() && plugin.getConfig().getBoolean("ritual.pause-below-min-players", true)
                && !enoughPlayers();
        if (paused) {
            for (Running r : running.values()) {
                r.endsAt += elapsed;                         // the clock stands still
            }
        } else if (plugin.events() != null && plugin.events().ritualSpeed() > 1) {
            long extra = (long) (elapsed * (plugin.events().ritualSpeed() - 1));
            for (Running r : running.values()) {
                r.endsAt -= extra;                           // Happy Hour: rituals run faster
            }
        }
        if (paused != wasPaused) {
            for (Running r : running.values()) {
                updateBar(r);
            }
        }
        for (Running r : new ArrayList<>(running.values())) {
            if (now >= r.endsAt) {
                complete(r);
                continue;
            }
            World world = r.table.getWorld();
            if (!world.isChunkLoaded(r.table.getBlockX() >> 4, r.table.getBlockZ() >> 4)) {
                continue; // nobody around, the timer keeps running
            }
            Location c = center(r);
            double progress = 1 - (double) (r.endsAt - now) / Math.max(1, r.total);

            // floating, spinning weapon above the table
            if (r.display != null && r.display.isValid()) {
                Location at = c.clone().add(0, 1.1 + 0.12 * Math.sin(ticks * 0.12), 0);
                at.setYaw((ticks * 9) % 360);
                r.display.teleport(at);
            } else {
                ItemManager.Entry weapon = items.get(r.weapon);
                if (weapon != null) {
                    spawnDisplay(r, weapon);
                }
            }

            if (ticks % 5 == 0) {
                double spin = ticks * 0.08;
                int points = 10;
                double radius = 1.7 - 0.5 * progress;
                for (int i = 0; i < points; i++) {
                    double a = spin + 2 * Math.PI * i / points;
                    Location p = c.clone().add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, RUNE);
                }
                // runes flowing into the table
                for (int i = 0; i < 4 + (int) (progress * 8); i++) {
                    double a = Math.random() * Math.PI * 2;
                    world.spawnParticle(Particle.ENCHANT, c.clone().add(0, 0.6, 0), 0,
                            Math.cos(a) * 1.6, 0.8 + Math.random(), Math.sin(a) * 1.6, 1);
                }
                world.spawnParticle(Particle.DUST, c.clone().add(0, 0.6, 0), 2, 0.25, 0.1, 0.25, 0, EMBER);
            }
            if (ticks % 20 == 0) {
                world.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.6, 0), 3, 0.1, 1.2, 0.1, 0.02);
            }
            if (ticks % 150 == 0) {
                world.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.6f);
                world.playSound(c, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 0.7f);
            }
            if (r.endsAt - now < 10_000 && ticks % 10 == 0) {
                world.playSound(c, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 0.5f + (float) progress);
            }
        }
        if (ticks % 10 == 0) {
            String mode = bossbarMode();
            double radius = plugin.getConfig().getDouble("ritual.bossbar-radius", 64);
            for (Running r : running.values()) {
                checkReveal(r);
                updateBar(r);
                if (mode.equals("NEARBY")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        boolean near = p.getWorld() == r.table.getWorld()
                                && p.getLocation().distanceSquared(r.table) <= radius * radius;
                        if (near) {
                            p.showBossBar(r.bar);
                        } else {
                            p.hideBossBar(r.bar);
                        }
                    }
                }
            }
            if (ticks % 600 == 0) {
                save(); // keep remaining time fresh in case of a crash
            }
        }
    }

    // ---------------------------------------------------------------- protection

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isRitualTable(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.msg("ritual-protected", "&cThe forge ritual protects this crafting table."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isRitualTable(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isRitualTable);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isRitualTable);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isRitualTable)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isRitualTable)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bossbarMode().equals("ALL")) {
            for (Running r : running.values()) {
                event.getPlayer().showBossBar(r.bar);
            }
        }
    }

    /** A unique weapon lying on the ground got destroyed (lava, fire, void, despawn): it can be forged again. */
    @EventHandler
    public void onItemRemoved(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item) || !uniqueEnabled()
                || !plugin.getConfig().getBoolean("unique-weapons.release-when-destroyed", true)) {
            return;
        }
        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause != EntityRemoveEvent.Cause.DEATH && cause != EntityRemoveEvent.Cause.DESPAWN
                && cause != EntityRemoveEvent.Cause.OUT_OF_WORLD) {
            return;
        }
        ItemStack stack = item.getItemStack();
        ItemManager.Entry weapon = items.customOf(stack);
        if (weapon == null || !isUnique(weapon)) {
            return;
        }
        Claim claim = data.claim(weapon.ref());
        if (claim == null || claim.state() != State.FORGED) {
            return;
        }
        data.release(weapon.ref());
        save();
        if (plugin.legends() != null) {
            plugin.legends().forgetHolder(weapon.ref());
        }
        Bukkit.broadcast(plugin.msg("weapon-destroyed", "&7{item} &7was destroyed and can be forged again!",
                "item", ItemManager.nameOf(weapon.item)));
        plugin.getLogger().info("[Rituals] " + weapon.ref() + " was destroyed (" + cause.name().toLowerCase(Locale.ROOT)
                + ") and can be forged again");
    }

    // ---------------------------------------------------------------- helpers

    private static boolean same(Location a, Block b) {
        return a.getWorld() == b.getWorld() && a.getBlockX() == b.getX() && a.getBlockY() == b.getY()
                && a.getBlockZ() == b.getZ();
    }

    private static Location center(Running r) {
        return r.table.clone().add(0.5, 1.0, 0.5);
    }

    // ---------------------------------------------------------------- hidden forger + location reveal

    /** The forger's name as other players see it (hidden unless ritual.show-player is true). */
    public String publicName(String name) {
        if (plugin.getConfig().getBoolean("ritual.show-player", false)) {
            return name;
        }
        return plugin.getConfig().getString("ritual.hidden-player-name", "Someone");
    }

    private record Stage(double percent, int radius) { }

    /** Reveal stages sorted by time. radius: -1 = only the world, 0 = exact, n = area of ±n blocks. */
    private List<Stage> stages() {
        List<Stage> list = new ArrayList<>();
        if (plugin.getConfig().getBoolean("ritual.location-reveal.enabled", true)) {
            for (Map<?, ?> map : plugin.getConfig().getMapList("ritual.location-reveal.stages")) {
                try {
                    double percent = Double.parseDouble(String.valueOf(map.get("at-percent")));
                    int radius = Integer.parseInt(String.valueOf(map.get("radius")));
                    list.add(new Stage(percent, radius));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("ritual.location-reveal.stages: bad entry " + map);
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new Stage(0, 0));   // no stages = exact location from the start
        }
        list.sort((x, y) -> Double.compare(x.percent(), y.percent()));
        return list;
    }

    private int stageIndex(double progress) {
        List<Stage> list = stages();
        int index = 0;
        for (int i = 0; i < list.size(); i++) {
            if (progress * 100 >= list.get(i).percent()) {
                index = i;
            }
        }
        return index;
    }

    private static double progress(Running r) {
        return Math.max(0, Math.min(1, 1 - (double) (r.endsAt - System.currentTimeMillis()) / Math.max(1, r.total)));
    }

    /** Announces a new reveal stage when the ritual reaches it. */
    private void checkReveal(Running r) {
        int index = stageIndex(progress(r));
        if (index == r.stage) {
            return;
        }
        r.stage = index;
        if (!plugin.getConfig().getBoolean("ritual.show-coordinates", true)) {
            return;
        }
        ItemManager.Entry weapon = items.get(r.weapon);
        Component item = weapon == null ? Component.text(r.weapon) : ItemManager.nameOf(weapon.item);
        Stage stage = stages().get(index);
        Component message;
        if (stage.radius() == 0) {
            message = plugin.msg("ritual-reveal-exact", "&5📍 &7The forge of {item} &7has been revealed{where}&7!",
                    "item", item, "where", where(r));
        } else {
            message = plugin.msg("ritual-reveal-area", "&5📍 &7The forge of {item} &7is narrowing down{where}&7.",
                    "item", item, "where", where(r));
        }
        Bukkit.broadcast(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), stage.radius() == 0 ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.BLOCK_BELL_USE,
                    stage.radius() == 0 ? 0.5f : 1f, 0.8f);
        }
    }

    /** Location text for the current reveal stage. */
    private Component where(Running r) {
        if (!plugin.getConfig().getBoolean("ritual.show-coordinates", true)) {
            return Component.empty();
        }
        List<Stage> list = stages();
        Stage stage = list.get(Math.max(0, Math.min(r.stage, list.size() - 1)));
        String world = switch (r.table.getWorld().getEnvironment()) {
            case NETHER -> "the Nether";
            case THE_END -> "the End";
            default -> "the Overworld";
        };
        String text;
        int x = r.table.getBlockX();
        int z = r.table.getBlockZ();
        if (stage.radius() < 0) {
            text = plugin.getConfig().getString("messages.ritual-where-world", " &7somewhere in &f{world}");
        } else if (stage.radius() == 0) {
            text = plugin.getConfig().getString("messages.ritual-where", " &7at &f{x} {y} {z}");
        } else {
            // The area is NOT centred on the table: shift it randomly (stable per ritual and stage),
            // so the middle of the area doesn't give the table away.
            long seed = (31L * x + z) * 31 + r.weapon.hashCode() * 17L + stage.radius();
            java.util.Random random = new java.util.Random(seed);
            int radius = stage.radius();
            int cx = x + (int) Math.round((random.nextDouble() * 2 - 1) * radius * 0.8);
            int cz = z + (int) Math.round((random.nextDouble() * 2 - 1) * radius * 0.8);
            x = Math.round(cx / 10f) * 10;
            z = Math.round(cz / 10f) * 10;
            text = plugin.getConfig().getString("messages.ritual-where-area", " &7around &f{x} {z} &8(±{radius} blocks)")
                    .replace("{radius}", String.valueOf(radius));
        }
        return ItemManager.color(text
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(r.table.getBlockY()))
                .replace("{z}", String.valueOf(z))
                .replace("{world}", world));
    }

    /** mm:ss (or h:mm:ss). */
    public static String format(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    public Map<String, Long> runningSnapshot() {
        Map<String, Long> map = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        running.forEach((k, r) -> map.put(k, Math.max(0, r.endsAt - now)));
        return map;
    }

    public boolean isRunning(String weapon) {
        return running.containsKey(weapon);
    }

    /** Material check helper for the menu. */
    public static boolean isCraftingTable(Block block) {
        return block != null && block.getType() == Material.CRAFTING_TABLE;
    }
}
