package cz.forgebound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Legendary crafting materials: rare drops from bosses / mobs (dropped on the ground where the mob dies)
 * and rare finds in structure chests and vaults.
 * The materials themselves are inert: they can't be placed, eaten, traded, brewed or used in vanilla crafting.
 */
public final class LootManager implements Listener {

    /** Inventories where a material must never go (it would be used up as a vanilla ingredient). */
    private static final Set<InventoryType> BLOCKED = Set.of(InventoryType.MERCHANT, InventoryType.BREWING,
            InventoryType.ANVIL, InventoryType.GRINDSTONE, InventoryType.CARTOGRAPHY, InventoryType.LOOM,
            InventoryType.STONECUTTER, InventoryType.SMITHING, InventoryType.FURNACE, InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER, InventoryType.BEACON, InventoryType.COMPOSTER, InventoryType.CRAFTER);

    private final Forgebound plugin;
    private final ItemManager items;

    private record MobDrop(String material, String entity, double chance, List<String> biomes,
                           double minY, double maxY, boolean broadcast) { }

    private record ChestDrop(String material, String lootTable, double chance, boolean broadcast) { }

    private final List<MobDrop> mobDrops = new ArrayList<>();
    private final List<ChestDrop> chestDrops = new ArrayList<>();

    public LootManager(Forgebound plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    /** Reads the drop tables from the materials section (call after ItemManager.load()). */
    public void load() {
        mobDrops.clear();
        chestDrops.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("materials");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            String ref = "material:" + id.toLowerCase(Locale.ROOT);
            if (items.get(ref) == null) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            for (Map<?, ?> m : s.getMapList("drops")) {
                WeaponAbilities.Params p = params(m);
                Object mob = m.get("mob");
                Object table = m.get("loot-table");
                boolean broadcast = Boolean.parseBoolean(String.valueOf(m.containsKey("broadcast") ? m.get("broadcast") : "true"));
                double chance = Math.min(maxChance(), p.d("chance", 1));
                if (mob != null) {
                    List<String> biomes = m.get("biomes") instanceof List<?> list
                            ? list.stream().map(o -> String.valueOf(o).toLowerCase(Locale.ROOT)).toList() : List.of();
                    mobDrops.add(new MobDrop(ref, String.valueOf(mob).toUpperCase(Locale.ROOT), chance, biomes,
                            p.d("min-y", -10_000), p.d("max-y", 10_000), broadcast));
                } else if (table != null) {
                    String key = String.valueOf(table).toLowerCase(Locale.ROOT);
                    chestDrops.add(new ChestDrop(ref, key.contains(":") ? key : "minecraft:" + key, chance, broadcast));
                } else {
                    plugin.getLogger().warning(ref + ": a drop needs 'mob' or 'loot-table'");
                }
            }
        }
    }

