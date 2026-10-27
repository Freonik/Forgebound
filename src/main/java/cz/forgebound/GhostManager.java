package cz.forgebound;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Doppelganger: when someone stays top 1 on the bounty board for hours, "they" join the server - a glitched
 * copy of that player in legendary gear, hunting them. If it wins, it takes one of their legends and keeps
 * coming back with it until somebody kills it.
 */
public final class GhostManager implements Listener {

    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(10, 10, 12), 1.4f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(235, 235, 240), 1.0f);
    private static final Particle.DustOptions ICE = new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.3f);
    private static final Particle.DustOptions VOID = new Particle.DustOptions(Color.fromRGB(110, 40, 190), 1.3f);
    private static final List<String> DEFAULT_LINES = List.of(
            "i can see you, {target}",
            "why do you run, {target}",
            "that legend was never yours",
            "do you hear the forge? it remembers you",
            "behind you",
            "{target}. {target}. {target}.",
            "we are the same, {target}. only one of us stays",
            "you made me");

    private final Forgebound plugin;
    private final NamespacedKey ghostKey;
    private final File file;
    private final YamlConfiguration data;

    private Mannequin body;
    private UUID target;
    private String copyName;            // whose copy it is (the original victim)
    private Component glitched;
    private double hp;
    private double maxHp;
    private long endsAt;
    private long nextAbilityAt;
    private long nextBlinkAt;
    private long nextChatAt;
    private long lastHitOnTarget;
    private long offlineSince;
    private int attackCooldown;
    private int healsUsed;
    private Location lastPos;
    private int stuckTicks;
    private int blockedTicks;
    private long nextWindAt;
    private long flyingUntil;
    private boolean gliding;
    private int rockets;
    private int apples;
    private int eatingTicks;
    private long nextEatAt;
    private double absorb;
    private ItemStack eatSwap;
    private long nextRocketAt;
    private long boostUntil;
    private Block digBlock;
    private int digProgress;
    private int digNeeded;
    private ItemStack weapon;
    /** Blocks it dug out: put back later, so nothing is broken for good. */
    private final java.util.Map<Location, String> dug = new java.util.LinkedHashMap<>();
    private final java.util.Map<Location, Long> dugAt = new java.util.HashMap<>();
    private BossBar bar;
    private final java.util.Map<UUID, Double> damage = new java.util.HashMap<>();

    public GhostManager(Forgebound plugin) {
        this.plugin = plugin;
        this.ghostKey = Forgebound.key("doppelganger");
        this.file = new File(plugin.getDataFolder(), "ghost.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private double cfg(String key, double def) {
        return plugin.getConfig().getDouble("doppelganger." + key, def);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("doppelganger.enabled", true);
    }

    private void save() {
        plugin.saveYaml(data, file);
    }

    public boolean active() {
        return body != null && body.isValid();
    }

    public String targetName() {
        Player p = target == null ? null : Bukkit.getPlayer(target);
        return p == null ? "?" : p.getName();
    }

    /** Refs of the legends the copy carries right now, and whose they were (for the console's holder list). */
    public java.util.Map<String, String> stolenRefs() {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        String victim = data.getString("stolen.victim-name", "?");
        for (ItemStack s : stolen()) {
            ItemManager.Entry e = plugin.items().customOf(s);
            if (e != null) {
                out.put(e.ref(), victim);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> stolen() {
        List<?> list = data.getList("stolen.items");
        List<ItemStack> out = new ArrayList<>();
        if (list != null) {
            for (Object o : list) {
                if (o instanceof ItemStack s) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public void start() {
        loadDug();
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (active()) {
                    safeTick();
                } else if (t % 1200 == 0) {
                    minute();
                }
                if (t % 20 == 0) {
                    restoreDug(!active() && t % 200 == 0);
                }
            }
        }.runTaskTimer(plugin, 100L, 1L);
    }

    public void shutdown() {
        if (active()) {
            leave(false);
        }
        restoreDug(true);
        save();
    }

    // ================================================================ when does it come

    /** Every minute: who has been top 1 on the bounty board, and for how long. */
    private void minute() {
        if (!enabled() || plugin.bounty() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // the stolen legends bring it back
        if (!stolen().isEmpty() && now >= data.getLong("stolen.next-return", 0)) {
            data.set("stolen.next-return", now + (long) (cfg("return-minutes", 30) * 60_000L));
            save();
            List<Player> candidates = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (survival(p)) {
                    candidates.add(p);
                }
            }
            if (candidates.size() >= (int) cfg("min-players", 1)) {
                Player victim = Bukkit.getPlayer(UUID.fromString(data.getString("stolen.victim", new UUID(0, 0).toString())));
                Player pick = victim != null && survival(victim) ? victim
                        : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                spawn(pick, data.getString("stolen.victim-name", pick.getName()));
                return;
            }
        }
        // top 1 on the bounty board (offline time counts too)
        UUID top = null;
        double best = cfg("min-bounty", 5) - 0.5;
        for (Map.Entry<UUID, Double> e : plugin.bounty().allBounties().entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                top = e.getKey();
            }
        }
        String saved = data.getString("top.uuid");
        if (top == null) {
            if (saved != null) {
                data.set("top", null);
                save();
            }
            return;
        }
        if (!top.toString().equals(saved)) {
            data.set("top.uuid", top.toString());
            data.set("top.since", now);
            save();
            return;
        }
        long since = data.getLong("top.since", now);
        if (now - since < (long) (cfg("hours-as-top-bounty", 3) * 3_600_000L)) {
            return;
        }
        Player p = Bukkit.getPlayer(top);
        if (p != null && survival(p) && Bukkit.getOnlinePlayers().size() >= (int) cfg("min-players", 1)) {
            spawn(p, p.getName());
        }
    }

    private static boolean survival(Player p) {
        return !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    // ================================================================ spawning

    /** The name with some letters scrambled (obfuscated). */
    private static Component glitch(String name) {
        Component c = Component.empty();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean any = false;
        for (int i = 0; i < name.length(); i++) {
            boolean scramble = r.nextDouble() < 0.35 || !any && i == name.length() - 1;
            any |= scramble;
            Component ch = Component.text(String.valueOf(name.charAt(i)));
            c = c.append(scramble ? ch.decorate(TextDecoration.OBFUSCATED) : ch);
        }
        return c;
    }

    private Location spawnSpot(Player victim) {
        Location at = victim.getLocation();
        if (victim.getWorld().getEnvironment() == World.Environment.NORMAL) {
            Location s = EventManager.surface(victim.getWorld(), at, 18, 32);
            if (s != null && Math.abs(s.getY() - at.getY()) < 20) {
                return s;
            }
        }
        for (int i = 0; i < 30; i++) {
            Location l = at.clone().add(ThreadLocalRandom.current().nextInt(-14, 15), 0, ThreadLocalRandom.current().nextInt(-14, 15));
            for (int dy = -3; dy <= 3; dy++) {
                Block feet = l.clone().add(0, dy, 0).getBlock();
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return at.clone().add(at.getDirection().multiply(-6).setY(0));
    }

    /** Admin / scheduler: the Doppelganger joins and hunts "victim". */
    public boolean spawn(Player victim, String copyOf) {
        if (active() || victim == null) {
            return false;
        }
        target = victim.getUniqueId();
        copyName = copyOf;
        glitched = glitch(copyOf);
        Player original = Bukkit.getPlayerExact(copyOf);
        ResolvableProfile skin = ResolvableProfile.resolvableProfile((original != null ? original : victim).getPlayerProfile());
        rockets = (int) cfg("elytra-rockets", 16);
        apples = (int) cfg("golden-apples", 3);
        eatingTicks = 0;
        absorb = 0;
        maxHp = cfg("health", 600);
        hp = maxHp;
        healsUsed = 0;
        damage.clear();
        long now = System.currentTimeMillis();
        endsAt = now + (long) (cfg("hunt-minutes", 30) * 60_000L);
        nextAbilityAt = now + 4000;
        nextBlinkAt = now + 15000;
        nextChatAt = now + 20000;
        offlineSince = 0;
        Location at = spawnSpot(victim);
        List<ItemStack> stolen = stolen();
        body = at.getWorld().spawn(at, Mannequin.class, m -> {
            m.setProfile(skin);
            m.setDescription(null);
            m.customName(glitched);
            m.setCustomNameVisible(true);
            m.setPersistent(false);
            m.setInvulnerable(false);
            m.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1);
            AttributeInstance kb = m.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.setBaseValue(Math.max(0, Math.min(1, cfg("knockback-resistance", 0.0))));
            }
            EntityEquipment eq = m.getEquipment();
            eq.setHelmet(trimmed(Material.NETHERITE_HELMET));
            eq.setChestplate(plugin.getConfig().getBoolean("doppelganger.elytra", false)
                    ? new ItemStack(Material.ELYTRA) : trimmed(Material.NETHERITE_CHESTPLATE));
            eq.setLeggings(trimmed(Material.NETHERITE_LEGGINGS));
            eq.setBoots(trimmed(Material.NETHERITE_BOOTS));
            eq.setItemInMainHand(handItem(stolen));
            if (plugin.getConfig().getBoolean("doppelganger.wind-charges", true)) {
                eq.setItemInOffHand(new ItemStack(Material.WIND_CHARGE, 16));
            }
        });
        bar = BossBar.bossBar(glitched.color(NamedTextColor.WHITE), 1f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_20);
        // a perfectly normal join message
        Bukkit.broadcast(Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW, glitched));
        glitchFx(at);
        if (!stolen.isEmpty()) {
            chat("i still have your " + plain(stolen.get(0)) + ", " + copyOf);
        }
        plugin.getLogger().info("Doppelganger of " + copyOf + " is hunting " + victim.getName());
        return true;
    }

    /** Netherite with the armor trim from the config (doppelganger.trim). */
    private ItemStack trimmed(Material piece) {
        ItemStack stack = new ItemStack(piece);
        if (!plugin.getConfig().getBoolean("doppelganger.trim.enabled", true)) {
            return stack;
        }
        try {
            var access = io.papermc.paper.registry.RegistryAccess.registryAccess();
            org.bukkit.inventory.meta.trim.TrimPattern pattern = access
                    .getRegistry(io.papermc.paper.registry.RegistryKey.TRIM_PATTERN)
                    .get(NamespacedKey.minecraft(plugin.getConfig().getString("doppelganger.trim.pattern", "silence")));
            org.bukkit.inventory.meta.trim.TrimMaterial material = access
                    .getRegistry(io.papermc.paper.registry.RegistryKey.TRIM_MATERIAL)
                    .get(NamespacedKey.minecraft(plugin.getConfig().getString("doppelganger.trim.material", "quartz")));
            if (pattern != null && material != null
                    && stack.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta meta) {
                meta.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(material, pattern));
                stack.setItemMeta(meta);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Doppelganger: bad armor trim in the config (" + ex.getMessage() + ")");
        }
        return stack;
    }

    private static String plain(ItemStack stack) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(ItemManager.nameOf(stack));
    }

    /** A legendary weapon for its hand: a stolen one if it has one, otherwise a copy that drops nothing. */
    private ItemStack handItem(List<ItemStack> stolen) {
        for (ItemStack s : stolen) {
            ItemManager.Entry e = plugin.items().customOf(s);
            if (e != null && e.kind == ItemManager.Kind.WEAPON) {
                return s.clone();
            }
        }
        List<ItemManager.Entry> weapons = plugin.items().all(ItemManager.Kind.WEAPON);
        return weapons.isEmpty() ? new ItemStack(Material.NETHERITE_SWORD)
                : weapons.get(ThreadLocalRandom.current().nextInt(weapons.size())).item.clone();
    }

    private void chat(String line) {
        String text = line.replace("{target}", copyName == null ? "" : copyName);
        Bukkit.broadcast(Component.text("<").append(glitched.color(NamedTextColor.WHITE)).append(Component.text("> " + text)));
    }

    private void glitchFx(Location at) {
        World w = at.getWorld();
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 40, 0.4, 0.9, 0.4, 0, BLACK);
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 20, 0.4, 0.9, 0.4, 0, WHITE);
        w.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        w.playSound(at, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1f, 0.6f);
    }

    // ================================================================ AI

    /** A direction straight up/down normalizes to NaN; such a velocity would throw ("x not finite"). */
    private static Vector finite(Vector v) {
        return Double.isFinite(v.getX()) && Double.isFinite(v.getY()) && Double.isFinite(v.getZ()) ? v : new Vector();
    }

    private long lastTickError;
    /** who set it on fire (Fire Aspect / flame arrow) and until when that burning counts */
    private UUID fireBy;
    private long fireUntil;

    /** Melee range like a player's: measured flat between the two bodies, and only when roughly level. */
    private static boolean inReach(Location me, Location other, double reach) {
        double dx = me.getX() - other.getX();
        double dz = me.getZ() - other.getZ();
        double dy = other.getY() - me.getY();
        return dx * dx + dz * dz <= reach * reach && dy > -1.5 && dy < 2.2;
    }
    /** after a hit it stops steering for a moment, so knockback actually moves it */
    private long staggerUntil;

    /** One bad tick (odd geometry, a world edge...) must not stop the whole fight or hide its boss bar. */
    private void safeTick() {
        try {
            tick();
        } catch (RuntimeException ex) {
            long now = System.currentTimeMillis();
            if (now - lastTickError > 60_000) {
                lastTickError = now;
                plugin.getLogger().warning("Doppelganger tick: " + ex);
            }
        }
        // boss bar for everyone near (outside tick(), so it's there even when tick() returns early)
        if (active() && bar != null && body.getTicksLived() % 10 == 0) {
            updateBar();
            Location me = body.getLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld() == me.getWorld() && p.getLocation().distanceSquared(me) < 64 * 64) {
                    p.showBossBar(bar);
                } else {
                    p.hideBossBar(bar);
                }
            }
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Player victim = Bukkit.getPlayer(target);
        // offline, or out of reach in creative / spectator: it waits (and leaves after 2 minutes)
        if (victim == null || !victim.isOnline()
                || victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
            if (offlineSince == 0) {
                offlineSince = now;
            }
            if (now - offlineSince > 120_000) {
                leave(true);
            }
            return;
        }
        offlineSince = 0;
        if (now >= endsAt) {
            chat("not today. soon.");
            leave(true);
            return;
        }
        Location me = body.getLocation();
        if (me.getY() < me.getWorld().getMinHeight() + 2 || victim.getWorld() != me.getWorld()
                || me.distanceSquared(victim.getLocation()) > Math.pow(cfg("teleport-distance", 60), 2)) {
            stopGliding();
            blink(victim);
            return;
        }
        if (victim.isDead()) {
            return;
        }
        Location there = victim.getLocation();
        double dist = me.distance(there);
        // face the victim
        Vector to = there.toVector().subtract(me.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.getY(), Math.hypot(to.getX(), to.getZ())));
        body.setRotation(yaw, pitch);
        body.setBodyYaw(yaw);
        // low on health: eats a golden apple (backs off while eating)
        if (eat(victim, to, now)) {
            // eating
        } else if (glide(victim, dist, now)) {
            // flying, nothing else moves it
        } else if (digTick(victim, to, dist)) {
            // digging through a wall
        } else if (now < staggerUntil) {
            // just got hit: let the knockback play out instead of walking straight through it
        } else if (dist > 2.2 && now >= flyingUntil) {
            Vector flat = to.clone().setY(0);
            if (flat.lengthSquared() > 1.0E-4) {
                flat.normalize();
            }
            double speed = cfg("speed", 0.19);
            // strafe a little when close, so it isn't a straight line
            if (dist < 7) {
                Vector side = new Vector(-flat.getZ(), 0, flat.getX()).multiply(Math.sin(now / 400.0) * 0.5);
                flat.add(side).normalize();
            }
            Vector v = body.getVelocity();
            Vector move = flat.clone().multiply(speed);
            double vy = v.getY();
            // a one-block step right in front of it?
            Block ahead = me.clone().add(flat.clone().multiply(0.8)).getBlock();
            boolean wall = ahead.getType().isSolid() && ahead.getRelative(0, 1, 0).isPassable()
                    && ahead.getRelative(0, 2, 0).isPassable() && me.clone().add(0, 2, 0).getBlock().isPassable();
            boolean grounded = body.isOnGround()
                    || me.clone().add(0, -0.05, 0).getBlock().getType().isSolid() && Math.abs(vy) < 0.1;
            if (wall) {
                blockedTicks++;
                if (grounded) {
                    vy = 0.5;
                }
            } else {
                blockedTicks = 0;
            }
            if (body.isInWater() || me.getBlock().isLiquid()) {
                vy = 0.12;
            }
            body.setVelocity(finite(new Vector(move.getX(), vy, move.getZ())));
        }
        // stuck -> glitch closer
        if (body.getTicksLived() % 60 == 0) {
            if (lastPos != null && lastPos.getWorld() == me.getWorld() && lastPos.distanceSquared(me) < 2 && dist > 5) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastPos = me.clone();
            if (digBlock != null) {
                stuckTicks = 0;                                 // digging is progress
            }
            if (stuckTicks >= 4) {
                // couldn't walk, jump or dig there for ~12 s (e.g. a protected block): last resort
                stuckTicks = 0;
                blink(victim);
                return;
            }
        }
        if (eatingTicks > 0) {
            return;                                             // no attacks while eating
        }
        // melee
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        double reach = cfg("attack-reach", 2.0);
        if (inReach(me, victim.getLocation(), reach) && attackCooldown == 0 && body.hasLineOfSight(victim)) {
            attackCooldown = 12;
            body.swingMainHand();
            hit(victim, cfg("attack-damage", 9));
            victim.setVelocity(finite(victim.getVelocity().add(to.clone().setY(0).normalize().multiply(0.4).setY(0.25))));
        }
        // other players close by get hit too
        if (attackCooldown == 6) {
            for (Player p : me.getWorld().getPlayers()) {
                if (!p.equals(victim) && survival(p) && inReach(me, p.getLocation(), reach)) {
                    body.swingMainHand();
                    hit(p, cfg("attack-damage", 9) * 0.7);
                    break;
                }
            }
        }
        // wind charges: jump up to the victim, chase, or blow them away
        if (now >= nextWindAt && plugin.getConfig().getBoolean("doppelganger.wind-charges", true)) {
            wind(victim, dist, now);
        }
        // abilities
        if (now >= nextAbilityAt) {
            nextAbilityAt = now + (long) (cfg("ability-seconds", 5) * 1000);
            ability(victim, dist);
        }
        if (now >= nextChatAt) {
            nextChatAt = now + ThreadLocalRandom.current().nextLong(40_000, 90_000);
            List<String> lines = plugin.getConfig().getStringList("doppelganger.chat-lines");
            if (lines.isEmpty()) {
                lines = DEFAULT_LINES;
            }
            chat(lines.get(ThreadLocalRandom.current().nextInt(lines.size())));
        }
        if (body.getTicksLived() % 3 == 0) {
            me.getWorld().spawnParticle(Particle.DUST, me.clone().add(0, 1, 0), 2, 0.3, 0.8, 0.3, 0,
                    ThreadLocalRandom.current().nextBoolean() ? BLACK : WHITE);
        }
    }

    /** Boss bar: its name, health in numbers (+ golden apple absorption), red when it's almost dead. */
    private void updateBar() {
        if (bar == null) {
            return;
        }
        double ratio = Math.max(0, Math.min(1, hp / maxHp));
        bar.progress((float) ratio);
        bar.color(ratio < 0.25 ? BossBar.Color.RED : ratio < 0.5 ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
        Component title = glitched.color(NamedTextColor.WHITE)
                .append(Component.text("   ❤ " + (int) Math.ceil(Math.max(0, hp)) + " / " + (int) maxHp,
                        ratio < 0.25 ? NamedTextColor.RED : NamedTextColor.GRAY));
        if (absorb >= 1) {
            title = title.append(Component.text("  +" + (int) Math.ceil(absorb), NamedTextColor.GOLD));
        }
        bar.name(title);
    }

    private void hit(Player p, double amount) {
        if (p.equals(Bukkit.getPlayer(target))) {
            lastHitOnTarget = System.currentTimeMillis();
        }
        p.damage(amount, body);
    }

    private void ability(Player victim, double dist) {
        World w = body.getWorld();
        Location me = body.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (dist < 6) {
            // frost wave: everyone close gets slowed and hurt
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI / 20;
                w.spawnParticle(Particle.DUST, me.clone().add(Math.cos(a) * 4, 0.3, Math.sin(a) * 4), 2, 0.1, 0.1, 0.1, 0, ICE);
            }
            w.spawnParticle(Particle.SNOWFLAKE, me.clone().add(0, 1, 0), 60, 2, 0.5, 2, 0.05);
            w.playSound(me, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
            for (Player p : w.getPlayers()) {
                if (survival(p) && p.getLocation().distanceSquared(me) < 25) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                    p.setFreezeTicks(Math.max(p.getFreezeTicks(), 100));
                    hit(p, cfg("ability-damage", 6));
                }
            }
        } else if (dist < 14 && r.nextBoolean()) {
            // echo dash: a burst straight at the victim
            Vector dir = victim.getLocation().toVector().subtract(me.toVector()).normalize();
            body.setVelocity(finite(dir.multiply(1.8).setY(0.25)));
            w.playSound(me, Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.7f);
            w.spawnParticle(Particle.SONIC_BOOM, me.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        } else if (dist < 22 && r.nextBoolean()) {
            // gravity pull: drags the victim in
            Location from = me.clone().add(0, 1.2, 0);
            Vector line = victim.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            double len = line.length();
            line.normalize();
            for (double d = 0; d < len; d += 0.5) {
                w.spawnParticle(Particle.DUST, from.clone().add(line.clone().multiply(d)), 1, 0, 0, 0, 0, VOID);
            }
            w.playSound(victim.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f);
            Vector pull = me.toVector().subtract(victim.getLocation().toVector()).normalize().multiply(Math.min(2.2, 0.6 + len * 0.08));
            victim.setVelocity(finite(pull.setY(0.4)));
            hit(victim, cfg("ability-damage", 6) * 0.5);
        } else if (body.hasLineOfSight(victim)) {
            // arrow volley
            Location eye = body.getEyeLocation();
            Vector aim = victim.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
            for (int i = 0; i < 5; i++) {
                Vector spread = aim.clone().add(new Vector(r.nextDouble(-0.08, 0.08), r.nextDouble(-0.04, 0.06), r.nextDouble(-0.08, 0.08)));
                Arrow arrow = w.spawnArrow(eye.clone().add(aim), spread, 2.2f, 0f);
                arrow.setShooter(body);
                arrow.setDamage(cfg("arrow-damage", 3));
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1);
            }
            w.playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 1.5f, 0.7f);
        }
    }

    private void wind(Player victim, double dist, long now) {
        Location me = body.getLocation();
        double up = victim.getLocation().getY() - me.getY();
        boolean grounded = body.isOnGround() || me.clone().add(0, -0.05, 0).getBlock().getType().isSolid();
        long cooldown = (long) (cfg("wind-charge-seconds", 4) * 1000);
        Vector toward = victim.getLocation().toVector().subtract(me.toVector()).setY(0);
        if (toward.lengthSquared() > 1.0E-4) {
            toward.normalize();
        }
        if (grounded && (up > 2.5 && dist < 14 || dist > 18 && dist < 40)) {
            // a wind charge at its own feet: launches it up and forward
            burst(me);
            double lift = up > 2.5 ? Math.min(1.6, 0.9 + up * 0.12) : 0.9;
            body.setVelocity(finite(toward.clone().multiply(up > 2.5 ? 0.6 : 1.3).setY(lift)));
            flyingUntil = now + 900;
            nextWindAt = now + cooldown;
        } else if (dist > 4 && dist < 20 && body.hasLineOfSight(victim) && ThreadLocalRandom.current().nextInt(3) == 0) {
            // thrown at the victim
            Vector aim = victim.getLocation().add(0, 0.8, 0).toVector().subtract(body.getEyeLocation().toVector()).normalize();
            body.swingOffHand();
            body.launchProjectile(org.bukkit.entity.WindCharge.class, aim.multiply(1.5));
            me.getWorld().playSound(me, Sound.ENTITY_WIND_CHARGE_THROW, 1f, 0.8f);
            nextWindAt = now + cooldown;
        } else {
            nextWindAt = now + 500;
        }
    }

    private void burst(Location at) {
        World w = at.getWorld();
        w.spawnParticle(Particle.GUST_EMITTER_SMALL, at, 1, 0, 0, 0, 0);
        w.playSound(at, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
        body.swingOffHand();
    }

    // ================================================================ golden apples

    /** Returns true while it is eating (it backs away slowly and doesn't attack). */
    private boolean eat(Player victim, Vector to, long now) {
        if (eatingTicks > 0) {
            eatingTicks--;
            Location me = body.getLocation();
            Vector away = to.clone().setY(0);
            if (away.lengthSquared() > 1.0E-4) {
                away.normalize().multiply(-0.12);
            }
            body.setVelocity(finite(new Vector(away.getX(), Math.min(0, body.getVelocity().getY()), away.getZ())));
            if (eatingTicks % 4 == 0) {
                me.getWorld().playSound(me, Sound.ENTITY_GENERIC_EAT, 1f, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
                me.getWorld().spawnParticle(Particle.ITEM, body.getEyeLocation().add(body.getLocation().getDirection().multiply(0.3)),
                        5, 0.1, 0.1, 0.1, 0.05, new ItemStack(Material.GOLDEN_APPLE));
                body.swingMainHand();
            }
            if (eatingTicks == 0) {
                hp = Math.min(maxHp, hp + maxHp * cfg("golden-apple-heal-percent", 12) / 100.0);
                absorb = maxHp * cfg("golden-apple-absorption-percent", 5) / 100.0;
                updateBar();
                me.getWorld().playSound(me, Sound.ENTITY_PLAYER_BURP, 1f, 0.9f);
                me.getWorld().spawnParticle(Particle.HEART, me.clone().add(0, 2.1, 0), 3, 0.3, 0.2, 0.3, 0);
                if (eatSwap != null) {
                    body.getEquipment().setItemInMainHand(eatSwap);
                    eatSwap = null;
                }
                nextEatAt = now + (long) (cfg("golden-apple-cooldown", 8) * 1000);
            }
            return true;
        }
        if (apples > 0 && now >= nextEatAt && hp < maxHp * cfg("eat-below-percent", 40) / 100.0
                && digBlock == null && !gliding) {
            apples--;
            eatingTicks = 32;                                   // 1.6 s, like a player
            eatSwap = body.getEquipment().getItemInMainHand();
            body.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_APPLE, Math.max(1, apples + 1)));
            return true;
        }
        return false;
    }

    // ================================================================ digging

    private boolean diggable(Block b) {
        if (b.isEmpty() || b.isLiquid() || b.isPassable()) {
            return false;
        }
        Material m = b.getType();
        if (m.getHardness() < 0 || plugin.getConfig().getStringList("doppelganger.dig-never").contains(m.name())) {
            return false;
        }
        if (b.getState() instanceof org.bukkit.block.TileState) {
            return false;                                  // chests, furnaces, signs, spawners... never
        }
        var data = b.getBlockData();
        return !(data instanceof org.bukkit.block.data.Bisected) && !(data instanceof org.bukkit.block.data.type.Bed);
    }

    /** The next block in its way to the victim, or null. */
    private Block nextObstacle(Vector to, double dist) {
        Location me = body.getLocation();
        Vector flat = to.clone().setY(0);
        double horiz = flat.length();
        Block feet = me.getBlock();
        if (to.getY() < -1.5 && horiz < 2.5) {
            Block below = feet.getRelative(0, -1, 0);
            return diggable(below) ? below : null;
        }
        if (to.getY() > 2 && horiz < 2.5) {
            Block above = feet.getRelative(0, 2, 0);
            return diggable(above) ? above : null;
        }
        if (horiz < 1.0E-3) {
            return null;
        }
        flat.normalize();
        Block ahead = me.clone().add(flat.clone().multiply(0.8)).getBlock();
        Block aheadHead = ahead.getRelative(0, 1, 0);
        // a single step is jumped, not dug
        if (ahead.getType().isSolid() && aheadHead.isPassable() && ahead.getRelative(0, 2, 0).isPassable()) {
            return null;
        }
        if (diggable(aheadHead) && aheadHead.getType().isSolid()) {
            return aheadHead;
        }
        if (diggable(ahead) && ahead.getType().isSolid()) {
            return ahead;
        }
        return null;
    }

    /** Digs through whatever is in the way. Returns true while digging (it doesn't walk then). */
    private boolean digTick(Player victim, Vector to, double dist) {
        if (!plugin.getConfig().getBoolean("doppelganger.dig", true) || dist < 1.5) {
            stopDigging();
            return false;
        }
        Block next = nextObstacle(to, dist);
        if (next == null) {
            stopDigging();
            return false;
        }
        if (!next.equals(digBlock)) {
            digBlock = next;
            digProgress = 0;
            float hardness = next.getType().getHardness();
            digNeeded = (int) Math.max(4, Math.min(60, (4 + hardness * 6) * cfg("dig-time-multiplier", 1.0)));
            if (weapon == null) {
                weapon = body.getEquipment().getItemInMainHand();
                body.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_PICKAXE));
            }
        }
        body.setVelocity(finite(new Vector(0, Math.min(0, body.getVelocity().getY()), 0)));
        digProgress++;
        Location loc = next.getLocation();
        float stage = Math.min(0.99f, (float) digProgress / digNeeded);
        for (Player p : next.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 48 * 48) {
                p.sendBlockDamage(loc, stage, body);
            }
        }
        if (digProgress % 4 == 0) {
            body.swingMainHand();
            next.getWorld().playSound(loc, next.getBlockSoundGroup().getHitSound(), 0.8f, 0.8f);
        }
        if (digProgress >= digNeeded) {
            Location key = loc.clone();
            if (!dug.containsKey(key)) {
                dug.put(key, next.getBlockData().getAsString());
                dugAt.put(key, System.currentTimeMillis());
                saveDug();
            }
            next.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, 0,
                    next.getBlockData());
            next.getWorld().playSound(loc, next.getBlockSoundGroup().getBreakSound(), 1f, 0.8f);
            next.setType(Material.AIR, false);
            digBlock = null;
        }
        return true;
    }

    private void stopDigging() {
        if (digBlock != null) {
            for (Player p : digBlock.getWorld().getPlayers()) {
                p.sendBlockDamage(digBlock.getLocation(), 0f, body);
            }
        }
        digBlock = null;
        if (weapon != null && active()) {
            body.getEquipment().setItemInMainHand(weapon);
        }
        weapon = null;
    }

    private void saveDug() {
        List<String> list = new ArrayList<>();
        dug.forEach((loc, d) -> list.add(loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";"
                + loc.getBlockZ() + ";" + d));
        data.set("dug", list);
        save();
    }

    /** Puts dug blocks back: after dig-restore-seconds, or all of them when it leaves (force). */
    private void restoreDug(boolean force) {
        if (dug.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long wait = (long) (cfg("dig-restore-seconds", 60) * 1000);
        boolean changed = false;
        for (Iterator<Map.Entry<Location, String>> it = dug.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Location, String> e = it.next();
            Location loc = e.getKey();
            if (!force && now - dugAt.getOrDefault(loc, 0L) < wait) {
                continue;
            }
            if (!loc.isChunkLoaded() && !force) {
                continue;
            }
            org.bukkit.util.BoundingBox box = org.bukkit.util.BoundingBox.of(loc.getBlock());
            boolean occupied = !loc.getWorld().getNearbyEntities(box, en -> en instanceof org.bukkit.entity.LivingEntity).isEmpty();
            if (occupied && !force) {
                continue;
            }
            try {
                Block b = loc.getBlock();
                if (b.isEmpty() || b.isPassable()) {
                    b.setBlockData(Bukkit.createBlockData(e.getValue()), false);
                    loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0, BLACK);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Could not restore a block at " + loc + ": " + ex.getMessage());
            }
            if (occupied) {
                for (org.bukkit.entity.Entity en : loc.getWorld().getNearbyEntities(box)) {
                    en.teleport(en.getLocation().add(0, 1.1, 0));    // don't bury anyone
                }
            }
            it.remove();
            dugAt.remove(loc);
            changed = true;
        }
        if (changed) {
            saveDug();
        }
    }

    /** After a crash: put back blocks that were still dug out. */
    private void loadDug() {
        for (String line : data.getStringList("dug")) {
            String[] p = line.split(";", 5);
            if (p.length < 5 || Bukkit.getWorld(p[0]) == null) {
                continue;
            }
            Location loc = new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            dug.put(loc, p[4]);
            dugAt.put(loc, 0L);
        }
    }

    // ================================================================ elytra

    /**
     * With "elytra: true" it flies after victims that are far away, like a player: a wind charge to take off,
     * then firework rockets for speed. Without rockets left it only glides. Returns true while flying.
     */
    private boolean glide(Player victim, double dist, long now) {
        if (!plugin.getConfig().getBoolean("doppelganger.elytra", false)) {
            return false;
        }
        Location me = body.getLocation();
        boolean grounded = body.isOnGround() || me.clone().add(0, -0.05, 0).getBlock().getType().isSolid();
        if (gliding) {
            if (dist < 5 || grounded && now > flyingUntil) {
                stopGliding();
                return false;
            }
            Vector aim = victim.getLocation().add(0, 2, 0).toVector().subtract(me.toVector()).normalize();
            if (rockets > 0 && now >= nextRocketAt && dist > 10) {
                rockets--;
                nextRocketAt = now + 1500;
                boostUntil = now + 1100;
                fireRocket();
            }
            Vector v = body.getVelocity();
            Vector want;
            if (now < boostUntil) {
                want = aim.multiply(cfg("elytra-speed", 1.6));                      // rocket boost
            } else {
                // plain gliding: keeps its speed, turns slowly, sinks a little
                double speed = Math.max(0.6, v.length() * 0.99);
                want = v.clone().normalize().multiply(0.8).add(aim.clone().multiply(0.2)).normalize().multiply(speed);
                want.setY(Math.min(want.getY(), -0.08));
            }
            body.setVelocity(finite(v.multiply(0.6).add(want.multiply(0.4))));
            body.setGliding(true);
            return true;
        }
        if (dist > cfg("elytra-distance", 25) && grounded && now >= nextWindAt && (rockets > 0 || dist < 45)) {
            burst(me);
            body.setVelocity(finite(new Vector(0, 1.5, 0)));
            nextWindAt = now + 4000;
            flyingUntil = now + 1500;
            nextRocketAt = now + 700;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (active()) {
                    gliding = true;
                    body.setGliding(true);
                    body.getEquipment().setItemInOffHand(new ItemStack(Material.FIREWORK_ROCKET, Math.max(1, rockets)));
                }
            }, 10L);
            return true;
        }
        return false;
    }

    /** A real (harmless) firework rocket on its back, like a player boosting. */
    private void fireRocket() {
        Location at = body.getLocation();
        body.swingOffHand();
        at.getWorld().spawn(at, org.bukkit.entity.Firework.class, f -> {
            f.setAttachedTo(body);
            f.setTicksToDetonate(20);
            f.setSilent(false);
            f.getPersistentDataContainer().set(ghostKey, PersistentDataType.BYTE, (byte) 1);
        });
        at.getWorld().playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 1f);
        ItemStack off = body.getEquipment().getItemInOffHand();
        if (off.getType() == Material.FIREWORK_ROCKET) {
            off.setAmount(Math.max(1, rockets));
            body.getEquipment().setItemInOffHand(rockets > 0 ? off : new ItemStack(Material.WIND_CHARGE, 16));
        }
    }

    private void stopGliding() {
        if (gliding && active()) {
            body.setGliding(false);
            body.getEquipment().setItemInOffHand(new ItemStack(Material.WIND_CHARGE, 16));
        }
        gliding = false;
    }

    /** Teleport right behind the victim. */
    private void blinkBehind(Player victim) {
        Location behind = victim.getLocation().clone();
        Vector back = behind.getDirection().setY(0);
        if (back.lengthSquared() < 1.0E-4) {
            back = new Vector(1, 0, 0);
        }
        behind.subtract(back.normalize().multiply(2.5));
        Block feet = behind.getBlock();
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
            behind = victim.getLocation();
        }
        glitchFx(body.getLocation());
        body.teleport(behind);
        glitchFx(behind);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.5f, 0.6f);
    }

    /** Teleport somewhere near the victim (stuck, fell, other world). */
    private void blink(Player victim) {
        Location at = spawnSpot(victim);
        if (at.distanceSquared(victim.getLocation()) > 30 * 30) {
            at = victim.getLocation();
        }
        glitchFx(body.getLocation());
        body.teleport(at);
        glitchFx(at);
    }

    // ================================================================ damage and death

    private boolean isGhost(Entity e) {
        return e != null && e.getPersistentDataContainer().has(ghostKey, PersistentDataType.BYTE);
    }

    /** It has its own health pool: hits are counted, everything else (fire, fall, lava) is ignored. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!active() || !event.getEntity().equals(body)) {
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            event.setCancelled(true);
            // burning from a player's Fire Aspect / flame arrow counts for that player; other fire doesn't
            if ((event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK)
                    && fireBy != null && System.currentTimeMillis() < fireUntil) {
                Player burner = Bukkit.getPlayer(fireBy);
                if (burner != null) {
                    body.playHurtAnimation(0);
                    take(burner, Math.max(0, event.getDamage()) * cfg("damage-taken-multiplier", 0.6));
                }
                return;
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.LAVA || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Player victim = Bukkit.getPlayer(target);
                if (victim != null) {
                    blink(victim);
                }
            }
            return;
        }
        Player attacker = byEntity.getDamager() instanceof Player p ? p
                : byEntity.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        if (attacker == null || !survival(attacker)) {
            event.setCancelled(true);                  // creative / spectator hits don't count
            return;
        }
        double amount = Math.max(0, event.getFinalDamage()) * cfg("damage-taken-multiplier", 0.6);
        boolean fire = byEntity.getDamager() instanceof Projectile pr2 ? pr2.getFireTicks() > 0
                : attacker.getInventory().getItemInMainHand().getEnchantmentLevel(
                        org.bukkit.enchantments.Enchantment.FIRE_ASPECT) > 0;
        if (fire) {
            fireBy = attacker.getUniqueId();
            fireUntil = System.currentTimeMillis() + 8000;
        }
        // The hit goes through as a tiny real hit (not cancelled): weapons that need a successful hit work,
        // e.g. the mace smash bounce (no fall damage for the attacker), sweeping and knockback.
        // Its real health pool is "hp"; the body itself is healed right back.
        for (EntityDamageEvent.DamageModifier mod : EntityDamageEvent.DamageModifier.values()) {
            if (mod != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(mod)) {
                event.setDamage(mod, 0);
            }
        }
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, 0.01);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (body != null && body.isValid()) {
                AttributeInstance max = body.getAttribute(Attribute.MAX_HEALTH);
                body.setHealth(max != null ? max.getValue() : 20);
                // it flies further than a normal mob when hit (doppelganger.knockback-taken)
                Vector v = body.getVelocity();
                double m = cfg("knockback-taken", 1.8);
                body.setVelocity(finite(new Vector(v.getX() * m, v.getY(), v.getZ() * m)));
            }
        });
        staggerUntil = System.currentTimeMillis() + 600;
        take(attacker, amount);                       // the hurt animation and sound come from the real hit
    }

    /** Damage to its own health pool, counted for the attacker's reward. */
    private void take(Player attacker, double amount) {
        if (!active()) {
            return;
        }
        double soaked = Math.min(absorb, amount);          // golden apple absorption goes first
        absorb -= soaked;
        hp -= amount - soaked;
        damage.merge(attacker.getUniqueId(), amount, Double::sum);
        updateBar();
        if (hp <= 0) {
            die(attacker);
            return;
        }
        // at 50 % and 20 %: glitches away for a moment and heals a bit
        if (healsUsed == 0 && hp < maxHp * 0.5 || healsUsed == 1 && hp < maxHp * 0.2) {
            healsUsed++;
            hp = Math.min(maxHp, hp + maxHp * 0.1);
            updateBar();
            body.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
            glitchFx(body.getLocation());
            chat(healsUsed == 1 ? "that hurt. do it again." : "you can't kill what you made");
        }
    }

    /** Its arrows don't hit itself. */
    @EventHandler(ignoreCancelled = true)
    public void onArrow(EntityDamageByEntityEvent event) {
        if (isGhost(event.getDamager()) && isGhost(event.getEntity())) {
            event.setCancelled(true);
        } else if (isGhost(event.getDamager()) && target != null && event.getEntity().getUniqueId().equals(target)) {
            lastHitOnTarget = System.currentTimeMillis();
        }
    }

    private void die(Player killer) {
        Location at = body.getLocation();
        World w = at.getWorld();
        // a perfectly normal death message, then it leaves
        Bukkit.broadcast(Component.translatable("death.attack.player", glitched, Component.text(killer.getName())));
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 120, 0.6, 1, 0.6, 0, BLACK);
        w.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1, 0), 3, 0.4, 0.6, 0.4, 0);
        w.playSound(at, Sound.ENTITY_WARDEN_DEATH, 1.5f, 1.2f);
        // the stolen legends come back
        for (ItemStack s : stolen()) {
            dropSafe(at, s);
        }
        data.set("stolen", null);
        // reward for the top damage dealer (fallback: the killer)
        UUID best = damage.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(killer.getUniqueId());
        Player winner = Bukkit.getPlayer(best) != null ? Bukkit.getPlayer(best) : killer;
        int tokens = plugin.shop() != null && plugin.shop().enabled() ? (int) cfg("reward-tokens", 25) : 0;
        if (tokens > 0 && plugin.bounty() != null) {
            dropSafe(at, plugin.bounty().token(tokens));
        }
        if (plugin.getConfig().getBoolean("doppelganger.reward-material", true) && plugin.bounty() != null) {
            List<ItemManager.Entry> mats = plugin.bounty().enabledMaterials();
            if (!mats.isEmpty()) {
                dropSafe(at, mats.get(ThreadLocalRandom.current().nextInt(mats.size())).item.clone());
            }
        }
        w.spawn(at, org.bukkit.entity.ExperienceOrb.class, o -> o.setExperience((int) cfg("xp", 500)));
        if (plugin.top() != null) {
            plugin.top().add(winner, "ghosts");
        }
        Bukkit.broadcast(plugin.msg("ghost-killed", "&8☠ &7The copy of &f{copy} &7is gone. &f{player} &7ended it.",
                "copy", copyName, "player", winner.getName()));
        resetTop();
        leave(true);
    }

    private void dropSafe(Location at, ItemStack stack) {
        at.getWorld().dropItem(at.clone().add(0, 0.5, 0), stack, i -> {
            i.setInvulnerable(true);
            i.setUnlimitedLifetime(true);
            i.setGlowing(true);
        });
    }

    /** Its target died: if the Doppelganger did it, it takes one of their legends. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVictimDeath(PlayerDeathEvent event) {
        if (!active() || !event.getPlayer().getUniqueId().equals(target)) {
            return;
        }
        Player victim = event.getPlayer();
        boolean byGhost = System.currentTimeMillis() - lastHitOnTarget < 5000
                || event.getDamageSource().getCausingEntity() != null && isGhost(event.getDamageSource().getCausingEntity());
        if (!byGhost) {
            return;
        }
        event.deathMessage(Component.translatable("death.attack.player", victim.displayName(), glitched));
        if (plugin.getConfig().getBoolean("doppelganger.steal-legend", true)
                && stolen().size() < (int) cfg("max-stolen", 3)) {
            ItemStack taken = takeLegend(event);
            if (taken != null) {
                List<ItemStack> list = stolen();
                list.add(taken);
                data.set("stolen.items", list);
                data.set("stolen.victim", victim.getUniqueId().toString());
                data.set("stolen.victim-name", victim.getName());
                data.set("stolen.next-return", System.currentTimeMillis() + (long) (cfg("return-minutes", 30) * 60_000L));
                save();
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(plugin.msg("ghost-stole",
                        "&8☠ &7The copy of &f{copy} &7took &r{item}&7. It will be back. Kill it to get it back.",
                        "copy", copyName, "item", ItemManager.nameOf(taken))));
            }
        }
        // a bounty claimed by nobody: it just resets
        if (plugin.bounty() != null) {
            plugin.bounty().addBounty(victim, -plugin.bounty().bountyOf(victim.getUniqueId()));
        }
        resetTop();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active()) {
                chat("thank you, " + victim.getName());
                leave(true);
            }
        }, 40L);
    }

    /** Removes one legend from the drops (or the inventory with keepInventory). */
    private ItemStack takeLegend(PlayerDeathEvent event) {
        for (Iterator<ItemStack> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            ItemManager.Entry e = plugin.items().customOf(s);
            if (e != null && e.kind != ItemManager.Kind.MATERIAL) {
                it.remove();
                return s;
            }
        }
        if (event.getKeepInventory()) {
            ItemStack[] contents = event.getPlayer().getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemManager.Entry e = plugin.items().customOf(contents[i]);
                if (e != null && e.kind != ItemManager.Kind.MATERIAL) {
                    ItemStack s = contents[i];
                    event.getPlayer().getInventory().setItem(i, null);
                    return s;
                }
            }
        }
        return null;
    }

    private void resetTop() {
        data.set("top.since", System.currentTimeMillis());
        save();
    }

    /** It "leaves the game". */
    public void leave(boolean message) {
        eatingTicks = 0;
        eatSwap = null;
        stopDigging();
        stopGliding();
        if (body != null && body.isValid()) {
            glitchFx(body.getLocation());
            body.remove();
        }
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        if (message && glitched != null) {
            Bukkit.broadcast(Component.translatable("multiplayer.player.left", NamedTextColor.YELLOW, glitched));
        }
        body = null;
        bar = null;
        target = null;
    }

    /** Admin: /forge ghost [player] */
    public boolean forceSpawn(Player victim) {
        return spawn(victim, victim.getName());
    }
}
