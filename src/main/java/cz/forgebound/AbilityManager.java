package cz.forgebound;

import cz.forgebound.WeaponAbilities.Active;
import cz.forgebound.WeaponAbilities.ActiveType;
import cz.forgebound.WeaponAbilities.HitType;
import cz.forgebound.WeaponAbilities.OnHit;
import cz.forgebound.WeaponAbilities.Params;
import cz.forgebound.WeaponAbilities.Passive;
import cz.forgebound.WeaponAbilities.Trigger;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Weapon abilities: actives with cooldowns, on-hit effects and passive effects while held.
 * Cooldowns are shown in the action bar while the weapon is held.
 */
public final class AbilityManager implements Listener {

    private static final long HISTORY_MS = 10_000;
    private static final long DAMAGE_MEMORY_MS = 15_000;
    private static final Set<String> UNLINKABLE_CAUSES = Set.of("VOID", "KILL", "SUICIDE", "WORLD_BORDER", "STARVATION");

    private static final Particle.DustOptions ECHO_DUST = new Particle.DustOptions(Color.fromRGB(60, 230, 230), 1.2f);
    private static final Particle.DustOptions GRAVITY_DUST = new Particle.DustOptions(Color.fromRGB(120, 80, 230), 1.4f);
    private static final Particle.DustOptions GRAVITY_CORE = new Particle.DustOptions(Color.fromRGB(20, 10, 50), 2.0f);
    private static final Particle.DustOptions HEX_DUST = new Particle.DustOptions(Color.fromRGB(80, 255, 140), 1.1f);
    private static final Particle.DustOptions HYDRA_DUST = new Particle.DustOptions(Color.fromRGB(70, 210, 120), 1.2f);
    private static final Particle.DustOptions SOUL_DUST = new Particle.DustOptions(Color.fromRGB(120, 240, 255), 1.0f);
    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(150, 12, 26), 1.3f);
    private static final Particle.DustOptions BLOOD_SMALL = new Particle.DustOptions(Color.fromRGB(190, 20, 34), 0.8f);
    private static final Particle.DustOptions PLAGUE_DUST = new Particle.DustOptions(Color.fromRGB(120, 170, 40), 0.9f);
    private static final Particle.DustOptions TONGUE_DUST = new Particle.DustOptions(Color.fromRGB(214, 84, 116), 1.5f);
    private static final Particle.DustOptions TONGUE_TIP = new Particle.DustOptions(Color.fromRGB(255, 150, 178), 2.0f);
    private static final Particle.DustOptions ABYSS_DUST = new Particle.DustOptions(Color.fromRGB(40, 210, 190), 1.0f);
    private static final Particle.DustOptions SILK_DUST = new Particle.DustOptions(Color.fromRGB(245, 240, 230), 0.8f);
    private static final Particle.DustOptions NEEDLE_RED = new Particle.DustOptions(Color.fromRGB(200, 30, 40), 0.9f);
    private static final Particle.DustOptions SOUL_WHITE = new Particle.DustOptions(Color.fromRGB(230, 236, 255), 1.2f);
    private static final Particle.DustOptions KNIGHT_GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 70), 1.2f);
    private static final Particle.DustOptions FROST_DUST = new Particle.DustOptions(Color.fromRGB(170, 230, 255), 1.1f);
    private static final Particle.DustOptions CHAIN_DUST = new Particle.DustOptions(Color.fromRGB(120, 124, 140), 0.8f);
    private static final Particle.DustOptions VALK_DUST = new Particle.DustOptions(Color.fromRGB(255, 236, 170), 1.0f);
    private static final Particle.DustOptions SEA_DUST = new Particle.DustOptions(Color.fromRGB(40, 140, 220), 1.1f);
    private static final Particle.DustOptions ABYSS_FULL = new Particle.DustOptions(Color.fromRGB(255, 190, 60), 1.1f);
    /** Effects Bloodrush shrugs off. */
    private static final Set<PotionEffectType> DEBUFFS = Set.of(PotionEffectType.POISON, PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.HUNGER, PotionEffectType.DARKNESS);

    private final Forgebound plugin;
    private final ItemManager items;
    private final NamespacedKey arrowKey;
    private final NamespacedKey mirrorKey;
    private final NamespacedKey killsKey;
    private final NamespacedKey pinKey;
    private final NamespacedKey finaleKey;
    private final NamespacedKey pinShotKey;

    /** player -> (weapon:ability -> cooldown end in ms) */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    /** player -> last activation attempt (one click can send two packets) */
    private final Map<UUID, Long> lastTrigger = new HashMap<>();
    /** player -> attack charge right before the hit (already reset inside the damage event) */
    private final Map<UUID, Float> charge = new HashMap<>();
    /** player -> recent positions and health (Rewind) */
    private final Map<UUID, Deque<Snapshot>> history = new HashMap<>();
    /** attacker -> (victim -> damage dealt recently) (Resonance) */
    private final Map<UUID, Map<UUID, Dealt>> dealt = new HashMap<>();
    /** player -> Mirror Shot armed until */
    private final Map<UUID, Long> mirrorArmed = new HashMap<>();
    /** entity -> Hex Mark */
    private final Map<UUID, Hex> hexed = new HashMap<>();
    /** player -> Soul Link */
    private final Map<UUID, Link> links = new HashMap<>();
    /** player -> stored souls */
    private final Map<UUID, Integer> souls = new HashMap<>();
    /** player -> Hydra's Wrath active until (ms) */
    private final Map<UUID, Long> frenzy = new HashMap<>();
    /** player -> Bloodrush active until (ms) */
    private final Map<UUID, Long> bloodrush = new HashMap<>();
    /** entity -> Taste stacks (Abyssal Maw) */
    private final Map<UUID, Taste> taste = new HashMap<>();
    /** swallowed entity -> Devour state */
    private final Map<UUID, Swallow> swallowed = new HashMap<>();
    /** devourer -> swallowed entity */
    private final Map<UUID, UUID> belly = new HashMap<>();
    /** player -> fall damage ignored until (ms) - Clawline, Dive, Silk Dart */
    private final Map<UUID, Long> noFall = new HashMap<>();
    /** player -> how far they fell when they last landed (Graviton Hammer slam right after landing) */
    private final Map<UUID, Landing> landed = new HashMap<>();
    /** player -> thrown Rimebreaker */
    private final Map<UUID, AxeState> axes = new HashMap<>();
    /** player -> Pin Bolt armed until; entity -> pinned until */
    private final Map<UUID, Long> pinArmed = new HashMap<>();
    /** The Last Act: players in the middle of their Finale */
    private final Map<UUID, Finale> finales = new HashMap<>();
    /** The Last Act passive: normal bolts shot since the last critical one */
    private final Map<UUID, Integer> actShots = new HashMap<>();
    private final Map<UUID, Long> pinned = new HashMap<>();
    /** entity -> who froze it with the Rimebreaker (Ice Cold) */
    private final Map<UUID, FrozenBy> frozenBy = new HashMap<>();
    /** player -> Cloak (true invisibility) until */
    private final Map<UUID, Long> cloaked = new HashMap<>();
    /** player -> "Sea King" (Great Tide) until */
    private final Map<UUID, Long> seaKing = new HashMap<>();
    /** player -> (weapon:ability -> charges used) */
    private final Map<UUID, Map<String, Integer>> chargeUse = new HashMap<>();
    /** attacker -> charged hits counted for Silk Bind */
    private final Map<UUID, Integer> silkHits = new HashMap<>();
    private RelicManager relics;
    private int ticks;

    private record Snapshot(Location location, double health, long time) { }

    private record Dealt(double amount, long last) { }

    private record Hex(double multiplier, long expires) { }

    private record Link(UUID target, double percent, double maxDistance, long expires) { }

    private record Taste(int stacks, int max, long expires) { }

    private record Landing(float distance, long time) { }

    private record FrozenBy(UUID player, long until) { }

    private static final class Swallow {
        final UUID devourer;
        final UUID target;
        final boolean ally;
        final long ends;
        final double spitDamage;
        final double launch;
        BukkitTask task;

        Swallow(UUID devourer, UUID target, boolean ally, long ends, double spitDamage, double launch) {
            this.devourer = devourer;
            this.target = target;
            this.ally = ally;
            this.ends = ends;
            this.spitDamage = spitDamage;
            this.launch = launch;
        }
    }

    public void setRelics(RelicManager relics) {
        this.relics = relics;
    }

    private double heal(LivingEntity entity, double amount) {
        return relics == null ? amount : relics.adjustHeal(entity, amount);
    }

    public AbilityManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.arrowKey = Forgebound.key("weapon");
        this.mirrorKey = Forgebound.key("mirror_shot");
        this.killsKey = Forgebound.key("weapon_kills");
        this.pinKey = Forgebound.key("pinned");
        this.finaleKey = Forgebound.key("finale_root");
        this.pinShotKey = Forgebound.key("pin_shot");
    }

    /** Starts the repeating task: history, HUD, passive effects, marks. */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }

    // ---------------------------------------------------------------- settings

    private boolean enabled() {
        return plugin.getConfig().getBoolean("ability-settings.enabled", true);
    }

    private boolean enabledIn(World world) {
        return enabled() && !plugin.getConfig().getStringList("ability-settings.disabled-worlds").contains(world.getName());
    }

    // ---------------------------------------------------------------- repeating task

    private void tick() {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean slow = (ticks++ & 1) == 0; // every 10 ticks
        boolean hud = plugin.getConfig().getBoolean("ability-settings.hud", true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Deque<Snapshot> past = history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
            if (!player.isDead()) {
                past.addLast(new Snapshot(player.getLocation(), player.getHealth(), now));
            }
            while (!past.isEmpty() && past.peekFirst().time() < now - HISTORY_MS) {
                past.removeFirst();
            }
            if (!slow) {
                continue;
            }
            ItemManager.Entry weapon = weaponOf(player.getInventory().getItemInMainHand());
            boolean hasWeapon = weapon != null && !weapon.abilities.isEmpty() && enabledIn(player.getWorld());
            if (hasWeapon && weapon.abilities.passiveUnlockKills <= killsOf(player.getInventory().getItemInMainHand())) {
                for (Passive passive : weapon.abilities.passives) {
                    player.addPotionEffect(new PotionEffect(passive.type(), 50, passive.amplifier(), true, false, true));
                }
                // low-health regeneration (every 10 ticks = half of the per-second amount)
                double max = maxHealth(player);
                if (weapon.abilities.regenPercent > 0 && !player.isDead()
                        && player.getHealth() < max * weapon.abilities.regenBelow / 100.0) {
                    double add = heal(player, max * weapon.abilities.regenPercent / 200.0);
                    if (add > 0) {
                        player.setHealth(Math.min(max, player.getHealth() + add));
                        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.1, 0), 2, 0.25, 0.4, 0.25, 0, PLAGUE_DUST);
                    }
                }
            }
            if (hud) {
                String bar = hasWeapon ? hud(player, weapon, now) : "";
                String relicBar = relics == null ? "" : relics.hud(player);
                if (!relicBar.isEmpty()) {
                    bar = bar.isEmpty() ? relicBar : bar + "  &8┃  " + relicBar;
                }
                if (!bar.isEmpty()) {
                    player.sendActionBar(ItemManager.color(bar));
                }
            }
        }

        if (slow) {
            // Hex Mark: floating rune ring above marked targets
            for (Iterator<Map.Entry<UUID, Hex>> it = hexed.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Hex> e = it.next();
                Entity entity = Bukkit.getEntity(e.getKey());
                if (e.getValue().expires() < now || entity == null || !entity.isValid()) {
                    it.remove();
                    continue;
                }
                Location top = entity.getLocation().add(0, entity.getHeight() + 0.4, 0);
                ring(top, 0.45, 8, l -> l.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, HEX_DUST));
            }
            // Taste: little stack orbs above tasted targets (gold ring when ready to devour)
            for (Iterator<Map.Entry<UUID, Taste>> it = taste.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Taste> e = it.next();
                Entity entity = Bukkit.getEntity(e.getKey());
                if (e.getValue().expires() < now || entity == null || !entity.isValid()) {
                    it.remove();
                    continue;
                }
                Taste t = e.getValue();
                Location top = entity.getLocation().add(0, entity.getHeight() + 0.35, 0);
                for (int i = 0; i < t.stacks(); i++) {
                    double off = (i - (t.stacks() - 1) / 2.0) * 0.3;
                    entity.getWorld().spawnParticle(Particle.DUST, top.clone().add(off, 0, 0), 1, 0, 0, 0, 0,
                            t.stacks() >= t.max() ? ABYSS_FULL : ABYSS_DUST);
                }
                if (t.stacks() >= t.max()) {
                    ring(top.clone().add(0, 0.2, 0), 0.5, 10,
                            l -> l.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, ABYSS_FULL));
                }
            }
            bloodrush.values().removeIf(t -> t < now);
            frozenBy.values().removeIf(f -> f.until() < now);
            pinArmed.values().removeIf(t -> t < now);
            seaKing.values().removeIf(t -> t < now);
            mirrorArmed.values().removeIf(t -> t < now);
            dealt.values().forEach(m -> m.values().removeIf(d -> d.last() < now - DAMAGE_MEMORY_MS));
            dealt.values().removeIf(Map::isEmpty);
        }
    }

    private String hud(Player player, ItemManager.Entry weapon, long now) {
        StringBuilder bar = new StringBuilder();
        for (Active active : weapon.abilities.actives) {
            if (!bar.isEmpty()) {
                bar.append("  &8┃  ");
            }
            bar.append(active.name()).append(' ');
            long left = remaining(player, weapon, active, now);
            int unlock = active.params().i("unlock-kills", 0);
            int charges = active.params().i("charges", 1);
            if (unlock > killsOf(player.getInventory().getItemInMainHand())) {
                bar.append("&8🔒");
            } else if ((active.type() == ActiveType.MIRROR_SHOT && mirrorArmed.containsKey(player.getUniqueId()))
                    || (active.type() == ActiveType.PIN_BOLT && pinArmed.containsKey(player.getUniqueId()))) {
                bar.append("&dARMED");
            } else if (active.type() == ActiveType.AXE_THROW && axes.containsKey(player.getUniqueId())) {
                bar.append("&bRECALL");
            } else if (active.type() == ActiveType.TIDAL_SURGE && seaKing(player)) {
                bar.append("&b∞");
            } else if (charges > 1 && left <= 0) {
                Map<String, Integer> use = chargeUse.get(player.getUniqueId());
                int used = use == null ? 0 : use.getOrDefault(weapon.ref() + ":" + active.key(), 0);
                bar.append("&a").append(charges - used).append("/").append(charges);
            } else {
                bar.append(left <= 0 ? "&a✔" : "&c" + seconds(left) + "s");
            }
        }
        if (weapon.abilities.actives.stream().anyMatch(a -> a.type() == ActiveType.FINALE)) {
            if (!bar.isEmpty()) {
                bar.append("  &8┃  ");
            }
            Finale f = finales.get(player.getUniqueId());
            if (f != null) {
                int left = f.shots - f.fired;
                bar.append("&6&lFINALE &e").append("◆".repeat(Math.max(0, left))).append("&8").append("◇".repeat(f.fired));
            } else {
                int n = actShots.getOrDefault(player.getUniqueId(), 0);
                bar.append("&6").append("●".repeat(n)).append("&8").append("○".repeat(Math.max(0, 3 - n)))
                        .append(n == 3 ? " &6&l4" : " &84");
            }
        }
        if (weapon.abilities.trackKills) {
            if (!bar.isEmpty()) {
                bar.append("  &8┃  ");
            }
            bar.append("&6⚔ ").append(killsOf(player.getInventory().getItemInMainHand()));
        }
        OnHit soul = weapon.abilities.onHit(HitType.SOUL);
        if (soul != null) {
            if (!bar.isEmpty()) {
                bar.append("  &8┃  ");
            }
            int max = Math.max(1, soul.params().i("max", 5));
            int have = Math.min(max, souls.getOrDefault(player.getUniqueId(), 0));
            bar.append("&3Souls &b").append("◆".repeat(have)).append("&8").append("◇".repeat(max - have));
        }
        return bar.toString();
    }

    // ---------------------------------------------------------------- activation

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        // Clicking a chest, door etc. keeps vanilla behaviour.
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return;
        }
        if (finales.containsKey(player.getUniqueId())) {
            event.setUseItemInHand(Event.Result.DENY);         // during the Finale, right-click fires the next shot
            event.setUseInteractedBlock(Event.Result.DENY);
            finaleShot(player);
            return;
        }
        Trigger trigger = player.isSneaking() ? Trigger.SHIFT_RIGHT_CLICK : Trigger.RIGHT_CLICK;
        if (activate(player, trigger)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemManager.Entry weapon = weaponOf(player.getInventory().getItemInMainHand());
        if (weapon == null) {
            return;
        }
        if (finales.containsKey(player.getUniqueId())) {
            event.setCancelled(true);                          // during the Finale, F fires the next shot
            finaleShot(player);
            return;
        }
        Trigger trigger = player.isSneaking() ? Trigger.SHIFT_SWAP : Trigger.SWAP;
        if (weapon.abilities.active(trigger) != null) {
            // A weapon with an F ability is never swapped into the off hand.
            event.setCancelled(true);
            activate(player, trigger);
        }
    }

    /** Tries to fire an ability. Returns true if the weapon has an ability on this trigger. */
    private boolean activate(Player player, Trigger trigger) {
        ItemManager.Entry weapon = weaponOf(player.getInventory().getItemInMainHand());
        if (weapon == null) {
            return false;
        }
        Active active = weapon.abilities.active(trigger);
        if (active == null) {
            return false;
        }
        if (!enabledIn(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR
                || (relics != null && relics.inStasis(player)) || swallowed.containsKey(player.getUniqueId())) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = lastTrigger.put(player.getUniqueId(), now);
        if (last != null && now - last < 150) {
            return true;
        }
        // Rimebreaker: pressing F again calls the thrown axe back (no cooldown)
        if (active.type() == ActiveType.AXE_THROW && axes.containsKey(player.getUniqueId())) {
            recallAxe(player);
            return true;
        }
        int unlock = active.params().i("unlock-kills", 0);
        int kills = killsOf(player.getInventory().getItemInMainHand());
        if (unlock > kills) {
            player.sendActionBar(plugin.raw("ability-locked", "&c{ability} &7unlocks at &6{need} kills &8({have}/{need})",
                    "ability", ItemManager.color(active.name()), "need", String.valueOf(unlock), "have", String.valueOf(kills)));
            return true;
        }
        boolean free = active.type() == ActiveType.TIDAL_SURGE && seaKing(player);
        long left = free ? 0 : remaining(player, weapon, active, now);
        if (left > 0) {
            player.sendActionBar(plugin.raw("ability-cooldown", "&c{ability} &7is recharging: &c{time}s",
                    "ability", ItemManager.color(active.name()), "time", seconds(left)));
            return true;
        }
        boolean used;
        try {
            used = execute(player, weapon, active);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Ability " + weapon.ref() + "/" + active.key() + " failed: " + ex);
            used = false;
        }
        if (used) {
            if (!free && !player.hasPermission("forgebound.nocooldown")) {
                String key = weapon.ref() + ":" + active.key();
                int charges = active.params().i("charges", 1);
                boolean start = true;
                if (charges > 1) {
                    Map<String, Integer> use = chargeUse.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
                    int count = use.merge(key, 1, Integer::sum);
                    start = count >= charges;
                    if (start) {
                        use.remove(key);
                    }
                }
                if (start) {
                    cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                            .put(key, now + (long) (active.cooldown() * 1000L
                                    * (plugin.events() == null ? 1 : plugin.events().cooldownMultiplier(player))
                                    * (plugin.stars() == null ? 1 : plugin.stars().cooldownMultiplier(player))));
                }
            }
            if (plugin.legends() != null) {
                plugin.legends().ability(player, weapon.ref(),
                        trigger == Trigger.SHIFT_SWAP || trigger == Trigger.SHIFT_RIGHT_CLICK);
            }
            player.sendActionBar(plugin.raw("ability-used", "&a» {ability}&a!",
                    "ability", ItemManager.color(active.name())));
        }
        return true;
    }

    /** Gives back part of an ability's remaining cooldown (e.g. when Cleaver Toss hits). */
    private void refund(Player player, ItemManager.Entry weapon, Active active, double fraction) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null || fraction <= 0) {
            return;
        }
        String key = weapon.ref() + ":" + active.key();
        Long until = map.get(key);
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            map.put(key, now + (long) ((until - now) * (1 - Math.min(1, fraction))));
        }
    }

    private long remaining(Player player, ItemManager.Entry weapon, Active active, long now) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            return 0;
        }
        Long until = map.get(weapon.ref() + ":" + active.key());
        return until == null ? 0 : until - now;
    }

    private static long seconds(long millis) {
        return (millis + 999) / 1000;
    }

    private boolean execute(Player player, ItemManager.Entry weapon, Active active) {
        Params p = active.params();
        return switch (active.type()) {
            case REWIND -> rewind(player, p);
            case RESONANCE -> resonance(player, p);
            case GRAVITY_ANCHOR -> gravityAnchor(player, p);
            case UPHEAVAL -> upheaval(player, p);
            case MIRROR_SHOT -> mirrorShot(player, p);
            case HEX_MARK -> hexMark(player, p);
            case SOUL_LINK -> soulLink(player, p);
            case HARVEST -> harvest(player, p);
            case CRESCENT -> crescent(player, p);
            case FRENZY -> frenzy(player, p);
            case CLEAVER_TOSS -> cleaverToss(player, weapon, active);
            case BLOODRUSH -> bloodrush(player, p);
            case TONGUE_LASH -> tongueLash(player, weapon, p);
            case DEVOUR -> devour(player, p);
            case CLAWLINE -> clawline(player, p);
            case DIVE -> dive(player, p);
            case SILK_DART -> silkDart(player, p);
            case THREAD_STORM -> threadStorm(player, p);
            case GRAPPLE -> grappleTo(player, p);
            case HAMMER_THROW -> hammerThrow(player, weapon, p);
            case AXE_THROW -> throwAxe(player, weapon, p);
            case FROST_WAVE -> frostWave(player, p);
            case PIN_BOLT -> pinBolt(player, p);
            case VOLLEY -> volley(player, weapon, p);
            case LUNGE -> lunge(player, p);
            case JAVELIN -> javelin(player, weapon, p);
            case TIDAL_SURGE -> tidalSurge(player, p);
            case GREAT_TIDE -> greatTide(player, p);
            case SPOTLIGHT -> spotlight(player, p);
            case FINALE -> finale(player, weapon, p);
        };
    }

    private void fail(Player player, String key, String def) {
        player.sendActionBar(plugin.raw(key, def));
    }

    // ================================================================ ECHO BLADE

    /** Rewind: snap back to where you were a few seconds ago and get back the health you had then. */
    private boolean rewind(Player player, Params p) {
        long target = System.currentTimeMillis() - (long) (p.d("seconds", 4) * 1000);
        Deque<Snapshot> past = history.get(player.getUniqueId());
        Snapshot found = null;
        if (past != null) {
            for (Snapshot snapshot : past) {
                if (snapshot.time() > target) {
                    break;
                }
                found = snapshot;
            }
        }
        if (found == null || found.location().getWorld() != player.getWorld()) {
            fail(player, "ability-rewind-none", "&cNo echo to return to yet.");
            return false;
        }
        Block feet = found.location().getBlock();
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
            fail(player, "ability-rewind-blocked", "&cYour past position is blocked.");
            return false;
        }
        Location from = player.getLocation();
        Location to = found.location().clone();
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        World world = player.getWorld();
        line(from.clone().add(0, 1, 0), to.clone().add(0, 1, 0), 0.4,
                l -> world.spawnParticle(Particle.DUST, l, 1, 0.05, 0.05, 0.05, 0, ECHO_DUST));
        world.spawnParticle(Particle.SCULK_SOUL, from.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
        world.playSound(from, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 1.6f);

        double before = player.getHealth();
        player.teleport(to);
        player.setFallDistance(0);
        player.setFireTicks(0);
        double restore = Math.min(maxHealth(player), found.health());
        if (restore > player.getHealth()) {
            player.setHealth(player.getHealth() + heal(player, restore - player.getHealth()));
        }
        if (before < 4 && player.getHealth() > before) {
            plugin.award(player, "not_today");
        }
        history.remove(player.getUniqueId()); // no chaining through older echoes

        world.spawnParticle(Particle.SCULK_SOUL, to.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.04);
        world.playSound(to, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        return true;
    }

    /** Resonance: everyone you recently hurt takes a share of all the damage you dealt them, again. */
    private boolean resonance(Player player, Params p) {
        double radius = p.d("radius", 12);
        double percent = p.d("percent", 50) / 100.0;
        double cap = p.d("max-damage", 14);
        long window = (long) (p.d("window", 10) * 1000);
        long now = System.currentTimeMillis();
        Map<UUID, Dealt> mine = dealt.get(player.getUniqueId());
        List<LivingEntity> hit = new ArrayList<>();
        List<Double> amounts = new ArrayList<>();
        if (mine != null) {
            for (Map.Entry<UUID, Dealt> e : mine.entrySet()) {
                if (e.getValue().last() < now - window) {
                    continue;
                }
                if (Bukkit.getEntity(e.getKey()) instanceof LivingEntity target && valid(player, target)
                        && target.getWorld() == player.getWorld()
                        && target.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                    hit.add(target);
                    amounts.add(Math.min(cap, e.getValue().amount() * percent));
                }
            }
        }
        if (hit.isEmpty()) {
            fail(player, "ability-resonance-none", "&cNothing is resonating. Hit something first.");
            return false;
        }
        dealt.remove(player.getUniqueId());
        World world = player.getWorld();
        Location center = player.getLocation();
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.4f);
        repeat(1, 6, step -> {
            ring(center.clone().add(0, 1, 0), radius * (step + 1) / 6.0, 40,
                    l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0.1, 0, 0, ECHO_DUST));
            return true;
        });
        for (int i = 0; i < hit.size(); i++) {
            LivingEntity target = hit.get(i);
            double amount = amounts.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid() || !player.isOnline()) {
                    return;
                }
                Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
                target.getWorld().spawnParticle(Particle.SONIC_BOOM, at, 1, 0, 0, 0, 0);
                target.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, at, 20, 0.3, 0.5, 0.3, 0.05);
                hurt(player, target, amount);
            }, 4L + i * 2L);
        }
        return true;
    }

    // ================================================================ GRAVITON HAMMER

    /** Gravity Anchor: a field that yanks back anyone trying to leave it and weighs them down. */
    private boolean gravityAnchor(Player player, Params p) {
        double radius = p.d("radius", 7);
        int seconds = p.i("duration", 6);
        double pull = p.d("pull", 0.5);
        int slowLevel = Math.max(0, p.i("slow-level", 2) - 1);
        Location center = player.getLocation();
        World world = player.getWorld();
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.5f);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
        int steps = seconds * 10;
        repeat(2, steps, step -> {
            double spin = step * 0.15;
            ring(center.clone().add(0, 0.15, 0), radius, Math.max(24, (int) (radius * 7)),
                    l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, GRAVITY_DUST));
            for (int k = 0; k < 4; k++) {
                double a = spin + k * Math.PI / 2;
                double r = radius * (1 - (step % 10) / 10.0);
                world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * r, 0.4, Math.sin(a) * r),
                        1, 0, 0, 0, 0, GRAVITY_DUST);
            }
            world.spawnParticle(Particle.DUST, center.clone().add(0, 0.6 + (step % 10) * 0.25, 0), 2, 0.1, 0.1, 0.1, 0, GRAVITY_CORE);
            if (step % 10 == 0) {
                world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1f, 0.5f);
            }
            for (LivingEntity target : targets(player, center, radius + 3)) {
                Vector to = center.toVector().subtract(target.getLocation().toVector()).setY(0);
                double distance = to.length();
                if (distance > radius * 0.8 && distance > 0.01) {
                    target.setVelocity(to.normalize().multiply(pull).setY(-0.1));
                    world.spawnParticle(Particle.REVERSE_PORTAL, target.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.05);
                }
                if (step % 10 == 0 && distance <= radius) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, slowLevel));
                }
            }
            return true;
        });
        return true;
    }

    /** Upheaval: lift everyone around you into the air, then slam them down. Higher = more damage. */
    private boolean upheaval(Player player, Params p) {
        double radius = p.d("radius", 6);
        int lift = Math.max(2, p.i("lift-ticks", 25));
        double base = p.d("base-damage", 4);
        double perBlock = p.d("damage-per-block", 1);
        double cap = p.d("max-damage", 12);
        Location center = player.getLocation();
        List<LivingEntity> victims = targets(player, center, radius);
        if (victims.isEmpty()) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        World world = player.getWorld();
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.6f);
        world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 0.5, 0), 80, radius / 2, 0.3, radius / 2, 0.1);
        for (LivingEntity target : victims) {
            target.setVelocity(new Vector(0, 0.55, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, lift, 2, false, true, true));
        }
        repeat(2, lift / 2, step -> {
            for (LivingEntity target : victims) {
                if (target.isValid()) {
                    world.spawnParticle(Particle.DUST, target.getLocation(), 3, 0.3, 0.1, 0.3, 0, GRAVITY_DUST);
                }
            }
            return true;
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity target : victims) {
                if (!target.isValid() || target.isDead() || !player.isOnline()) {
                    continue;
                }
                target.removePotionEffect(PotionEffectType.LEVITATION);
                double height = heightAboveGround(target.getLocation());
                target.setVelocity(new Vector(0, -2.8, 0));
                hurt(player, target, Math.min(cap, base + perBlock * height));
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation(), 20, 0.3, 0.6, 0.3, 0, GRAVITY_CORE);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f);
            }
        }, lift);
        return true;
    }

    // ================================================================ HEXBOW

    /** Mirror Shot: your next arrow (within a few seconds) swaps your place with whatever it hits. */
    private boolean mirrorShot(Player player, Params p) {
        mirrorArmed.put(player.getUniqueId(), System.currentTimeMillis() + (long) (p.d("window", 10) * 1000));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.2f);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
        return true;
    }

    /** Hex Mark: the entity you look at glows and takes extra damage from every source. */
    private boolean hexMark(Player player, Params p) {
        LivingEntity target = lookTarget(player, p.d("range", 32));
        if (target == null) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        int seconds = p.i("duration", 10);
        hexed.put(target.getUniqueId(), new Hex(1 + p.d("amplify", 30) / 100.0,
                System.currentTimeMillis() + seconds * 1000L));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, seconds * 20, 0, false, false, false));
        World world = player.getWorld();
        line(player.getEyeLocation(), target.getLocation().add(0, target.getHeight() / 2, 0), 0.5,
                l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, HEX_DUST));
        world.spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0.1);
        world.playSound(target.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.6f);
        if (target instanceof Player victim) {
            victim.sendActionBar(plugin.raw("ability-hexed", "&2You have been hexed!"));
        }
        return true;
    }

    // ================================================================ SOULBINDER

    /** Soul Link: chain yourself to a target; part of all damage you take is sent to it instead. */
    private boolean soulLink(Player player, Params p) {
        LivingEntity target = lookTarget(player, p.d("range", 20));
        if (target == null) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        int seconds = p.i("duration", 6);
        Link link = new Link(target.getUniqueId(), p.d("percent", 50) / 100.0, p.d("break-distance", 25),
                System.currentTimeMillis() + seconds * 1000L);
        links.put(player.getUniqueId(), link);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 2f, 0.6f);
        world.playSound(target.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 0.5f);
        repeat(3, seconds * 7, step -> {
            if (links.get(player.getUniqueId()) != link) {
                return false;
            }
            if (!linkHolds(player, link, target)) {
                links.remove(player.getUniqueId());
                if (player.isOnline() && System.currentTimeMillis() < link.expires()) {
                    fail(player, "ability-link-broken", "&3Soul link broken.");
                }
                return false;
            }
            line(player.getLocation().add(0, 1, 0), target.getLocation().add(0, target.getHeight() / 2, 0), 0.6,
                    l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, SOUL_DUST));
            if (step % 4 == 0) {
                world.spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 2, 0.3, 0.4, 0.3, 0.02);
            }
            return true;
        });
        return true;
    }

    private boolean linkHolds(Player player, Link link, LivingEntity target) {
        return player.isOnline() && !player.isDead() && target.isValid() && !target.isDead()
                && System.currentTimeMillis() < link.expires() && target.getWorld() == player.getWorld()
                && target.getLocation().distanceSquared(player.getLocation()) <= link.maxDistance() * link.maxDistance();
    }

    /** Harvest: spend all souls to heal, gain absorption and launch homing soul wisps. */
    private boolean harvest(Player player, Params p) {
        int count = souls.getOrDefault(player.getUniqueId(), 0);
        if (count <= 0) {
            fail(player, "ability-no-souls", "&3You have no souls to harvest.");
            return false;
        }
        souls.remove(player.getUniqueId());
        double heal = count * p.d("heal-per-soul", 2);
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + heal(player, heal)));
        if (count >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, p.i("absorption-seconds", 15) * 20,
                    (count - 1) / 2, false, true, true));
        }
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.5f, 0.8f);
        world.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 2f, 1f);
        world.spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 15 * count, 0.5, 0.8, 0.5, 0.05);

        double range = p.d("range", 16);
        double damage = p.d("damage-per-soul", 3);
        Location origin = player.getLocation();
        List<LivingEntity> enemies = new ArrayList<>(targets(player, origin, range));
        enemies.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(origin)));
        for (int i = 0; i < count && !enemies.isEmpty(); i++) {
            LivingEntity target = enemies.get(i % enemies.size());
            Bukkit.getScheduler().runTaskLater(plugin, () -> wisp(player, target, damage), i * 4L);
        }
        return true;
    }

    /** A homing soul wisp flying from the player to the target. */
    private void wisp(Player player, LivingEntity target, double damage) {
        if (!player.isOnline() || !target.isValid()) {
            return;
        }
        Location pos = player.getEyeLocation().add(ThreadLocalRandom.current().nextDouble(-0.6, 0.6), 0.4,
                ThreadLocalRandom.current().nextDouble(-0.6, 0.6));
        World world = pos.getWorld();
        world.playSound(pos, Sound.ENTITY_ALLAY_DEATH, 0.6f, 1.6f);
        repeat(1, 80, step -> {
            if (!target.isValid() || target.isDead() || target.getWorld() != world) {
                return false;
            }
            Location goal = target.getLocation().add(0, target.getHeight() / 2, 0);
            Vector to = goal.toVector().subtract(pos.toVector());
            if (to.lengthSquared() < 1.0) {
                hurt(player, target, damage);
                world.spawnParticle(Particle.SCULK_SOUL, goal, 12, 0.2, 0.3, 0.2, 0.05);
                world.playSound(goal, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 1.4f);
                return false;
            }
            pos.add(to.normalize().multiply(0.75));
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, pos, 2, 0.05, 0.05, 0.05, 0);
            world.spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0, SOUL_DUST);
            return true;
        });
    }

    // ================================================================ HYDRA EDGE

    /** Crescent: a full 360° swing that hits everyone around you and heals per enemy hit. */
    private boolean crescent(Player player, Params p) {
        double radius = p.d("radius", 4.5);
        double damage = p.d("damage", 8);
        double healPer = p.d("heal-per-target", 2);
        Location center = player.getLocation();
        List<LivingEntity> hit = targets(player, center, radius);
        World world = player.getWorld();
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
        world.playSound(center, Sound.ITEM_TRIDENT_RETURN, 1.2f, 0.6f);
        double yaw0 = Math.toRadians(center.getYaw());
        repeat(1, 6, step -> {
            for (int k = 0; k < 10; k++) {
                double a = yaw0 + (step * 10 + k) * (2 * Math.PI / 60);
                for (double r = 1.2; r <= radius; r += 0.9) {
                    world.spawnParticle(Particle.DUST, center.clone().add(-Math.sin(a) * r, 1.0, Math.cos(a) * r),
                            1, 0, 0, 0, 0, HYDRA_DUST);
                }
            }
            world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0, 1, 0), 2, radius / 2, 0.1, radius / 2, 0);
            return true;
        });
        for (LivingEntity target : hit) {
            hurt(player, target, damage);
            Vector out = target.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (out.lengthSquared() > 1.0E-4) {
                out.normalize().multiply(0.6);
            }
            target.setVelocity(target.getVelocity().add(out.setY(0.25)));
        }
        if (!hit.isEmpty()) {
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + heal(player, healPer * hit.size())));
        }
        return true;
    }

    /** Hydra's Wrath: a few seconds of frenzy - faster, stronger, and every hit cleaves twice as hard. */
    private boolean frenzy(Player player, Params p) {
        int seconds = p.i("duration", 6);
        frenzy.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, seconds * 20, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0, false, true, true));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.3f);
        repeat(4, seconds * 5, step -> {
            if (!player.isOnline() || player.isDead()) {
                return false;
            }
            Location l = player.getLocation().add(0, 1, 0);
            for (int head = 0; head < 3; head++) {
                double a = step * 0.5 + head * 2 * Math.PI / 3;
                world.spawnParticle(Particle.DUST, l.clone().add(Math.cos(a) * 0.8, 0.6 + 0.2 * Math.sin(step + head), Math.sin(a) * 0.8),
                        2, 0.05, 0.05, 0.05, 0, HYDRA_DUST);
            }
            return true;
        });
        return true;
    }

    // ================================================================ BUTCHER'S CLEAVER

    /**
     * Cleaver Toss: throw the cleaver (costs a bit of your own health). It hits the first enemy for
     * a share of its CURRENT health and slows it; a hit heals you and gives back part of the cooldown.
     */
    private boolean cleaverToss(Player player, ItemManager.Entry weapon, Active active) {
        Params p = active.params();
        double cost = p.d("health-cost", 2);
        if (player.getHealth() <= cost + 1) {
            fail(player, "ability-too-hurt", "&cYou're too hurt for that.");
            return false;
        }
        double range = p.d("range", 18);
        double speed = Math.max(0.3, p.d("speed", 1.4));
        double base = p.d("damage", 4);
        double percent = p.d("current-health-percent", 15) / 100.0;
        double cap = p.d("max-damage", 16);
        int slowTicks = p.i("slow-ticks", 40);
        int slowLevel = Math.max(0, p.i("slow-level", 2) - 1);
        double healPercent = p.d("heal-percent-max-hp", 5) / 100.0;
        double refundShare = p.d("refund-percent", 50) / 100.0;

        player.setHealth(Math.max(1, player.getHealth() - cost));
        World world = player.getWorld();
        Location pos = player.getEyeLocation().subtract(0, 0.35, 0);
        Vector dir = pos.getDirection().normalize();
        float yaw = pos.getYaw() + 90f;
        ItemStack look = weapon.item.clone();
        ItemDisplay display = world.spawn(pos.clone(), ItemDisplay.class, d -> {
            d.setItemStack(look);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setRotation(yaw, 0);
        });
        world.playSound(pos, Sound.ENTITY_WITCH_THROW, 1.2f, 0.6f);
        world.playSound(pos, Sound.ITEM_TRIDENT_THROW, 1f, 0.7f);
        world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0, BLOOD_SMALL);
        int steps = (int) Math.ceil(range / speed);
        repeat(1, steps + 1, step -> {
            if (!display.isValid()) {
                return false;
            }
            if (step >= steps || !player.isOnline()) {
                world.spawnParticle(Particle.DUST, pos, 10, 0.2, 0.2, 0.2, 0, BLOOD_SMALL);
                display.remove();
                return false;
            }
            RayTraceResult hit = world.rayTrace(pos, dir, speed, FluidCollisionMode.NEVER, true, 0.6, e -> valid(player, e));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
                hurt(player, target, Math.min(cap, base + target.getHealth() * percent));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowLevel));
                double max = maxHealth(player);
                if (!player.isDead()) {
                    player.setHealth(Math.min(max, player.getHealth() + heal(player, max * healPercent)));
                }
                refund(player, weapon, active, refundShare);
                world.spawnParticle(Particle.DUST, at, 30, 0.35, 0.45, 0.35, 0, BLOOD_DUST);
                world.spawnParticle(Particle.DUST, at, 10, 0.3, 0.4, 0.3, 0, PLAGUE_DUST);
                world.spawnParticle(Particle.CRIT, at, 15, 0.3, 0.4, 0.3, 0.3);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
                world.playSound(at, Sound.BLOCK_HONEY_BLOCK_BREAK, 1.2f, 0.6f);
                display.remove();
                return false;
            }
            if (hit != null && hit.getHitBlock() != null) {
                Location at = hit.getHitPosition().toLocation(world);
                world.spawnParticle(Particle.BLOCK, at, 15, 0.2, 0.2, 0.2, 0, hit.getHitBlock().getBlockData());
                world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.7f);
                display.remove();
                return false;
            }
            pos.add(dir.clone().multiply(speed));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf().rotateZ((float) (-step * 0.9)), new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf()));
            Location to = pos.clone();
            to.setYaw(yaw);
            to.setPitch(0);
            display.teleport(to);
            world.spawnParticle(Particle.DUST, pos, 1, 0.05, 0.05, 0.05, 0, BLOOD_SMALL);
            if (step % 3 == 0) {
                world.playSound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4f, 1.6f);
            }
            return true;
        });
        return true;
    }

    /**
     * Bloodrush: pay a slice of your current health, then regenerate a big share of your MAX health
     * over a few seconds, run faster and shrug off debuffs. Scales with max HP (Heartsteel!).
     */
    private boolean bloodrush(Player player, Params p) {
        int seconds = Math.max(1, p.i("duration", 10));
        double total = p.d("heal-percent-max-hp", 40) / 100.0;
        double cost = p.d("health-cost-percent", 10) / 100.0;
        int speedLevel = Math.max(0, p.i("speed-level", 2) - 1);
        player.setHealth(Math.max(1, player.getHealth() * (1 - cost)));
        bloodrush.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        for (PotionEffectType type : DEBUFFS) {
            player.removePotionEffect(type);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, speedLevel, false, true, true));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.7f);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.2f);
        world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0, BLOOD_DUST);
        int pulses = seconds * 4;
        repeat(5, pulses, step -> {
            if (!player.isOnline() || player.isDead()) {
                bloodrush.remove(player.getUniqueId());
                return false;
            }
            double max = maxHealth(player);
            double add = heal(player, max * total / pulses);
            player.setHealth(Math.min(max, player.getHealth() + add));
            Location l = player.getLocation();
            for (int k = 0; k < 2; k++) {
                double a = step * 0.8 + k * Math.PI;
                world.spawnParticle(Particle.DUST, l.clone().add(Math.cos(a) * 0.7, 0.2 + (step % 8) * 0.22, Math.sin(a) * 0.7),
                        2, 0.03, 0.03, 0.03, 0, BLOOD_SMALL);
            }
            if (step % 4 == 0) {
                world.playSound(l, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 1.4f);
            }
            if (step == pulses - 1) {
                bloodrush.remove(player.getUniqueId());
            }
            return true;
        });
        return true;
    }

    /** During Bloodrush, new debuffs simply don't stick. */
    @EventHandler(ignoreCancelled = true)
    public void onDebuff(EntityPotionEffectEvent event) {
        if (event.getNewEffect() == null || !DEBUFFS.contains(event.getNewEffect().getType())) {
            return;
        }
        Long until = bloodrush.get(event.getEntity().getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    // ================================================================ ABYSSAL MAW

    private int tasteOf(Entity entity) {
        Taste t = taste.get(entity.getUniqueId());
        return t == null || t.expires() < System.currentTimeMillis() ? 0 : t.stacks();
    }

    /** Adds Taste stacks to a target (refreshes the timer). */
    private void addTaste(LivingEntity target, int amount, int max, double seconds) {
        int now = Math.min(max, tasteOf(target) + amount);
        taste.put(target.getUniqueId(), new Taste(now, max, System.currentTimeMillis() + (long) (seconds * 1000)));
        World world = target.getWorld();
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        world.spawnParticle(Particle.DUST, at, 6, 0.3, 0.4, 0.3, 0, ABYSS_DUST);
        if (now >= max) {
            world.playSound(at, Sound.ENTITY_FROG_AMBIENT, 1.5f, 0.6f);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.6f);
        }
    }

    /** Taste settings of the weapon (on-hit TASTE entry), used by the tongue too. */
    private static Params tasteParams(ItemManager.Entry weapon) {
        OnHit hit = weapon.abilities.onHit(HitType.TASTE);
        return hit == null ? new Params(Map.of()) : hit.params();
    }

    /**
     * Tongue Lash: a long tongue that grabs the first enemy, damages and slows it and yanks it to you.
     * Its reach and damage grow with your MAX health.
     */
    private boolean tongueLash(Player player, ItemManager.Entry weapon, Params p) {
        double max = maxHealth(player);
        double range = Math.min(p.d("max-range", 20),
                p.d("range", 8) + Math.max(0, max - 20) * p.d("range-per-health", 0.25));
        double damage = p.d("damage", 3) + max * p.d("damage-percent-max-hp", 4) / 100.0;
        double pull = p.d("pull", 1.1);
        int slowTicks = p.i("slow-ticks", 40);
        int slowLevel = Math.max(0, p.i("slow-level", 2) - 1);
        double speed = 2.5;
        Params tp = tasteParams(weapon);
        World world = player.getWorld();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        world.playSound(player.getLocation(), Sound.ENTITY_FROG_TONGUE, 1.5f, 0.6f);
        double[] reach = {0};
        repeat(1, (int) Math.ceil(range / speed) + 2, step -> {
            if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                return false;
            }
            Location mouth = mouth(player);
            double seg = Math.min(speed, range - reach[0]);
            Location tip = mouth.clone().add(dir.clone().multiply(reach[0]));
            RayTraceResult hit = seg <= 0 ? null
                    : world.rayTrace(tip, dir, seg, FluidCollisionMode.NEVER, true, 0.5, e -> valid(player, e));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                Location grab = hit.getHitPosition().toLocation(world);
                tongue(mouth, grab);
                hurt(player, target, damage);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowLevel));
                addTaste(target, tp.i("tongue-stacks", 1), Math.max(1, tp.i("max", 3)), tp.d("duration", 6));
                Vector toward = player.getLocation().toVector().subtract(target.getLocation().toVector());
                double distance = toward.length();
                if (distance > 1.5) {
                    Vector v = toward.normalize().multiply(Math.min(2.6, pull * (0.45 + distance * 0.09))).setY(0.38);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (target.isValid()) {
                            target.setVelocity(v);
                        }
                    });
                }
                world.playSound(grab, Sound.ENTITY_SLIME_ATTACK, 1.2f, 0.7f);
                world.playSound(grab, Sound.ENTITY_FROG_EAT, 1.2f, 0.8f);
                world.spawnParticle(Particle.ITEM_SLIME, grab, 12, 0.2, 0.3, 0.2, 0.05);
                retract(player, grab);
                return false;
            }
            if ((hit != null && hit.getHitBlock() != null) || reach[0] + seg >= range) {
                Location end = hit != null ? hit.getHitPosition().toLocation(world) : tip.clone().add(dir.clone().multiply(seg));
                tongue(mouth, end);
                if (hit != null) {
                    world.playSound(end, Sound.BLOCK_SLIME_BLOCK_HIT, 1f, 0.8f);
                }
                retract(player, end);
                return false;
            }
            reach[0] += seg;
            tongue(mouth, mouth.clone().add(dir.clone().multiply(reach[0])));
            return true;
        });
        return true;
    }

    private static Location mouth(Player player) {
        return player.getEyeLocation().subtract(0, 0.3, 0).add(player.getEyeLocation().getDirection().multiply(0.3));
    }

    private void tongue(Location from, Location to) {
        World world = from.getWorld();
        line(from, to, 0.22, l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, TONGUE_DUST));
        world.spawnParticle(Particle.DUST, to, 3, 0.06, 0.06, 0.06, 0, TONGUE_TIP);
    }

    /** The tongue snaps back into the mouth over a few ticks. */
    private void retract(Player player, Location end) {
        repeat(1, 3, step -> {
            if (!player.isOnline()) {
                return false;
            }
            Location mouth = mouth(player);
            if (mouth.getWorld() != end.getWorld()) {
                return false;
            }
            double t = 1 - (step + 1) / 4.0;
            tongue(mouth, mouth.clone().add(end.toVector().subtract(mouth.toVector()).multiply(t)));
            return true;
        });
    }

    /**
     * Devour: swallow the entity you look at.
     * Ally (any player without full Taste): hidden and untouchable inside you, sneak to hop out.
     * Enemy (full Taste stacks): trapped and blinded for a moment, then spat out far and hurt.
     */
    private boolean devour(Player player, Params p) {
        if (belly.containsKey(player.getUniqueId())) {
            fail(player, "ability-devour-full", "&cYour belly is already full.");
            return false;
        }
        Location eye = player.getEyeLocation();
        RayTraceResult ray = player.getWorld().rayTrace(eye, eye.getDirection(), p.d("range", 4.5),
                FluidCollisionMode.NEVER, true, 0.6, e -> e instanceof LivingEntity living && !e.equals(player)
                        && living.isValid() && !living.isDead() && !(e instanceof ArmorStand)
                        && !swallowed.containsKey(e.getUniqueId()) && !belly.containsKey(e.getUniqueId()));
        if (ray == null || !(ray.getHitEntity() instanceof LivingEntity target)) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        List<String> immune = plugin.getConfig().getStringList("ability-settings.devour-immune");
        if (target instanceof Boss || immune.contains(target.getType().name())
                || (target instanceof Player tp && tp.getGameMode() == GameMode.SPECTATOR)) {
            fail(player, "ability-devour-too-big", "&cThat's way too big to swallow.");
            return false;
        }
        int need = Math.max(1, p.i("stacks-needed", 3));
        boolean ally;
        boolean teams = plugin.teams() != null && plugin.teams().enabled();
        if (target instanceof Player tp && teams && plugin.teams().allies(player, tp)) {
            ally = true;                                         // a teammate: hidden inside you
        } else if (tasteOf(target) >= need && valid(player, target)) {
            ally = false;
        } else if (target instanceof Player && !teams) {
            ally = true;
        } else {
            player.sendActionBar(plugin.raw("ability-devour-taste", "&cIt needs &6{stacks} Taste &cstacks first.",
                    "stacks", String.valueOf(need)));
            return false;
        }
        double seconds = ally ? p.d("ally-duration", 4) : p.d("enemy-duration", 2);
        int ticks = (int) Math.round(seconds * 20);
        target.leaveVehicle();
        target.eject();
        Swallow s = new Swallow(player.getUniqueId(), target.getUniqueId(), ally,
                System.currentTimeMillis() + (long) (seconds * 1000),
                p.d("spit-damage", 6) + maxHealth(player) * p.d("spit-damage-percent-max-hp", 5) / 100.0,
                p.d("spit-launch", 1.8));
        taste.remove(target.getUniqueId());
        if (!player.addPassenger(target)) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        swallowed.put(target.getUniqueId(), s);
        belly.put(player.getUniqueId(), target.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) {
                viewer.hideEntity(plugin, target);
            }
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, ally ? 0 : 1, false, true, true));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_FROG_EAT, 2f, 0.5f);
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.5f, 0.6f);
        world.spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.1);
        world.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0, ABYSS_DUST);
        if (target instanceof Player victim) {
            if (ally) {
                victim.sendActionBar(plugin.raw("ability-devoured-ally",
                        "&3You were swallowed for safety. &7Sneak to hop out."));
            } else {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks + 20, 0, false, false, false));
                victim.sendActionBar(plugin.raw("ability-devoured-enemy", "&4You've been devoured!"));
            }
        }
        if (!ally) {
            plugin.award(player, "swallowed_whole");
        }
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> devourTick(s), 1L, 1L);
        return true;
    }

    private void devourTick(Swallow s) {
        Player devourer = Bukkit.getPlayer(s.devourer);
        Entity target = Bukkit.getEntity(s.target);
        if (devourer == null || target == null || !target.isValid() || devourer.isDead()
                || !devourer.getPassengers().contains(target)) {
            release(s, false);
            return;
        }
        if (System.currentTimeMillis() >= s.ends) {
            release(s, true);
            return;
        }
        target.setFireTicks(0);
        target.setFallDistance(0);
        if (target instanceof LivingEntity living) {
            living.setRemainingAir(living.getMaximumAir());
        }
        if (devourer.getTicksLived() % 5 == 0) {
            Location belly = devourer.getLocation().add(0, 0.9, 0);
            devourer.getWorld().spawnParticle(Particle.DUST, belly, 2, 0.3, 0.3, 0.3, 0, ABYSS_DUST);
            devourer.getWorld().spawnParticle(Particle.BUBBLE_POP, belly, 2, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Lets a swallowed entity out. launch = time is up (enemies get spat out and hurt). */
    private void release(Swallow s, boolean launch) {
        if (swallowed.get(s.target) != s) {
            return;
        }
        swallowed.remove(s.target);
        belly.remove(s.devourer);
        if (s.task != null) {
            s.task.cancel();
        }
        Player devourer = Bukkit.getPlayer(s.devourer);
        Entity target = Bukkit.getEntity(s.target);
        if (target != null) {
            target.leaveVehicle();
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                viewer.showEntity(plugin, target);
            }
        }
        if (devourer != null) {
            devourer.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        if (!(target instanceof LivingEntity living) || !living.isValid() || devourer == null
                || !devourer.isOnline() || devourer.getWorld() != living.getWorld()) {
            return;
        }
        if (living instanceof Player victim && !s.ally) {
            victim.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        Location base = devourer.getLocation();
        Vector facing = base.getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Location out = base.clone().add(facing.clone().multiply(1.1));
        if (!out.getBlock().isPassable() || !out.clone().add(0, 1, 0).getBlock().isPassable()) {
            out = base.clone();
        }
        out.setYaw(living.getLocation().getYaw());
        out.setPitch(living.getLocation().getPitch());
        living.teleport(out);
        living.setFallDistance(0);
        World world = living.getWorld();
        world.playSound(out, Sound.ENTITY_PLAYER_BURP, 1.5f, 0.6f);
        world.playSound(out, Sound.ENTITY_LLAMA_SPIT, 1.2f, 0.5f);
        world.spawnParticle(Particle.ITEM_SLIME, out.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.15);
        world.spawnParticle(Particle.SPLASH, out.clone().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.2);
        if (!s.ally && launch) {
            Vector v = facing.multiply(s.launch).setY(0.55);
            hurt(devourer, living, s.spitDamage);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (living.isValid()) {
                    living.setVelocity(v);
                }
            });
        }
    }

    /** Lets everybody out (plugin shutdown). */
    public void shutdown() {
        for (Swallow s : new ArrayList<>(swallowed.values())) {
            release(s, false);
        }
        for (UUID id : new ArrayList<>(axes.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                dropAxeState(player);
            } else {
                AxeState state = axes.remove(id);
                state.display.remove();
            }
        }
        for (UUID id : new ArrayList<>(cloaked.keySet())) {
            uncloak(id);
        }
        for (UUID id : new ArrayList<>(pinned.keySet())) {
            if (Bukkit.getEntity(id) instanceof LivingEntity living) {
                unpin(living);
            }
        }
    }

    public boolean isSwallowed(Entity entity) {
        return swallowed.containsKey(entity.getUniqueId());
    }

    /** Allies may hop out by sneaking; enemies stay in until they're spat out. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDismount(EntityDismountEvent event) {
        Swallow s = swallowed.get(event.getEntity().getUniqueId());
        if (s == null || !event.getDismounted().getUniqueId().equals(s.devourer)) {
            return;
        }
        Player devourer = Bukkit.getPlayer(s.devourer);
        if (!s.ally && devourer != null && devourer.isOnline() && !devourer.isDead()) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> release(s, false));
    }

    /** Nothing can hurt a swallowed entity (except the void / kill). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwallowedDamage(EntityDamageEvent event) {
        if (swallowed.containsKey(event.getEntity().getUniqueId())
                && event.getCause() != EntityDamageEvent.DamageCause.VOID
                && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
        }
    }

    /** ... and a swallowed entity can't hurt anyone either. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwallowedAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (swallowed.containsKey(damager.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwallowedShoot(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Entity shooter && swallowed.containsKey(shooter.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Teleporting the devourer or the swallowed one ends Devour first. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Swallow s = swallowed.get(id);
        if (s == null) {
            UUID inside = belly.get(id);
            s = inside == null ? null : swallowed.get(inside);
        }
        if (s != null) {
            release(s, false);
        }
    }

    @EventHandler
    public void onJoinCloak(PlayerJoinEvent event) {
        for (UUID id : cloaked.keySet()) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !hidden.equals(event.getPlayer())) {
                event.getPlayer().hidePlayer(plugin, hidden);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (UUID id : swallowed.keySet()) {
            Entity hidden = Bukkit.getEntity(id);
            if (hidden != null && !hidden.equals(event.getPlayer())) {
                event.getPlayer().hideEntity(plugin, hidden);
            }
        }
    }

    /** Ends Devour when the devourer or the swallowed entity dies or leaves. */
    private void releaseFor(UUID id) {
        Swallow s = swallowed.get(id);
        if (s == null) {
            UUID inside = belly.get(id);
            s = inside == null ? null : swallowed.get(inside);
        }
        if (s != null) {
            release(s, false);
        }
    }

    // ================================================================ CLAWLINE PICK

    /** Clawline: shoot a hook at the block you look at and get yanked there. */
    private boolean clawline(Player player, Params p) {
        Location eye = player.getEyeLocation();
        RayTraceResult ray = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), p.d("range", 24),
                FluidCollisionMode.NEVER, true);
        if (ray == null || ray.getHitBlock() == null) {
            fail(player, "ability-clawline-miss", "&cNothing to hook onto.");
            return false;
        }
        Location hook = ray.getHitPosition().toLocation(player.getWorld());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.2f, 0.6f);
        grapple(player, hook, null, p.d("power", 1.0), 0);
        return true;
    }

    /**
     * A silk thread flies to the point (2 ticks), then yanks the player toward it.
     * If target is set, the thread follows that entity and the player stops a bit short of it.
     */
    private void grapple(Player player, Location point, LivingEntity target, double power, double stopShort) {
        World world = player.getWorld();
        int flyTicks = 2;
        repeat(1, flyTicks + 8, step -> {
            if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                return false;
            }
            Location to = target != null && target.isValid()
                    ? target.getLocation().add(0, target.getHeight() / 2, 0) : point;
            Location hand = player.getLocation().add(0, 1.2, 0);
            if (step < flyTicks) {
                Location tip = hand.clone().add(to.toVector().subtract(hand.toVector()).multiply((step + 1.0) / flyTicks));
                line(hand, tip, 0.3, l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, SILK_DUST));
                return true;
            }
            if (step == flyTicks) {
                world.playSound(to, Sound.BLOCK_CHAIN_HIT, 1.2f, 1.4f);
                world.playSound(hand, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.2f, 0.8f);
                Vector pull = to.toVector().subtract(player.getLocation().toVector());
                double distance = Math.max(0, pull.length() - stopShort);
                if (pull.lengthSquared() > 1.0E-4) {
                    pull.normalize().multiply(Math.min(3.2, (0.45 + distance * 0.11) * power));
                    pull.setY(Math.max(pull.getY(), 0) + 0.3);
                    player.setVelocity(pull);
                }
                noFall.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
            }
            line(hand, to, 0.35, l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, SILK_DUST));
            return player.getLocation().distanceSquared(to) > 2.5;
        });
    }

    /**
     * Dive: hop up, then slam straight down, carving a 3x3 shaft (protection plugins are respected).
     * Landing sends out a shockwave.
     */
    private boolean dive(Player player, Params p) {
        int depth = Math.max(1, p.i("depth", 8));
        double maxHardness = p.d("max-hardness", 30);
        double damage = p.d("impact-damage", 6);
        double radius = p.d("impact-radius", 4);
        World world = player.getWorld();
        Block start = player.getLocation().getBlock();
        int cx = start.getX();
        int cz = start.getZ();
        int topY = start.getY() - 1;
        ItemStack tool = player.getInventory().getItemInMainHand();
        player.setVelocity(new Vector(0, 0.8, 0));
        noFall.put(player.getUniqueId(), System.currentTimeMillis() + 8000);
        world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1.2f, 0.7f);
        world.spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.4, 0.1, 0.4, 0.05);
        int[] dug = {0};
        boolean[] stopped = {false};
        repeat(1, 8 + depth + 60, step -> {
            if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                return false;
            }
            if (step < 8) {
                return true;                                   // still rising
            }
            if (step == 8) {
                world.playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_AIR, 1.2f, 0.6f);
            }
            Location at = player.getLocation();
            world.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 3, 0.25, 0.5, 0.25, 0, SOUL_WHITE);
            if (!stopped[0] && dug[0] < depth) {
                int y = topY - dug[0];
                if (y < world.getMinHeight()
                        || !breakable(world.getBlockAt(cx, y, cz), maxHardness)) {
                    stopped[0] = true;
                } else {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block b = world.getBlockAt(cx + dx, y, cz + dz);
                            if (breakable(b, maxHardness)) {
                                breakAs(player, b, tool);
                            }
                        }
                    }
                    world.playSound(new Location(world, cx + 0.5, y, cz + 0.5), Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.7f);
                    dug[0]++;
                }
            }
            player.setVelocity(new Vector(0, -1.6, 0));
            boolean done = stopped[0] || dug[0] >= depth;
            if (done && (player.isOnGround() || step >= 8 + depth + 55)) {
                impact(player, damage, radius);
                return false;
            }
            return true;
        });
        return true;
    }

    private static boolean breakable(Block block, double maxHardness) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) {
            return false;
        }
        float hardness = type.getHardness();
        return hardness >= 0 && hardness <= maxHardness
                && !(block.getState(false) instanceof org.bukkit.block.Container);
    }

    /** Breaks a block as if the player mined it (fires BlockBreakEvent so claims / regions apply). */
    private void breakAs(Player player, Block block, ItemStack tool) {
        org.bukkit.event.block.BlockBreakEvent check = new org.bukkit.event.block.BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(check);
        if (check.isCancelled()) {
            return;
        }
        if (check.isDropItems() && player.getGameMode() != GameMode.CREATIVE) {
            block.breakNaturally(tool, true);
        } else {
            block.setType(Material.AIR);
        }
    }

    private void impact(Player player, double damage, double radius) {
        Location at = player.getLocation();
        World world = player.getWorld();
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 0.7f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        ring(at.clone().add(0, 0.2, 0), radius, 40, l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0.05, 0, 0, SOUL_WHITE));
        world.spawnParticle(Particle.EXPLOSION, at, 2, 0.5, 0.2, 0.5, 0);
        for (LivingEntity target : targets(player, at, radius)) {
            hurt(player, target, damage);
            Vector out = target.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (out.lengthSquared() > 1.0E-4) {
                out.normalize().multiply(0.9);
            }
            target.setVelocity(out.setY(0.5));
        }
    }

    /** Tools with ore-double-chance: a mined ore sometimes drops twice. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(org.bukkit.event.block.BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!event.isDropItems() || player.getGameMode() == GameMode.CREATIVE || !enabledIn(player.getWorld())) {
            return;
        }
        ItemManager.Entry tool = weaponOf(player.getInventory().getItemInMainHand());
        if (tool == null || tool.abilities.oreDoubleChance <= 0) {
            return;
        }
        Block block = event.getBlock();
        String name = block.getType().name();
        if (!name.endsWith("_ORE") && block.getType() != Material.ANCIENT_DEBRIS) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)
                || ThreadLocalRandom.current().nextDouble(100) >= tool.abilities.oreDoubleChance) {
            return;
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : block.getDrops(hand, player)) {
            block.getWorld().dropItemNaturally(center, drop);
        }
        block.getWorld().spawnParticle(Particle.WAX_OFF, center, 12, 0.3, 0.3, 0.3, 0.5);
        block.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
    }

    /** Remembers landings, so a hammer hit right after landing still counts as a slam. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLanding(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && player.getFallDistance() >= 1.5) {
            landed.put(player.getUniqueId(), new Landing(player.getFallDistance(), System.currentTimeMillis()));
        }
    }

    /** Clawline / Dive / Silk Dart: no fall damage right after. */
    @EventHandler(ignoreCancelled = true)
    public void onToolFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Long until = noFall.remove(event.getEntity().getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    // ================================================================ SILK NEEDLE

    /** Silk Dart: throw the needle on a thread. It stabs the first enemy and pulls you to it (or to a block). */
    private boolean silkDart(Player player, Params p) {
        Location eye = player.getEyeLocation();
        RayTraceResult ray = player.getWorld().rayTrace(eye, eye.getDirection(), p.d("range", 16),
                FluidCollisionMode.NEVER, true, 0.5, e -> valid(player, e));
        if (ray == null) {
            fail(player, "ability-no-target", "&cNo target in range.");
            return false;
        }
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.8f);
        if (ray.getHitEntity() instanceof LivingEntity target) {
            double damage = p.d("damage", 4);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isValid() && player.isOnline()) {
                    hurt(player, target, damage);
                    world.spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() / 2, 0), 12, 0.2, 0.3, 0.2, 0.3);
                }
            }, 2L);
            grapple(player, target.getLocation(), target, p.d("power", 1.1), 1.6);
        } else {
            grapple(player, ray.getHitPosition().toLocation(world), null, p.d("power", 1.1), 0);
        }
        return true;
    }

    /** Thread Storm: spin in a storm of silk for a few seconds, cutting everything around you. */
    private boolean threadStorm(Player player, Params p) {
        int ticks = Math.max(10, p.i("duration-ticks", 60));
        double radius = p.d("radius", 3.5);
        double damage = p.d("damage", 1.5);
        int every = Math.max(2, p.i("hit-every-ticks", 5));
        World world = player.getWorld();
        if (!player.isOnGround()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks, 0, false, false, true));
        }
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.5f);
        repeat(1, ticks, step -> {
            if (!player.isOnline() || player.isDead()) {
                return false;
            }
            Location c = player.getLocation();
            for (int k = 0; k < 6; k++) {
                double a = step * 0.55 + k * Math.PI / 3;
                double r = radius * (0.35 + 0.65 * (((step + k * 3) % 10) / 10.0));
                double y = 0.3 + (k % 3) * 0.6 + 0.15 * Math.sin(step * 0.4 + k);
                world.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 1, 0, 0, 0, 0,
                        k % 3 == 0 ? NEEDLE_RED : SILK_DUST);
            }
            if (step % every == 0) {
                world.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.8f);
                world.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(0, 1, 0), 2, radius / 2, 0.2, radius / 2, 0);
                for (LivingEntity target : targets(player, c, radius)) {
                    hurt(player, target, damage);
                }
            }
            return true;
        });
        return true;
    }

    // ================================================================ shared: flying items

    private ItemDisplay spawnFlying(World world, Location pos, ItemStack look, float scale) {
        return world.spawn(pos.clone(), ItemDisplay.class, d -> {
            d.setItemStack(look);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    /** Moves a flying item one step: spin (tumbling) or point along the direction (spears). */
    private static void moveFlying(ItemDisplay display, Location pos, Vector dir, int step, boolean spin, float scale) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        Location to = pos.clone();
        to.setYaw(yaw + 90f);
        to.setPitch(0);
        Quaternionf rot = spin ? new Quaternionf().rotateZ((float) (-step * 0.9))
                : new Quaternionf().rotateZ((float) (Math.atan2(dir.getY(), Math.hypot(dir.getX(), dir.getZ())) - Math.PI / 4));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(scale, scale, scale), new Quaternionf()));
        display.teleport(to);
    }

    /**
     * Throws the weapon as a flying item. It hits the first enemy (onEntity) or block, optionally homes on a target,
     * and then flies back to the thrower.
     */
    private void throwWeapon(Player player, ItemStack look, Vector direction, double range, double speed, LivingEntity homing,
                             boolean spin, Consumer<LivingEntity> onEntity, Particle.DustOptions trail) {
        World world = player.getWorld();
        Location pos = player.getEyeLocation().subtract(0, 0.3, 0);
        Vector dir = direction.clone().normalize();
        ItemDisplay display = spawnFlying(world, pos, look, 1.3f);
        int steps = (int) Math.ceil(range / speed);
        repeat(1, steps + 1, step -> {
            if (!display.isValid()) {
                return false;
            }
            if (!player.isOnline() || player.getWorld() != world) {
                display.remove();
                return false;
            }
            if (homing != null && homing.isValid() && !homing.isDead() && homing.getWorld() == world) {
                Vector want = homing.getLocation().add(0, homing.getHeight() / 2, 0).toVector().subtract(pos.toVector());
                if (want.lengthSquared() > 1.0E-4) {
                    dir.multiply(0.5).add(want.normalize().multiply(0.5)).normalize();
                }
            }
            RayTraceResult hit = step >= steps ? null
                    : world.rayTrace(pos, dir, speed, FluidCollisionMode.NEVER, true, 0.6, e -> valid(player, e));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                onEntity.accept(target);
                flyBack(player, display, hit.getHitPosition().toLocation(world), spin, null, 0, null);
                return false;
            }
            if (step >= steps || (hit != null && hit.getHitBlock() != null)) {
                if (hit != null) {
                    world.playSound(pos, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
                }
                flyBack(player, display, pos, spin, null, 0, null);
                return false;
            }
            pos.add(dir.clone().multiply(speed));
            moveFlying(display, pos, dir, step, spin, 1.3f);
            if (trail != null) {
                world.spawnParticle(Particle.DUST, pos, 2, 0.08, 0.08, 0.08, 0, trail);
            }
            return true;
        });
    }

    /**
     * The flying item returns to the player's hand. With onPass set, everything it passes through
     * gets hit once on the way back.
     */
    private void flyBack(Player player, ItemDisplay display, Location from, boolean spin, Consumer<LivingEntity> onPass,
                         double passRadius, Runnable onCaught) {
        World world = from.getWorld();
        Location pos = from.clone();
        Set<UUID> passed = new java.util.HashSet<>();
        repeat(1, 100, step -> {
            if (!display.isValid() || !player.isOnline() || player.getWorld() != world) {
                display.remove();
                if (onCaught != null) {
                    onCaught.run();
                }
                return false;
            }
            Location hand = player.getLocation().add(0, 1.2, 0);
            Vector to = hand.toVector().subtract(pos.toVector());
            double distance = to.length();
            if (distance < 1.6 || step == 99) {
                display.remove();
                world.playSound(hand, Sound.ITEM_TRIDENT_RETURN, 1f, 1.2f);
                if (onCaught != null) {
                    onCaught.run();
                }
                return false;
            }
            Vector dir = to.multiply(1 / distance);
            pos.add(dir.clone().multiply(Math.min(distance, 1.8)));
            moveFlying(display, pos, dir, step, spin, 1.3f);
            if (onPass != null) {
                for (LivingEntity near : targets(player, pos, passRadius)) {
                    if (passed.add(near.getUniqueId())) {
                        onPass.accept(near);
                    }
                }
            }
            return true;
        });
    }

    // ================================================================ KNIGHTFALL (mace that upgrades with kills)

    private int killsOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        return stack.getPersistentDataContainer().getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
    }

    /** A kill with a kill-tracking weapon: +1 on the item, lore updated, unlocks announced. */
    private void addKill(Player killer, ItemManager.Entry weapon) {
        ItemStack hand = killer.getInventory().getItemInMainHand();
        org.bukkit.inventory.meta.ItemMeta meta = hand.getItemMeta();
        int kills = meta.getPersistentDataContainer().getOrDefault(killsKey, PersistentDataType.INTEGER, 0) + 1;
        meta.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, kills);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(
                weapon.item.getItemMeta().lore() == null ? List.of() : weapon.item.getItemMeta().lore());
        lore.add(ItemManager.color("&6⚔ Kills: &f" + kills));
        meta.lore(lore);
        hand.setItemMeta(meta);
        if (plugin.stars() != null && plugin.stars().enabled()) {
            plugin.stars().decorate(hand, weapon);                 // keeps the stars in the name and lore
        }
        killer.getInventory().setItemInMainHand(hand);
        List<String> unlocked = new ArrayList<>();
        for (Active active : weapon.abilities.actives) {
            if (active.params().i("unlock-kills", 0) == kills) {
                unlocked.add(active.name());
            }
        }
        if (weapon.abilities.passiveUnlockKills == kills) {
            unlocked.add("&dPassive");
        }
        if (!unlocked.isEmpty()) {
            killer.showTitle(net.kyori.adventure.title.Title.title(ItemManager.color("&6&lUPGRADE"),
                    ItemManager.color("&7Unlocked: " + String.join("&7, ", unlocked))));
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        }
    }

    /** Grapple: hook the enemy you look at and get pulled to it (several charges per cooldown). */
    private boolean grappleTo(Player player, Params p) {
        Location eye = player.getEyeLocation();
        RayTraceResult ray = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), p.d("range", 24),
                FluidCollisionMode.NEVER, true);
        if (ray == null || ray.getHitBlock() == null) {
            fail(player, "ability-clawline-miss", "&cNothing to hook onto.");
            return false;
        }
        World world = player.getWorld();
        Location hook = ray.getHitPosition().toLocation(world);
        double power = p.d("power", 1.15);
        double leap = p.d("leap", 0.9);
        long window = (long) (p.d("leap-window-ms", 1500));
        long start = System.currentTimeMillis();
        world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.7f);
        noFall.put(player.getUniqueId(), start + 4000);
        Object chain = new Object();
        repeat(1, 40, step -> {
            if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                ChainVisual.remove(chain);
                return false;
            }
            Location hand = player.getLocation().add(0, 1.2, 0);
            if (step < 2) {
                Location tip = hand.clone().add(hook.toVector().subtract(hand.toVector()).multiply((step + 1) / 2.0));
                ChainVisual.draw(plugin, chain, hand, tip);
                return true;
            }
            if (step == 2) {
                world.playSound(hook, Sound.BLOCK_CHAIN_HIT, 1.2f, 1.2f);
                Vector pull = hook.toVector().subtract(player.getLocation().toVector());
                double distance = pull.length();
                if (distance > 0.1) {
                    pull.normalize().multiply(Math.min(3.2, (0.45 + distance * 0.11) * power));
                    pull.setY(Math.max(pull.getY(), 0) + 0.3);
                    player.setVelocity(pull);
                }
            }
            ChainVisual.draw(plugin, chain, hand, hook);
            // reaching the end of the chain in time: leap
            if (player.getLocation().distanceSquared(hook) < 6.5) {
                ChainVisual.remove(chain);
                if (System.currentTimeMillis() - start <= window) {
                    Vector v = player.getVelocity();
                    player.setVelocity(new Vector(v.getX() * 0.6, leap, v.getZ() * 0.6));
                    world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
                    world.spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
                }
                return false;
            }
            return true;
        });
        return true;
    }

    /** Cloak: become truly invisible (armor and items too) for a few seconds. */
    private void cloak(Player player, double seconds) {
        boolean fresh = cloaked.put(player.getUniqueId(), System.currentTimeMillis() + (long) (seconds * 1000)) == null;
        if (fresh) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(player)) {
                    viewer.hidePlayer(plugin, player);
                }
            }
        }
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.7f);
        player.sendActionBar(plugin.raw("ability-cloaked", "&8☁ &7You vanished!"));
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long until = cloaked.get(id);
            if (until != null && until <= System.currentTimeMillis() + 50) {
                uncloak(id);
            }
        }, (long) Math.ceil(seconds * 20) + 1);
    }

    private void uncloak(UUID id) {
        if (cloaked.remove(id) == null) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, player);
        }
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
    }

    /** Some enchantments can only go on the weapon after enough kills (anvil). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getFirstItem();
        ItemStack result = event.getResult();
        ItemManager.Entry weapon = weaponOf(base);
        if (weapon == null || result == null || weapon.abilities.enchantUnlocks.isEmpty()) {
            return;
        }
        int kills = killsOf(base);
        for (org.bukkit.enchantments.Enchantment ench : result.getEnchantments().keySet()) {
            Integer need = weapon.abilities.enchantUnlocks.get(ench.getKey().getKey());
            if (need != null && need > kills && !base.containsEnchantment(ench)) {
                event.setResult(null);
                return;
            }
        }
    }

    /** Same rule for the enchanting table. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantTable(org.bukkit.event.enchantment.EnchantItemEvent event) {
        ItemManager.Entry weapon = weaponOf(event.getItem());
        if (weapon == null || weapon.abilities.enchantUnlocks.isEmpty()) {
            return;
        }
        int kills = killsOf(event.getItem());
        event.getEnchantsToAdd().keySet().removeIf(ench -> {
            Integer need = weapon.abilities.enchantUnlocks.get(ench.getKey().getKey());
            return need != null && need > kills;
        });
    }

    /** Hammer Throw: hurl the mace, a heavy hit that pulls the target to you, then it flies home. */
    private boolean hammerThrow(Player player, ItemManager.Entry weapon, Params p) {
        double damage = p.d("damage", 12);
        double knock = p.d("knockback", 1.5);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_AIR, 1.2f, 0.7f);
        throwWeapon(player, weapon.item.clone(), player.getEyeLocation().getDirection(), p.d("range", 24), 1.5, null, true,
                target -> {
                    hurt(player, target, damage);
                    // the hit yanks the enemy toward you
                    Vector v = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                    double distance = v.length();
                    if (distance > 1.0E-4) {
                        v.normalize().multiply(Math.min(2.8, knock * (0.4 + distance * 0.09)));
                    }
                    Vector pull = v.setY(0.45);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (target.isValid()) {
                            target.setVelocity(pull);
                        }
                    });
                    World world = target.getWorld();
                    Location at = target.getLocation().add(0, 1, 0);
                    world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 0.8f);
                    world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.DUST, at, 20, 0.4, 0.5, 0.4, 0, KNIGHT_GOLD);
                }, KNIGHT_GOLD);
        return true;
    }

    // ================================================================ RIMEBREAKER (war axe: throw, recall)

    private static final class AxeState {
        ItemManager.Entry weapon;
        Params params;
        ItemDisplay display;
        Location pos;
        LivingEntity stuck;
        long thrownAt;
        BukkitTask task;
    }

    private void freeze(Player source, LivingEntity target, Params p) {
        frozenBy.put(target.getUniqueId(), new FrozenBy(source.getUniqueId(), System.currentTimeMillis() + 6000));
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), p.i("freeze-ticks", 160)));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, p.i("slow-ticks", 50),
                Math.max(0, p.i("slow-level", 3) - 1)));
        World world = target.getWorld();
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        world.spawnParticle(Particle.SNOWFLAKE, at, 20, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticle(Particle.DUST, at, 10, 0.3, 0.5, 0.3, 0, FROST_DUST);
        world.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
    }

    /** Shows the hand item in its "thrown" look (or back to normal). */
    private void setAxeLook(Player player, ItemManager.Entry weapon, boolean thrown, int thrownModel) {
        Integer normal = weapon.item.getItemMeta().hasCustomModelData() ? weapon.item.getItemMeta().getCustomModelData() : null;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !weapon.ref().equals(items.idOf(stack))) {
                continue;
            }
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            meta.setCustomModelData(thrown && thrownModel > 0 ? Integer.valueOf(thrownModel) : normal);
            stack.setItemMeta(meta);
            player.getInventory().setItem(i, stack);
        }
    }

    private boolean throwAxe(Player player, ItemManager.Entry weapon, Params p) {
        World world = player.getWorld();
        AxeState state = new AxeState();
        state.weapon = weapon;
        state.params = p;
        state.thrownAt = System.currentTimeMillis();
        state.pos = player.getEyeLocation().subtract(0, 0.3, 0);
        state.display = spawnFlying(world, state.pos, weapon.item.clone(), 1.3f);
        axes.put(player.getUniqueId(), state);
        setAxeLook(player, weapon, true, p.i("thrown-model-data", 0));
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.2f, 0.6f);
        Vector dir = player.getEyeLocation().getDirection().normalize();
        double speed = 1.6;
        int steps = (int) Math.ceil(p.d("range", 30) / speed);
        double damage = p.d("damage", 7);
        long maxOut = (long) (p.d("max-out-seconds", 30) * 1000);
        int[] step = {0};
        boolean[] flying = {true};
        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (axes.get(player.getUniqueId()) != state || !state.display.isValid()) {
                return;
            }
            if (!player.isOnline() || System.currentTimeMillis() - state.thrownAt > maxOut) {
                recallAxe(player);
                return;
            }
            if (flying[0]) {
                RayTraceResult hit = step[0] >= steps ? null
                        : world.rayTrace(state.pos, dir, speed, FluidCollisionMode.NEVER, true, 0.6, e -> valid(player, e));
                if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                    hurt(player, target, damage);
                    freeze(player, target, p);
                    state.stuck = target;
                    flying[0] = false;
                    world.playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.2f, 0.7f);
                } else if (step[0] >= steps || (hit != null && hit.getHitBlock() != null)) {
                    if (hit != null) {
                        state.pos = hit.getHitPosition().toLocation(world).subtract(dir.clone().multiply(0.3));
                        world.playSound(state.pos, Sound.ITEM_TRIDENT_HIT_GROUND, 1.2f, 0.7f);
                        world.spawnParticle(Particle.SNOWFLAKE, state.pos, 15, 0.2, 0.2, 0.2, 0.02);
                    }
                    flying[0] = false;
                } else {
                    state.pos.add(dir.clone().multiply(speed));
                    moveFlying(state.display, state.pos, dir, step[0], true, 1.3f);
                    world.spawnParticle(Particle.SNOWFLAKE, state.pos, 1, 0.05, 0.05, 0.05, 0);
                }
                step[0]++;
                return;
            }
            // stuck in a block or riding in an enemy
            if (state.stuck != null && state.stuck.isValid() && !state.stuck.isDead()) {
                state.pos = state.stuck.getLocation().add(0, state.stuck.getHeight() * 0.6, 0);
                state.display.teleport(state.pos);
            }
            if (step[0]++ % 8 == 0) {
                world.spawnParticle(Particle.SNOWFLAKE, state.pos, 2, 0.15, 0.15, 0.15, 0.01);
            }
        }, 1L, 1L);
        return true;
    }

    /** Calls the thrown axe back. It hits and freezes everything on its way home. */
    private void recallAxe(Player player) {
        AxeState state = axes.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.task != null) {
            state.task.cancel();
        }
        if (!player.isOnline()) {
            state.display.remove();
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.2f, 0.6f);
        double damage = state.params.d("recall-damage", 6);
        if (state.display.getWorld() != player.getWorld()) {
            state.display.remove();
            setAxeLook(player, state.weapon, false, 0);
            return;
        }
        flyBack(player, state.display, state.display.getLocation(), true, target -> {
            hurt(player, target, damage);
            freeze(player, target, state.params);
        }, 1.4, () -> {
            if (player.isOnline()) {
                setAxeLook(player, state.weapon, false, 0);
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1.2, 0), 15, 0.3, 0.3, 0.3, 0.05);
            }
        });
    }

    /** Puts the axe back instantly (quit, death, shutdown). */
    private void dropAxeState(Player player) {
        AxeState state = axes.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.task != null) {
            state.task.cancel();
        }
        state.display.remove();
        setAxeLook(player, state.weapon, false, 0);
    }

    /** Frost Runic: a wave of ice rolls forward, freezing and hurting everything it passes. */
    private boolean frostWave(Player player, Params p) {
        World world = player.getWorld();
        Vector dir = player.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location origin = player.getLocation().add(0, 0.2, 0);
        int length = Math.max(2, p.i("length", 12));
        double damage = p.d("damage", 6);
        Set<UUID> hitOnce = new java.util.HashSet<>();
        world.playSound(origin, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 0.6f);
        world.playSound(origin, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
        Vector d = dir;
        repeat(1, length, step -> {
            double width = 1 + step * 2.0 / length;
            Location center = origin.clone().add(d.clone().multiply(step + 1));
            for (double w = -width; w <= width; w += 0.5) {
                Location l = center.clone().add(side.clone().multiply(w));
                world.spawnParticle(Particle.SNOWFLAKE, l, 1, 0.1, 0.2, 0.1, 0.01);
                world.spawnParticle(Particle.DUST, l.clone().add(0, 0.3, 0), 1, 0.05, 0.2, 0.05, 0, FROST_DUST);
                Block below = l.getBlock().getRelative(0, -1, 0);
                if (below.getType() == Material.WATER && l.getBlock().getType().isAir()
                        && below.getBlockData() instanceof org.bukkit.block.data.Levelled lv && lv.getLevel() == 0) {
                    below.setType(Material.FROSTED_ICE);
                }
            }
            if (step % 3 == 0) {
                world.playSound(center, Sound.BLOCK_POWDER_SNOW_BREAK, 1f, 0.8f);
            }
            for (LivingEntity target : targets(player, center, width + 0.8)) {
                if (hitOnce.add(target.getUniqueId())) {
                    hurt(player, target, damage);
                    freeze(player, target, p);
                }
            }
            return true;
        });
        return true;
    }

    // ================================================================ PINNING REPEATER (crossbow)

    private boolean pinBolt(Player player, Params p) {
        pinArmed.put(player.getUniqueId(), System.currentTimeMillis() + (long) (p.d("window", 10) * 1000));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.8f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.2, 0), 15, 0.3, 0.4, 0.3, 0, CHAIN_DUST);
        return true;
    }

    /** Pins an entity in place: no walking, no jumping, a chain to where the bolt landed. */
    private void pin(LivingEntity target, Location anchor, double seconds) {
        long until = System.currentTimeMillis() + (long) (seconds * 1000);
        boolean fresh = pinned.put(target.getUniqueId(), until) == null;
        if (fresh) {
            for (Attribute attribute : new Attribute[] {Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
                AttributeInstance inst = target.getAttribute(attribute);
                if (inst != null) {
                    inst.removeModifier(pinKey);
                    inst.addTransientModifier(new org.bukkit.attribute.AttributeModifier(pinKey, -1,
                            org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                }
            }
        }
        World world = target.getWorld();
        world.playSound(target.getLocation(), Sound.BLOCK_CHAIN_HIT, 1.5f, 0.6f);
        if (target instanceof Player victim) {
            victim.sendActionBar(plugin.raw("ability-pinned", "&7You've been &fpinned&7!"));
        }
        Object chain = "pin:" + target.getUniqueId();
        repeat(2, (int) Math.ceil(seconds * 10) + 1, step -> {
            Long end = pinned.get(target.getUniqueId());
            if (end == null || end < System.currentTimeMillis() || !target.isValid() || target.isDead()) {
                unpin(target);
                ChainVisual.remove(chain);
                return false;
            }
            target.setVelocity(new Vector(0, Math.min(0, target.getVelocity().getY()), 0));
            if (anchor.getWorld() == target.getWorld()) {
                ChainVisual.draw(plugin, chain, anchor, target.getLocation().add(0, target.getHeight() / 2, 0));
            }
            return true;
        });
    }

    private void unpin(LivingEntity target) {
        pinned.remove(target.getUniqueId());
        for (Attribute attribute : new Attribute[] {Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = target.getAttribute(attribute);
            if (inst != null) {
                inst.removeModifier(pinKey);
            }
        }
    }

    /** Volley: five bolts at once in a fan, no reload needed. */
    private boolean volley(Player player, ItemManager.Entry weapon, Params p) {
        int count = Math.max(1, p.i("arrows", 5));
        double spread = p.d("spread-degrees", 24);
        double damage = p.d("arrow-damage", 4);
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        for (int i = 0; i < count; i++) {
            double offset = count == 1 ? 0 : -spread / 2 + spread * i / (count - 1);
            Location aim = eye.clone();
            aim.setYaw(eye.getYaw() + (float) offset);
            Vector v = aim.getDirection().multiply(p.d("speed", 3.0));
            org.bukkit.entity.Arrow arrow = world.spawnArrow(eye.clone().add(aim.getDirection().multiply(0.5)), v, (float) v.length(), 0f);
            arrow.setShooter(player);
            arrow.setDamage(damage);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setCritical(true);
            arrow.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, weapon.ref());
        }
        world.playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 1.4f, 0.8f);
        world.playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 1.4f, 1.2f);
        return true;
    }

    // ================================================================ THE LAST ACT (rifle)

    private static final Particle.DustOptions ACT_GOLD = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 205, 90), 1.1f);
    private static final Particle.DustOptions ACT_RED = new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 30, 50), 1.5f);

    private static final class Finale {
        final Params p;
        final Vector center;
        final int shots;
        final long until;
        int fired;
        long nextShot;

        Finale(Params p, Vector center, int shots, long until) {
            this.p = p;
            this.center = center;
            this.shots = shots;
            this.until = until;
        }
    }

    /** Spotlight: a long golden shot at the first enemy in line; someone you hit in the last seconds is rooted. */
    private boolean spotlight(Player player, Params p) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        double range = p.d("range", 45);
        RayTraceResult ray = world.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.6, e -> valid(player, e));
        Location end = ray != null ? ray.getHitPosition().toLocation(world) : eye.clone().add(dir.clone().multiply(range));
        line(eye.clone().add(dir.clone().multiply(0.8)), end, 0.5, l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, ACT_GOLD));
        world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.4f);
        world.playSound(eye, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
        if (ray != null && ray.getHitEntity() instanceof LivingEntity target) {
            Map<UUID, Dealt> mine = dealt.get(player.getUniqueId());
            Dealt recent = mine == null ? null : mine.get(target.getUniqueId());
            boolean marked = recent != null && recent.last() >= System.currentTimeMillis() - (long) (p.d("mark-seconds", 4) * 1000);
            hurt(player, target, p.d("damage", 5));
            world.spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() / 2, 0), 20, 0.3, 0.4, 0.3, 0, ACT_GOLD);
            if (marked && target.isValid() && !target.isDead()) {
                pin(target, target.getLocation(), p.d("root-seconds", 1.5));
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (p.d("root-seconds", 1.5) * 20) + 20, 0, false, false));
                world.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f, 0.8f);
            }
        }
        return true;
    }

    /** Finale: rooted in place, 4 shots inside a cone, more damage the lower the target's health, the 4th is critical. */
    private boolean finale(Player player, ItemManager.Entry weapon, Params p) {
        Vector center = player.getEyeLocation().getDirection().setY(0);
        if (center.lengthSquared() < 1.0E-6) {
            center = new Vector(-Math.sin(Math.toRadians(player.getYaw())), 0, Math.cos(Math.toRadians(player.getYaw())));
        }
        center.normalize();
        long now = System.currentTimeMillis();
        Finale f = new Finale(p, center, Math.max(1, p.i("shots", 4)), now + (long) (p.d("duration", 10) * 1000));
        f.nextShot = now + 400;                                // a short moment to aim
        finales.put(player.getUniqueId(), f);
        root(player, true);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 0.5f);
        world.playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1.4f);
        player.showTitle(net.kyori.adventure.title.Title.title(net.kyori.adventure.text.Component.empty(),
                ItemManager.color("&6&lFinale"), net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ZERO, java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(300))));
        String ref = weapon.ref();
        repeat(1, (int) (p.d("duration", 10) * 20) + 2, step -> {
            Finale cur = finales.get(player.getUniqueId());
            ItemManager.Entry held = weaponOf(player.getInventory().getItemInMainHand());
            if (cur != f || !player.isOnline() || player.isDead() || held == null || !held.ref().equals(ref)
                    || System.currentTimeMillis() > f.until || f.fired >= f.shots) {
                if (cur == f) {
                    endFinale(player);
                }
                return false;
            }
            player.setVelocity(new Vector(0, Math.min(0, player.getVelocity().getY()), 0));
            if (step % 5 == 0) {
                drawCone(player, f);
            }
            return true;
        });
        return true;
    }

    /** The cone everyone can see: two edges and an arc at its far end. */
    private void drawCone(Player player, Finale f) {
        World world = player.getWorld();
        Location base = player.getLocation().add(0, 0.15, 0);
        double half = Math.toRadians(f.p.d("cone-degrees", 60) / 2);
        double len = Math.min(24, f.p.d("range", 60));
        for (double a : new double[] {-half, half}) {
            Vector edge = rotate(f.center, a);
            for (double d = 1.5; d <= len; d += 1.0) {
                world.spawnParticle(Particle.DUST, base.clone().add(edge.clone().multiply(d)), 1, 0, 0, 0, 0, ACT_GOLD);
            }
        }
        for (double a = -half; a <= half + 1.0E-6; a += Math.toRadians(4)) {
            world.spawnParticle(Particle.DUST, base.clone().add(rotate(f.center, a).multiply(len)), 1, 0, 0, 0, 0, ACT_GOLD);
        }
    }

    /** Rotates a flat (y = 0) vector around the vertical axis. */
    static Vector rotate(Vector flat, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector(flat.getX() * cos - flat.getZ() * sin, 0, flat.getX() * sin + flat.getZ() * cos);
    }

    /** Signed horizontal angle from a to b (radians). */
    static double angle(Vector a, Vector b) {
        return Math.atan2(a.getX() * b.getZ() - a.getZ() * b.getX(), a.getX() * b.getX() + a.getZ() * b.getZ());
    }

    private void finaleShot(Player player) {
        Finale f = finales.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (f == null || now < f.nextShot || f.fired >= f.shots) {
            return;
        }
        f.nextShot = now + (long) (f.p.d("shot-delay", 0.6) * 1000);
        f.fired++;
        boolean last = f.fired >= f.shots;
        // aim, kept inside the cone
        Vector look = player.getEyeLocation().getDirection();
        Vector flat = look.clone().setY(0);
        if (flat.lengthSquared() < 1.0E-6) {
            flat = f.center.clone();
        }
        flat.normalize();
        double half = Math.toRadians(f.p.d("cone-degrees", 60) / 2);
        double a = Math.max(-half, Math.min(half, angle(f.center, flat)));
        flat = rotate(f.center, a);
        double y = Math.max(-0.9, Math.min(0.9, look.getY()));
        double h = Math.sqrt(1 - y * y);
        Vector dir = new Vector(flat.getX() * h, y, flat.getZ() * h).normalize();

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        float pitch = new float[] {1.0f, 1.2f, 1.4f, 1.8f}[Math.min(3, f.fired - 1)];
        world.playSound(eye, Sound.BLOCK_NOTE_BLOCK_BELL, 1.6f, pitch);
        world.playSound(eye, last ? Sound.ITEM_TRIDENT_THUNDER : Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, last ? 0.8f : 1.4f, last ? 1.4f : 0.9f);
        double range = f.p.d("range", 60);
        double damage = f.p.d("damage", 6);
        double bonus = f.p.d("missing-health-bonus", 2.0);
        double lastMult = last ? f.p.d("last-shot-multiplier", 2.0) : 1.0;
        int slowTicks = (int) (f.p.d("slow-seconds", 1.5) * 20);
        Location pos = eye.clone().add(dir.clone().multiply(0.6));
        double[] travelled = {0};
        repeat(1, (int) Math.ceil(range / 4) + 2, step -> {
            for (int i = 0; i < 4; i++) {
                pos.add(dir);
                travelled[0] += 1;
                if (travelled[0] > range || !pos.isChunkLoaded()) {
                    return false;
                }
                world.spawnParticle(Particle.DUST, pos, last ? 3 : 1, 0.03, 0.03, 0.03, 0, last ? ACT_RED : ACT_GOLD);
                if (!pos.getBlock().isPassable()) {
                    world.spawnParticle(Particle.CRIT, pos, 8, 0.1, 0.1, 0.1, 0.2);
                    return false;
                }
                for (Entity e : world.getNearbyEntities(pos, 0.7, 0.9, 0.7, e -> valid(player, e))) {
                    LivingEntity target = (LivingEntity) e;
                    double missing = 1 - Math.max(0, Math.min(1, target.getHealth() / maxHealth(target)));
                    hurt(player, target, damage * (1 + bonus * missing) * lastMult);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 2, false, true));
                    Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
                    world.spawnParticle(Particle.DUST, at, last ? 40 : 15, 0.3, 0.4, 0.3, 0, last ? ACT_RED : ACT_GOLD);
                    if (last) {
                        world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
                        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
                    }
                    return false;                              // one target per shot
                }
            }
            return true;
        });
        if (last) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> endFinale(player), 10L);
        }
    }

    /** Rooted while performing (and free again afterwards). */
    private void root(Player player, boolean on) {
        for (Attribute attribute : new Attribute[] {Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = player.getAttribute(attribute);
            if (inst != null) {
                inst.removeModifier(finaleKey);
                if (on) {
                    inst.addTransientModifier(new org.bukkit.attribute.AttributeModifier(finaleKey, -1,
                            org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                }
            }
        }
    }

    private void endFinale(Player player) {
        if (finales.remove(player.getUniqueId()) != null || player.isOnline()) {
            root(player, false);
        }
    }

    // ================================================================ VALKYRIE'S LANCE (spear)

    /** Lunge: dash forward, piercing everyone in the way. */
    private boolean lunge(Player player, Params p) {
        Vector dir = player.getEyeLocation().getDirection();
        dir.setY(Math.max(-0.2, Math.min(0.35, dir.getY())));
        dir.normalize().multiply(p.d("power", 1.8));
        player.setVelocity(dir);
        noFall.put(player.getUniqueId(), System.currentTimeMillis() + 2500);
        double damage = p.d("damage", 7);
        Set<UUID> hitOnce = new java.util.HashSet<>();
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 1.2f);
        repeat(1, p.i("ticks", 9), step -> {
            if (!player.isOnline() || player.isDead()) {
                return false;
            }
            Location at = player.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.DUST, at, 3, 0.3, 0.3, 0.3, 0, VALK_DUST);
            world.spawnParticle(Particle.CLOUD, at, 1, 0.1, 0.1, 0.1, 0.01);
            for (LivingEntity target : targets(player, at, 1.8)) {
                if (hitOnce.add(target.getUniqueId())) {
                    hurt(player, target, damage);
                    target.setVelocity(target.getVelocity().add(player.getVelocity().clone().multiply(0.5)).setY(0.3));
                    world.playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.2f, 1.3f);
                    world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.3);
                }
            }
            return true;
        });
        return true;
    }

    /** Javelin: throw the lance; it homes in on the target you look at, then flies back. */
    private boolean javelin(Player player, ItemManager.Entry weapon, Params p) {
        LivingEntity target = lookTarget(player, p.d("lock-range", 32));
        double damage = p.d("damage", 10);
        int glow = p.i("glow-ticks", 60);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.2f, 1.1f);
        throwWeapon(player, weapon.item.clone(), player.getEyeLocation().getDirection(), p.d("range", 40), 1.7, target, false,
                hit -> {
                    hurt(player, hit, damage);
                    hit.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glow, 0, false, false, false));
                    World world = hit.getWorld();
                    Location at = hit.getLocation().add(0, hit.getHeight() / 2, 0);
                    world.playSound(at, Sound.ITEM_TRIDENT_HIT, 1.4f, 0.9f);
                    world.spawnParticle(Particle.END_ROD, at, 12, 0.3, 0.4, 0.3, 0.08);
                    world.spawnParticle(Particle.DUST, at, 15, 0.3, 0.4, 0.3, 0, VALK_DUST);
                }, VALK_DUST);
        return true;
    }

    // ================================================================ TRIDENT OF THE GREAT SEA

    private boolean seaKing(Player player) {
        Long until = seaKing.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** Tidal Surge: a riptide launch that works anywhere - no water or rain needed. */
    private boolean tidalSurge(Player player, Params p) {
        if (!player.isInWaterOrRain()) {
            plugin.award(player, "riptide_anywhere");
        }
        Vector dir = player.getEyeLocation().getDirection().normalize().multiply(p.d("power", 2.2));
        if (player.isOnGround()) {
            player.teleport(player.getLocation().add(0, 1.1, 0));    // like vanilla riptide on the ground
        }
        player.setVelocity(dir);
        player.startRiptideAttack(p.i("spin-ticks", 20), (float) p.d("spin-damage", 8), player.getInventory().getItemInMainHand());
        noFall.put(player.getUniqueId(), System.currentTimeMillis() + 3500);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 1f);
        repeat(1, 14, step -> {
            if (!player.isOnline()) {
                return false;
            }
            Location at = player.getLocation().add(0, 0.8, 0);
            world.spawnParticle(Particle.SPLASH, at, 12, 0.4, 0.4, 0.4, 0.2);
            world.spawnParticle(Particle.BUBBLE_POP, at, 4, 0.3, 0.3, 0.3, 0.05);
            world.spawnParticle(Particle.DUST, at, 2, 0.3, 0.3, 0.3, 0, SEA_DUST);
            return true;
        });
        return true;
    }

    /** The Great Sea: a tidal wave rolls out from you, and for a few seconds you rule the sea. */
    private boolean greatTide(Player player, Params p) {
        double radius = p.d("radius", 8);
        double damage = p.d("damage", 6);
        int seconds = p.i("king-seconds", 8);
        World world = player.getWorld();
        Location center = player.getLocation();
        Set<UUID> hitOnce = new java.util.HashSet<>();
        world.playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.4f);
        world.playSound(center, Sound.AMBIENT_UNDERWATER_ENTER, 1.5f, 0.7f);
        repeat(1, 10, step -> {
            double r = radius * (step + 1) / 10.0;
            ring(center.clone().add(0, 0.3, 0), r, Math.max(16, (int) (r * 8)), l -> {
                world.spawnParticle(Particle.SPLASH, l, 3, 0.1, 0.3, 0.1, 0.1);
                world.spawnParticle(Particle.DUST, l.clone().add(0, 0.6, 0), 1, 0.05, 0.3, 0.05, 0, SEA_DUST);
            });
            for (LivingEntity target : targets(player, center, r + 0.5)) {
                if (hitOnce.add(target.getUniqueId())) {
                    hurt(player, target, damage);
                    Vector out = target.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if (out.lengthSquared() > 1.0E-4) {
                        out.normalize().multiply(p.d("push", 1.3));
                    }
                    target.setVelocity(out.setY(0.5));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                }
            }
            return true;
        });
        seaKing.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, seconds * 20, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, seconds * 20, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1, false, true, true));
        player.sendActionBar(plugin.raw("ability-sea-king", "&3The law doesn't cover the Great Sea. &bSurge has no cooldown!"));
        repeat(5, seconds * 4, step -> {
            if (!player.isOnline() || !seaKing(player)) {
                return false;
            }
            Location l = player.getLocation();
            ring(l.clone().add(0, 2.3, 0), 0.4, 8, x -> world.spawnParticle(Particle.DUST, x, 1, 0, 0, 0, 0, SEA_DUST));
            world.spawnParticle(Particle.DRIPPING_WATER, l.clone().add(0, 2, 0), 3, 0.3, 0.1, 0.3, 0);
            return true;
        });
        return true;
    }

    /** Tags thrown tridents with their weapon, so on-hit effects work on throws too. */
    @EventHandler(ignoreCancelled = true)
    public void onTridentThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Trident trident)
                || !(trident.getShooter() instanceof Player player)) {
            return;
        }
        ItemManager.Entry weapon = weaponOf(trident.getItemStack());
        if (weapon != null) {
            trident.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, weapon.ref());
        }
    }

    // ================================================================ bows

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !enabledIn(player.getWorld())) {
            return;
        }
        ItemManager.Entry weapon = weaponOf(event.getBow());
        if (weapon == null || weapon.abilities.isEmpty()) {
            return;
        }
        Entity projectile = event.getProjectile();
        projectile.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, weapon.ref());
        Active finale = weapon.abilities.actives.stream().filter(a -> a.type() == ActiveType.FINALE).findFirst().orElse(null);
        if (finale != null && projectile instanceof AbstractArrow arrow) {
            // The Last Act: every 4th bolt is critical
            int n = actShots.merge(player.getUniqueId(), 1, Integer::sum);
            if (n >= 4) {
                actShots.put(player.getUniqueId(), 0);
                arrow.setDamage(arrow.getDamage() * finale.params().d("fourth-shot-multiplier", 2.0));
                arrow.setCritical(true);
                World w = player.getWorld();
                w.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 2.0f);
                w.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.7f);
                w.spawnParticle(Particle.DUST, player.getEyeLocation().add(player.getEyeLocation().getDirection()),
                        12, 0.15, 0.15, 0.15, 0, ACT_RED);
            }
        }
        if (pinArmed.remove(player.getUniqueId()) != null) {
            projectile.getPersistentDataContainer().set(pinShotKey, PersistentDataType.BYTE, (byte) 1);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.8f);
        }
        boolean mirror = mirrorArmed.remove(player.getUniqueId()) != null;
        if (mirror) {
            projectile.getPersistentDataContainer().set(mirrorKey, PersistentDataType.BYTE, (byte) 1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f);
        }
        Particle trail = weapon.abilities.arrowTrail;
        if (trail != null || mirror) {
            repeat(1, 100, step -> {
                if (!projectile.isValid() || (projectile instanceof AbstractArrow arrow && arrow.isInBlock())) {
                    return false;
                }
                World world = projectile.getWorld();
                if (trail != null) {
                    world.spawnParticle(trail, projectile.getLocation(), 2, 0.05, 0.05, 0.05, 0);
                }
                if (mirror) {
                    world.spawnParticle(Particle.END_ROD, projectile.getLocation(), 1, 0, 0, 0, 0);
                }
                return true;
            });
        }
    }

    // ================================================================ damage

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        charge.put(event.getPlayer().getUniqueId(), event.getPlayer().getAttackCooldown());
    }

    /** Hex Mark amplifies all damage the marked entity takes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHexedDamage(EntityDamageEvent event) {
        Hex hex = hexed.get(event.getEntity().getUniqueId());
        if (hex == null) {
            return;
        }
        if (hex.expires() < System.currentTimeMillis()) {
            hexed.remove(event.getEntity().getUniqueId());
            return;
        }
        event.setDamage(event.getDamage() * hex.multiplier());
        Entity entity = event.getEntity();
        entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, entity.getHeight() / 2, 0), 8, 0.3, 0.4, 0.3, 0.05);
    }

    /** Soul Link sends part of the damage the player takes to the linked target. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLinkedDamage(EntityDamageEvent event) {
        if (DamageGuard.active() || !(event.getEntity() instanceof Player player)
                || UNLINKABLE_CAUSES.contains(event.getCause().name())) {
            return;
        }
        Link link = links.get(player.getUniqueId());
        if (link == null) {
            return;
        }
        if (!(Bukkit.getEntity(link.target()) instanceof LivingEntity target) || !linkHolds(player, link, target)) {
            links.remove(player.getUniqueId());
            return;
        }
        double redirected = event.getDamage() * link.percent();
        event.setDamage(event.getDamage() - redirected);
        hurt(player, target, redirected);
        target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.03);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (DamageGuard.active() || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = null;
        ItemManager.Entry weapon = null;
        boolean melee = false;
        if (event.getDamager() instanceof Player player
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            attacker = player;
            weapon = weaponOf(player.getInventory().getItemInMainHand());
            melee = true;
            AxeState out = axes.get(player.getUniqueId());
            if (weapon != null && out != null && out.weapon.ref().equals(weapon.ref())) {
                event.setDamage(1);          // the axe is out: you're fighting with your fists
                return;
            }
            if (weapon != null && isRanged(weapon)) {
                weapon = null;
            }
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            String ref = projectile.getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING);
            if (ref != null) {
                attacker = shooter;
                weapon = items.get(ref);
            }
            if (projectile.getPersistentDataContainer().has(mirrorKey, PersistentDataType.BYTE)
                    && !shooter.equals(victim) && valid(shooter, victim)) {
                swapPlaces(shooter, victim);
            }
            if (projectile.getPersistentDataContainer().has(pinShotKey, PersistentDataType.BYTE)
                    && !shooter.equals(victim) && valid(shooter, victim) && weapon_pinSeconds(ref) > 0) {
                pin(victim, projectile.getLocation(), weapon_pinSeconds(ref));
            }
        }
        if (attacker == null || attacker.equals(victim) || weapon == null
                || weapon.abilities.onHit.isEmpty() || !enabledIn(attacker.getWorld())) {
            return;
        }
        if (victim instanceof Player mate && plugin.teams() != null && plugin.teams().allies(attacker, mate)) {
            return;                                              // no on-hit effects / stacks on teammates
        }
        // Spam-clicking without a charged attack doesn't trigger on-hit effects.
        Float strength = charge.remove(attacker.getUniqueId());
        if (melee && strength != null && strength < 0.85f) {
            return;
        }
        int weaponKills = melee ? killsOf(attacker.getInventory().getItemInMainHand()) : Integer.MAX_VALUE;
        for (OnHit hit : weapon.abilities.onHit) {
            if (hit.params().i("unlock-kills", 0) > weaponKills) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble(100) < hit.chance()) {
                applyOnHit(attacker, victim, hit, event);
            }
        }
    }

    /** Remembers damage dealt by players (for Resonance). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s) {
            attacker = s;
        }
        if (attacker == null || !(event.getEntity() instanceof LivingEntity) || attacker.equals(event.getEntity())) {
            return;
        }
        long now = System.currentTimeMillis();
        dealt.computeIfAbsent(attacker.getUniqueId(), k -> new HashMap<>())
                .merge(event.getEntity().getUniqueId(), new Dealt(event.getFinalDamage(), now),
                        (old, add) -> old.last() < now - DAMAGE_MEMORY_MS ? add
                                : new Dealt(old.amount() + add.amount(), now));
    }

    private void applyOnHit(Player attacker, LivingEntity victim, OnHit hit, EntityDamageByEntityEvent event) {
        Params p = hit.params();
        World world = victim.getWorld();
        Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
        switch (hit.type()) {
            case ECHO -> {
                double amount = event.getDamage() * p.d("percent", 40) / 100.0;
                long delay = Math.max(1, p.i("delay-ticks", 30));
                world.spawnParticle(Particle.DUST, at, 6, 0.3, 0.4, 0.3, 0, ECHO_DUST);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!victim.isValid() || victim.isDead() || !attacker.isOnline()) {
                        return;
                    }
                    Location now = victim.getLocation().add(0, victim.getHeight() / 2, 0);
                    victim.getWorld().spawnParticle(Particle.SCULK_SOUL, now, 10, 0.3, 0.4, 0.3, 0.03);
                    victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, now, 1, 0, 0, 0, 0);
                    victim.getWorld().playSound(now, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.2f, 1.4f);
                    hurt(attacker, victim, amount);
                }, delay);
            }
            case CRUSH -> {
                // 1) standing above the target
                double height = attacker.getLocation().getY() - victim.getLocation().getY();
                double bonus = height >= 1 ? Math.min(p.d("max-bonus", 8), height * p.d("damage-per-block", 1.5)) : 0;
                // 2) falling onto it: the fall so far, or a landing that happened a moment ago
                double fall = attacker.getFallDistance();
                Landing landing = landed.get(attacker.getUniqueId());
                if (landing != null && System.currentTimeMillis() - landing.time() <= p.d("landing-window-ms", 700)) {
                    fall = Math.max(fall, landing.distance());
                }
                boolean slammed = false;
                if (fall >= 1.5) {
                    double fallBonus = Math.min(p.d("max-fall-bonus", 20), fall * p.d("fall-damage-per-block", 1.0));
                    if (fallBonus > bonus) {
                        bonus = fallBonus;
                        slammed = true;
                    }
                }
                if (bonus > 0) {
                    event.setDamage(event.getDamage() + scaled(victim, bonus));
                    world.spawnParticle(Particle.DUST, at, 20, 0.3, 0.5, 0.3, 0, GRAVITY_CORE);
                    world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
                }
                if (slammed) {
                    // like a mace: the slam eats the fall, so a mid-air hit means no fall damage
                    attacker.setFallDistance(0);
                    landed.remove(attacker.getUniqueId());
                    Location ground = victim.getLocation();
                    world.playSound(ground, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.3f, 0.6f);
                    ring(ground.clone().add(0, 0.2, 0), 2.5, 28,
                            l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0.05, 0, 0, GRAVITY_DUST));
                    world.spawnParticle(Particle.EXPLOSION, ground, 1, 0, 0, 0, 0);
                    if (attacker instanceof Player hammer) {
                        hammer.sendActionBar(ItemManager.color("&5⬇ Slam &d+" + Math.round(bonus) + " &7damage"));
                        if (event.getDamage() >= 20) {
                            plugin.award(hammer, "hammer_time");
                        }
                    }
                }
                if (!victim.isOnGround()) {
                    double slam = p.d("slam", 1.6);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (victim.isValid()) {
                            victim.setVelocity(victim.getVelocity().setY(-slam));
                        }
                    });
                }
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, p.i("slow-ticks", 30), 0));
            }
            case RICOCHET -> {
                LivingEntity next = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity other : targets(attacker, victim.getLocation(), p.d("range", 8))) {
                    double d = other.getLocation().distanceSquared(victim.getLocation());
                    if (!other.equals(victim) && d < best) {
                        best = d;
                        next = other;
                    }
                }
                if (next != null) {
                    LivingEntity bounce = next;
                    line(at, bounce.getLocation().add(0, bounce.getHeight() / 2, 0), 0.35,
                            l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, HEX_DUST));
                    world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.5f);
                    hurt(attacker, bounce, event.getDamage() * p.d("percent", 60) / 100.0);
                }
            }
            case SOUL -> {
                int max = Math.max(1, p.i("max", 5));
                souls.put(attacker.getUniqueId(), Math.min(max, souls.getOrDefault(attacker.getUniqueId(), 0) + 1));
                world.spawnParticle(Particle.SOUL, at, 4, 0.2, 0.3, 0.2, 0.02);
            }
            case CLEAVE -> {
                boolean wrath = frenzy.getOrDefault(attacker.getUniqueId(), 0L) > System.currentTimeMillis();
                double splash = event.getDamage() * p.d("percent", 40) / 100.0 * (wrath ? p.d("wrath-multiplier", 2) : 1);
                double radius = p.d("radius", 3) + (wrath ? 1.5 : 0);
                double healed = 0;
                for (LivingEntity near : targets(attacker, victim.getLocation(), radius)) {
                    if (near.equals(victim)) {
                        continue;
                    }
                    hurt(attacker, near, splash);
                    healed += splash * p.d("lifesteal", 10) / 100.0;
                    near.getWorld().spawnParticle(Particle.DUST, near.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0, HYDRA_DUST);
                }
                ring(victim.getLocation().add(0, 0.9, 0), radius, 18,
                        l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, HYDRA_DUST));
                world.spawnParticle(Particle.SWEEP_ATTACK, at, wrath ? 3 : 1, 0.6, 0.1, 0.6, 0);
                if (healed > 0) {
                    attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + heal(attacker, healed)));
                }
            }
            case BLUNT_FORCE -> {
                // more damage the more health YOU are missing
                double missing = Math.max(0, maxHealth(attacker) - attacker.getHealth());
                double bonus = Math.min(p.d("max-bonus", 6), missing / 2 * p.d("damage-per-missing-heart", 0.6));
                if (bonus > 0.05) {
                    event.setDamage(event.getDamage() + scaled(victim, bonus));
                    world.spawnParticle(Particle.DUST, at, 12, 0.3, 0.4, 0.3, 0, BLOOD_DUST);
                    world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.6f);
                }
            }
            case TASTE -> addTaste(victim, 1, Math.max(1, p.i("max", 3)), p.d("duration", 6));
            case CLOAK -> cloak(attacker, p.d("seconds", 3));
            case TIDE -> {
                if (victim.isInWaterOrRain() || attacker.isInWaterOrRain() || seaKing(attacker)) {
                    event.setDamage(event.getDamage() * (1 + p.d("bonus-percent", 40) / 100.0));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, p.i("slow-ticks", 40), 1));
                    world.spawnParticle(Particle.SPLASH, at, 25, 0.3, 0.5, 0.3, 0.2);
                    world.spawnParticle(Particle.DUST, at, 8, 0.3, 0.4, 0.3, 0, SEA_DUST);
                    world.playSound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.8f, 1.2f);
                }
            }
            case SILK_BIND -> {
                int every = Math.max(1, p.i("every", 3));
                int count = silkHits.merge(attacker.getUniqueId(), 1, Integer::sum);
                if (count >= every) {
                    silkHits.remove(attacker.getUniqueId());
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, p.i("bind-ticks", 25),
                            Math.max(0, p.i("slow-level", 4) - 1)));
                    for (int k = 0; k < 3; k++) {
                        ring(victim.getLocation().add(0, 0.4 + k * 0.6, 0), 0.6, 12,
                                l -> world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, SILK_DUST));
                    }
                    world.playSound(at, Sound.BLOCK_WOOL_PLACE, 1.4f, 0.6f);
                }
            }
            case LIFESTEAL -> {
                double heal = event.getFinalDamage() * p.d("percent", 20) / 100.0;
                attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + heal(attacker, heal)));
                world.spawnParticle(Particle.DUST, at, 10, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 20), 1.1f));
            }
        }
    }

    /** Mirror Shot: swap shooter and victim (next tick, outside the damage event). */
    private void swapPlaces(Player shooter, LivingEntity victim) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!shooter.isOnline() || !victim.isValid() || victim.isDead() || shooter.getWorld() != victim.getWorld()) {
                return;
            }
            Location a = shooter.getLocation();
            Location b = victim.getLocation();
            Location shooterTo = b.clone();
            shooterTo.setYaw(a.getYaw());
            shooterTo.setPitch(a.getPitch());
            Location victimTo = a.clone();
            victimTo.setYaw(b.getYaw());
            victimTo.setPitch(b.getPitch());
            World world = shooter.getWorld();
            line(a.clone().add(0, 1, 0), b.clone().add(0, 1, 0), 0.5,
                    l -> world.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0));
            shooter.teleport(shooterTo);
            victim.teleport(victimTo);
            shooter.setFallDistance(0);
            world.spawnParticle(Particle.WITCH, shooterTo.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
            world.spawnParticle(Particle.WITCH, victimTo.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
            world.playSound(shooterTo, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
            world.playSound(victimTo, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
        });
    }

    /** Killing with a soul weapon grants extra souls. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        releaseFor(event.getEntity().getUniqueId());
        taste.remove(event.getEntity().getUniqueId());
        unpin(event.getEntity());
        if (event.getEntity() instanceof Player dead && axes.containsKey(dead.getUniqueId())) {
            AxeState state = axes.get(dead.getUniqueId());
            Integer normal = state.weapon.item.getItemMeta().hasCustomModelData()
                    ? state.weapon.item.getItemMeta().getCustomModelData() : null;
            for (ItemStack drop : event.getDrops()) {
                if (state.weapon.ref().equals(items.idOf(drop))) {
                    org.bukkit.inventory.meta.ItemMeta meta = drop.getItemMeta();
                    meta.setCustomModelData(normal);
                    drop.setItemMeta(meta);
                }
            }
            dropAxeState(dead);
        }
        Player killer = event.getEntity().getKiller();
        FrozenBy frost = frozenBy.remove(event.getEntity().getUniqueId());
        if (killer != null && frost != null && frost.player().equals(killer.getUniqueId())
                && frost.until() > System.currentTimeMillis()) {
            plugin.award(killer, "ice_cold");
        }
        if (killer != null) {
            ItemManager.Entry killWeapon = weaponOf(killer.getInventory().getItemInMainHand());
            if (killWeapon != null && killWeapon.abilities.trackKills && !killer.equals(event.getEntity())
                    && (event.getEntity() instanceof Player || killWeapon.abilities.countMobs)) {
                addKill(killer, killWeapon);
            }
        }
        if (killer == null) {
            return;
        }
        ItemManager.Entry weapon = weaponOf(killer.getInventory().getItemInMainHand());
        OnHit soul = weapon == null ? null : weapon.abilities.onHit(HitType.SOUL);
        if (soul == null) {
            return;
        }
        int max = Math.max(1, soul.params().i("max", 5));
        souls.merge(killer.getUniqueId(), soul.params().i("on-kill", 2), (a, b) -> Math.min(max, a + b));
        killer.getWorld().spawnParticle(Particle.SOUL, event.getEntity().getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        releaseFor(id);
        taste.remove(id);
        bloodrush.remove(id);
        noFall.remove(id);
        silkHits.remove(id);
        landed.remove(id);
        dropAxeState(event.getPlayer());
        uncloak(id);
        unpin(event.getPlayer());
        endFinale(event.getPlayer());
        actShots.remove(id);
        pinArmed.remove(id);
        seaKing.remove(id);
        chargeUse.remove(id);
        charge.remove(id);
        lastTrigger.remove(id);
        history.remove(id);
        mirrorArmed.remove(id);
        frenzy.remove(id);
        links.remove(id);
        dealt.remove(id);
        Map<String, Long> map = cooldowns.get(id);
        if (map != null) {
            long now = System.currentTimeMillis();
            map.values().removeIf(t -> t < now);
            if (map.isEmpty()) {
                cooldowns.remove(id);
            }
        }
    }

    // ================================================================ helpers

    private ItemManager.Entry weaponOf(ItemStack stack) {
        String ref = items.idOf(stack);
        if (ref == null || !ref.startsWith("weapon:")) {
            return null;
        }
        return items.get(ref);
    }

    /** Pin length (seconds) of the weapon's PIN_BOLT ability, 0 if it has none. */
    private double weapon_pinSeconds(String ref) {
        ItemManager.Entry weapon = ref == null ? null : items.get(ref);
        if (weapon == null) {
            return 0;
        }
        for (Active active : weapon.abilities.actives) {
            if (active.type() == ActiveType.PIN_BOLT) {
                return active.params().d("pin-seconds", 2);
            }
        }
        return 0;
    }

    private static boolean isRanged(ItemManager.Entry weapon) {
        Material type = weapon.item.getType();
        return type == Material.BOW || type == Material.CROSSBOW;
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    /** Is the entity a valid ability target for this player? */
    private boolean valid(Player source, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.equals(source) || living.isDead() || !living.isValid()) {
            return false;
        }
        if (entity instanceof ArmorStand || living.isInvulnerable()) {
            return false;
        }
        if (entity instanceof Player target) {
            GameMode mode = target.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                return false;
            }
            if (!plugin.getConfig().getBoolean("ability-settings.damage-players", true)) {
                return false;
            }
            if (plugin.teams() != null && plugin.teams().allies(source, target) && !plugin.teams().friendlyFire()) {
                return false;                                    // teammates
            }
        }
        return !(entity instanceof Tameable tameable && source.getUniqueId().equals(tameable.getOwnerUniqueId()));
    }

    private List<LivingEntity> targets(Player source, Location center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (valid(source, entity) && entity.getLocation().distanceSquared(center) <= r2) {
                result.add((LivingEntity) entity);
            }
        }
        return result;
    }

    /** The living entity the player is looking at (blocks stop the ray). */
    private LivingEntity lookTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult ray = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER,
                true, 0.8, e -> valid(player, e));
        return ray != null && ray.getHitEntity() instanceof LivingEntity target ? target : null;
    }

    /** Ability damage goes through the normal damage event (region protection, armor etc. apply). */
    private void hurt(Player source, LivingEntity target, double amount) {
        if (amount <= 0 || !valid(source, target)) {
            return;
        }
        DamageGuard.run(() -> {
            target.setNoDamageTicks(0);
            target.damage(scaled(target, amount), source);
        });
    }

    private double scaled(LivingEntity target, double amount) {
        if (target instanceof Player) {
            return amount * plugin.getConfig().getDouble("ability-settings.pvp-multiplier", 1.0);
        }
        return amount;
    }

    private static double heightAboveGround(Location location) {
        Block block = location.getBlock();
        int min = location.getWorld().getMinHeight();
        for (int i = 1; i <= 40 && block.getY() - i >= min; i++) {
            Block below = block.getRelative(0, -i, 0);
            if (below.getType().isSolid()) {
                return Math.max(0, location.getY() - (below.getY() + 1));
            }
        }
        return 0;
    }

    private static void ring(Location center, double radius, int points, Consumer<Location> spawn) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            spawn.accept(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
    }

    private static void line(Location from, Location to, double step, Consumer<Location> spawn) {
        if (from.getWorld() != to.getWorld()) {
            return;
        }
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        if (length < 0.01) {
            return;
        }
        dir.multiply(1 / length);
        for (double d = 0; d <= length; d += step) {
            spawn.accept(from.clone().add(dir.clone().multiply(d)));
        }
    }

    /**
     * Repeats a step every {@code period} ticks, at most {@code times} times.
     * The step returns false to stop early.
     */
    private void repeat(long period, int times, IntPredicate step) {
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                boolean keep;
                try {
                    keep = step.test(i);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Ability animation error: " + ex);
                    keep = false;
                }
                if (!keep || ++i >= times) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, period);
    }
}
