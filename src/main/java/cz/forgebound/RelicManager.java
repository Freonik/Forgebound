package cz.forgebound;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Relic effects: Stasis Hourglass, Angel's Wings, Thorn Plate, Heartsteel Core, Thick Hide, Soul Vessel Mask,
 * Stitchguard.
 * A relic only works in its own slot (off-hand, chestplate, boots...).
 * Cooldowns and Heartsteel stacks are stored on the item itself, so they survive restarts and trades.
 */
public final class RelicManager implements Listener {

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 60), 1.3f);
    private static final Particle.DustOptions WING = new Particle.DustOptions(Color.fromRGB(255, 250, 230), 1.1f);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(160, 10, 20), 1.2f);
    private static final Particle.DustOptions BLOOD_SMALL = new Particle.DustOptions(Color.fromRGB(200, 20, 34), 0.7f);
    private static final Particle.DustOptions WING_SMALL = new Particle.DustOptions(Color.fromRGB(255, 255, 250), 0.55f);
    private static final Particle.DustOptions GOLD_SMALL = new Particle.DustOptions(Color.fromRGB(255, 210, 90), 0.55f);
    private static final Particle.DustOptions THORN_SMALL = new Particle.DustOptions(Color.fromRGB(60, 110, 40), 0.7f);
    private static final Particle.DustOptions SPIKE_SMALL = new Particle.DustOptions(Color.fromRGB(225, 230, 170), 0.8f);
    private static final Particle.DustOptions SOUL_SMALL = new Particle.DustOptions(Color.fromRGB(225, 232, 255), 0.7f);
    private static final Particle.DustOptions SOUL_BIG = new Particle.DustOptions(Color.fromRGB(235, 240, 255), 1.3f);
    private static final Particle.DustOptions SILK = new Particle.DustOptions(Color.fromRGB(245, 240, 230), 0.9f);
    private static final Particle.DustOptions NEEDLE = new Particle.DustOptions(Color.fromRGB(200, 30, 40), 1.1f);
    private static final Particle.DustOptions SHELL = new Particle.DustOptions(Color.fromRGB(230, 225, 200), 1.0f);
    private static final Particle.DustOptions HIDE_GREY = new Particle.DustOptions(Color.fromRGB(150, 150, 140), 0.8f);
    private static final Particle.DustOptions HIDE_SHRUG = new Particle.DustOptions(Color.fromRGB(60, 200, 180), 1.2f);
    /** Damage Thick Hide never delays. */
    private static final Set<EntityDamageEvent.DamageCause> RAW_CAUSES = Set.of(EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.KILL, EntityDamageEvent.DamageCause.SUICIDE,
            EntityDamageEvent.DamageCause.WORLD_BORDER, EntityDamageEvent.DamageCause.STARVATION);

    private final Forgebound plugin;
    private final ItemManager items;
    private final NamespacedKey readyKey;
    private final NamespacedKey stacksKey;
    private final NamespacedKey heartKey;
    private final NamespacedKey setScaledKey = Forgebound.key("heartsteel_set");

    /** player -> stasis end (ms) */
    private final Map<UUID, Long> stasis = new HashMap<>();
    /** entity -> grievous wounds end (ms) */
    private final Map<UUID, Long> wounded = new HashMap<>();
    /** attacker -> (target -> Heartsteel ready again at) */
    private final Map<UUID, Map<UUID, Long>> heartsteel = new HashMap<>();
    /** player -> attack charge right before the hit */
    private final Map<UUID, Float> charge = new HashMap<>();
    /** player -> grey health: damage taken that hasn't been subtracted yet (Thick Hide) */
    private final Map<UUID, Double> grey = new HashMap<>();
    /** player -> last time sneak was pressed (double-tap detection) */
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    /** true while Thick Hide itself deals the last bit of grey damage */
    private boolean draining;
    /** Soul Vessel Mask: player -> soul (0-99), focus progress in ticks, last position */
    private final Map<UUID, Double> soul = new HashMap<>();
    private final Map<UUID, Integer> focus = new HashMap<>();
    private final Map<UUID, Location> focusAnchor = new HashMap<>();
    /** Stitchguard: parry window end, last raise, shell hits left, shell broken until */
    private final Map<UUID, Long> parryUntil = new HashMap<>();
    private final Map<UUID, Long> lastRaise = new HashMap<>();
    private final Map<UUID, Integer> shell = new HashMap<>();
    private final Map<UUID, Long> shellBroken = new HashMap<>();

    public RelicManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.readyKey = Forgebound.key("relic_ready_at");
        this.stacksKey = Forgebound.key("heartsteel_stacks");
        this.heartKey = Forgebound.key("heartsteel_health");
    }

    public void start() {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                long now = System.currentTimeMillis();
                stasis.entrySet().removeIf(e -> {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p == null) {
                        return true;
                    }
                    if (e.getValue() <= now) {
                        endStasis(p);
                        return true;
                    }
                    stasisVisual(p, ticks);
                    return false;
                });
                boolean particles = plugin.getConfig().getBoolean("relic-settings.particles", true);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (particles) {
                        ambient(p, ticks);
                    }
                    focusTick(p);
                }
                if (ticks % 5 == 0) {
                    drainGrey();
                }
                if (ticks % 20 == 0) {
                    wounded.values().removeIf(t -> t < now);
                    heartsteel.values().forEach(m -> m.values().removeIf(t -> t < now));
                    heartsteel.values().removeIf(Map::isEmpty);
                }
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    // ================================================================ helpers

    /** The relic of this type the player has in its proper slot, or null. */
    private ItemManager.Entry equipped(Player player, Relic.Type type) {
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemStack stack = player.getInventory().getItem(slot.equipment);
            ItemManager.Entry entry = items.relicOf(stack);
            if (entry != null && entry.relic.type() == type && entry.relic.slot() == slot) {
                return entry;
            }
        }
        return null;
    }

    /** Pieces of one relic type worn in their own slots, and how strong that makes the effect. */
    record SetWear(ItemManager.Entry entry, int count, double factor) { }

    /**
     * Set relics: each piece gives 1/4 of the effect, all four together 5/4 (relic-settings.set-piece / set-full).
     * A set has the effects of all its pieces (Pale Seraph = Soul Vessel + Angel's Wings), so every worn piece of the
     * set counts toward each of them; the numbers come from the set's piece of that type.
     * A relic without a set works fully on its own.
     */
    private SetWear setWear(Player player, Relic.Type type) {
        ItemManager.Entry plain = null;
        Map<String, Integer> counts = new HashMap<>();
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemManager.Entry entry = items.relicOf(player.getInventory().getItem(slot.equipment));
            if (entry == null || entry.relic.slot() != slot) {
                continue;
            }
            if (entry.relic.set() != null) {
                counts.merge(entry.relic.set(), 1, Integer::sum);
            } else if (entry.relic.type() == type && plain == null) {
                plain = entry;
            }
        }
        SetWear best = null;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            ItemManager.Entry provider = provider(e.getKey(), type);
            if (provider != null && (best == null || e.getValue() > best.count())) {
                best = new SetWear(provider, e.getValue(), setFactor(e.getValue()));
            }
        }
        if (best != null) {
            return best;
        }
        return plain == null ? null : new SetWear(plain, 1, 1.0);
    }

    /** The piece of this set that has the effect of this type, or null. */
    private ItemManager.Entry provider(String set, Relic.Type type) {
        if (type == Relic.Type.SET) {
            return null;
        }
        for (ItemManager.Entry e : items.all(ItemManager.Kind.RELIC)) {
            if (e.relic != null && e.relic.type() == type && set.equals(e.relic.set())) {
                return e;
            }
        }
        return null;
    }

    /** Does this relic take part in the effect of this type (its own type, or one its set has)? */
    private boolean partOf(ItemManager.Entry entry, Relic.Type type) {
        return entry.relic.type() == type || (entry.relic.set() != null && provider(entry.relic.set(), type) != null);
    }

    double setFactor(int pieces) {
        double piece = plugin.getConfig().getDouble("relic-settings.set-piece", 0.25);
        double full = plugin.getConfig().getDouble("relic-settings.set-full", 1.25);
        return factor(pieces, piece, full);
    }

    /** 1 piece = piece, 2 = 2x piece ..., 4 = full. */
    static double factor(int pieces, double piece, double full) {
        return pieces <= 0 ? 0 : pieces >= 4 ? full : pieces * piece;
    }

    /** Remaining cooldown of the relic item in ms. */
    private long cooldownLeft(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Long ready = stack.getPersistentDataContainer().get(readyKey, PersistentDataType.LONG);
        return ready == null ? 0 : Math.max(0, ready - System.currentTimeMillis());
    }

    private void startCooldown(Player player, ItemManager.Entry entry) {
        if (entry.relic.cooldown() <= 0 || player.hasPermission("forgebound.nocooldown")) {
            return;
        }
        EquipmentSlot slot = entry.relic.slot().equipment;
        ItemStack stack = player.getInventory().getItem(slot);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(readyKey, PersistentDataType.LONG,
                System.currentTimeMillis() + entry.relic.cooldown() * 1000L);
        stack.setItemMeta(meta);
        player.getInventory().setItem(slot, stack);
    }

    /** Set relics share one cooldown: the longest one left on any worn piece of that type. */
    private long setCooldownLeft(Player player, Relic.Type type) {
        long left = 0;
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemStack stack = player.getInventory().getItem(slot.equipment);
            ItemManager.Entry entry = items.relicOf(stack);
            if (entry != null && entry.relic.slot() == slot && partOf(entry, type)) {
                left = Math.max(left, cooldownLeft(stack));
            }
        }
        return left;
    }

    /** Starts the cooldown on every worn piece of that type (seconds). */
    private void startSetCooldown(Player player, Relic.Type type, double seconds) {
        if (seconds <= 0 || player.hasPermission("forgebound.nocooldown")) {
            return;
        }
        long ready = System.currentTimeMillis() + (long) (seconds * 1000);
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemStack stack = player.getInventory().getItem(slot.equipment);
            ItemManager.Entry entry = items.relicOf(stack);
            if (entry != null && entry.relic.slot() == slot && partOf(entry, type)) {
                ItemMeta meta = stack.getItemMeta();
                meta.getPersistentDataContainer().set(readyKey, PersistentDataType.LONG, ready);
                stack.setItemMeta(meta);
                player.getInventory().setItem(slot.equipment, stack);
            }
        }
    }

    private boolean valid(Player source, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.equals(source) || living.isDead() || !living.isValid()
                || entity instanceof ArmorStand || living.isInvulnerable()) {
            return false;
        }
        if (entity instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        if (entity instanceof Player p && plugin.teams() != null && plugin.teams().allies(source, p)
                && !plugin.teams().friendlyFire()) {
            return false;                                    // teammates
        }
        return !(entity instanceof Tameable t && source.getUniqueId().equals(t.getOwnerUniqueId()));
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    /** " (2/4)" for set relics, "" otherwise. */
    private static String setLabel(SetWear w) {
        if (w == null || w.entry().relic.set() == null) {
            return "";
        }
        return w.count() >= 4 ? " &6(4/4 ✦)" : " &8(" + w.count() + "/4)";
    }

    private static String seconds(long ms) {
        return ((ms + 999) / 1000) + "s";
    }

    /** Action bar part for the player's relics (cooldowns, Heartsteel stacks). Empty if none. */
    /** Last complete set seen on each player (so Full Set / Wardrobe are checked only when it changes). */
    private final Map<UUID, String> fullSet = new HashMap<>();

    /** Full Set and Wardrobe: wearing all four pieces of a set. */
    private void checkFullSet(Player player) {
        Map<String, Integer> counts = new HashMap<>();
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemManager.Entry entry = items.relicOf(player.getInventory().getItem(slot.equipment));
            if (entry != null && entry.relic.slot() == slot && entry.relic.set() != null) {
                counts.merge(entry.relic.set(), 1, Integer::sum);
            }
        }
        String full = counts.entrySet().stream().filter(e -> e.getValue() >= 4).map(Map.Entry::getKey).findFirst().orElse("");
        if (full.equals(fullSet.getOrDefault(player.getUniqueId(), ""))) {
            return;
        }
        fullSet.put(player.getUniqueId(), full);
        if (full.isEmpty() || plugin.advancements() == null) {
            return;
        }
        plugin.award(player, "full_set");
        java.util.Set<String> sets = new java.util.HashSet<>();
        for (ItemManager.Entry e : items.all(ItemManager.Kind.RELIC)) {
            if (e.relic != null && e.relic.set() != null) {
                sets.add(e.relic.set());
            }
        }
        plugin.advancements().progress(player.getUniqueId(), "wardrobe", full, sets.size());
    }

    public String hud(Player player) {
        checkFullSet(player);
        // which effects the worn relics give (a set piece gives all of its set's effects), in slot order
        java.util.Set<Relic.Type> types = new java.util.LinkedHashSet<>();
        ItemStack hourglass = null;
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemStack stack = player.getInventory().getItem(slot.equipment);
            ItemManager.Entry entry = items.relicOf(stack);
            if (entry == null || entry.relic.slot() != slot) {
                continue;
            }
            if (entry.relic.type() == Relic.Type.HEARTSTEEL) {
                migrateHeartsteel(stack, entry);
            }
            if (entry.relic.type() == Relic.Type.STASIS) {
                hourglass = stack;
            }
            if (entry.relic.type() != Relic.Type.SET) {
                types.add(entry.relic.type());
            }
            if (entry.relic.set() != null) {
                for (Relic.Type t : Relic.Type.values()) {
                    if (t != Relic.Type.SET && provider(entry.relic.set(), t) != null) {
                        types.add(t);
                    }
                }
            }
        }
        StringBuilder bar = new StringBuilder();
        for (Relic.Type type : types) {
            String part = hudPart(player, type, hourglass);
            if (part != null) {
                if (!bar.isEmpty()) {
                    bar.append("  &8┃  ");
                }
                bar.append(part);
            }
        }
        return bar.toString();
    }

    private String hudPart(Player player, Relic.Type type, ItemStack hourglass) {
        SetWear w = setWear(player, type);
        if (w == null && type != Relic.Type.STASIS) {
            return null;
        }
        switch (type) {
            case STASIS -> {
                long left = cooldownLeft(hourglass);
                return "&6⧗ " + (left <= 0 ? "&a✔" : "&c" + seconds(left));
            }
            case REVIVE -> {
                long left = setCooldownLeft(player, Relic.Type.REVIVE);
                return "&f✦ " + (left <= 0 ? "&a✔" : "&c" + seconds(left)) + setLabel(w);
            }
            case HEARTSTEEL -> {
                double total = 0;
                for (Relic.Slot s2 : Relic.Slot.values()) {
                    ItemStack st = player.getInventory().getItem(s2.equipment);
                    ItemManager.Entry e2 = items.relicOf(st);
                    if (e2 != null && e2.relic.type() == Relic.Type.HEARTSTEEL && e2.relic.slot() == s2) {
                        total += heartBonus(st);
                    }
                }
                return "&c❤ &f+" + trim(total) + setLabel(w);
            }
            case THORNS -> {
                String label = setLabel(w);
                return label.isEmpty() ? null : "&2✹" + label;
            }
            case SOUL_VESSEL -> {
                WeaponAbilities.Params p = w.entry().relic.params();
                int cost = Math.max(1, p.i("focus-cost", 33));
                int max = Math.max(cost, p.i("max-soul", 99));
                int have = soulOf(player);
                StringBuilder vessel = new StringBuilder("&f◉ ");
                for (int i = 0; i < max / cost; i++) {
                    vessel.append(have >= (i + 1) * cost ? "&f◆" : have > i * cost ? "&7◈" : "&8◇");
                }
                vessel.append(setLabel(w));
                if (focus.containsKey(player.getUniqueId())) {
                    vessel.append(" &bfocusing...");
                }
                return vessel.toString();
            }
            case STITCHGUARD -> {
                WeaponAbilities.Params p = w.entry().relic.params();
                int max = Math.max(1, p.i("shell-hits", 3));
                int left = shellOf(player, p);
                Long broken = shellBroken.get(player.getUniqueId());
                return "&e⛉ " + (left > 0 ? "&f" + "◆".repeat(left) + "&8" + "◇".repeat(Math.max(0, max - left))
                        : "&c" + seconds(broken == null ? 0 : broken - System.currentTimeMillis()));
            }
            case THICK_HIDE -> {
                long left = setCooldownLeft(player, Relic.Type.THICK_HIDE);
                double pool = grey.getOrDefault(player.getUniqueId(), 0.0);
                return "&7⛨ " + (left <= 0 ? "&a✔" : "&c" + seconds(left)) + setLabel(w)
                        + (pool > 0.05 ? " &8(&7-" + trim(Math.ceil(pool) / 2) + " ❤&8)" : "");
            }
            default -> {
                return null;
            }
        }
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    public boolean inStasis(Player player) {
        Long until = stasis.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** Healing after Grievous Wounds (Thorn Plate). Used by weapon abilities that heal directly. */
    public double adjustHeal(LivingEntity entity, double amount) {
        Long until = wounded.get(entity.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            return amount;
        }
        double reduction = plugin.getConfig().getDouble("relics.thorn_plate.relic.heal-reduction", 100) / 100.0;
        return amount * Math.max(0, 1 - reduction);
    }

    // ================================================================ ambient relic effects

    /** Small, cheap visual effects for worn relics (called every 2 ticks). */
    private void ambient(Player player, int ticks) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || inStasis(player)) {
            return;
        }
        World world = player.getWorld();
        Location loc = player.getLocation();
        double yaw = Math.toRadians(loc.getYaw());
        org.bukkit.util.Vector forward = new org.bukkit.util.Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-Math.cos(yaw), 0, -Math.sin(yaw));

        // Angel's Wings: little feather wings flap at the ankles while sprinting or in the air
        if (ticks % 2 == 0 && (player.isSprinting() || !player.isOnGround()) && setWear(player, Relic.Type.REVIVE) != null) {
            double flap = 0.08 * Math.sin(ticks * 0.9);
            for (int side = -1; side <= 1; side += 2) {
                Location ankle = loc.clone().add(right.clone().multiply(0.17 * side)).add(0, 0.2, 0)
                        .add(forward.clone().multiply(-0.05));
                for (int i = 1; i <= 4; i++) {
                    double t = i / 4.0;
                    Location p = ankle.clone()
                            .add(right.clone().multiply(side * (0.08 + 0.22 * t)))
                            .add(forward.clone().multiply(-0.18 * t))
                            .add(0, 0.12 + 0.18 * t - 0.12 * t * t + flap * t, 0);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, i == 4 ? GOLD_SMALL : WING_SMALL);
                }
            }
        }
        // Heartsteel: a heartbeat on the chest every 3 seconds (lub-dub)
        int beat = ticks % 30;
        if ((beat == 0 || beat == 3) && equipped(player, Relic.Type.HEARTSTEEL) != null) {
            Location chest = loc.clone().add(0, 1.25, 0).add(forward.clone().multiply(0.28));
            world.spawnParticle(Particle.DUST, chest, beat == 0 ? 6 : 3, 0.08, 0.08, 0.08, 0, BLOOD_SMALL);
        }
        // Thorn Plate: now and then a few green spores drift from the shoulders
        if (ticks % 20 == 7 && equipped(player, Relic.Type.THORNS) != null) {
            for (int side = -1; side <= 1; side += 2) {
                world.spawnParticle(Particle.DUST, loc.clone().add(right.clone().multiply(0.35 * side)).add(0, 1.45, 0),
                        1, 0.05, 0.05, 0.05, 0, THORN_SMALL);
            }
        }
    }

    /** Thorn Plate retaliation: thorns burst out of the wearer. */
    private void thornBurst(Player wearer) {
        Location c = wearer.getLocation().add(0, 1.1, 0);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            for (double r = 0.4; r <= 0.9; r += 0.25) {
                wearer.getWorld().spawnParticle(Particle.DUST,
                        c.clone().add(Math.cos(a) * r, (i % 2 == 0 ? 0.2 : -0.2) * r, Math.sin(a) * r),
                        1, 0, 0, 0, 0, r > 0.8 ? SPIKE_SMALL : THORN_SMALL);
            }
        }
    }

    // ================================================================ STASIS HOURGLASS

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemManager.Entry entry = items.relicOf(event.getItem());
        if (entry == null || entry.relic.type() != Relic.Type.STASIS) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            player.sendActionBar(plugin.raw("relic-wrong-slot", "&cThis relic only works in your {slot}.",
                    "slot", entry.relic.slot().label.toLowerCase()));
            return;
        }
        if (inStasis(player)) {
            return;
        }
        long left = cooldownLeft(event.getItem());
        if (left > 0) {
            player.sendActionBar(plugin.raw("relic-cooldown", "&c{relic} &7is recharging: &c{time}",
                    "relic", ItemManager.nameOf(entry.item), "time", seconds(left)));
            return;
        }
        double secs = entry.relic.params().d("duration", 2.5);
        stasis.put(player.getUniqueId(), System.currentTimeMillis() + (long) (secs * 1000));
        startCooldown(player, entry);
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (secs * 20), 0, false, false, false));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.2f, 1.4f);
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1.2f, 0.6f);
        world.spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
    }

    private void stasisVisual(Player player, int ticks) {
        Location base = player.getLocation();
        World world = player.getWorld();
        for (int i = 0; i < 8; i++) {
            double a = ticks * 0.3 + i * Math.PI / 4;
            double y = (ticks % 20) / 10.0;
            world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 0.7, y, Math.sin(a) * 0.7), 1, 0, 0, 0, 0, GOLD);
        }
    }

    private void endStasis(Player player) {
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0, GOLD);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!inStasis(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location stay = from.clone();
            stay.setYaw(to.getYaw());
            stay.setPitch(to.getPitch());
            event.setTo(stay);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStasisDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && inStasis(player)
                && event.getCause() != EntityDamageEvent.DamageCause.VOID
                && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            if (event.getFinalDamage() >= player.getHealth() + player.getAbsorptionAmount()) {
                plugin.award(player, "frozen_in_time");
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStasisAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player player && inStasis(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStasisShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && inStasis(player)) {
            event.setCancelled(true);
        }
    }

    // ================================================================ ANGEL'S WINGS

    @EventHandler(priority = EventPriority.HIGH)
    public void onResurrect(EntityResurrectEvent event) {
        if (!event.isCancelled() || !(event.getEntity() instanceof Player player)) {
            return; // a totem already saves them
        }
        SetWear set = setWear(player, Relic.Type.REVIVE);
        if (set == null || setCooldownLeft(player, Relic.Type.REVIVE) > 0) {
            return;
        }
        ItemManager.Entry entry = set.entry();
        event.setCancelled(false);
        // set: fewer pieces = a longer cooldown (1 piece 4x, the full set 0.8x)
        startSetCooldown(player, Relic.Type.REVIVE, entry.relic.cooldown() / Math.max(0.05, set.factor()));
        plugin.award(player, "saved_by_an_angel");
        double heal = entry.relic.params().d("heal-percent", 50) / 100.0;
        int protect = entry.relic.params().i("protection-seconds", 3);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            player.setHealth(Math.max(1, Math.min(maxHealth(player), maxHealth(player) * heal)));
            player.setFireTicks(0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, protect * 20, 4, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true, true));
            wings(player);
        });
        Bukkit.broadcast(plugin.msg("relic-revived", "&f✦ {player} &7was saved by {relic}&7!",
                "player", player.getName(), "relic", ItemManager.nameOf(entry.item)));
    }

    /** Angel wings drawn behind the player for a moment. */
    private void wings(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.3f);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!player.isOnline() || t++ > 30) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                double yaw = Math.toRadians(loc.getYaw());
                org.bukkit.util.Vector back = new org.bukkit.util.Vector(Math.sin(yaw), 0, -Math.cos(yaw)).multiply(0.35);
                org.bukkit.util.Vector side = new org.bukkit.util.Vector(Math.cos(yaw), 0, Math.sin(yaw));
                double flap = 0.25 * Math.sin(t * 0.5);
                for (int s = -1; s <= 1; s += 2) {
                    for (double u = 0.2; u <= 1.4; u += 0.15) {
                        double h = 1.5 + 0.6 * Math.sin(u * 2.2) - 0.25 * u;
                        for (double d = 0; d <= 0.5 * (1.4 - u) + 0.15; d += 0.15) {
                            Location p = loc.clone().add(back).add(side.clone().multiply(s * u))
                                    .add(back.clone().multiply(flap * u * 2)).add(0, h - d, 0);
                            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, WING);
                        }
                    }
                }
                player.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Angel's Wings: lighter falls. */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        SetWear set = setWear(player, Relic.Type.REVIVE);
        if (set != null) {
            double cut = Math.min(95, set.entry().relic.params().d("fall-reduction", 50) * set.factor());
            event.setDamage(event.getDamage() * (1 - cut / 100.0));
        }
    }

    // ================================================================ attacks: THORNS, HEARTSTEEL

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        charge.put(event.getPlayer().getUniqueId(), event.getPlayer().getAttackCooldown());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (DamageGuard.active() || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // ---- Thorn Plate (victim side): reflect damage + Grievous Wounds
        if (victim instanceof Player wearer) {
            LivingEntity attacker = null;
            double share = 1.0;
            if (event.getDamager() instanceof LivingEntity living
                    && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
                attacker = living;
            } else if (event.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof LivingEntity shooter) {
                attacker = shooter;
                share = 0.5;
            }
            SetWear thornSet = attacker == null ? null : setWear(wearer, Relic.Type.THORNS);
            ItemManager.Entry thorns = thornSet == null ? null : thornSet.entry();
            if (thorns != null && !attacker.equals(wearer)
                    && !(attacker instanceof Player hitter && plugin.teams() != null && plugin.teams().allies(wearer, hitter))) {
                WeaponAbilities.Params p = thorns.relic.params();
                double reflect = (p.d("reflect-flat", 2) + event.getDamage() * p.d("reflect-percent", 25) / 100.0) * share
                        * thornSet.factor();
                LivingEntity target = attacker;
                DamageGuard.run(() -> target.damage(reflect, wearer));
                double cleanse = attacker instanceof Player ap && plugin.customEnchants() != null
                        ? plugin.customEnchants().cleanseFactor(ap) : 1;
                wounded.put(attacker.getUniqueId(), System.currentTimeMillis()
                        + (long) (p.d("wound-seconds", 3) * 1000 * Math.min(1.25, thornSet.factor()) * cleanse));
                Location at = attacker.getLocation().add(0, attacker.getHeight() / 2, 0);
                attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at, 4, 0.3, 0.4, 0.3, 0.1);
                attacker.getWorld().spawnParticle(Particle.DUST, at, 10, 0.3, 0.4, 0.3, 0, BLOOD);
                attacker.getWorld().playSound(at, Sound.ENCHANT_THORNS_HIT, 1f, 0.8f);
                thornBurst(wearer);
                if (attacker instanceof Player hit) {
                    hit.sendActionBar(plugin.raw("relic-wounded", "&4✖ Grievous Wounds &7- you can't heal for a moment"));
                }
            }
        }

        // ---- attacker side: a charged melee hit by a player
        if (!(event.getDamager() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (event.getEntity() instanceof Player mate && plugin.teams() != null && plugin.teams().allies(player, mate)) {
            return;                                              // hitting a teammate: no Heartsteel / Soul stacks
        }
        Float strength = charge.remove(player.getUniqueId());
        if (strength != null && strength < 0.85f) {
            return;
        }

        // Soul Vessel Mask: every charged hit fills the vessel
        if (!(victim instanceof ArmorStand)) {
            gainSoul(player);
        }

        // Heartsteel Core: big hit on a worthy target, grows your max HP for good
        SetWear heartSet = setWear(player, Relic.Type.HEARTSTEEL);
        ItemManager.Entry heart = heartSet == null ? null : heartSet.entry();
        // only players count (stack-from-mobs: true lets big mobs with 20+ max HP count too)
        if (heart != null && (victim instanceof Player
                || (heart.relic.params().b("stack-from-mobs", false) && maxHealth(victim) >= 20))) {
            long now = System.currentTimeMillis();
            Map<UUID, Long> ready = heartsteel.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            Long until = ready.get(victim.getUniqueId());
            if (until == null || until <= now) {
                WeaponAbilities.Params p = heart.relic.params();
                ready.put(victim.getUniqueId(), now + (long) (p.d("target-cooldown", 30) * 1000));
                event.setDamage(event.getDamage() + (p.d("bonus-damage", 3) + maxHealth(player) * p.d("bonus-percent-max-hp", 5) / 100.0)
                        * heartSet.factor());
                growHeartsteel(player, heartSet);
                Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
                victim.getWorld().spawnParticle(Particle.DUST, at, 25, 0.4, 0.5, 0.4, 0, BLOOD);
                victim.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2.1, 0), 3, 0.3, 0.1, 0.3, 0);
                victim.getWorld().playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.2f);
            }
        }

    }

    private double heartBonus(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Double bonus = stack.getPersistentDataContainer().get(heartKey, PersistentDataType.DOUBLE);
        return bonus == null ? 0 : bonus;
    }

    /**
     * +max HP stored on each worn Heartsteel piece itself (an attribute modifier for its slot). Every piece grows by
     * its share: 1/4 of the stack each, 5/16 each with the full set; each piece holds its share of max-bonus-health.
     */
    private void growHeartsteel(Player player, SetWear set) {
        WeaponAbilities.Params p = set.entry().relic.params();
        double max = p.d("max-bonus-health", 20);
        double share = set.entry().relic.set() == null ? 1.0 : set.factor() / set.count();
        double total = 0;
        for (Relic.Slot slot : Relic.Slot.values()) {
            ItemStack stack = player.getInventory().getItem(slot.equipment);
            ItemManager.Entry entry = items.relicOf(stack);
            if (entry == null || entry.relic.type() != Relic.Type.HEARTSTEEL || entry.relic.slot() != slot) {
                continue;
            }
            migrateHeartsteel(stack, entry);
            double cap = max * share;
            double bonus = Math.min(Math.max(cap, heartBonus(stack)), heartBonus(stack) + p.d("health-per-stack", 1) * share);
            ItemMeta meta = stack.getItemMeta();
            int stacks = meta.getPersistentDataContainer().getOrDefault(stacksKey, PersistentDataType.INTEGER, 0) + 1;
            meta.getPersistentDataContainer().set(stacksKey, PersistentDataType.INTEGER, stacks);
            setHeartBonus(meta, entry, slot, bonus, stacks);
            stack.setItemMeta(meta);
            player.getInventory().setItem(slot.equipment, stack);
            total += bonus;
        }
        if (total >= max - 0.01) {
            plugin.award(player, "heart_of_steel");
        }
    }

    private void setHeartBonus(ItemMeta meta, ItemManager.Entry entry, Relic.Slot slot, double bonus, int stacks) {
        meta.getPersistentDataContainer().set(heartKey, PersistentDataType.DOUBLE, bonus);
        meta.removeAttributeModifier(Attribute.MAX_HEALTH);
        // one id per slot: several pieces with the same id would replace each other
        meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                Forgebound.key("heartsteel_bonus_" + slot.name().toLowerCase(java.util.Locale.ROOT)),
                bonus, AttributeModifier.Operation.ADD_NUMBER, slot.group));
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(
                entry.item.getItemMeta().lore() == null ? java.util.List.of() : entry.item.getItemMeta().lore());
        lore.add(ItemManager.color("&c❤ Stacks: &f" + stacks + " &8• &c+" + trim(bonus) + " max HP"));
        meta.lore(lore);
    }

    /**
     * Heartsteel became a set: a Core made before keeps a quarter of the max HP it had grown
     * (like every other piece, it's now 1/4 of the set). Done once per item.
     */
    private void migrateHeartsteel(ItemStack stack, ItemManager.Entry entry) {
        if (entry.relic.set() == null || stack == null || !stack.hasItemMeta()
                || stack.getPersistentDataContainer().has(setScaledKey, PersistentDataType.BYTE)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        double bonus = heartBonus(stack);
        if (bonus > 0) {
            int stacks = meta.getPersistentDataContainer().getOrDefault(stacksKey, PersistentDataType.INTEGER, 0);
            setHeartBonus(meta, entry, entry.relic.slot(), bonus * plugin.getConfig().getDouble("relic-settings.set-piece", 0.25), stacks);
        }
        meta.getPersistentDataContainer().set(setScaledKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }

    /** Grievous Wounds also reduce natural and potion healing. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        double amount = adjustHeal((LivingEntity) event.getEntity(), event.getAmount());
        if (amount <= 0) {
            event.setCancelled(true);
        } else {
            event.setAmount(amount);
        }
    }

    /** Relics and custom weapons can't be trimmed or upgraded at a smithing table. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(org.bukkit.event.inventory.PrepareSmithingEvent event) {
        if (items.customOf(event.getInventory().getInputEquipment()) != null) {
            event.setResult(null);
        }
    }

    // ================================================================ SOUL VESSEL MASK

    private int soulOf(Player player) {
        return (int) Math.floor(soul.getOrDefault(player.getUniqueId(), 0.0) + 1.0E-6);
    }

    /** Called every 2 ticks: sneaking still with enough soul slowly focuses it into health. */
    private void focusTick(Player player) {
        UUID id = player.getUniqueId();
        SetWear vessel = setWear(player, Relic.Type.SOUL_VESSEL);
        if (vessel == null) {
            focus.remove(id);
            return;
        }
        WeaponAbilities.Params p = vessel.entry().relic.params();
        int cost = Math.max(1, p.i("focus-cost", 33));
        Location here = player.getLocation();
        Location anchor = focusAnchor.get(id);
        boolean still = anchor != null && anchor.getWorld() == here.getWorld()
                && Math.abs(anchor.getX() - here.getX()) < 0.04 && Math.abs(anchor.getZ() - here.getZ()) < 0.04;
        focusAnchor.put(id, here);
        if (!player.isSneaking() || !player.isOnGround() || !still || soulOf(player) < cost
                || player.getHealth() >= maxHealth(player) || inStasis(player)) {
            focus.remove(id);
            return;
        }
        int ticks = focus.merge(id, 2, Integer::sum);
        int need = Math.max(2, p.i("focus-ticks", 30));
        World world = player.getWorld();
        Location chest = here.clone().add(0, 1, 0);
        for (int i = 0; i < 3; i++) {
            double a = ticks * 0.7 + i * 2.1;
            double r = 1.1 * (1 - (double) ticks / need);
            world.spawnParticle(Particle.DUST, chest.clone().add(Math.cos(a) * r, Math.sin(a * 0.5) * 0.4, Math.sin(a) * r),
                    1, 0, 0, 0, 0, SOUL_SMALL);
        }
        if (ticks % 10 == 0) {
            world.playSound(here, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.8f);
        }
        if (ticks >= need) {
            focus.remove(id);
            soul.put(id, Math.max(0, soul.getOrDefault(id, 0.0) - cost));
            double max = maxHealth(player);
            player.setHealth(Math.min(max, player.getHealth() + adjustHeal(player, p.d("focus-heal", 6))));
            world.spawnParticle(Particle.DUST, chest, 25, 0.35, 0.5, 0.35, 0, SOUL_BIG);
            world.spawnParticle(Particle.END_ROD, chest, 8, 0.3, 0.4, 0.3, 0.03);
            world.playSound(here, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.6f);
            world.playSound(here, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
        }
    }

    /** Charged hits fill the Soul Vessel. */
    private void gainSoul(Player player) {
        SetWear set = setWear(player, Relic.Type.SOUL_VESSEL);
        if (set == null) {
            return;
        }
        WeaponAbilities.Params p = set.entry().relic.params();
        int max = Math.max(1, p.i("max-soul", 99));
        int before = soulOf(player);
        soul.put(player.getUniqueId(), Math.min(max,
                soul.getOrDefault(player.getUniqueId(), 0.0) + p.d("soul-per-hit", 11) * set.factor()));
        int now = soulOf(player);
        int cost = Math.max(1, p.i("focus-cost", 33));
        if (now / cost > before / cost) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
        }
    }

    /** Soul is lost on death. */
    @EventHandler
    public void onSoulDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        soul.remove(event.getPlayer().getUniqueId());
        focus.remove(event.getPlayer().getUniqueId());
    }

    /** Getting hit breaks your focus. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFocusHit(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            focus.remove(player.getUniqueId());
        }
    }

    // ================================================================ STITCHGUARD

    /** Raising the shield opens a short parry window (not again until retry-ms passed). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRaise(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemManager.Entry guard = items.relicOf(event.getItem());
        if (guard == null || guard.relic.type() != Relic.Type.STITCHGUARD) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastRaise.get(player.getUniqueId());
        if (last != null && now - last < guard.relic.params().d("retry-ms", 900)) {
            return;
        }
        lastRaise.put(player.getUniqueId(), now);
        parryUntil.put(player.getUniqueId(), now + (long) guard.relic.params().d("parry-window-ms", 350));
    }

    /** Parry: a hit inside the window does nothing, and you stitch through to the attacker's back. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onParry(EntityDamageByEntityEvent event) {
        if (DamageGuard.active() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = parryUntil.get(player.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            return;
        }
        ItemManager.Entry guard = equipped(player, Relic.Type.STITCHGUARD);
        if (guard == null) {
            return;
        }
        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity living) {
            attacker = living;
        } else if (event.getDamager() instanceof Projectile projectile) {
            projectile.remove();
            if (projectile.getShooter() instanceof LivingEntity shooter) {
                attacker = shooter;
            }
        } else {
            return;
        }
        event.setCancelled(true);
        parryUntil.remove(player.getUniqueId());
        WeaponAbilities.Params p = guard.relic.params();
        World world = player.getWorld();
        Location at = player.getLocation().add(0, 1.2, 0);
        world.playSound(at, Sound.ITEM_SHIELD_BLOCK, 1.5f, 1.6f);
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.6f, 2f);
        world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.DUST, at, 20, 0.4, 0.4, 0.4, 0, SILK);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, p.i("protect-ticks", 12), 4, false, false, true));
        player.sendActionBar(plugin.raw("relic-parry", "&f✦ Parry!"));
        if (plugin.advancements() != null) {
            plugin.advancements().parried(player);
        }
        if (attacker == null || attacker.equals(player) || attacker.getWorld() != world
                || attacker.getLocation().distanceSquared(player.getLocation()) > p.d("counter-range", 6) * p.d("counter-range", 6)) {
            return;
        }
        LivingEntity target = attacker;
        double damage = p.d("counter-damage", 8);
        Bukkit.getScheduler().runTask(plugin, () -> crossStitch(player, target, damage));
    }

    /** Dash through to the attacker's back with a silk slash. */
    private void crossStitch(Player player, LivingEntity target, double damage) {
        if (!player.isOnline() || player.isDead() || !target.isValid() || target.isDead()) {
            return;
        }
        Location from = player.getLocation();
        Location t = target.getLocation();
        org.bukkit.util.Vector back = t.toVector().subtract(from.toVector()).setY(0);
        if (back.lengthSquared() < 1.0E-4) {
            back = t.getDirection().setY(0).multiply(-1);
        }
        back.normalize();
        Location behind = t.clone().add(back.clone().multiply(1.3));
        if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) {
            behind = from.clone();
        }
        behind.setDirection(t.toVector().subtract(behind.toVector()));
        World world = player.getWorld();
        Location a = from.clone().add(0, 1, 0);
        Location b = behind.clone().add(0, 1, 0);
        org.bukkit.util.Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        for (double s = 0; s <= len; s += 0.25) {
            Location l = a.clone().add(d.clone().multiply(s / Math.max(0.01, len)));
            world.spawnParticle(Particle.DUST, l.add(0, 0.25 * Math.sin(s * 3), 0), 1, 0, 0, 0, 0, SILK);
        }
        player.teleport(behind);
        player.setFallDistance(0);
        if (valid(player, target)) {
            DamageGuard.run(() -> {
                target.setNoDamageTicks(0);
                target.damage(damage, player);
            });
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, t.clone().add(0, 1, 0), 3, 0.3, 0.2, 0.3, 0);
        world.spawnParticle(Particle.DUST, t.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0, NEEDLE);
        world.playSound(t, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 1.4f);
        world.playSound(t, Sound.ITEM_TRIDENT_RETURN, 1f, 1.8f);
    }

    private int shellOf(Player player, WeaponAbilities.Params p) {
        UUID id = player.getUniqueId();
        Long broken = shellBroken.get(id);
        int max = Math.max(1, p.i("shell-hits", 3));
        if (broken != null) {
            if (broken > System.currentTimeMillis()) {
                return 0;
            }
            shellBroken.remove(id);
            shell.put(id, max);
        }
        return shell.getOrDefault(id, max);
    }

    /** The shell: while blocking, hits from the sides and back are absorbed too (a few, then it cracks). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShell(EntityDamageByEntityEvent event) {
        if (DamageGuard.active() || !(event.getEntity() instanceof Player player) || !player.isBlocking()
                || event.getFinalDamage() <= 0.01) {
            return;
        }
        ItemManager.Entry guard = equipped(player, Relic.Type.STITCHGUARD);
        if (guard == null) {
            return;
        }
        WeaponAbilities.Params p = guard.relic.params();
        int left = shellOf(player, p);
        if (left <= 0) {
            return;
        }
        event.setCancelled(true);
        left--;
        shell.put(player.getUniqueId(), left);
        World world = player.getWorld();
        Location at = player.getLocation().add(0, 1, 0);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 0.9, Math.sin(a * 2) * 0.3, Math.sin(a) * 0.9),
                    1, 0, 0, 0, 0, SHELL);
        }
        world.playSound(at, Sound.ENTITY_TURTLE_EGG_CRACK, 1.2f, 1.2f);
        if (left <= 0) {
            shellBroken.put(player.getUniqueId(), System.currentTimeMillis() + (long) (p.d("shell-recharge", 20) * 1000));
            world.playSound(at, Sound.ENTITY_TURTLE_EGG_BREAK, 1.4f, 0.8f);
            world.spawnParticle(Particle.BLOCK, at, 25, 0.4, 0.5, 0.4, 0, org.bukkit.Material.WHITE_CONCRETE.createBlockData());
            player.sendActionBar(plugin.raw("relic-shell-broken", "&7The shell cracked - it grows back in {time}",
                    "time", seconds((long) (p.d("shell-recharge", 20) * 1000))));
        }
    }

    // ================================================================ THICK HIDE

    /** Part of the damage you take turns into grey health: it's subtracted slowly instead of all at once. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHideDamage(EntityDamageEvent event) {
        if (draining || !(event.getEntity() instanceof Player player) || RAW_CAUSES.contains(event.getCause())
                || inStasis(player)) {
            return;
        }
        SetWear hide = setWear(player, Relic.Type.THICK_HIDE);
        if (hide == null) {
            return;
        }
        double full = event.getFinalDamage();
        if (full <= 0.05) {
            return;
        }
        WeaponAbilities.Params p = hide.entry().relic.params();
        double cap = maxHealth(player) * Math.min(90, p.d("max-grey-percent", 50) * hide.factor()) / 100.0;
        double pool = grey.getOrDefault(player.getUniqueId(), 0.0);
        double defer = Math.min(full * Math.min(90, p.d("grey-percent", 40) * hide.factor()) / 100.0, Math.max(0, cap - pool));
        if (defer <= 0.05) {
            return;
        }
        event.setDamage(event.getDamage() * (full - defer) / full);
        grey.put(player.getUniqueId(), pool + defer);
    }

    /** Every 10 ticks a slice of the grey health is really lost. */
    private void drainGrey() {
        for (Iterator<Map.Entry<UUID, Double>> it = grey.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Double> e = it.next();
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || player.isDead()) {
                it.remove();
                continue;
            }
            if (inStasis(player)) {
                continue;
            }
            ItemManager.Entry hide = equipped(player, Relic.Type.THICK_HIDE);
            double percent = hide == null ? 50 : hide.relic.params().d("drain-percent", 20);
            double pool = e.getValue();
            double amount = Math.min(pool, Math.max(0.5, pool * percent / 100.0));
            pool -= amount;
            if (pool <= 0.01) {
                it.remove();
            } else {
                e.setValue(pool);
            }
            loseHealth(player, amount);
        }
    }

    /** Takes health without a new hit (absorption first); only the killing blow is real damage. */
    private void loseHealth(Player player, double amount) {
        double absorb = Math.min(player.getAbsorptionAmount(), amount);
        player.setAbsorptionAmount(player.getAbsorptionAmount() - absorb);
        amount -= absorb;
        if (amount <= 0) {
            return;
        }
        if (player.getHealth() - amount > 0.5) {
            player.setHealth(player.getHealth() - amount);
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 2, 0.25, 0.4, 0.25, 0, HIDE_GREY);
            return;
        }
        double hit = amount;
        draining = true;
        try {
            DamageGuard.run(() -> {
                player.setNoDamageTicks(0);
                player.damage(hit, DamageSource.builder(DamageType.MAGIC).build());
            });
        } finally {
            draining = false;
        }
    }

    /** Double-tap sneak with grey health: shrug it off (the grey health is never lost). */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastSneak.put(player.getUniqueId(), now);
        ItemManager.Entry hide = equipped(player, Relic.Type.THICK_HIDE);
        if (hide == null || last == null || now - last > hide.relic.params().d("double-tap-ms", 350)) {
            return;
        }
        lastSneak.remove(player.getUniqueId());
        double pool = grey.getOrDefault(player.getUniqueId(), 0.0);
        if (pool <= 0.05 || inStasis(player)) {
            return;
        }
        long left = setCooldownLeft(player, Relic.Type.THICK_HIDE);
        if (left > 0) {
            player.sendActionBar(plugin.raw("relic-cooldown", "&c{relic} &7is recharging: &c{time}",
                    "relic", ItemManager.nameOf(hide.item), "time", seconds(left)));
            return;
        }
        grey.remove(player.getUniqueId());
        startSetCooldown(player, Relic.Type.THICK_HIDE, hide.relic.cooldown());
        int resist = hide.relic.params().i("resistance-ticks", 20);
        if (resist > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resist, 1, false, true, true));
        }
        World world = player.getWorld();
        Location c = player.getLocation().add(0, 1, 0);
        world.playSound(c, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.5f, 0.6f);
        world.playSound(c, Sound.ENTITY_IRON_GOLEM_REPAIR, 1f, 0.8f);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            world.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.8, -0.6 + (i % 4) * 0.35, Math.sin(a) * 0.8),
                    1, 0, 0, 0, 0, HIDE_SHRUG);
        }
        player.sendActionBar(plugin.raw("relic-shrugged", "&3⛨ Shrugged off &f{amount} ❤",
                "amount", trim(Math.ceil(pool) / 2)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Double pool = grey.remove(id);
        if (pool != null && !event.getPlayer().isDead()) {
            // logging out doesn't dodge the pending damage (but never kills)
            Player player = event.getPlayer();
            player.setHealth(Math.max(1, player.getHealth() - pool));
        }
        lastSneak.remove(id);
        fullSet.remove(id);
        soul.remove(id);
        focus.remove(id);
        focusAnchor.remove(id);
        parryUntil.remove(id);
        lastRaise.remove(id);
        shell.remove(id);
        shellBroken.remove(id);
        stasis.remove(id);
        charge.remove(id);
        heartsteel.remove(id);
    }
}
