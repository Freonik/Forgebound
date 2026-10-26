package cz.forgebound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Season start ceremony: countdown, "Welcome to ... SMP", effects around the host, a white beam into the sky,
 * then everyone is scattered across the SMP world. /forge season start | preview | cancel
 */
public final class SeasonManager implements org.bukkit.event.Listener {

    private static final java.util.Set<Material> DANGER = java.util.EnumSet.of(Material.CACTUS, Material.MAGMA_BLOCK,
            Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE,
            Material.SOUL_FIRE, Material.LAVA, Material.POINTED_DRIPSTONE, Material.WITHER_ROSE, Material.BUBBLE_COLUMN);

    /** Safe landing spots, found in the background while the ceremony plays. */
    private final List<Location> spots = new ArrayList<>();
    private int spotAttempts;
    private int spotsWanted;
    private boolean searching;
    /** Players who just landed: mobs ignore them for a moment. */
    private final java.util.Map<java.util.UUID, Long> fresh = new java.util.HashMap<>();

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 210, 90), 1.4f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.6f);

    private final Forgebound plugin;
    private BukkitRunnable running;
    private final List<BlockDisplay> beam = new ArrayList<>();
    private final List<Display> halo = new ArrayList<>();
    /** The glowing rune circle on the ground. */
    private final List<BlockDisplay> sigil = new ArrayList<>();
    /** Lines of the rune circle still to be drawn (from, to, width). */
    private final List<float[]> sigilQueue = new ArrayList<>();
    /** The spiked crown above the center: spikes first, then the band. */
    private final List<BlockDisplay> crown = new ArrayList<>();
    private int crownSpikes;
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 140, 40), 1.2f);
    private static final Particle.DustTransition COOLING =
            new Particle.DustTransition(Color.fromRGB(255, 170, 60), Color.fromRGB(70, 60, 55), 1.0f);
    private org.bukkit.entity.TextDisplay hologram;

    public SeasonManager(Forgebound plugin) {
        this.plugin = plugin;
    }

    private String cfg(String key, String def) {
        return plugin.getConfig().getString("season." + key, def);
    }

    private double num(String key, double def) {
        return plugin.getConfig().getDouble("season." + key, def);
    }

    /** "forge" (default) or "soul". */
    private boolean forge() {
        return !cfg("theme", "forge").equalsIgnoreCase("soul");
    }

    private List<ItemManager.Entry> legends() {
        List<ItemManager.Entry> list = new ArrayList<>();
        for (ItemManager.Kind kind : new ItemManager.Kind[] {ItemManager.Kind.WEAPON, ItemManager.Kind.RELIC}) {
            for (ItemManager.Entry e : plugin.items().all(kind)) {
                if (plugin.items().craftable(e)) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    public boolean running() {
        return running != null;
    }

    public void cancel() {
        searching = false;
        if (running != null) {
            running.cancel();
            running = null;
        }
        removeBeam();
        removeHalo();
        removeHologram();
        removeSigil();
        removeCrown();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
    }

    public void shutdown() {
        cancel();
    }

    private static Title title(Component t, Component s, int fadeIn, int stay, int fadeOut) {
        return Title.title(t, s, Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)));
    }

    /** Starts the ceremony around "host" (or the world spawn). teleport=false is the preview. */
    public boolean start(Player host, boolean teleport) {
        if (running != null) {
            return false;
        }
        Location center = host != null ? host.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
        World world = center.getWorld();
        int countdown = Math.max(1, (int) num("countdown-seconds", 5));
        int welcomeAt = countdown * 20;
        int beamAt = welcomeAt + 60;
        int teleportAt = beamAt + (int) (num("beam-seconds", 5) * 20);
        int endAt = teleportAt + 60;
        boolean freeze = plugin.getConfig().getBoolean("season.freeze-players", true);
        if (freeze) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(host)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, teleportAt + 20, 6, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, teleportAt + 20, 200, false, false));
                }
            }
        }
        if (teleport) {
            findSpots(Bukkit.getOnlinePlayers().size() + 5);
        }
        hologram(center, cfg("hologram-countdown", forge() ? "&6The Forge Awakens..." : "&7Hold still..."));
        running = new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                ambient(center, t);
                // countdown
                if (t < welcomeAt && t % 20 == 0) {
                    int n = countdown - t / 20;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(title(ItemManager.color(cfg("countdown-color", "&6&l") + n),
                                ItemManager.color(cfg("countdown-subtitle", "&7The season begins...")), 0, 18, 4));
                        if (forge()) {
                            // hammer on the anvil, louder every time
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f + (countdown - n) * 0.12f,
                                    0.6f + (countdown - n) * 0.08f);
                        } else {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f + (countdown - n) * 0.15f);
                        }
                    }
                    if (forge()) {
                        Location c = center.clone().add(0, 1, 0);
                        world.spawnParticle(Particle.LAVA, c, 10 + (t / 20) * 4, 0.3, 0.2, 0.3, 0, null, true);
                        world.spawnParticle(Particle.DUST, c, 30 + (t / 20) * 10, 0.6, 0.4, 0.6, 0, EMBER, true);
                    }
                }
                if (t < welcomeAt) {
                    ring(center, 1.5 + t * 0.06, t, forge() ? Particle.FLAME : Particle.END_ROD, 24);
                }
                // the moment
                if (t == welcomeAt) {
                    Component big = ItemManager.color(cfg("title", "&6&lWelcome to FORGEBOUND SMP"));
                    Component sub = ItemManager.color(cfg("subtitle", "&eSeason 1 has begun"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(title(big, sub, 10, 80, 20));
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
                    }
                    Bukkit.broadcast(ItemManager.color(cfg("chat", "&6&l⚒ &eWelcome to &6&lFORGEBOUND SMP&e! Season 1 has begun.")));
                    if (hologram != null && hologram.isValid()) {
                        hologram.text(ItemManager.color(cfg("hologram", forge()
                                ? "&6&lWelcome to FORGEBOUND SMP\n&e{legends} legends. One of each. &7Season 1"
                                : "&6&lThe FORGEBOUND SMP Begins").replace("\\n", "\n")
                                .replace("{legends}", String.valueOf(legends().size()))));
                    }
                    if (forge() || !on("crown")) {
                        spawnHalo(center);                  // soul: the crown takes the ring's place
                    }
                    if (on("sigil")) {
                        planSigil(center);
                    }
                    if (on("crown")) {
                        spawnCrown(center);
                    }
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2;
                        world.strikeLightningEffect(center.clone().add(Math.cos(a) * 6, 0, Math.sin(a) * 6));
                    }
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                    firework(center.clone().add(0, 1, 0), Color.ORANGE, Color.YELLOW);
                    if (host != null && plugin.getConfig().getBoolean("season.lift-host", true)) {
                        host.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0, false, false));
                        host.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, endAt - t + 60, 0, false, false));
                    }
                }
                // building up around the host
                if (t >= welcomeAt && t < teleportAt) {
                    Location c = host != null && host.isOnline() ? host.getLocation() : center;
                    double power = Math.min(1, (t - welcomeAt) / 80.0);
                    helix(c, t, power);
                    if (t % 4 == 0) {
                        ring(c, 2.5 + (t % 40) * 0.15, t, Particle.ELECTRIC_SPARK, 32);
                    }
                    if (t % 25 == 0) {
                        double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                        firework(c.clone().add(Math.cos(a) * 8, 0, Math.sin(a) * 8),
                                ThreadLocalRandom.current().nextBoolean() ? Color.WHITE : Color.YELLOW, Color.ORANGE);
                    }
                }
                // the rune circle draws itself, line after line
                if (!sigilQueue.isEmpty()) {
                    drawSigil(center, 6);
                }
                if (t > welcomeAt && t < teleportAt && t % 5 == 0) {
                    sigilSparks(center);
                }
                if (t > welcomeAt && t < teleportAt && t % 2 == 0) {
                    double close = t < beamAt ? 0 : Math.min(1, (double) (t - beamAt) / (teleportAt - beamAt));
                    moveCrown(center, t, close);
                }
                // the world goes dark for a moment, then the beam lights it up
                if (t == beamAt - 30 && on("darkness")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 75, 0, false, false));
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.5f);
                        p.playSound(p.getLocation(), forge() ? Sound.BLOCK_FIRE_EXTINGUISH : Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.6f);
                    }
                }
                if (t == beamAt) {
                    recolorSigil(forge() ? Material.YELLOW_CONCRETE : Material.WHITE_CONCRETE);
                }
                // ash (and embers) drifting down while the beam shines
                if (t >= beamAt && t < teleportAt && t % 2 == 0 && on("ash")) {
                    ash(center);
                }
                // the beam
                if (t == beamAt) {
                    spawnBeam(center);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.7f);
                        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 0.5f);
                    }
                }
                if (t > welcomeAt && t < teleportAt) {
                    // the halo spins, and closes in on the beam towards the end
                    double close = t < beamAt ? 0 : Math.min(1, (double) (t - beamAt) / (teleportAt - beamAt));
                    moveHalo(center, t, close);
                }
                if (t > beamAt && t < teleportAt) {
                    pulseBeam(t - beamAt);
                    if (t % 10 == 0) {
                        world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 2f, 0.8f);
                    }
                }
                // everyone goes
                if (t == teleportAt - 12) {
                    boolean pack;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        pack = plugin.menus() != null && plugin.menus().usesPack(p);
                        if (pack) {
                            // a white flash over the whole screen (a full-screen glyph from the resource pack)
                            p.showTitle(title(flash(net.kyori.adventure.text.format.NamedTextColor.WHITE), Component.empty(),
                                    8, forge() ? 10 : 18, forge() ? 0 : 30));
                            if (forge()) {
                                // ...that turns into the glow of an open forge
                                Bukkit.getScheduler().runTaskLater(plugin, () -> p.showTitle(title(
                                        flash(net.kyori.adventure.text.format.TextColor.color(255, 140, 40)),
                                        Component.empty(), 0, 6, 30)), 18L);
                            }
                        } else {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false));
                        }
                        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.6f);
                        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                    }
                    center.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 4, 0), 3, 1, 2, 1, 0, Color.WHITE, true);
                    burstHalo();
                    recolorSigil(Material.WHITE_CONCRETE);
                }
                if (t == teleportAt) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    }
                    if (teleport) {
                        scatter();
                    }
                    removeBeam();
                    removeHalo();
                    removeHologram();
                    removeSigil();
                    removeCrown();
                }
                if (t >= endAt) {
                    cancel();
                    running = null;
                    return;
                }
                t++;
            }
        };
        running.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ================================================================ effects

    private void ring(Location c, double r, int t, Particle particle, int points) {
        World w = c.getWorld();
        for (int i = 0; i < points; i++) {
            double a = i * 2 * Math.PI / points + t * 0.05;
            w.spawnParticle(particle, c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, null, true);
        }
    }

    private void helix(Location c, int t, double power) {
        World w = c.getWorld();
        int arms = 3;
        for (int arm = 0; arm < arms; arm++) {
            for (int k = 0; k < 2; k++) {
                double h = ((t * 0.12 + k * 1.5) % 4.5);
                double a = t * 0.25 + arm * 2 * Math.PI / arms + h;
                double r = 1.6 - h * 0.25;
                w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), 1, 0, 0, 0, 0,
                        arm == 0 ? WHITE : GOLD, true);
            }
        }
        if (power > 0.5 && t % 2 == 0) {
            w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), (int) (4 * power), 0.6, 0.8, 0.6, 0.05, null, true);
        }
    }

    private void firework(Location at, Color a, Color b) {
        at.getWorld().spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(a, b)
                    .withFade(Color.WHITE).trail(true).flicker(true).build());
            meta.setPower(1);
            f.setFireworkMeta(meta);
            f.setTicksToDetonate(20 + ThreadLocalRandom.current().nextInt(15));
        });
    }

    private static Component flash(net.kyori.adventure.text.format.TextColor color) {
        return Component.text("\ue100").font(net.kyori.adventure.key.Key.key("customcraft", "flash")).color(color);
    }

    // ================================================================ hologram, ambient, halo

    private void hologram(Location center, String text) {
        removeHologram();
        Location at = center.clone().add(0, num("hologram-height", 4.5), 0);
        hologram = at.getWorld().spawn(at, org.bukkit.entity.TextDisplay.class, d -> {
            d.text(ItemManager.color(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setViewRange(4f);
            d.setBrightness(new Display.Brightness(15, 15));
            float sc = (float) num("hologram-scale", 2.5);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
        });
    }

    private void removeHologram() {
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
        hologram = null;
    }

    /** Forge: embers and sparks over the whole area. Soul: drifting soul flames. */
    private void ambient(Location c, int t) {
        if (forge()) {
            double radius = num("ambient-radius", 20);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            World w = c.getWorld();
            for (int i = 0; i < (int) num("ambient-amount", 12); i++) {
                double a = r.nextDouble(Math.PI * 2);
                double d = Math.sqrt(r.nextDouble()) * radius;
                Location at = c.clone().add(Math.cos(a) * d, r.nextDouble(0.5, 9), Math.sin(a) * d);
                switch (i % 3) {
                    case 0 -> w.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0, 0, 0, 0, COOLING, true);
                    case 1 -> w.spawnParticle(Particle.SMALL_FLAME, at, 0, r.nextDouble(-0.01, 0.01), 0.03,
                            r.nextDouble(-0.01, 0.01), 1, null, true);
                    default -> w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, EMBER, true);
                }
            }
            if (t % 5 == 0) {
                w.spawnParticle(Particle.LAVA, c.clone().add(r.nextDouble(-6, 6), 0.5, r.nextDouble(-6, 6)), 1, 0, 0, 0, 0, null, true);
            }
            return;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(cfg("ambient-particle", "SOUL_FIRE_FLAME"));
        } catch (IllegalArgumentException ex) {
            particle = Particle.SOUL_FIRE_FLAME;
        }
        if (particle.getDataType() != Void.class) {
            particle = Particle.SOUL_FIRE_FLAME;
        }
        double radius = num("ambient-radius", 20);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < (int) num("ambient-amount", 12); i++) {
            double a = r.nextDouble(Math.PI * 2);
            double d = Math.sqrt(r.nextDouble()) * radius;
            c.getWorld().spawnParticle(particle, c.clone().add(Math.cos(a) * d, r.nextDouble(0.5, 9), Math.sin(a) * d),
                    0, r.nextDouble(-0.01, 0.01), -0.04, r.nextDouble(-0.01, 0.01), 1, null, true);
        }
    }

    /** Forge: every legend on the server, spinning above the center. Soul: a ring of blocks. */
    private void spawnHalo(Location center) {
        removeHalo();
        if (forge()) {
            List<ItemManager.Entry> list = legends();
            for (ItemManager.Entry e : list) {
                org.bukkit.inventory.ItemStack shown = e.item.clone();
                halo.add(center.getWorld().spawn(center.clone().add(0, 7, 0), org.bukkit.entity.ItemDisplay.class, d -> {
                    d.setItemStack(shown);
                    d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                    d.setPersistent(false);
                    d.setTeleportDuration(2);
                    d.setViewRange(6f);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setGlowing(true);
                    d.setGlowColorOverride(Color.fromRGB(255, 150, 50));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                            new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf()));
                }));
            }
            return;
        }
        Material block = Material.matchMaterial(cfg("halo-block", "CRYING_OBSIDIAN"));
        if (block == null || !block.isBlock()) {
            block = Material.CRYING_OBSIDIAN;
        }
        int n = (int) num("halo-blocks", 18);
        Material b = block;
        for (int i = 0; i < n; i++) {
            halo.add(center.getWorld().spawn(center.clone().add(0, 7, 0), BlockDisplay.class, d -> {
                d.setBlock(b.createBlockData());
                d.setPersistent(false);
                d.setTeleportDuration(2);
                d.setViewRange(6f);
                d.setBrightness(new Display.Brightness(12, 12));
                d.setTransformation(new Transformation(new Vector3f(-0.4f, -0.4f, -0.4f), new Quaternionf(),
                        new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
            }));
        }
    }

    private void moveHalo(Location center, int t, double close) {
        int n = halo.size();
        double base = num("halo-radius", 5) * (1 - 0.6 * close);
        double height = 7 + Math.sin(t * 0.05) * 0.4;
        for (int i = 0; i < n; i++) {
            Display d = halo.get(i);
            if (!d.isValid()) {
                continue;
            }
            double radius = base;
            double y = height;
            // near the end the legends dive into the beam one after another
            double start = 0.55 + 0.4 * i / Math.max(1, n);
            double dive = Math.max(0, Math.min(1, (close - start) / 0.08));
            if (dive > 0) {
                radius *= 1 - dive;
                y = height - 3 * dive;
            }
            double a = i * 2 * Math.PI / n + t * (0.04 + 0.12 * close);
            Location at = center.clone().add(Math.cos(a) * radius, y + Math.sin(a) * radius * 0.25, Math.sin(a) * radius);
            at.setYaw((float) Math.toDegrees(-a) + 90f);
            d.teleport(at);
            if (dive >= 1) {
                World w = center.getWorld();
                w.spawnParticle(Particle.LAVA, at, 6, 0.2, 0.2, 0.2, 0, null, true);
                w.spawnParticle(Particle.DUST, at, 15, 0.3, 0.3, 0.3, 0, forge() ? EMBER : WHITE, true);
                w.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.6f);
                d.remove();
            }
        }
    }

    private void burstHalo() {
        for (Display d : halo) {
            if (!d.isValid()) {
                continue;
            }
            if (d instanceof BlockDisplay bd) {
                d.getWorld().spawnParticle(Particle.BLOCK, d.getLocation(), 20, 0.3, 0.3, 0.3, 0, bd.getBlock(), true);
            }
            d.getWorld().spawnParticle(Particle.END_ROD, d.getLocation(), 6, 0.2, 0.2, 0.2, 0.2, null, true);
        }
        removeHalo();
    }

    private void removeHalo() {
        for (Display d : halo) {
            if (d.isValid()) {
                d.remove();
            }
        }
        halo.clear();
    }

    private boolean on(String key) {
        return plugin.getConfig().getBoolean("season." + key, true);
    }

    // ================================================================ the rune circle (thin glowing block displays)

    /** A flat glowing bar on the ground from (x1, z1) to (x2, z2), relative to the center. */
    private BlockDisplay bar(Location origin, Material block, float x1, float z1, float x2, float z2, float width, float y) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        Quaternionf rot = new Quaternionf().rotationY((float) Math.atan2(-dz, dx));
        Vector3f scale = new Vector3f(len, 0.02f, width);
        Vector3f mid = new Vector3f((x1 + x2) / 2, y, (z1 + z2) / 2);
        Vector3f half = rot.transform(new Vector3f(0.5f, 0.5f, 0.5f).mul(scale));
        Vector3f translation = mid.sub(half);
        return origin.getWorld().spawn(origin, BlockDisplay.class, d -> {
            d.setBlock(block.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(8f);
            d.setDisplayWidth(40);
            d.setDisplayHeight(4);
            d.setTransformation(new Transformation(translation, rot, scale, new Quaternionf()));
        });
    }

    /** Rings, a star and spokes: queued now, drawn a few lines per tick. */
    private void planSigil(Location center) {
        removeSigil();
        float r = (float) num("sigil-radius", 9);
        ringLines(r, 0.16f);
        ringLines(r - 0.45f, 0.08f);
        float inner = r * 0.62f;
        ringLines(inner, 0.12f);
        // an eight-pointed star in the inner ring
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            double b = (i + 3) * Math.PI / 4;
            sigilQueue.add(new float[] {(float) (Math.cos(a) * inner), (float) (Math.sin(a) * inner),
                    (float) (Math.cos(b) * inner), (float) (Math.sin(b) * inner), 0.1f});
        }
        ringLines(r * 0.22f, 0.1f);
        // spokes: four long ones reaching out past the circle, four short ones
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            float from = r * 0.22f;
            float to = i % 2 == 0 ? r + 4.5f : r;
            sigilQueue.add(new float[] {(float) (Math.cos(a) * from), (float) (Math.sin(a) * from),
                    (float) (Math.cos(a) * to), (float) (Math.sin(a) * to), 0.1f});
        }
        center.getWorld().playSound(center, forge() ? Sound.ITEM_FIRECHARGE_USE : Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.6f);
    }

    private void ringLines(float radius, float width) {
        int n = Math.max(16, Math.round((float) (2 * Math.PI * radius / 1.1)));
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n;
            double b = (i + 1.04) * 2 * Math.PI / n;
            sigilQueue.add(new float[] {(float) (Math.cos(a) * radius), (float) (Math.sin(a) * radius),
                    (float) (Math.cos(b) * radius), (float) (Math.sin(b) * radius), width});
        }
    }

    private void drawSigil(Location center, int perTick) {
        Location origin = center.clone();
        origin.setYaw(0);
        origin.setPitch(0);
        float y = (float) (Math.floor(center.getY()) - center.getY()) + 0.03f;
        Material block = forge() ? Material.ORANGE_CONCRETE : Material.LIGHT_BLUE_CONCRETE;
        for (int i = 0; i < perTick && !sigilQueue.isEmpty(); i++) {
            float[] l = sigilQueue.removeFirst();
            sigil.add(bar(origin, block, l[0], l[1], l[2], l[3], l[4], y));
            if (i == 0) {
                Location at = center.clone().add(l[2], 0.1, l[3]);
                center.getWorld().spawnParticle(forge() ? Particle.FLAME : Particle.SOUL_FIRE_FLAME, at, 2, 0.05, 0.05, 0.05, 0.01, null, true);
            }
        }
    }

    private void recolorSigil(Material block) {
        for (BlockDisplay d : sigil) {
            if (d.isValid()) {
                d.setBlock(block.createBlockData());
            }
        }
    }

    /** Little flames rising off the lines. */
    private void sigilSparks(Location center) {
        if (sigil.isEmpty()) {
            return;
        }
        double r = num("sigil-radius", 9);
        World w = center.getWorld();
        for (int i = 0; i < 6; i++) {
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double rr = ThreadLocalRandom.current().nextBoolean() ? r : r * 0.62;
            Location at = center.clone().add(Math.cos(a) * rr, 0.15, Math.sin(a) * rr);
            w.spawnParticle(forge() ? Particle.SMALL_FLAME : Particle.SOUL_FIRE_FLAME, at, 1, 0, 0.05, 0, 0.01, null, true);
        }
    }

    private void removeSigil() {
        sigilQueue.clear();
        for (BlockDisplay d : sigil) {
            if (d.isValid()) {
                d.remove();
            }
        }
        sigil.clear();
    }

    // ================================================================ the spiked crown above the center

    private void spawnCrown(Location center) {
        removeCrown();
        Location origin = center.clone();
        origin.setYaw(0);
        origin.setPitch(0);
        int spikes = (int) num("crown-spikes", 12);
        int band = 28;
        Material spike = Material.POINTED_DRIPSTONE;
        Material ring = forge() ? Material.POLISHED_BLACKSTONE : Material.CRYING_OBSIDIAN;
        Color glow = forge() ? Color.fromRGB(255, 120, 30) : Color.fromRGB(120, 230, 255);
        for (int i = 0; i < spikes + band; i++) {
            boolean isSpike = i < spikes;
            crown.add(origin.getWorld().spawn(origin, BlockDisplay.class, d -> {
                d.setBlock((isSpike ? spike : ring).createBlockData());
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(13, 13));
                d.setViewRange(8f);
                d.setDisplayWidth(20);
                d.setDisplayHeight(20);
                d.setGlowing(true);
                d.setGlowColorOverride(glow);
                d.setTransformation(new Transformation(new Vector3f(0, 12, 0), new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f),
                        new Quaternionf()));
            }));
        }
        crownSpikes = spikes;
    }

    /** Spins, grows in at the start, and at the end shrinks and drops into the beam. */
    private void moveCrown(Location center, int t, double close) {
        if (crown.isEmpty()) {
            return;
        }
        double radius = num("crown-radius", 3.5) * (1 - 0.85 * Math.max(0, (close - 0.6) / 0.4));
        double height = (forge() ? 10.5 : 8) - 4 * Math.max(0, (close - 0.75) / 0.25) + Math.sin(t * 0.06) * 0.3;
        double spin = t * (0.03 + 0.1 * close);
        int band = crown.size() - crownSpikes;
        for (int i = 0; i < crown.size(); i++) {
            BlockDisplay d = crown.get(i);
            if (!d.isValid()) {
                continue;
            }
            Vector3f pos;
            Quaternionf rot;
            Vector3f scale;
            if (i < crownSpikes) {
                double a = spin + i * 2 * Math.PI / crownSpikes;
                pos = new Vector3f((float) (Math.cos(a) * radius), (float) height, (float) (Math.sin(a) * radius));
                Vector3f inward = new Vector3f((float) -Math.cos(a), -0.35f, (float) -Math.sin(a)).normalize();
                rot = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), inward);
                scale = new Vector3f(0.7f, 1.7f, 0.7f);
            } else {
                int k = i - crownSpikes;
                double a = -spin * 0.5 + k * 2 * Math.PI / band;
                double rr = radius + 0.45;
                pos = new Vector3f((float) (Math.cos(a) * rr), (float) height + 0.35f, (float) (Math.sin(a) * rr));
                rot = new Quaternionf().rotationY((float) (-a + Math.PI / 2));
                scale = new Vector3f((float) (2 * Math.PI * rr / band * 1.08), 0.35f, 0.3f);
            }
            Vector3f half = rot.transform(new Vector3f(0.5f, 0.5f, 0.5f).mul(scale));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(2);
            d.setTransformation(new Transformation(pos.sub(half), rot, scale, new Quaternionf()));
        }
    }

    private void removeCrown() {
        for (BlockDisplay d : crown) {
            if (d.isValid()) {
                Location at = d.getLocation().add(d.getTransformation().getTranslation().x(),
                        d.getTransformation().getTranslation().y(), d.getTransformation().getTranslation().z());
                d.getWorld().spawnParticle(Particle.BLOCK, at, 6, 0.2, 0.2, 0.2, 0, d.getBlock(), true);
                d.remove();
            }
        }
        crown.clear();
    }

    // ================================================================ ash

    private void ash(Location center) {
        World w = center.getWorld();
        double r = Math.max(12, num("ambient-radius", 20));
        Location sky = center.clone().add(0, 14, 0);
        w.spawnParticle(Particle.WHITE_ASH, sky, 70, r, 4, r, 0, null, true);
        if (forge()) {
            w.spawnParticle(Particle.FALLING_DUST, sky, 6, r * 0.7, 3, r * 0.7, 0, Material.ORANGE_CONCRETE_POWDER.createBlockData(), true);
            w.spawnParticle(Particle.ASH, sky, 30, r, 4, r, 0, null, true);
        } else {
            w.spawnParticle(Particle.FALLING_DUST, sky, 6, r * 0.7, 3, r * 0.7, 0, Material.WHITE_CONCRETE_POWDER.createBlockData(), true);
        }
    }

    // ================================================================ the beam (block displays, not particles)

    private BlockDisplay pillar(Location base, Material block, float width, float height, int brightness) {
        return base.getWorld().spawn(base, BlockDisplay.class, d -> {
            d.setBlock(block.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(brightness, brightness));
            d.setViewRange(16f);                            // visible from far away
            d.setDisplayHeight(height + 2);
            d.setDisplayWidth(width + 2);
            d.setInterpolationDuration(0);
            d.setTransformation(new Transformation(new Vector3f(-width / 2, 0, -width / 2), new Quaternionf(),
                    new Vector3f(width, 0.01f, width), new Quaternionf()));
        });
    }

    private void spawnBeam(Location center) {
        removeBeam();
        float height = (float) Math.max(16, center.getWorld().getMaxHeight() + 64 - center.getY());
        Location base = center.clone();
        base.setYaw(0);
        base.setPitch(0);
        BlockDisplay core = pillar(base, Material.WHITE_CONCRETE, 0.5f, height, 15);
        Material glowBlock = Material.matchMaterial(cfg("beam-glow-block", forge() ? "ORANGE_STAINED_GLASS" : "WHITE_STAINED_GLASS"));
        BlockDisplay glow = pillar(base, glowBlock == null ? Material.WHITE_STAINED_GLASS : glowBlock, 1.1f, height, 15);
        beam.add(core);
        beam.add(glow);
        // grow up into the sky over 1.5 s
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (BlockDisplay d : beam) {
                if (!d.isValid()) {
                    continue;
                }
                float w = d == core ? 0.5f : 1.1f;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(30);
                d.setTransformation(new Transformation(new Vector3f(-w / 2, 0, -w / 2), new Quaternionf(),
                        new Vector3f(w, height, w), new Quaternionf()));
            }
        }, 2L);
        center.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 2, 0, 0, 0, 0, Color.WHITE, true);
    }

    /** The beam slowly gets wider and brighter while it pulses. */
    private void pulseBeam(int age) {
        if (age < 32 || age % 10 != 0 || beam.size() < 2) {
            return;
        }
        BlockDisplay glow = beam.get(1);
        if (!glow.isValid()) {
            return;
        }
        Transformation tr = glow.getTransformation();
        float h = tr.getScale().y();
        float w = 1.1f + Math.min(1.4f, age / 60f) + (age / 10 % 2 == 0 ? 0.25f : 0f);
        glow.setInterpolationDelay(0);
        glow.setInterpolationDuration(10);
        glow.setTransformation(new Transformation(new Vector3f(-w / 2, 0, -w / 2), new Quaternionf(),
                new Vector3f(w, h, w), new Quaternionf()));
    }

    private void removeBeam() {
        for (BlockDisplay d : beam) {
            if (d.isValid()) {
                d.remove();
            }
        }
        beam.clear();
    }

    // ================================================================ scatter

    private World seasonWorld() {
        String name = cfg("world", "");
        World world = name.isEmpty() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(name);
        if (world == null) {
            plugin.getLogger().warning("Season: world '" + name + "' doesn't exist, nobody is teleported.");
            return null;
        }
        if (world.getEnvironment() != World.Environment.NORMAL) {
            plugin.getLogger().warning("Season: '" + world.getName() + "' isn't an overworld (nether/end have no open sky), "
                    + "nobody is teleported. Set season.world to an overworld.");
            return null;
        }
        return world;
    }

    /** Starts looking for safe spots; chunks are loaded in the background, one candidate at a time. */
    private void findSpots(int wanted) {
        spots.clear();
        spotAttempts = 0;
        spotsWanted = wanted;
        World world = seasonWorld();
        if (world == null) {
            return;
        }
        searching = true;
        nextCandidate(world);
    }

    private void nextCandidate(World world) {
        if (!searching || spots.size() >= spotsWanted || spotAttempts >= spotsWanted * 25) {
            searching = false;
            return;
        }
        spotAttempts++;
        int min = (int) num("radius-min", 200);
        int max = Math.max(min + 1, (int) num("radius-max", 1500));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location spawn = world.getSpawnLocation();
        double a = r.nextDouble(Math.PI * 2);
        double d = r.nextDouble(min, max);
        int x = (int) Math.floor(spawn.getX() + Math.cos(a) * d);
        int z = (int) Math.floor(spawn.getZ() + Math.sin(a) * d);
        world.getChunkAtAsync(x >> 4, z >> 4, true, chunk -> {
            Location ok = check(world, x, z);
            if (ok != null) {
                spots.add(ok);
            }
            nextCandidate(world);
        });
    }

    /** A safe spot at x/z, or null: open sky, solid ground, nothing dangerous close, far enough from others. */
    private Location check(World world, int x, int z) {
        Location probe = new Location(world, x + 0.5, 0, z + 0.5);
        if (!world.getWorldBorder().isInside(probe)) {
            return null;
        }
        Block top = world.getHighestBlockAt(x, z);
        Material m = top.getType();
        if (top.isLiquid() || !m.isSolid() || m.name().contains("LEAVES") || DANGER.contains(m)) {
            return null;
        }
        Block feet = top.getRelative(0, 1, 0);
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable() || feet.isLiquid()) {
            return null;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = top.getRelative(dx, dy, dz);
                    if (DANGER.contains(b.getType())) {
                        return null;
                    }
                }
            }
        }
        Location at = feet.getLocation().add(0.5, 0, 0.5);
        double spacing = num("min-distance-between-players", 100);
        for (Location other : spots) {
            if (other.distanceSquared(at) < spacing * spacing) {
                return null;
            }
        }
        return at;
    }

    private void scatter() {
        searching = false;
        World world = seasonWorld();
        if (world == null) {
            return;
        }
        boolean sky = plugin.getConfig().getBoolean("season.drop-from-sky", true);
        Component title = ItemManager.color(cfg("arrive-title", "&6&lGood luck"));
        Component sub = ItemManager.color(cfg("arrive-subtitle", "&7Forge your legend."));
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        java.util.Collections.shuffle(players);
        if (spots.size() < players.size()) {
            plugin.getLogger().warning("Season: found only " + spots.size() + " safe spots for " + players.size()
                    + " players; the rest land at the world spawn. Try a bigger radius-max.");
        }
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                for (int n = 0; n < 2 && i < players.size(); n++, i++) {
                    Player p = players.get(i);
                    if (!p.isOnline()) {
                        continue;
                    }
                    Location ground = i < spots.size() ? spots.get(i) : world.getSpawnLocation();
                    Location at = ground.clone();
                    if (sky) {
                        at.add(0, Math.min(80, world.getMaxHeight() - ground.getY() - 5), 0);
                        // slow falling until they land (removed by land())
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 120, 0, false, true));
                    }
                    p.setFallDistance(0);
                    p.teleportAsync(at).thenRun(() -> land(p));
                    p.showTitle(title(title, sub, 10, 50, 20));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
                    if (plugin.codex() != null) {
                        plugin.codex().giveIfMissing(p);
                    }
                }
                if (i >= players.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Waits until the player is on the ground, then a short protection. */
    private void land(Player p) {
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                boolean down = p.isOnGround() || p.isInWater() || p.isGliding();
                if (!p.isOnline() || down && t > 5 || t > 20 * 120) {
                    cancel();
                    if (!p.isOnline()) {
                        return;
                    }
                    p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    p.setFallDistance(0);
                    int secs = (int) num("protection-seconds", 15);
                    if (secs > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, secs * 20, 3, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, secs * 20, 0, false, true));
                        fresh.put(p.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Mobs leave freshly landed players alone for a few seconds. */
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player p) || fresh.isEmpty()) {
            return;
        }
        Long until = fresh.get(p.getUniqueId());
        if (until == null) {
            return;
        }
        if (System.currentTimeMillis() > until) {
            fresh.remove(p.getUniqueId());
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.Monster) {
            event.setCancelled(true);
        }
    }
}
