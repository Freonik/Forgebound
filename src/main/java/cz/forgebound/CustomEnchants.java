package cz.forgebound;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Forgebound's custom enchantments. They are never in the enchanting table, on villagers or in vanilla loot:
 * they only come out of Sealed Tomes (rare finds in structure chests and vaults), opened with experience levels.
 * Only normal gear takes them - legendary weapons and relics can't. The offensive ones (Lifesteal, Bleed, Execute,
 * Cripple) only work on mobs and on players who carry a legend, so they help hunt legends and never replace them.
 * Off by default (custom-enchants.enabled).
 */
public final class CustomEnchants implements Listener {

    public enum Rarity {
        COMMON("&a", "Common", 10), RARE("&b", "Rare", 20), LEGENDARY("&6", "Legendary", 30);

        final String color;
        final String title;
        final int levels;

        Rarity(String color, String title, int levels) {
            this.color = color;
            this.title = title;
            this.levels = levels;
        }
    }

    /** Every custom enchantment: id, name, max level, what takes it, where it works, how rare its tome is. */
    public enum Spec {
        LIFESTEAL("lifesteal", "Lifesteal", 3, ItemTypeTagKeys.ENCHANTABLE_WEAPON, EquipmentSlotGroup.MAINHAND, Rarity.LEGENDARY,
                "Heals you for 5 % of the damage you deal per level."),
        BLEED("bleed", "Bleed", 2, ItemTypeTagKeys.ENCHANTABLE_WEAPON, EquipmentSlotGroup.MAINHAND, Rarity.RARE,
                "12 % chance per level to make the target bleed (½ heart a second for 3-4 s)."),
        EXECUTE("execute", "Execute", 3, ItemTypeTagKeys.ENCHANTABLE_WEAPON, EquipmentSlotGroup.MAINHAND, Rarity.RARE,
                "+6 % damage per level against targets under 30 % health."),
        CRIPPLE("cripple", "Cripple", 2, ItemTypeTagKeys.ENCHANTABLE_WEAPON, EquipmentSlotGroup.MAINHAND, Rarity.COMMON,
                "12 % chance per level to slow the target for 2 s."),
        WISDOM("wisdom", "Wisdom", 3, ItemTypeTagKeys.ENCHANTABLE_WEAPON, EquipmentSlotGroup.MAINHAND, Rarity.COMMON,
                "+25 % experience per level from the mobs you kill."),
        OVERLOAD("overload", "Overload", 3, ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST, Rarity.LEGENDARY,
                "+1 heart of max health per level."),
        DODGE("dodge", "Dodge", 3, ItemTypeTagKeys.ENCHANTABLE_LEG_ARMOR, EquipmentSlotGroup.LEGS, Rarity.RARE,
                "A 3 / 5 / 7 % chance to dodge a melee hit completely."),
        IMPLANTS("implants", "Implants", 2, ItemTypeTagKeys.ENCHANTABLE_HEAD_ARMOR, EquipmentSlotGroup.HEAD, Rarity.COMMON,
                "Slowly fills your hunger (a point every 10 / 5 s)."),
        SPRINGS("springs", "Springs", 3, ItemTypeTagKeys.ENCHANTABLE_FOOT_ARMOR, EquipmentSlotGroup.FEET, Rarity.COMMON,
                "Jump higher and take less fall damage."),
        WARDING("warding", "Warding", 3, ItemTypeTagKeys.ENCHANTABLE_ARMOR, EquipmentSlotGroup.ARMOR, Rarity.LEGENDARY,
                "-2.5 % damage from legendary weapons per level (all pieces add up, at most -30 %)."),
        GROUNDED("grounded", "Grounded", 2, ItemTypeTagKeys.ENCHANTABLE_FOOT_ARMOR, EquipmentSlotGroup.FEET, Rarity.RARE,
                "-20 % damage and -40 % knockback from maces per level."),
        CLEANSE("cleanse", "Cleanse", 2, ItemTypeTagKeys.ENCHANTABLE_HEAD_ARMOR, EquipmentSlotGroup.HEAD, Rarity.RARE,
                "Harmful effects and Grievous Wounds last 25 % shorter per level."),
        TELEPATHY("telepathy", "Telepathy", 1, ItemTypeTagKeys.ENCHANTABLE_MINING, EquipmentSlotGroup.MAINHAND, Rarity.COMMON,
                "Mined blocks go straight into your inventory."),
        VEIN_MINER("vein_miner", "Vein Miner", 1, ItemTypeTagKeys.ENCHANTABLE_MINING, EquipmentSlotGroup.MAINHAND, Rarity.RARE,
                "Breaking an ore breaks the whole vein (up to 16 blocks, costs durability).");

