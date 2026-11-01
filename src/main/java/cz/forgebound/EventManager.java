package cz.forgebound;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server events and their scheduler.
 * Big: Blood Moon (a chance every night) and the Rift (see RiftEvent).
 * Small (one at a time): Happy Hour, Forge Market, Golden Shipment.
 */
public final class EventManager implements Listener {

    public enum HappyType { XP, LOOT, RITUAL }

    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(170, 10, 20), 1.6f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 210, 60), 2f);

    private final Forgebound plugin;
    private final NamespacedKey eliteKey;
    private RiftEvent rift;
    private LegendHunt hunt;
    private long nextHuntAt;

    private long nextSmallAt;
    private long nextRiftAt;
    private long lastWorldTime = -1;

    // Blood Moon
    private boolean bloodMoon;
    private BossBar bloodBar;

    // Happy Hour
    private HappyType happy;
    private long happyEndsAt;
    private BossBar happyBar;

    // Forge Market
    private long marketEndsAt;
    private BossBar marketBar;

    // Golden Shipment
    private Location shipment;
    private boolean shipmentFound;
    private long shipmentExpiresAt;
    private BossBar shipmentBar;

    public EventManager(Forgebound plugin) {
        this.plugin = plugin;
        this.eliteKey = Forgebound.key("blood_elite");
    }

    public void setRift(RiftEvent rift) {
        this.rift = rift;
    }

    public void setHunt(LegendHunt hunt) {
        this.hunt = hunt;
    }

    public LegendHunt hunt() {
        return hunt;
    }

    /** Ability cooldowns are shorter for the player being hunted. */
    public double cooldownMultiplier(Player player) {
        return hunt != null && hunt.isTarget(player)
                ? Math.max(0.05, plugin.getConfig().getDouble("events.legend-hunt.cooldown-multiplier", 0.5)) : 1;
    }

    private boolean on(String event) {
        return plugin.getConfig().getBoolean("events.enabled", true)
                && plugin.getConfig().getBoolean("events." + event + ".enabled", true);
    }

    private int minPlayers() {
        return plugin.getConfig().getInt("events.min-players", 3);
    }

    private boolean enoughPlayers() {
        return Bukkit.getOnlinePlayers().size() >= minPlayers();
    }

    World world() {
        String name = plugin.getConfig().getString("events.world", "");
        World w = name.isEmpty() ? null : Bukkit.getWorld(name);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    private long minutes(String key, int def) {
        return Math.max(1, plugin.getConfig().getInt(key, def)) * 60_000L;
    }

    private long randomMinutes(String minKey, int minDef, String maxKey, int maxDef) {
        long min = minutes(minKey, minDef);
        long max = Math.max(min, minutes(maxKey, maxDef));
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    public void start() {
        long now = System.currentTimeMillis();
        nextSmallAt = now + randomMinutes("events.small-interval-minutes-min", 40, "events.small-interval-minutes-max", 80);
        nextRiftAt = now + randomMinutes("events.rift.interval-minutes-min", 90, "events.rift.interval-minutes-max", 180);
        nextHuntAt = now + randomMinutes("events.legend-hunt.interval-minutes-min", 120, "events.legend-hunt.interval-minutes-max", 240);
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 100L, 20L);
    }

    public void shutdown() {
        endBloodMoon(false);
        endHappyHour(false);
        endMarket(false);
        endShipment(false, !shipmentFound);
        if (rift != null) {
            rift.shutdown();
        }
        if (hunt != null) {
            hunt.end();
        }
    }

    /** Is a big event on (Blood Moon or a Rift)? Meteors wait for them. */
    public boolean bigActive() {
        return bloodMoon || rift != null && rift.active();
    }

    private boolean smallActive() {
        return happy != null || marketEndsAt > 0 || shipment != null;
    }

    // ================================================================ scheduler

    private void tick() {
        long now = System.currentTimeMillis();
        World world = world();
        // Blood Moon: rolled when night falls
        long time = world.getTime();
        if (lastWorldTime >= 0 && lastWorldTime < 13000 && time >= 13000 && !bloodMoon && on("blood-moon")
                && enoughPlayers() && (rift == null || !rift.active())
                && ThreadLocalRandom.current().nextDouble(100) < plugin.getConfig().getDouble("events.blood-moon.chance", 20)) {
            startBloodMoon();
        }
        lastWorldTime = time;
        if (bloodMoon && (time < 12500 || time >= 23000)) {
            endBloodMoon(true);
        }
        if (bloodMoon) {
            bloodTick(world);
        }
        // Rift
        if (now >= nextRiftAt) {
            nextRiftAt = now + randomMinutes("events.rift.interval-minutes-min", 90, "events.rift.interval-minutes-max", 180);
            if (rift != null && on("rift") && enoughPlayers() && !bigActive()
                    && (plugin.meteor() == null || !plugin.meteor().active())) {
                rift.begin(null);
            }
        }
        // Legend Hunt
        if (now >= nextHuntAt) {
            nextHuntAt = now + randomMinutes("events.legend-hunt.interval-minutes-min", 120, "events.legend-hunt.interval-minutes-max", 240);
            if (hunt != null && on("legend-hunt") && enoughPlayers() && !hunt.active() && (rift == null || !rift.active())) {
                hunt.begin(null);
            }
        }
        if (hunt != null) {
            hunt.tick();
        }
        // small events
        if (now >= nextSmallAt && !smallActive()) {
            nextSmallAt = now + randomMinutes("events.small-interval-minutes-min", 40, "events.small-interval-minutes-max", 80);
            if (enoughPlayers()) {
                startRandomSmall();
            }
        }
        if (happy != null) {
            if (now >= happyEndsAt) {
                endHappyHour(true);
            } else {
                happyBar.progress(progress(happyEndsAt, minutes("events.happy-hour.duration-minutes", 45)));
                happyBar.name(happyName());
            }
        }
        if (marketEndsAt > 0) {
            if (now >= marketEndsAt) {
                endMarket(true);
            } else {
                marketBar.progress(progress(marketEndsAt, minutes("events.forge-market.duration-minutes", 30)));
                marketBar.name(plugin.raw("market-bossbar", "&6⚖ Forge Market &8• &f{time} &7- /shop",
                        "time", RitualManager.format(marketEndsAt - now)));
            }
        }
        if (shipment != null) {
            shipmentTick(now);
        }
        showBars();
    }

    private static float progress(long endsAt, long total) {
        return (float) Math.max(0, Math.min(1, (double) (endsAt - System.currentTimeMillis()) / Math.max(1, total)));
    }

    private void showBars() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (BossBar bar : new BossBar[] {bloodBar, happyBar, marketBar, shipmentBar}) {
                if (bar == null) {
                    continue;
                }
                if (bar == bloodBar && p.getWorld() != world()) {
                    p.hideBossBar(bar);
                } else {
                    p.showBossBar(bar);
                }
            }
        }
    }

    private void hide(BossBar bar) {
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
    }

    private void startRandomSmall() {
        List<String> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String e : new String[] {"happy-hour", "forge-market", "golden-shipment"}) {
            if (!on(e) || e.equals("forge-market") && (plugin.shop() == null || !plugin.shop().enabled())) {
                continue;
            }
            double w = plugin.getConfig().getDouble("events." + e + ".weight", 1);
            if (w > 0) {
                pool.add(e);
                weights.add(w);
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                start(pool.get(i), null);
                return;
            }
        }
    }

    /** Starts an event by name (admin or scheduler). Returns false if it can't start right now. */
    public boolean start(String name, Player admin) {
        switch (name.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "blood-moon" -> {
                if (bloodMoon) {
                    return false;
                }
                World w = world();
                if (w.getTime() < 13000 || w.getTime() >= 22000) {
                    w.setTime(13000);
                    lastWorldTime = 13000;
                }
                startBloodMoon();
                return true;
            }
            case "rift" -> {
                return rift != null && rift.begin(admin == null ? null : admin.getLocation());
            }
            case "legend-hunt" -> {
                return hunt != null && hunt.begin(null);
            }
            case "happy-hour" -> {
                if (happy != null) {
                    return false;
                }
                startHappyHour();
                return true;
            }
            case "forge-market" -> {
                if (marketEndsAt > 0 || plugin.shop() == null || !plugin.shop().enabled()) {
                    return false;
                }
                startMarket();
                return true;
            }
            case "golden-shipment" -> {
                return shipment == null && startShipment(admin == null ? null : admin.getLocation());
            }
            default -> {
                return false;
            }
        }
    }

    /** Admin: stops every running event. */
    public void stopAll() {
        endBloodMoon(true);
        endHappyHour(true);
        endMarket(true);
        endShipment(false, true);
        if (rift != null) {
            rift.collapse(false);
        }
        if (hunt != null && hunt.active()) {
            hunt.end();
        }
    }

    public static List<String> names() {
        return List.of("blood_moon", "rift", "legend_hunt", "happy_hour", "forge_market", "golden_shipment", "stop");
    }

    // ================================================================ Blood Moon

    public boolean bloodMoon() {
        return bloodMoon;
    }

    private void startBloodMoon() {
        bloodMoon = true;
        bloodBar = BossBar.bossBar(plugin.raw("blood-moon-bossbar", "&4&l☾ Blood Moon &8• &7stronger monsters, better loot"),
                1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Bukkit.broadcast(plugin.msg("blood-moon-start",
                "&4☾ &cThe &4&lBlood Moon &crises! &7Monsters are stronger and more numerous, but drop better loot. Nobody can sleep tonight."));
        for (Player p : world().getPlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);
        }
    }

    private void endBloodMoon(boolean announce) {
        if (!bloodMoon) {
            return;
        }
        bloodMoon = false;
        hide(bloodBar);
        bloodBar = null;
        if (announce) {
            Bukkit.broadcast(plugin.msg("blood-moon-end", "&7☾ The Blood Moon sets. The night is quiet again."));
        }
    }

    private void bloodTick(World world) {
        long time = world.getTime();
        bloodBar.progress((float) Math.max(0, Math.min(1, (23000 - time) / 10000.0)));
        if (time % 40 < 20) {
            for (Player p : world.getPlayers()) {
                p.spawnParticle(Particle.DUST, p.getLocation().add(0, 2, 0), 6, 6, 3, 6, 0, BLOOD);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!bloodMoon || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                || !(event.getEntity() instanceof Monster mob) || mob.getWorld() != world()) {
            return;
        }
        buff(mob);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble(100) < plugin.getConfig().getDouble("events.blood-moon.extra-spawn-chance", 40)) {
            Location at = mob.getLocation().add(r.nextInt(-2, 3), 0, r.nextInt(-2, 3));
            if (at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (bloodMoon) {
                        LivingEntity extra = (LivingEntity) at.getWorld().spawnEntity(at, mob.getType(),
                                CreatureSpawnEvent.SpawnReason.CUSTOM);
                        buff(extra);
                    }
                });
            }
        }
    }

    private void buff(LivingEntity mob) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean elite = r.nextDouble(100) < plugin.getConfig().getDouble("events.blood-moon.elite-chance", 8);
        double hp = plugin.getConfig().getDouble("events.blood-moon.health-multiplier", 1.5) * (elite ? 2 : 1);
        AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(max.getBaseValue() * hp);
            mob.setHealth(max.getValue());
        }
        AttributeInstance dmg = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() * plugin.getConfig().getDouble("events.blood-moon.damage-multiplier", 1.3));
        }
        if (elite) {
            AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(1.25);
            }
            mob.customName(ItemManager.color("&4Blood-crazed &c" + pretty(mob.getType().name())));
            mob.setGlowing(true);
            mob.getPersistentDataContainer().set(eliteKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    static String pretty(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    @EventHandler
    public void onEliteDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(eliteKey, PersistentDataType.BYTE)
                || event.getEntity().getKiller() == null) {
            return;
        }
        event.setDroppedExp(event.getDroppedExp() * 3);
        if (plugin.shop() != null && plugin.shop().enabled() && plugin.bounty() != null) {
            int min = Math.max(0, plugin.getConfig().getInt("events.blood-moon.elite-tokens-min", 1));
            int max = Math.max(min, plugin.getConfig().getInt("events.blood-moon.elite-tokens-max", 2));
            int n = ThreadLocalRandom.current().nextInt(min, max + 1);
            if (n > 0) {
                event.getDrops().add(plugin.bounty().token(n));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (bloodMoon && event.getPlayer().getWorld() == world()) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(plugin.raw("blood-moon-no-sleep", "&cYou can't sleep under the Blood Moon."));
        }
    }

    // ================================================================ Happy Hour

    /** Multiplier for legendary material drop chances (Happy Hour "loot", Blood Moon). */
    public double lootMultiplier() {
        double m = 1;
        if (happy == HappyType.LOOT) {
            m *= plugin.getConfig().getDouble("events.happy-hour.multiplier", 2);
        }
        if (bloodMoon) {
            m *= plugin.getConfig().getDouble("events.blood-moon.loot-multiplier", 1.5);
        }
        return m;
    }

    /** How fast forge rituals run (Happy Hour "ritual"). */
    public double ritualSpeed() {
        return happy == HappyType.RITUAL ? Math.max(1, plugin.getConfig().getDouble("events.happy-hour.multiplier", 2)) : 1;
    }

    private Component happyName() {
        String what = switch (happy) {
            case XP -> plugin.getConfig().getString("messages.happy-hour-xp", "double XP");
            case LOOT -> plugin.getConfig().getString("messages.happy-hour-loot", "double legendary material drops");
            case RITUAL -> plugin.getConfig().getString("messages.happy-hour-ritual", "rituals run twice as fast");
        };
        return plugin.raw("happy-hour-bossbar", "&a✦ Happy Hour &8• &f{what} &8• &f{time}",
                "what", ItemManager.color(what), "time", RitualManager.format(happyEndsAt - System.currentTimeMillis()));
    }

    private void startHappyHour() {
        List<HappyType> types = new ArrayList<>();
        for (HappyType t : HappyType.values()) {
            if (plugin.getConfig().getBoolean("events.happy-hour.types." + t.name().toLowerCase(Locale.ROOT), true)) {
                types.add(t);
            }
        }
        if (types.isEmpty()) {
            return;
        }
        happy = types.get(ThreadLocalRandom.current().nextInt(types.size()));
        happyEndsAt = System.currentTimeMillis() + minutes("events.happy-hour.duration-minutes", 45);
        happyBar = BossBar.bossBar(happyName(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        Bukkit.broadcast(plugin.msg("happy-hour-start", "&a✦ &lHappy Hour! &7For &f{minutes} minutes&7: &a{what}&7!",
                "minutes", String.valueOf(minutes("events.happy-hour.duration-minutes", 45) / 60_000),
                "what", ItemManager.color(switch (happy) {
                    case XP -> plugin.getConfig().getString("messages.happy-hour-xp", "double XP");
                    case LOOT -> plugin.getConfig().getString("messages.happy-hour-loot", "double legendary material drops");
                    case RITUAL -> plugin.getConfig().getString("messages.happy-hour-ritual", "rituals run twice as fast");
                })));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        }
    }

    private void endHappyHour(boolean announce) {
        if (happy == null) {
            return;
        }
        happy = null;
        hide(happyBar);
        happyBar = null;
        if (announce) {
            Bukkit.broadcast(plugin.msg("happy-hour-end", "&7✦ Happy Hour is over."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onXp(PlayerExpChangeEvent event) {
        if (happy == HappyType.XP && event.getAmount() > 0) {
            event.setAmount((int) Math.round(event.getAmount() * plugin.getConfig().getDouble("events.happy-hour.multiplier", 2)));
        }
    }

    // ================================================================ Forge Market

    /** The running Happy Hour type, or null. */
    public HappyType happyHour() {
        return happy;
    }

    /** Is a Golden Shipment out there that nobody has found yet? */
    public boolean shipmentWaiting() {
        return shipment != null && !shipmentFound;
    }

    public RiftEvent rift() {
        return rift;
    }

    /** A short colored text of the most important running event (sidebar, placeholders). */
    public String shortStatus() {
        if (rift != null && rift.active()) {
            return rift.opening() ? "&5Rift opening" : "&5Rift &7" + rift.wave() + "/" + rift.waveTotal();
        }
        if (hunt != null && hunt.active()) {
            return "&cHunt: &f" + hunt.targetName();
        }
        if (plugin.ghost() != null && plugin.ghost().active()) {
            return "&8&k??&r &7Something is here";
        }
        if (bloodMoon) {
            return "&4Blood Moon";
        }
        if (plugin.meteor() != null && plugin.meteor().active()) {
            return "&6Meteor";
        }
        if (plugin.warden() != null && plugin.warden().alive()) {
            return "&4Forge Warden";
        }
        if (happy != null) {
            return "&aHappy Hour";
        }
        if (marketOpen()) {
            return "&6Forge Market";
        }
        if (shipment != null && !shipmentFound) {
            return "&eGolden Shipment";
        }
        return plugin.getConfig().getString("messages.event-none", "&7-");
    }

    public boolean riftActive() {
        return rift != null && rift.active();
    }

    public boolean riftOpening() {
        return rift != null && rift.opening();
    }

    public int riftWave() {
        return rift == null ? 0 : rift.wave();
    }

    public int riftWaves() {
        return rift == null ? 0 : rift.waveTotal();
    }

    public HappyType happyType() {
        return happy;
    }

    /** What the current Happy Hour does, as text. */
    public Component happyWhat() {
        if (happy == null) {
            return Component.empty();
        }
        return ItemManager.color(switch (happy) {
            case XP -> plugin.getConfig().getString("messages.happy-hour-xp", "double XP");
            case LOOT -> plugin.getConfig().getString("messages.happy-hour-loot", "double legendary material drops");
            case RITUAL -> plugin.getConfig().getString("messages.happy-hour-ritual", "rituals run twice as fast");
        });
    }

    public boolean marketOpen() {
        return marketEndsAt > System.currentTimeMillis();
    }

    private void startMarket() {
        marketEndsAt = System.currentTimeMillis() + minutes("events.forge-market.duration-minutes", 30);
        plugin.shop().newMarket();
        marketBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        Bukkit.broadcast(plugin.msg("market-start",
                "&6⚖ The &lForge Market &6is open for &f{minutes} minutes&6! Special offers in &e/shop&6.",
                "minutes", String.valueOf(minutes("events.forge-market.duration-minutes", 30) / 60_000)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
        }
    }

    private void endMarket(boolean announce) {
        if (marketEndsAt == 0) {
            return;
        }
        marketEndsAt = 0;
        hide(marketBar);
        marketBar = null;
        if (announce) {
            Bukkit.broadcast(plugin.msg("market-end", "&7⚖ The Forge Market has closed."));
        }
    }

    // ================================================================ Golden Shipment

    /** A random spot on the surface between min and max blocks from center (null if none found). */
    static Location surface(World world, Location center, int min, int max) {
        for (int attempt = 0; attempt < 25; attempt++) {
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double d = ThreadLocalRandom.current().nextDouble(min, Math.max(min + 1, max));
            int x = (int) Math.floor(center.getX() + Math.cos(a) * d);
            int z = (int) Math.floor(center.getZ() + Math.sin(a) * d);
            Location probe = new Location(world, x + 0.5, 0, z + 0.5);
            if (!world.getWorldBorder().isInside(probe)) {
                continue;
            }
            Block top = world.getHighestBlockAt(x, z);
            if (top.isLiquid() || !top.getType().isSolid() || top.getType().name().contains("LEAVES")) {
                continue;
            }
            Block above = top.getRelative(0, 1, 0);
            if (!above.getType().isAir()) {
                continue;
            }
            return above.getLocation().add(0.5, 0, 0.5);
        }
        return null;
    }

    private boolean startShipment(Location near) {
        World world = near == null ? world() : near.getWorld();
        Location center = near == null ? world.getSpawnLocation() : near;
        int radius = plugin.getConfig().getInt("events.golden-shipment.radius", 1500);
        Location at = near == null ? surface(world, center, 200, radius) : surface(world, center, 20, 60);
        if (at == null) {
            return false;
        }
        shipment = at;
        shipmentFound = false;
        shipmentExpiresAt = System.currentTimeMillis() + minutes("events.golden-shipment.despawn-minutes", 20) + 8000;
        int area = plugin.getConfig().getInt("events.golden-shipment.area-radius", 150);
        int hx = at.getBlockX() + ThreadLocalRandom.current().nextInt(-area / 2, area / 2 + 1);
        int hz = at.getBlockZ() + ThreadLocalRandom.current().nextInt(-area / 2, area / 2 + 1);
        shipmentBar = BossBar.bossBar(plugin.raw("shipment-bossbar", "&e✉ Golden Shipment &8• &7around &f{x} {z} &8(±{radius})",
                "x", String.valueOf(hx), "z", String.valueOf(hz), "radius", String.valueOf(area)),
                1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        Bukkit.broadcast(plugin.msg("shipment-start",
                "&e✉ A &6&lGolden Shipment &eis falling from the sky around &f{x} {z} &8(±{radius} blocks) &ein {world}&e! First come, first served.",
                "x", String.valueOf(hx), "z", String.valueOf(hz), "radius", String.valueOf(area), "world", world.getName()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.8f);
        }
        // the crate floats down for 8 seconds
        Location from = at.clone().add(0, 40, 0);
        BlockDisplay crate = world.spawn(from, BlockDisplay.class, d -> {
            d.setBlock(Material.CHEST.createBlockData());
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new Quaternionf(),
                    new Vector3f(1, 1, 1), new Quaternionf()));
        });
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                i += 2;
                double t = Math.min(1, i / 160.0);
                Location now = from.clone().add(0, -40 * t, 0);
                if (crate.isValid()) {
                    crate.teleport(now);
                }
                world.spawnParticle(Particle.FIREWORK, now.clone().add(0, 1.2, 0), 3, 0.2, 0.2, 0.2, 0.02);
                world.spawnParticle(Particle.DUST, now.clone().add(0, 2, 0), 2, 0.3, 0.3, 0.3, 0, GOLD);
                if (t >= 1) {
                    cancel();
                    crate.remove();
                    landShipment(at);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    private void landShipment(Location at) {
        if (shipment == null) {
            return;
        }
        Block block = at.getBlock();
        if (!block.getType().isAir() && !block.isPassable()) {
            block = block.getRelative(0, 1, 0);
            shipment = block.getLocation().add(0.5, 0, 0.5);
        }
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            chest.customName(ItemManager.color("&6&lGolden Shipment"));
            chest.update();
            fillShipment(((Chest) block.getState()).getBlockInventory());
        }
        at.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, 1.5f, 1.2f);
        at.getWorld().spawnParticle(Particle.FIREWORK, at.clone().add(0, 0.5, 0), 40, 0.5, 0.5, 0.5, 0.1);
    }

    private void fillShipment(Inventory inv) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        inv.setItem(r.nextInt(0, 9), new ItemStack(Material.GOLD_INGOT, 8 + r.nextInt(17)));
        inv.setItem(r.nextInt(9, 13), new ItemStack(Material.DIAMOND, 1 + r.nextInt(3)));
        inv.setItem(r.nextInt(14, 18), new ItemStack(Material.GOLDEN_APPLE, 1 + r.nextInt(3)));
        inv.setItem(r.nextInt(18, 22), new ItemStack(Material.GOLDEN_CARROT, 8 + r.nextInt(17)));
        if (r.nextInt(100) < 25) {
            inv.setItem(r.nextInt(22, 27), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        }
        if (plugin.shop() != null && plugin.shop().enabled() && plugin.bounty() != null) {
            int min = Math.max(0, plugin.getConfig().getInt("events.golden-shipment.tokens-min", 1));
            int max = Math.max(min, plugin.getConfig().getInt("events.golden-shipment.tokens-max", 3));
            int n = r.nextInt(min, max + 1);
            if (n > 0) {
                inv.setItem(13, plugin.bounty().token(n));
            }
        }
        if (plugin.bounty() != null
                && r.nextDouble(100) < plugin.getConfig().getDouble("events.golden-shipment.material-chance", 15) * LootManager.dropMultiplier(plugin)) {
            List<ItemManager.Entry> materials = plugin.bounty().enabledMaterials();
            if (!materials.isEmpty()) {
                inv.setItem(4, materials.get(r.nextInt(materials.size())).item.clone());
            }
        }
    }

    private void shipmentTick(long now) {
        if (!shipmentFound) {
            World w = shipment.getWorld();
            for (int y = 0; y < 40; y += 2) {
                w.spawnParticle(Particle.END_ROD, shipment.clone().add(0, 1 + y, 0), 1, 0.05, 0.4, 0.05, 0, null, true);
            }
        }
        if (shipmentBar != null) {
            shipmentBar.progress(Math.max(0, Math.min(1, (float) (shipmentExpiresAt - now)
                    / minutes("events.golden-shipment.despawn-minutes", 20))));
        }
        if (now >= shipmentExpiresAt) {
            endShipment(!shipmentFound, !shipmentFound);        // a found crate stays with what's left in it
        }
    }

    private boolean isShipment(Location loc) {
        return shipment != null && loc != null && loc.getWorld() == shipment.getWorld()
                && loc.getBlockX() == shipment.getBlockX() && loc.getBlockY() == shipment.getBlockY()
                && loc.getBlockZ() == shipment.getBlockZ();
    }

    /** Removes the chest (if still there) and the boss bar. */
    private void endShipment(boolean announceLost, boolean removeChest) {
        if (shipment == null) {
            return;
        }
        Block block = shipment.getBlock();
        if (removeChest && block.getType() == Material.CHEST) {
            if (block.getState() instanceof Chest chest) {
                chest.getBlockInventory().clear();
            }
            block.setType(Material.AIR);
        }
        shipment = null;
        hide(shipmentBar);
        shipmentBar = null;
        if (announceLost) {
            Bukkit.broadcast(plugin.msg("shipment-lost", "&7✉ Nobody found the Golden Shipment in time. It's gone."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (shipment == null || shipmentFound || !(event.getInventory().getHolder() instanceof Chest chest)
                || !isShipment(chest.getLocation()) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        shipmentFound = true;
        Bukkit.broadcast(plugin.msg("shipment-found", "&e✉ &f{player} &efound the Golden Shipment!", "player", player.getName()));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        hide(shipmentBar);
        shipmentBar = null;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (shipment == null || !(event.getInventory().getHolder() instanceof Chest chest) || !isShipment(chest.getLocation())) {
            return;
        }
        if (event.getInventory().isEmpty()) {
            Location loc = shipment.clone();
            shipment = null;                                    // looted: the crate falls apart
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (loc.getBlock().getType() == Material.CHEST) {
                    loc.getBlock().setType(Material.AIR);
                    loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.5, 0), 30, 0.3, 0.3, 0.3, 0,
                            Material.CHEST.createBlockData());
                    loc.getWorld().playSound(loc, Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
                }
            });
        }
    }

    /** Breaking the crate drops its contents normally, but it also ends the event. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isShipment(event.getBlock().getLocation())) {
            if (!shipmentFound) {
                shipmentFound = true;
                Bukkit.broadcast(plugin.msg("shipment-found", "&e✉ &f{player} &efound the Golden Shipment!",
                        "player", event.getPlayer().getName()));
            }
            hide(shipmentBar);
            shipmentBar = null;
            shipment = null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (shipment != null) {
            event.blockList().removeIf(b -> isShipment(b.getLocation()));
        }
    }

    // ================================================================ /events

    public boolean onCommand(CommandSender sender, String[] args) {
        long now = System.currentTimeMillis();
        List<Component> lines = new ArrayList<>();
        if (bloodMoon) {
            lines.add(ItemManager.color("&4☾ Blood Moon &7- until dawn"));
        }
        if (rift != null && rift.active()) {
            lines.add(rift.statusLine());
        }
        if (hunt != null && hunt.active()) {
            lines.add(ItemManager.color("&c☠ Legend Hunt on &f" + hunt.targetName() + " &7- "
                    + RitualManager.format(hunt.remaining()) + " left"));
        }
        if (plugin.meteor() != null && plugin.meteor().active()) {
            lines.add(ItemManager.color("&6☄ A meteor is falling"));
        }
        if (happy != null) {
            lines.add(happyName());
        }
        if (marketOpen()) {
            lines.add(ItemManager.color("&6⚖ Forge Market &7- " + RitualManager.format(marketEndsAt - now) + " left, &e/shop"));
        }
        if (shipment != null && !shipmentFound) {
            lines.add(ItemManager.color("&e✉ A Golden Shipment is waiting to be found"));
        }
        if (plugin.warden() != null && plugin.warden().alive()) {
            lines.add(ItemManager.color("&4⚒ The Forge Warden is awake"));
        }
        sender.sendMessage(plugin.msg("events-header", "&6&lEvents"));
        if (lines.isEmpty()) {
            sender.sendMessage(plugin.raw("events-none", "&7Nothing is happening right now."));
        }
        for (Component line : lines) {
            sender.sendMessage(Component.text(" ").append(line));
        }
        return true;
    }
}
