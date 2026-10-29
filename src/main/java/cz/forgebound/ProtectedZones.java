package cz.forgebound;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawn (the Forge Citadel from /forge spawnbuild) and every Ancient Forge can't be griefed for good: blocks can be
 * mined and blown up, but nothing drops, and after protection.regen-minutes every changed block comes back (placed
 * blocks disappear again). A block only comes back when nobody stands in it, and a block under someone is only
 * taken away when nobody is above it, so the regeneration never suffocates or drops anyone.
 */
public final class ProtectedZones implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Forgebound plugin;
    private final File file;
    private final YamlConfiguration data;
    /** "world;x;y;z" -> the original block (data string, or "sign|data|l1|l2|l3|l4") and when it was changed */
    private final Map<String, String> original = new LinkedHashMap<>();
    private final Map<String, Long> changedAt = new LinkedHashMap<>();
    private BukkitTask task;
    private boolean dirty;

    public ProtectedZones(Forgebound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "protected-zones.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        var sec = data.getConfigurationSection("regen");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                original.put(k.replace(',', ';'), sec.getString(k + ".block"));
                changedAt.put(k.replace(',', ';'), sec.getLong(k + ".at"));
            }
        }
    }

    private boolean on(String key) {
        return plugin.getConfig().getBoolean("protection.enabled", true) && plugin.getConfig().getBoolean("protection." + key, true);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::regen, 20L * 30, 20L * 30);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        save();
    }

    /** The citadel built by /forge spawnbuild (center = the floor under the admin), or null to forget it. */
    public void setSpawn(World world, int x, int y, int z) {
        if (world == null) {
            data.set("spawn", null);
        } else {
            data.set("spawn.world", world.getName());
            data.set("spawn.x", x);
            data.set("spawn.y", y);
            data.set("spawn.z", z);
        }
        dirty = true;
        save();
    }

    /** Is this block inside the spawn citadel or an Ancient Forge? */
    public boolean isProtected(Block b) {
        World w = b.getWorld();
        if (on("spawn") && w.getName().equals(data.getString("spawn.world"))) {
            int dx = b.getX() - data.getInt("spawn.x");
            int dz = b.getZ() - data.getInt("spawn.z");
            int dy = b.getY() - data.getInt("spawn.y");
            if (dx * dx + dz * dz <= 36 * 36 && dy >= -4 && dy <= 34) {
                return true;
            }
        }
        if (on("ancient-forges") && plugin.stars() != null) {
            int r = plugin.getConfig().getInt("protection.forge-radius", 15);
            for (int[] a : plugin.stars().forgeAnvils(w)) {
                if (Math.abs(b.getX() - a[0]) <= r && Math.abs(b.getZ() - a[2]) <= r
                        && b.getY() >= a[1] - 8 && b.getY() <= a[1] + 22) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean bypass(Player p) {
        return p != null && p.getGameMode() == GameMode.CREATIVE && p.hasPermission("forgebound.admin");
    }

    private static String id(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    /** Remembers how the block looked before its first change (later changes keep the first one). */
    private void remember(Block b) {
        String id = id(b);
        if (original.containsKey(id)) {
            return;
        }
        String block = b.getBlockData().getAsString();
        if (b.getState() instanceof Sign sign) {
            StringBuilder sb = new StringBuilder("sign|").append(block);
            for (int i = 0; i < 4; i++) {
                sb.append('|').append(LEGACY.serialize(sign.getSide(Side.FRONT).line(i)).replace("|", ""));
            }
            block = sb.toString();
        }
        original.put(id, block);
        changedAt.put(id, System.currentTimeMillis());
        dirty = true;
    }

    // ---------------------------------------------------------------- what can change a protected block

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (bypass(event.getPlayer()) || !isProtected(b)) {
            return;
        }
        if (plugin.stars() != null && plugin.stars().isForgeAnvil(b)) {
            event.setCancelled(true);                      // the Ancient Forge itself stays usable
            return;
        }
        remember(b);
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        if (bypass(event.getPlayer()) || !isProtected(b)) {
            return;
        }
        // the block that was there before (air, water...) comes back: placed blocks don't stay
        String id = id(b);
        if (!original.containsKey(id)) {
            original.put(id, event.getBlockReplacedState().getBlockData().getAsString());
            changedAt.put(id, System.currentTimeMillis());
            dirty = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Block b = event.getBlock();
        if (!bypass(event.getPlayer()) && isProtected(b)) {
            remember(b);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList());
    }

    /** Protected blocks in an explosion: gone for now, nothing drops, back later. */
    private void explode(List<Block> blocks) {
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            if (!isProtected(b)) {
                continue;
            }
            it.remove();
            if (plugin.stars() != null && plugin.stars().isForgeAnvil(b)) {
                continue;
            }
            remember(b);
            b.setType(org.bukkit.Material.AIR, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        // endermen, ravagers, withers... taking blocks away (falling blocks landing are fine)
        if (!(event.getEntity() instanceof org.bukkit.entity.FallingBlock) && !(event.getEntity() instanceof Player)
                && event.getTo().isAir() && isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- regeneration

    private void regen() {
        long due = System.currentTimeMillis() - (long) (plugin.getConfig().getDouble("protection.regen-minutes", 25) * 60_000);
        int restored = 0;
        for (Iterator<Map.Entry<String, Long>> it = changedAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() > due) {
                continue;
            }
            String[] p = e.getKey().split(";");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) {
                it.remove();
                original.remove(e.getKey());
                dirty = true;
                continue;
            }
            int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;                                   // comes back once someone is near it
            }
            String spec = original.get(e.getKey());
            String[] sign = spec.startsWith("sign|") ? spec.split("\\|", -1) : null;
            org.bukkit.block.data.BlockData want = Bukkit.createBlockData(sign != null ? sign[1] : spec);
            Block b = w.getBlockAt(x, y, z);
            if (!safe(b, want)) {
                continue;                                   // someone is in the way: try again in 30 s
            }
            b.setBlockData(want, false);
            if (sign != null && b.getState() instanceof Sign s) {
                for (int i = 0; i < 4 && i + 2 < sign.length; i++) {
                    s.getSide(Side.FRONT).line(i, LEGACY.deserialize(sign[i + 2]));
                }
                s.setWaxed(true);
                s.update(true, false);
            }
            it.remove();
            original.remove(e.getKey());
            dirty = true;
            restored++;
        }
        if (restored > 0) {
            plugin.getLogger().info("[Protect] " + restored + " blocks regenerated");
        }
        if (dirty) {
            save();
        }
    }

    /**
     * Never hurts anyone: a solid block doesn't come back inside a living entity, and a block isn't taken away
     * (or turned into something you'd fall through) while someone stands on or above it.
     */
    private static boolean safe(Block b, org.bukkit.block.data.BlockData want) {
        BoundingBox cell = BoundingBox.of(b).expand(0.3);
        BoundingBox above = new BoundingBox(b.getX() - 1, b.getY(), b.getZ() - 1, b.getX() + 2, b.getY() + 6, b.getZ() + 2);
        for (Entity e : b.getWorld().getNearbyEntities(above.clone().union(cell))) {
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            if (e.getBoundingBox().overlaps(cell)) {
                return false;
            }
            boolean removesSupport = b.getType().isSolid() && !want.getMaterial().isSolid();
            if (removesSupport && e.getBoundingBox().overlaps(above)) {
                return false;
            }
        }
        return true;
    }

    private void save() {
        if (!dirty) {
            return;
        }
        data.set("regen", null);
        for (Map.Entry<String, String> e : original.entrySet()) {
            String k = e.getKey().replace(';', ',');
            data.set("regen." + k + ".block", e.getValue());
            data.set("regen." + k + ".at", changedAt.getOrDefault(e.getKey(), 0L));
        }
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save protected-zones.yml: " + ex.getMessage());
        }
    }

    /** How many blocks are waiting to come back (for /forge status). */
    public int pending() {
        return original.size();
    }
}
