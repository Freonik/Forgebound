package cz.forgebound;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.loot.LootTable;

import java.util.Random;
import java.util.SplittableRandom;

/**
 * Shattered Peaks and the Frozen Vaults under them: a rare 3D biome pair painted onto NEW overworld chunks.
 * - Shattered Peaks (surface): frozen ground (snow, packed ice, a few powder snow traps), frosted trees,
 *   frozen water and jagged shards of packed and blue ice tilted out of the ground.
 * - Frozen Vaults (underground): caves frozen over - packed ice, blue ice, snow and calcite walls, icicles,
 *   frozen water - and now and then a frozen vault hall with a stray spawner and a chest.
 * Runs on the world generation threads: only the LimitedRegion it's given is touched.
 */
public final class ShatteredPeaks implements Listener {

    static final Key PEAKS = Key.key("forgebound", "shattered_peaks");
    static final Key VAULTS = Key.key("forgebound", "frozen_vaults");
    private static final long SALT = 0x5A77E4L;

    private final Forgebound plugin;
    private final Populator populator = new Populator();
    private volatile boolean enabled = true;
    private volatile double rarity = 0.02;
    private volatile double zoneSize = 900;
    private volatile double vaultChance = 0.5;
    private volatile Biome peaks;
    private volatile Biome vaults;
    private volatile LootTable loot;
    private final java.util.Map<Long, ScorchedNoise> noises = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, BlockData> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public ShatteredPeaks(Forgebound plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("shattered-peaks.enabled", true);
        rarity = c.getDouble("shattered-peaks.rarity", 0.025);
        zoneSize = c.getDouble("shattered-peaks.zone-size", 900);
        vaultChance = c.getDouble("shattered-peaks.vault-chance", 0.5);
        noises.clear();
        var reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        peaks = reg.get(PEAKS);
        vaults = reg.get(VAULTS);
        if (peaks == null || vaults == null) {
            plugin.getLogger().warning("Shattered Peaks: the biomes from the Forgebound datapack are missing, nothing will generate.");
        }
        loot = Bukkit.getLootTable(new NamespacedKey("forgebound", "chests/forge_ruin"));
    }

    public void start() {
        load();
        for (World w : Bukkit.getWorlds()) {
            attach(w);
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attach(event.getWorld());
    }

    private void attach(World world) {
        if (world.getEnvironment() == World.Environment.NORMAL && !world.getPopulators().contains(populator)) {
            world.getPopulators().add(populator);
        }
    }

    /** Whether this biome pair generates at all (on, and the datapack biomes are there). */
    boolean on() {
        return enabled && peaks != null;
    }

    ScorchedNoise noise(long seed) {
        return noises.computeIfAbsent(seed, k -> new ScorchedNoise(k ^ SALT, zoneSize, rarity));
    }

    private BlockData data(String s) {
        return cache.computeIfAbsent(s, Bukkit::createBlockData);
    }

    private final class Populator extends BlockPopulator {
        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            if (!enabled || peaks == null || vaults == null) {
                return;
            }
            try {
                generate(info, chunkX, chunkZ, region);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Shattered Peaks at chunk " + chunkX + "," + chunkZ + ": " + ex);
            }
        }
    }