        public final String id;
        public final String name;
        public final int max;
        final TagKey<org.bukkit.inventory.ItemType> items;
        final EquipmentSlotGroup slot;
        public final Rarity rarity;
        public final String description;

        Spec(String id, String name, int max, TagKey<org.bukkit.inventory.ItemType> items, EquipmentSlotGroup slot,
             Rarity rarity, String description) {
            this.id = id;
            this.name = name;
            this.max = max;
            this.items = items;
            this.slot = slot;
            this.rarity = rarity;
            this.description = description;
        }

        public Key key() {
            return Key.key("forgebound", id);
        }
    }

    /** Called from {@link ForgeboundBootstrap}: registers them (in no tag, so nothing hands them out but the tomes). */
    static void register(RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event) {
        for (Spec s : Spec.values()) {
            TypedKey<Enchantment> key = EnchantmentKeys.create(s.key());
            event.registry().register(key, b -> b
                    .description(Component.translatable("enchantment.forgebound." + s.id, s.name))
                    .supportedItems(event.getOrCreateTag(s.items))
                    .weight(1)
                    .maxLevel(s.max)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(30, 0))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(60, 0))
                    .anvilCost(s.rarity == Rarity.LEGENDARY ? 8 : s.rarity == Rarity.RARE ? 4 : 2)
                    .activeSlots(s.slot));
        }
    }

    private final Forgebound plugin;
    private final Map<Spec, Enchantment> ench = new HashMap<>();
    private final NamespacedKey tomeKey;
    private final NamespacedKey overloadKey;
    private final NamespacedKey springsKey;
    private final NamespacedKey springsFallKey;
    private final Set<UUID> veinMining = new HashSet<>();
    private final Set<UUID> cleansing = new HashSet<>();
    private int ticks;

    public CustomEnchants(Forgebound plugin) {
        this.plugin = plugin;
        this.tomeKey = new NamespacedKey(plugin, "sealed_tome");
        this.overloadKey = new NamespacedKey(plugin, "overload");
        this.springsKey = new NamespacedKey(plugin, "springs");
        this.springsFallKey = new NamespacedKey(plugin, "springs_fall");
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (Spec s : Spec.values()) {
            Enchantment e = registry.get(s.key());
            if (e != null) {
                ench.put(s, e);
            }
        }
        if (ench.size() < Spec.values().length) {
            plugin.getLogger().warning("Some custom enchantments are missing (restart the server after updating).");
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks++;
            for (Player p : Bukkit.getOnlinePlayers()) {
                int implants = level(p.getInventory().getHelmet(), Spec.IMPLANTS);
                if (implants > 0 && ticks % (implants >= 2 ? 1 : 2) == 0 && p.getFoodLevel() < 20) {
                    p.setFoodLevel(p.getFoodLevel() + 1);
                }
            }
        }, 100L, 100L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("custom-enchants.enabled", false);
    }

    public Enchantment get(Spec s) {
        return ench.get(s);
    }

    private int level(ItemStack item, Spec s) {
        Enchantment e = ench.get(s);
        return e == null || item == null || item.isEmpty() ? 0 : item.getEnchantmentLevel(e);
    }

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /** Melee attacker of this hit (arrows don't count), or null. */
    private static Player melee(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Player p && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK ? p : null;
    }

    // ================================================================ weapons

    /**
     * Offensive enchantments only work on mobs and on players who carry a legend: they are for hunting legends,
     * never an edge against players without one.
     */
    private boolean fairTarget(LivingEntity target) {
        if (!(target instanceof Player p)) {
            return true;
        }
        for (ItemStack it : p.getInventory().getContents()) {
            ItemManager.Entry e = it == null || it.isEmpty() ? null : plugin.items().customOf(it);
            if (e != null && e.kind != ItemManager.Kind.MATERIAL) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitModify(EntityDamageByEntityEvent event) {
        if (!on() || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player attacker = melee(event);
        if (attacker != null && fairTarget(target)) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            int execute = level(weapon, Spec.EXECUTE);
            if (execute > 0) {
                AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
                if (max != null && target.getHealth() <= max.getValue() * 0.3) {
                    event.setDamage(event.getDamage() * (1 + 0.06 * execute));
                }
            }
        }
        // the victim's armor
        if (target instanceof Player victim) {
            Entity source = event.getDamager() instanceof AbstractArrow a && a.getShooter() instanceof Entity s ? s : event.getDamager();
            int dodge = level(victim.getInventory().getLeggings(), Spec.DODGE);
            if (dodge > 0 && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && roll(0.01 + 0.02 * dodge)) {
                event.setCancelled(true);
                victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.6f);
                victim.sendActionBar(ItemManager.color("&b✦ Dodged!"));
                return;
            }
            if (source instanceof Player hitter) {
                ItemStack held = hitter.getInventory().getItemInMainHand();
                // Warding: less damage from legendary weapons (all armor pieces add up)
                if (plugin.items().customOf(held) != null && held.getType() != Material.AIR) {
                    int warding = 0;
                    for (ItemStack piece : victim.getInventory().getArmorContents()) {
                        warding += level(piece, Spec.WARDING);
                    }
                    if (warding > 0) {
                        event.setDamage(event.getDamage() * (1 - Math.min(0.30, 0.025 * warding)));
                    }
                }
                // Grounded: less damage and knockback from maces
                int grounded = level(victim.getInventory().getBoots(), Spec.GROUNDED);
                if (grounded > 0 && held.getType() == Material.MACE) {
                    event.setDamage(event.getDamage() * (1 - 0.2 * grounded));
                    double keep = Math.max(0, 1 - 0.4 * grounded);
                    Bukkit.getScheduler().runTask(plugin, () -> victim.setVelocity(victim.getVelocity().multiply(
                            new org.bukkit.util.Vector(keep, keep, keep))));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitDone(EntityDamageByEntityEvent event) {
        if (!on() || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player attacker = melee(event);
        if (attacker == null || !fairTarget(target)) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        int lifesteal = level(weapon, Spec.LIFESTEAL);
        if (lifesteal > 0 && event.getFinalDamage() > 0) {
            AttributeInstance max = attacker.getAttribute(Attribute.MAX_HEALTH);
            double heal = event.getFinalDamage() * 0.05 * lifesteal;
            if (plugin.relics() != null) {
                heal = plugin.relics().adjustHeal(attacker, heal);
            }
            if (max != null && heal > 0) {
                attacker.setHealth(Math.min(max.getValue(), attacker.getHealth() + heal));
            }
        }
        int bleed = level(weapon, Spec.BLEED);
        if (bleed > 0 && roll(0.12 * bleed)) {
            bleed(target, 2 + bleed);
        }
        int cripple = level(weapon, Spec.CRIPPLE);
        if (cripple > 0 && roll(0.12 * cripple)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, cripple - 1, false, true, true));
        }
    }

    private void bleed(LivingEntity target, int seconds) {
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(150, 0, 10), 1.2f);
        int[] left = {seconds};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (left[0]-- <= 0 || !target.isValid() || target.isDead()) {
                task.cancel();
                return;
            }
            DamageGuard.run(() -> target.damage(1.0));
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() * 0.6, 0),
                    6, 0.25, 0.3, 0.25, 0, red);
        }, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (!on() || killer == null || event.getEntity() instanceof Player) {
            return;
        }
        int wisdom = level(killer.getInventory().getItemInMainHand(), Spec.WISDOM);
        if (wisdom > 0) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1 + 0.25 * wisdom)));
        }
    }

    // ================================================================ armor

    @EventHandler
    public void onArmor(PlayerArmorChangeEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refresh(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    /** Overload (max health) and Springs (jump + safe fall) as attribute modifiers on the player. */
    private void refresh(Player p) {
        int overload = on() ? level(p.getInventory().getChestplate(), Spec.OVERLOAD) : 0;
        int springs = on() ? level(p.getInventory().getBoots(), Spec.SPRINGS) : 0;
        modifier(p, Attribute.MAX_HEALTH, overloadKey, 2.0 * overload);
        modifier(p, Attribute.JUMP_STRENGTH, springsKey, 0.06 * springs);
        modifier(p, Attribute.SAFE_FALL_DISTANCE, springsFallKey, 1.0 * springs);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && p.getHealth() > max.getValue()) {
            p.setHealth(max.getValue());
        }
    }

    private static void modifier(Player p, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        AttributeModifier old = inst.getModifier(key);
        if (old != null && old.getAmount() == amount) {
            return;
        }
        if (old != null) {
            inst.removeModifier(key);
        }
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /** Cleanse: harmful effects last shorter. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!on() || !(event.getEntity() instanceof Player p) || event.getNewEffect() == null
                || cleansing.contains(p.getUniqueId())) {
            return;
        }
        PotionEffect effect = event.getNewEffect();
        if (effect.getType().getCategory() != PotionEffectTypeCategory.HARMFUL || effect.isInfinite()) {
            return;
        }
        double factor = cleanseFactor(p);
        if (factor >= 1) {
            return;
        }
        event.setCancelled(true);
        PotionEffect shorter = effect.withDuration(Math.max(1, (int) (effect.getDuration() * factor)));
        Bukkit.getScheduler().runTask(plugin, () -> {
            cleansing.add(p.getUniqueId());
            try {
                p.addPotionEffect(shorter);
            } finally {
                cleansing.remove(p.getUniqueId());
            }
        });
    }

    /** How much of a debuff (and Grievous Wounds) is left after Cleanse: 1, 0.75 or 0.5. */
    public double cleanseFactor(Player p) {
        return on() ? 1 - 0.25 * level(p.getInventory().getHelmet(), Spec.CLEANSE) : 1;
    }

    // ================================================================ tools

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player p = event.getPlayer();
        if (!on() || level(p.getInventory().getItemInMainHand(), Spec.TELEPATHY) <= 0) {
            return;
        }
        for (java.util.Iterator<Item> it = event.getItems().iterator(); it.hasNext(); ) {
            Item drop = it.next();
            Map<Integer, ItemStack> left = p.getInventory().addItem(drop.getItemStack());
            if (left.isEmpty()) {
                it.remove();
            } else {
                drop.setItemStack(left.values().iterator().next());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!on() || veinMining.contains(p.getUniqueId())) {
            return;
        }
        ItemStack tool = p.getInventory().getItemInMainHand();
        Material type = event.getBlock().getType();
        if (level(tool, Spec.VEIN_MINER) <= 0 || !type.name().endsWith("_ORE") || p.isSneaking()) {
            return;
        }
        List<Block> vein = new ArrayList<>();
        ArrayDeque<Block> queue = new ArrayDeque<>(List.of(event.getBlock()));
        Set<Block> seen = new HashSet<>(queue);
        while (!queue.isEmpty() && vein.size() < 16) {
            Block b = queue.poll();
            for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block n = b.getRelative(f);
                if (n.getType() == type && seen.add(n)) {
                    vein.add(n);
                    queue.add(n);
                }
            }
        }
        if (vein.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            veinMining.add(p.getUniqueId());
            try {
                for (Block b : vein) {
                    ItemStack now = p.getInventory().getItemInMainHand();
                    if (now.isEmpty() || level(now, Spec.VEIN_MINER) <= 0 || b.getType() != type) {
                        break;
                    }
                    p.breakBlock(b);
                }
            } finally {
                veinMining.remove(p.getUniqueId());
            }
        });
    }

    // ================================================================ no custom enchantments on legends

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getFirstItem();
        ItemStack result = event.getResult();
        if (base == null || result == null || result.isEmpty() || plugin.items().customOf(base) == null) {
            return;
        }
        for (Enchantment e : ench.values()) {
            if (result.getEnchantmentLevel(e) > base.getEnchantmentLevel(e)) {
                event.setResult(null);
                return;
            }
        }
    }

    // ================================================================ Sealed Tomes

    public ItemStack tome(Rarity rarity) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemManager.color(rarity.color + "&l" + rarity.title + " Sealed Tome").decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ItemManager.color("&7An enchantment no table knows.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                ItemManager.color("&7Right-click with &e" + rarity.levels + " levels &7to break the seal.").decoration(
                        net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(tomeKey, PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        return item;
    }

    private Rarity tomeOf(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) {
            return null;
        }
        String r = item.getItemMeta().getPersistentDataContainer().get(tomeKey, PersistentDataType.STRING);
        try {
            return r == null ? null : Rarity.valueOf(r);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Rarity rarity = tomeOf(hand);
        if (rarity == null) {
            return;
        }
        event.setCancelled(true);
        int need = plugin.getConfig().getInt("custom-enchants.open-levels." + rarity.name().toLowerCase(), rarity.levels);
        if (p.getLevel() < need && p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            p.sendMessage(ItemManager.color("&7The seal holds. You need &e" + need + " levels&7."));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 0.8f);
            return;
        }
        List<Spec> pool = new ArrayList<>();
        for (Spec s : Spec.values()) {
            if (s.rarity == rarity && ench.containsKey(s)
                    && plugin.getConfig().getBoolean("custom-enchants.list." + s.id, true)) {
                pool.add(s);
            }
        }
        if (pool.isEmpty()) {
            p.sendMessage(ItemManager.color("&7This tome is empty (all its enchantments are turned off)."));
            return;
        }
        Spec spec = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        // lower levels are more common: level k has weight (max - k + 1)
        int total = spec.max * (spec.max + 1) / 2;
        int roll = ThreadLocalRandom.current().nextInt(total);
        int level = 1;
        for (int k = 1; k <= spec.max; k++) {
            roll -= spec.max - k + 1;
            if (roll < 0) {
                level = k;
                break;
            }
        }
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            p.setLevel(p.getLevel() - need);
        }
        hand.setAmount(hand.getAmount() - 1);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(ench.get(spec), level, true);
        book.setItemMeta(meta);
        for (ItemStack left : p.getInventory().addItem(book).values()) {
            p.getWorld().dropItem(p.getLocation(), left);
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.5, 0), 80, 0.6, 0.6, 0.6, 1);
        String roman = new String[]{"I", "II", "III", "IV", "V"}[Math.min(4, level - 1)];
        p.sendMessage(ItemManager.color(rarity.color + "✦ &7The seal breaks: " + rarity.color + "&l" + spec.name
                + (spec.max > 1 ? " " + roman : "") + " &8- &7" + spec.description));
        plugin.getLogger().info("[Enchants] " + p.getName() + " opened a " + rarity.title + " tome: " + spec.name + " " + level);
    }

    // ================================================================ loot

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestLoot(LootGenerateEvent event) {
        addTomes(event.getLootTable(), event.getLoot());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVaultLoot(BlockDispenseLootEvent event) {
        List<ItemStack> loot = new ArrayList<>(event.getDispensedLoot());
        if (addTomes(event.getLootTable(), loot)) {
            event.setDispensedLoot(loot);
        }
    }

    private boolean addTomes(org.bukkit.loot.LootTable table, List<ItemStack> loot) {
        if (!on() || table == null) {
            return false;
        }
        var sec = plugin.getConfig().getConfigurationSection("custom-enchants.loot");
        if (sec == null) {
            return false;
        }
        String key = table.getKey().toString();
        for (String k : sec.getKeys(false)) {
            if (!key.equals(sec.getString(k + ".table"))) {
                continue;
            }
            // one tome at most, the rarest that hits
            for (Rarity r : new Rarity[]{Rarity.LEGENDARY, Rarity.RARE, Rarity.COMMON}) {
                if (roll(sec.getDouble(k + "." + r.name().toLowerCase(), 0) / 100.0)) {
                    loot.add(tome(r));
                    return true;
                }
            }
            return false;
        }
        return false;
    }
}