    private static WeaponAbilities.Params params(Map<?, ?> raw) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        raw.forEach((k, v) -> map.put(String.valueOf(k), v));
        return new WeaponAbilities.Params(map);
    }

    private double maxChance() {
        return plugin.getConfig().getDouble("loot-settings.max-chance", 45);
    }

    private boolean roll(double chance) {
        double boost = plugin.events() == null ? 1 : plugin.events().lootMultiplier();
        return ThreadLocalRandom.current().nextDouble(100) < Math.min(maxChance(), chance * boost);
    }

    private void announce(Player player, ItemStack stack, boolean broadcast) {
        if (broadcast && plugin.getConfig().getBoolean("loot-settings.broadcast", true)) {
            Bukkit.broadcast(plugin.msg("material-found", "&5✦ &d{player} &7found {item}&7!",
                    "player", player.getName(), "item", ItemManager.nameOf(stack)));
        } else {
            player.sendMessage(plugin.msg("material-found-private", "&5✦ &7You found {item}&7!",
                    "item", ItemManager.nameOf(stack)));
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
    }

    /**
     * Ender Dragon loot: the material rises out of the dying dragon and slowly spirals down onto the top of the
     * exit portal pillar (where the dragon egg appears), surrounded by effects. Then it hovers there until picked up.
     */
    private void dragonDescent(Location from, ItemStack stack) {
        org.bukkit.World world = from.getWorld();
        Location target;
        org.bukkit.boss.DragonBattle battle = world.getEnderDragonBattle();
        Location portal = battle == null ? null : battle.getEndPortalLocation();
        int px = portal == null ? 0 : portal.getBlockX();
        int pz = portal == null ? 0 : portal.getBlockZ();
        target = new Location(world, px + 0.5, world.getHighestBlockYAt(px, pz) + 2.6, pz + 0.5);   // just above where the egg appears
        Location start = from.clone();
        if (start.getY() < target.getY() + 6) {
            start.setY(target.getY() + 12);
        }
        int duration = (int) (plugin.getConfig().getDouble("loot-settings.dragon-descent-seconds", 10) * 20);
        org.bukkit.entity.Item item = world.dropItem(start, stack, i -> {
            i.setGravity(false);
            i.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            i.setUnlimitedLifetime(true);
            i.setInvulnerable(true);
            i.setGlowing(true);
            i.setCanMobPickup(false);
            i.setPickupDelay(32767);
        });
        world.playSound(start, Sound.BLOCK_BEACON_ACTIVATE, 4f, 1.6f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!item.isValid()) {
                    cancel();
                    return;
                }
                t++;
                if (t <= duration) {
                    double k = t / (double) duration;
                    double ease = 1 - Math.pow(1 - k, 2);                    // slows down near the end
                    double radius = 3.5 * (1 - ease);
                    double angle = t * 0.12;
                    Location pos = start.clone().add(target.clone().subtract(start).toVector().multiply(ease));
                    pos.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    item.teleport(pos);
                    item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    world.spawnParticle(Particle.END_ROD, pos, 2, 0.05, 0.05, 0.05, 0.01);
                    if (t % 2 == 0) {
                        for (int i = 0; i < 3; i++) {
                            double a = angle * 2 + i * 2 * Math.PI / 3;
                            world.spawnParticle(Particle.REVERSE_PORTAL,
                                    pos.clone().add(Math.cos(a) * 0.7, 0.1, Math.sin(a) * 0.7), 1, 0, 0, 0, 0);
                        }
                    }
                    if (t % 6 == 0) {
                        world.spawnParticle(Particle.DRAGON_BREATH, pos, 3, 0.15, 0.15, 0.15, 0.01, 1.0f);
                    }
                    if (t % 20 == 0) {
                        world.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 0.6f + (float) k);
                    }
                    if (t == duration) {
                        item.setPickupDelay(10);
                        world.playSound(pos, Sound.ENTITY_PLAYER_LEVELUP, 2f, 1.4f);
                        world.spawnParticle(Particle.END_ROD, pos, 60, 0.2, 0.2, 0.2, 0.15);
                        world.spawnParticle(Particle.DRAGON_BREATH, pos, 30, 0.6, 0.2, 0.6, 0.02, 1.0f);
                    }
                    return;
                }
                // landed: hover gently above the pillar with a soft aura until somebody picks it up
                Location hover = target.clone().add(0, 0.15 * Math.sin(t * 0.08), 0);
                item.teleport(hover);
                item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                if (t % 4 == 0) {
                    double a = t * 0.1;
                    world.spawnParticle(Particle.END_ROD, hover.clone().add(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6), 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.PORTAL, hover, 4, 0.3, 0.3, 0.3, 0.3);
                }
                if (t > duration + 20 * 60 * 30) {
                    cancel();   // safety stop after 30 minutes (the item itself stays)
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Drops the material on the ground: glowing, can't burn or blow up and never despawns. */
    private void dropProtected(Location at, ItemStack stack) {
        at.getWorld().dropItemNaturally(at, stack, item -> {
            item.setUnlimitedLifetime(true);
            item.setInvulnerable(true);
            item.setGlowing(true);
            item.setCanMobPickup(false);
        });
        at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 0.5, 0), 25, 0.3, 0.5, 0.3, 0.05);
    }

    // ---------------------------------------------------------------- drops

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || mobDrops.isEmpty()) {
            return;   // only player kills count (no mob farms)
        }
        String type = event.getEntity().getType().name();
        Location loc = event.getEntity().getLocation();
        String biome = loc.getBlock().getBiome().getKey().getKey();
        int looting = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        double lootingBonus = plugin.getConfig().getDouble("loot-settings.looting-bonus-percent", 20) / 100.0;
        for (MobDrop drop : mobDrops) {
            if (!drop.entity().equals(type) || loc.getY() < drop.minY() || loc.getY() > drop.maxY()) {
                continue;
            }
            if (!drop.biomes().isEmpty() && !drop.biomes().contains(biome)) {
                continue;
            }
            if (!roll(drop.chance() * (1 + looting * lootingBonus))) {
                continue;
            }
            ItemManager.Entry material = items.get(drop.material());
            if (material == null || !items.craftable(material)) {
                continue;
            }
            ItemStack stack = material.item.clone();
            if (event.getEntity().getType() == org.bukkit.entity.EntityType.ENDER_DRAGON) {
                dragonDescent(loc, stack);   // floats down onto the egg pillar
            } else {
                dropProtected(loc, stack);
            }
            announce(killer, stack, drop.broadcast());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestLoot(LootGenerateEvent event) {
        addLoot(event.getLootTable(), event.getLoot(), event.getEntity() instanceof Player p ? p : null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVaultLoot(BlockDispenseLootEvent event) {
        List<ItemStack> loot = new ArrayList<>(event.getDispensedLoot());
        if (addLoot(event.getLootTable(), loot, event.getPlayer())) {
            event.setDispensedLoot(loot);
        }
    }

    private boolean addLoot(LootTable table, List<ItemStack> loot, Player player) {
        if (table == null || chestDrops.isEmpty() || !plugin.getConfig().getBoolean("loot-settings.chest-drops", false)) {
            return false;
        }
        NamespacedKey key = table.getKey();
        boolean added = false;
        for (ChestDrop drop : chestDrops) {
            if (!drop.lootTable().equals(key.toString()) || !roll(drop.chance())) {
                continue;
            }
            ItemManager.Entry material = items.get(drop.material());
            if (material == null || !items.craftable(material)) {
                continue;
            }
            loot.add(material.item.clone());
            added = true;
            if (player != null) {
                announce(player, material.item, drop.broadcast());
            }
        }
        return added;
    }

    // ---------------------------------------------------------------- materials are inert

    private boolean isMaterial(ItemStack stack) {
        return items.materialOf(stack) != null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && isMaterial(event.getItem())) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (event.getClickedBlock() != null && !event.getClickedBlock().getType().isInteractable()) {
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseOnEntity(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (isMaterial(hand)) {
            event.setCancelled(true);   // no feeding / trading / giving to allays
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (items.customOf(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isMaterial(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (isMaterial(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Custom items never work as vanilla crafting ingredients. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (isMaterial(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryType top = event.getView().getTopInventory().getType();
        if (!BLOCKED.contains(top)) {
            return;
        }
        boolean intoTop = event.getClickedInventory() == event.getView().getTopInventory();
        if ((intoTop && (isMaterial(event.getCursor()) || (event.getHotbarButton() >= 0
                && isMaterial(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())))))
                || (!intoTop && event.isShiftClick() && isMaterial(event.getCurrentItem()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!BLOCKED.contains(event.getView().getTopInventory().getType()) || !isMaterial(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }
}