    private static double hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ (y * 0x9E3779B97F4A7C15L);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    private void generate(WorldInfo info, int chunkX, int chunkZ, LimitedRegion r) {
        ScorchedNoise noise = noise(info.getSeed());
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        boolean near = false;
        for (int[] p : new int[][]{{0, 0}, {15, 0}, {0, 15}, {15, 15}, {8, 8}}) {
            if (noise.value(bx + p[0], bz + p[1]) > noise.threshold - 0.02) {
                near = true;
                break;
            }
        }
        if (!near) {
            return;
        }
        // the other biomes win where they overlap
        long seed = info.getSeed();
        ScorchedNoise scorch = plugin.scorched() != null ? plugin.scorched().noise(seed) : null;
        ScorchedNoise crags = plugin.emberCrags() != null ? plugin.emberCrags().noise(seed) : null;
        ScorchedNoise glass = plugin.glassedPlains() != null ? plugin.glassedPlains().noise(seed) : null;
        ScorchedNoise ash = plugin.ashenForest() != null ? plugin.ashenForest().noise(seed) : null;
        SplittableRandom rnd = new SplittableRandom(seed ^ SALT ^ (chunkX * 341873128712L + chunkZ * 132897987541L));
        int[][] surface = new int[16][16];
        double[][] strength = new double[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                surface[x][z] = Integer.MIN_VALUE;
                double s = noise.strength(bx + x, bz + z);
                strength[x][z] = s;
                int wx = bx + x, wz = bz + z;
                if (s <= 0 || s < 1 && rnd.nextDouble() > s
                        || scorch != null && scorch.inside(wx, wz) || crags != null && crags.inside(wx, wz)
                        || glass != null && glass.inside(wx, wz) || ash != null && ash.inside(wx, wz)) {
                    continue;
                }
                surface[x][z] = surfaceColumn(info, r, wx, wz, rnd, s);
                if (surface[x][z] != Integer.MIN_VALUE && s > 0.35) {
                    vaultsColumn(info, r, wx, wz, surface[x][z]);
                }
            }
        }
        vault(info, r, chunkX, chunkZ, strength, surface);
    }

    // ---------------------------------------------------------------- the surface

    private int surfaceColumn(WorldInfo info, LimitedRegion r, int x, int z, SplittableRandom rnd, double s) {
        int min = info.getMinHeight();
        int top = Integer.MIN_VALUE;
        Material m = Material.AIR;
        for (int y = info.getMaxHeight() - 1; y > min; y--) {
            if (!r.isInRegion(x, y, z)) {
                return Integer.MIN_VALUE;
            }
            m = r.getType(x, y, z);
            if (m.isAir() || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || !m.isSolid() && m != Material.WATER && m != Material.LAVA) {
                continue;
            }
            top = y;
            break;
        }
        if (top == Integer.MIN_VALUE || m == Material.LAVA) {
            return Integer.MIN_VALUE;
        }
        long seed = info.getSeed();
        if (m == Material.WATER) {
            // the water freezes over (a thick layer, so the lakes and rivers become ice fields)
            for (int y = top; y > top - 3 && r.isInRegion(x, y, z) && r.getType(x, y, z) == Material.WATER; y--) {
                r.setType(x, y, z, y == top && hash(seed, x, y, z) < 0.15 ? Material.PACKED_ICE : Material.ICE);
            }
            paintSurfaceBiome(info, r, x, z, top);
            return top;
        }
        // the plants above freeze off, leaves get a snow cover
        boolean hadTree = false;
        for (int y = top + 1; y < Math.min(info.getMaxHeight(), top + 40); y++) {
            Material a = r.getType(x, y, z);
            if (a.isAir()) {
                continue;
            }
            if (Tag.LOGS.isTagged(a)) {
                hadTree = true;
            } else if (Tag.LEAVES.isTagged(a)) {
                if (r.isInRegion(x, y + 1, z) && r.getType(x, y + 1, z).isAir() && hash(seed, x, y, z) < 0.7) {
                    r.setType(x, y + 1, z, Material.SNOW);
                }
            } else if (!a.isSolid() && a != Material.WATER && a != Material.LAVA && a != Material.SNOW) {
                r.setType(x, y, z, Material.AIR);
            }
        }
        boolean natural = Tag.DIRT.isTagged(m) || m == Material.MOSS_BLOCK || m == Material.SNOW_BLOCK || m == Material.MUD
                || m == Material.SAND || m == Material.GRAVEL || Tag.BASE_STONE_OVERWORLD.isTagged(m);
        if (natural) {
            double p = hash(seed, x >> 2, 5, z >> 2);
            boolean stone = Tag.BASE_STONE_OVERWORLD.isTagged(m);
            Material ground = p < 0.4 ? Material.SNOW_BLOCK : p < 0.6 ? Material.PACKED_ICE : p < 0.72 ? (stone ? Material.CALCITE : Material.SNOW_BLOCK)
                    : p < 0.8 ? Material.BLUE_ICE : stone ? m : Material.SNOW_BLOCK;
            // a rare powder snow trap: looks like snow, you sink in
            if (ground == Material.SNOW_BLOCK && hash(seed, x, top, z) < 0.012) {
                for (int y = top; y > top - 3 && r.isInRegion(x, y, z); y--) {
                    r.setType(x, y, z, Material.POWDER_SNOW);
                }
            } else {
                r.setType(x, top, z, ground);
                if (ground == Material.SNOW_BLOCK && r.isInRegion(x, top - 1, z) && Tag.DIRT.isTagged(r.getType(x, top - 1, z))) {
                    r.setType(x, top - 1, z, Material.SNOW_BLOCK);
                }
            }
            if (r.isInRegion(x, top + 1, z) && r.getType(x, top + 1, z).isAir() && ground != Material.BLUE_ICE && ground != Material.PACKED_ICE) {
                double c = hash(seed, x, top + 1, z);
                r.setBlockData(x, top + 1, z, data("minecraft:snow[layers=" + (1 + (int) (c * 3)) + "]"));
            }
            // shards of ice out of the ground
            if (!hadTree && s > 0.45 && rnd.nextInt(160) == 0) {
                shard(r, x, top, z, rnd);
            }
        }
        paintSurfaceBiome(info, r, x, z, top);
        return top;
    }

    private void paintSurfaceBiome(WorldInfo info, LimitedRegion r, int x, int z, int top) {
        for (int y = Math.max(info.getMinHeight(), top - 8); y < Math.min(info.getMaxHeight(), top + 48); y += 4) {
            r.setBiome(x, y, z, peaks);
        }
    }

    /** A jagged shard of ice: a tilted, tapering spike of packed ice with a blue ice core. */
    private void shard(LimitedRegion r, int x, int ground, int z, SplittableRandom rnd) {
        int h = 6 + rnd.nextInt(9);
        double tx = (rnd.nextDouble() - 0.5) * 0.8, tz = (rnd.nextDouble() - 0.5) * 0.8;
        double radius = 1.6 + rnd.nextDouble() * 0.8;
        for (int i = -2; i <= h; i++) {
            double t = Math.max(0, i) / (double) h;
            double rad = radius * (1 - t) + 0.3;
            double cx = x + tx * Math.max(0, i), cz = z + tz * Math.max(0, i);
            int y = ground + i;
            int ri = (int) Math.ceil(rad);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    int bx = (int) Math.round(cx) + dx, bz = (int) Math.round(cz) + dz;
                    double d = Math.hypot(bx - cx, bz - cz);
                    if (d > rad || !r.isInRegion(bx, y, bz)) {
                        continue;
                    }
                    Material at = r.getType(bx, y, bz);
                    if (i > 0 && !at.isAir() && at != Material.SNOW) {
                        continue;
                    }
                    r.setType(bx, y, bz, d < rad * 0.45 && i < h - 2 ? Material.BLUE_ICE : Material.PACKED_ICE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the Frozen Vaults

    private static boolean rock(Material m) {
        return Tag.BASE_STONE_OVERWORLD.isTagged(m) || m == Material.DIRT || m == Material.GRAVEL || m == Material.CLAY
                || m == Material.COBBLED_DEEPSLATE || m == Material.DRIPSTONE_BLOCK;
    }

    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /** The caves under the peaks: icy walls, icicles, snow on the floor and frozen water. */
    private void vaultsColumn(WorldInfo info, LimitedRegion r, int x, int z, int surface) {
        int min = info.getMinHeight();
        int bottom = Math.max(min + 6, -40);
        int top = surface - 8;
        long seed = info.getSeed();
        for (int y = bottom; y <= top; y++) {
            Material m = r.getType(x, y, z);
            if (m == Material.WATER) {
                if (r.isInRegion(x, y + 1, z) && r.getType(x, y + 1, z).isAir()) {
                    r.setType(x, y, z, Material.ICE);                  // underground lakes freeze on top
                }
                continue;
            }
            if (m == Material.LAVA) {
                r.setType(x, y, z, Material.OBSIDIAN);
                continue;
            }
            if (!rock(m)) {
                continue;
            }
            BlockFace open = openFace(r, x, y, z);
            if (open == null) {
                continue;
            }
            double h = hash(seed, x, y, z);
            double patch = hash(seed, x >> 2, y >> 2, z >> 2);
            r.setType(x, y, z, patch < 0.4 ? Material.PACKED_ICE : patch < 0.6 ? Material.SNOW_BLOCK
                    : patch < 0.75 ? Material.CALCITE : patch < 0.88 ? Material.BLUE_ICE : Material.ICE);
            int ox = x + open.getModX(), oy = y + open.getModY(), oz = z + open.getModZ();
            if (!r.isInRegion(ox, oy, oz) || !r.getType(ox, oy, oz).isAir()) {
                continue;
            }
            if (open == BlockFace.DOWN && h < 0.18) {
                icicle(r, ox, oy, oz, 1 + (int) (hash(seed, x, y + 7, z) * 3));
            } else if (open == BlockFace.UP && h < 0.35) {
                r.setBlockData(ox, oy, oz, data("minecraft:snow[layers=" + (1 + (int) (h * 6)) + "]"));
            } else if (open == BlockFace.DOWN && h > 0.975) {
                // an ice pillar from the ceiling down to the floor
                for (int py = oy; py > bottom && r.isInRegion(ox, py, oz) && r.getType(ox, py, oz).isAir(); py--) {
                    r.setType(ox, py, oz, Material.PACKED_ICE);
                }
            }
        }
        for (int y = bottom - (bottom & 3); y <= top; y += 4) {
            r.setBiome(x, y, z, vaults);
        }
    }

    /** An icicle hanging from the ceiling: one to three blocks of ice. */
    private void icicle(LimitedRegion r, int x, int y, int z, int len) {
        for (int i = 0; i < len && r.isInRegion(x, y - i, z) && r.getType(x, y - i, z).isAir(); i++) {
            r.setType(x, y - i, z, i == 0 ? Material.PACKED_ICE : Material.ICE);
        }
    }

    private static BlockFace openFace(LimitedRegion r, int x, int y, int z) {
        for (BlockFace f : FACES) {
            int ax = x + f.getModX(), ay = y + f.getModY(), az = z + f.getModZ();
            if (r.isInRegion(ax, ay, az)) {
                Material a = r.getType(ax, ay, az);
                if (a.isAir() || a == Material.WATER) {
                    return f;
                }
            }
        }
        return null;
    }

    /** One possible frozen vault per 6x6-chunk cell, deep inside: an ice hall with pillars, a stray spawner and a chest. */
    private void vault(WorldInfo info, LimitedRegion r, int chunkX, int chunkZ, double[][] strength, int[][] surface) {
        if (strength[8][8] < 0.8 || surface[8][8] == Integer.MIN_VALUE) {
            return;
        }
        int cellX = Math.floorDiv(chunkX, 6);
        int cellZ = Math.floorDiv(chunkZ, 6);
        SplittableRandom cell = new SplittableRandom(info.getSeed() ^ SALT ^ (cellX * 73856093L + cellZ * 19349663L));
        if (Math.floorMod(chunkX, 6) != 1 + cell.nextInt(4) || Math.floorMod(chunkZ, 6) != 1 + cell.nextInt(4)
                || cell.nextDouble() >= vaultChance) {
            return;
        }
        int cx = (chunkX << 4) + 8;
        int cz = (chunkZ << 4) + 8;
        int cy = Math.max(info.getMinHeight() + 14, Math.min(0, surface[8][8] - 30));
        long seed = info.getSeed();
        int rx = 10, ry = 6, rz = 10;
        for (int x = cx - rx - 1; x <= cx + rx + 1; x++) {
            for (int z = cz - rz - 1; z <= cz + rz + 1; z++) {
                for (int y = cy - ry - 1; y <= cy + ry + 1; y++) {
                    if (!r.isInRegion(x, y, z)) {
                        continue;
                    }
                    double dx = (x - cx) / (double) rx, dy = (y - cy) / (double) ry, dz = (z - cz) / (double) rz;
                    double d = dx * dx + dy * dy + dz * dz + 0.12 * (hash(seed, x >> 1, y >> 1, z >> 1) - 0.5);
                    if (d <= 1) {
                        r.setType(x, y, z, y <= cy - ry + 1 ? (hash(seed, x, y, z) < 0.6 ? Material.BLUE_ICE : Material.PACKED_ICE) : Material.AIR);
                    } else if (d <= 1.4 && (rock(r.getType(x, y, z)) || r.getType(x, y, z) == Material.WATER)) {
                        r.setType(x, y, z, hash(seed, x, y, z) < 0.5 ? Material.PACKED_ICE : Material.SNOW_BLOCK);
                    }
                    if (r.isInRegion(x, y, z)) {
                        r.setBiome(x, y, z, vaults);
                    }
                }
            }
        }
        int floor = cy - ry + 2;
        // ice pillars around, icicles from the ceiling
        for (int[] p : new int[][]{{-6, -5}, {5, -6}, {-5, 6}, {6, 5}}) {
            for (int y = floor; y <= cy + ry; y++) {
                for (int[] o : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                    int px = cx + p[0] + o[0], pz = cz + p[1] + o[1];
                    if (r.isInRegion(px, y, pz)) {
                        r.setType(px, y, pz, (y - floor) % 4 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE);
                    }
                }
            }
        }
        for (int x = cx - rx + 2; x <= cx + rx - 2; x++) {
            for (int z = cz - rz + 2; z <= cz + rz - 2; z++) {
                if (hash(seed, x, cy, z) >= 0.12) {
                    continue;
                }
                for (int y = cy + ry; y > cy; y--) {
                    if (r.isInRegion(x, y, z) && r.getType(x, y, z).isAir() && r.isInRegion(x, y + 1, z) && r.getType(x, y + 1, z).isSolid()) {
                        icicle(r, x, y, z, 1 + (int) (hash(seed, x, y, z) * 3));
                        break;
                    }
                }
            }
        }
        // a frozen altar: the spawner on blue ice, the chest next to it
        if (r.isInRegion(cx, floor, cz)) {
            r.setType(cx, floor, cz, Material.SPAWNER);
            if (r.getBlockState(cx, floor, cz) instanceof org.bukkit.block.CreatureSpawner sp) {
                sp.setSpawnedType(org.bukkit.entity.EntityType.STRAY);
                r.setBlockState(cx, floor, cz, sp);
            }
        }
        if (r.isInRegion(cx + 2, floor, cz)) {
            r.setBlockData(cx + 2, floor, cz, data("minecraft:chest[facing=west]"));
            BlockState state = r.getBlockState(cx + 2, floor, cz);
            if (loot != null && state instanceof Chest chest) {
                chest.setLootTable(loot, seed ^ (cx * 31L + cz));
                r.setBlockState(cx + 2, floor, cz, chest);
            }
        }
    }
}
