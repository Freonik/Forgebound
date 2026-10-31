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
 * Ember Crags and the Molten Depths under them: a rare 3D biome pair painted onto NEW overworld chunks (like the
 * Scorched Lands, so it works on top of any terrain datapack).
 * - Ember Crags (surface): barren basalt and blackstone ground with magma veins, basalt spires and small lava pools.
 * - Molten Depths (underground): the existing caves below get basalt / blackstone walls, magma, gold-flecked stone,
 *   lava instead of water, and now and then a carved lava hall with a loot chest on an island.
 * Runs on the world generation threads: only the LimitedRegion it's given is touched.
 */
public final class EmberCrags implements Listener {

    static final Key CRAGS = Key.key("forgebound", "ember_crags");
    static final Key DEPTHS = Key.key("forgebound", "molten_depths");
    private static final long SALT = 0xE3B7C1L;

    private final Forgebound plugin;
    private final Populator populator = new Populator();
    private volatile boolean enabled = true;
    private volatile double rarity = 0.02;
    private volatile double zoneSize = 900;
    private volatile double hallChance = 0.5;
    private volatile Biome crags;
    private volatile Biome depths;
    private volatile LootTable loot;
    private final java.util.Map<Long, ScorchedNoise> noises = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, BlockData> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public EmberCrags(Forgebound plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("ember-crags.enabled", true);
        rarity = c.getDouble("ember-crags.rarity", 0.02);
        zoneSize = c.getDouble("ember-crags.zone-size", 900);
        hallChance = c.getDouble("ember-crags.hall-chance", 0.5);
        noises.clear();
        var reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        crags = reg.get(CRAGS);
        depths = reg.get(DEPTHS);
        if (crags == null || depths == null) {
            plugin.getLogger().warning("Ember Crags: the biomes from the Forgebound datapack are missing, nothing will generate.");
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
        return enabled && crags != null;
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
            if (!enabled || crags == null || depths == null) {
                return;
            }
            try {
                generate(info, chunkX, chunkZ, region);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Ember Crags at chunk " + chunkX + "," + chunkZ + ": " + ex);
            }
        }
    }

    /** A cheap stable hash -> 0..1 for a block position (so the same seed always gives the same blocks). */
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
        // the Scorched Lands win where both would be
        ScorchedLands scorched = plugin.scorched();
        ScorchedNoise scorch = scorched != null ? scorched.noise(info.getSeed()) : null;
        SplittableRandom rnd = new SplittableRandom(info.getSeed() ^ SALT ^ (chunkX * 341873128712L + chunkZ * 132897987541L));
        ScorchedNoise veins = noises.computeIfAbsent(info.getSeed() ^ 0x5EEDL ^ Long.MIN_VALUE,
                k -> new ScorchedNoise(info.getSeed() ^ 0x5EEDL, 60, 0.5));   // magma veins (cached, built once)
        int[][] surface = new int[16][16];
        double[][] strength = new double[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                surface[x][z] = Integer.MIN_VALUE;
                double s = noise.strength(bx + x, bz + z);
                strength[x][z] = s;
                if (s <= 0 || s < 1 && rnd.nextDouble() > s || scorch != null && scorch.inside(bx + x, bz + z)) {
                    continue;
                }
                surface[x][z] = surfaceColumn(info, r, bx + x, bz + z, rnd, veins, s);
                if (surface[x][z] != Integer.MIN_VALUE && s > 0.35) {
                    depthsColumn(info, r, bx + x, bz + z, surface[x][z]);
                }
            }
        }
        lavaPool(info, r, bx, bz, surface, rnd);
        lavaHall(info, r, chunkX, chunkZ, strength, surface);
    }

    // ---------------------------------------------------------------- the surface

    private int surfaceColumn(WorldInfo info, LimitedRegion r, int x, int z, SplittableRandom rnd, ScorchedNoise veins, double s) {
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
        if (top == Integer.MIN_VALUE || m == Material.WATER || m == Material.LAVA || m == Material.ICE) {
            return Integer.MIN_VALUE;
        }
        // barren: no trees, no plants
        for (int y = top + 1; y < Math.min(info.getMaxHeight(), top + 40); y++) {
            Material a = r.getType(x, y, z);
            if (!a.isAir() && (Tag.LOGS.isTagged(a) || Tag.LEAVES.isTagged(a) || !a.isSolid() && a != Material.WATER && a != Material.LAVA)) {
                r.setType(x, y, z, Material.AIR);
            }
        }
        boolean natural = Tag.DIRT.isTagged(m) || Tag.SAND.isTagged(m) || Tag.BASE_STONE_OVERWORLD.isTagged(m)
                || m == Material.GRAVEL || m == Material.SNOW_BLOCK || m == Material.MUD || m == Material.CLAY
                || m == Material.MOSS_BLOCK || m == Material.CALCITE || m == Material.TERRACOTTA;
        if (natural) {
            double v = veins.value(x * 6.0, z * 6.0);
            Material ground;
            if (Math.abs(v) < 0.025) {
                ground = Material.MAGMA_BLOCK;                         // glowing veins across the ground
            } else {
                double p = hash(info.getSeed(), x >> 2, 7, z >> 2);   // patches of 4x4
                ground = p < 0.4 ? Material.BASALT : p < 0.7 ? Material.BLACKSTONE : p < 0.88 ? Material.SMOOTH_BASALT : Material.TUFF;
            }
            r.setType(x, top, z, ground);
            for (int y = top - 1; y >= top - 4 && y > min; y--) {
                Material below = r.getType(x, y, z);
                if (Tag.DIRT.isTagged(below) || Tag.SAND.isTagged(below) || below == Material.GRAVEL || Tag.BASE_STONE_OVERWORLD.isTagged(below)) {
                    r.setType(x, y, z, y > top - 3 ? Material.BLACKSTONE : Material.BASALT);
                }
            }
            // basalt spires
            if (s > 0.7 && rnd.nextInt(170) == 0) {
                int h = 4 + rnd.nextInt(8);
                for (int y = top + 1; y <= top + h && y < info.getMaxHeight(); y++) {
                    r.setType(x, y, z, y == top + h && rnd.nextBoolean() ? Material.MAGMA_BLOCK : Material.BASALT);
                }
            }
        }
        for (int y = Math.max(min, top - 8); y < Math.min(info.getMaxHeight(), top + 48); y += 4) {
            r.setBiome(x, y, z, crags);
        }
        return top;
    }

    /** Maybe one small lava pool with a magma rim in this chunk, where the ground is flat. */
    private void lavaPool(WorldInfo info, LimitedRegion r, int bx, int bz, int[][] surface, SplittableRandom rnd) {
        if (rnd.nextInt(4) != 0) {
            return;
        }
        int cx = 4 + rnd.nextInt(8);
        int cz = 4 + rnd.nextInt(8);
        int y = surface[cx][cz];
        if (y == Integer.MIN_VALUE) {
            return;
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int s = surface[cx + dx][cz + dz];
                if (s == Integer.MIN_VALUE || Math.abs(s - y) > 1) {
                    return;                                         // not flat enough
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.hypot(dx, dz);
                int x = bx + cx + dx;
                int z = bz + cz + dz;
                if (d <= 2.2) {
                    r.setType(x, y, z, Material.LAVA);
                    r.setType(x, y - 1, z, d <= 1.2 ? Material.LAVA : Material.MAGMA_BLOCK);
                    r.setType(x, y - 2, z, Material.BLACKSTONE);
                    for (int up = 1; up <= 3; up++) {
                        r.setType(x, y + up, z, Material.AIR);
                    }
                } else if (d <= 3.2) {
                    r.setType(x, y, z, Material.MAGMA_BLOCK);
                }
            }
        }
    }

    // ---------------------------------------------------------------- the Molten Depths

    private static boolean rock(Material m) {
        return Tag.BASE_STONE_OVERWORLD.isTagged(m) || m == Material.DIRT || m == Material.GRAVEL || m == Material.CLAY
                || m == Material.COBBLED_DEEPSLATE || m == Material.CALCITE || m == Material.DRIPSTONE_BLOCK;
    }

    /** The caves under the crags: new walls, magma, gold-flecked stone, lava for water. */
    private void depthsColumn(WorldInfo info, LimitedRegion r, int x, int z, int surface) {
        int min = info.getMinHeight();
        int bottom = Math.max(min + 6, -56);
        int top = surface - 14;
        long seed = info.getSeed();
        for (int y = bottom; y <= top; y++) {
            Material m = r.getType(x, y, z);
            if (m == Material.WATER) {
                r.setType(x, y, z, Material.LAVA);
                continue;
            }
            if (m.isAir()) {
                // basalt stalactites now and then
                if (r.getType(x, y + 1, z).isSolid() && hash(seed, x, y, z) < 0.04) {
                    r.setType(x, y, z, Material.BASALT);
                }
                continue;
            }
            if (!rock(m) || !exposed(r, x, y, z)) {
                continue;
            }
            double h = hash(seed, x, y, z);
            double patch = hash(seed, x >> 2, y >> 2, z >> 2);
            Material wall;
            if (h < 0.035) {
                wall = Material.MAGMA_BLOCK;
            } else if (h < 0.042) {
                wall = Material.GILDED_BLACKSTONE;                     // a little gold in the walls
            } else if (h < 0.0445) {
                wall = Material.CRYING_OBSIDIAN;
            } else {
                wall = patch < 0.45 ? Material.BASALT : patch < 0.75 ? Material.BLACKSTONE : patch < 0.9 ? Material.SMOOTH_BASALT
                        : Material.POLISHED_BLACKSTONE;
            }
            r.setType(x, y, z, wall);
        }
        for (int y = bottom - (bottom & 3); y <= top; y += 4) {
            r.setBiome(x, y, z, depths);
        }
    }

    private static boolean exposed(LimitedRegion r, int x, int y, int z) {
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : around) {
            int ax = x + d[0], ay = y + d[1], az = z + d[2];
            if (r.isInRegion(ax, ay, az)) {
                Material a = r.getType(ax, ay, az);
                if (a.isAir() || a == Material.WATER || a == Material.LAVA) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One possible lava hall per 6x6-chunk cell, deep inside the crags: a carved cavern with a lava lake. */
    private void lavaHall(WorldInfo info, LimitedRegion r, int chunkX, int chunkZ, double[][] strength, int[][] surface) {
        if (strength[8][8] < 0.8 || surface[8][8] == Integer.MIN_VALUE) {
            return;
        }
        int cellX = Math.floorDiv(chunkX, 6);
        int cellZ = Math.floorDiv(chunkZ, 6);
        SplittableRandom cell = new SplittableRandom(info.getSeed() ^ SALT ^ (cellX * 73856093L + cellZ * 19349663L));
        if (Math.floorMod(chunkX, 6) != 1 + cell.nextInt(4) || Math.floorMod(chunkZ, 6) != 1 + cell.nextInt(4)
                || cell.nextDouble() >= hallChance) {
            return;
        }
        int cx = (chunkX << 4) + 8;
        int cz = (chunkZ << 4) + 8;
        int cy = Math.max(info.getMinHeight() + 14, Math.min(-12, surface[8][8] - 40));
        int rx = 11, ry = 7, rz = 11;
        long seed = info.getSeed();
        for (int x = cx - rx - 1; x <= cx + rx + 1; x++) {
            for (int z = cz - rz - 1; z <= cz + rz + 1; z++) {
                for (int y = cy - ry - 1; y <= cy + ry + 1; y++) {
                    if (!r.isInRegion(x, y, z)) {
                        continue;
                    }
                    double dx = (x - cx) / (double) rx, dy = (y - cy) / (double) ry, dz = (z - cz) / (double) rz;
                    double wobble = 0.12 * (hash(seed, x >> 1, y >> 1, z >> 1) - 0.5);
                    double d = dx * dx + dy * dy + dz * dz + wobble;
                    if (d <= 1) {
                        int floor = cy - ry + 2;
                        if (y <= floor) {
                            r.setType(x, y, z, y == floor ? Material.LAVA : Material.MAGMA_BLOCK);
                        } else {
                            r.setType(x, y, z, Material.AIR);
                        }
                    } else if (d <= 1.35 && rock(r.getType(x, y, z))) {
                        double h = hash(seed, x, y, z);
                        r.setType(x, y, z, h < 0.05 ? Material.MAGMA_BLOCK : h < 0.06 ? Material.GILDED_BLACKSTONE
                                : h < 0.5 ? Material.BASALT : Material.BLACKSTONE);
                    }
                    if (r.isInRegion(x, y, z)) {
                        r.setBiome(x, y, z, depths);
                    }
                }
            }
        }
        // basalt pillars holding the roof, and an island in the middle with a chest
        int floor = cy - ry + 2;
        for (int[] p : new int[][]{{-6, -5}, {5, -6}, {-5, 6}, {6, 5}}) {
            for (int y = floor - 1; y <= cy + ry; y++) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        if (r.isInRegion(cx + p[0] + dx, y, cz + p[1] + dz)) {
                            r.setType(cx + p[0] + dx, y, cz + p[1] + dz, Material.BASALT);
                        }
                    }
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz <= 5 && r.isInRegion(cx + dx, floor, cz + dz)) {
                    r.setType(cx + dx, floor, cz + dz, Math.abs(dx) + Math.abs(dz) <= 1 ? Material.POLISHED_BLACKSTONE : Material.BLACKSTONE);
                }
            }
        }
        if (r.isInRegion(cx, floor + 1, cz)) {
            r.setBlockData(cx, floor + 1, cz, data("minecraft:chest[facing=south]"));
            BlockState state = r.getBlockState(cx, floor + 1, cz);
            if (loot != null && state instanceof Chest chest) {
                chest.setLootTable(loot, seed ^ (cx * 31L + cz));
                r.setBlockState(cx, floor + 1, cz, chest);
            }
            r.setBlockData(cx + 1, floor + 1, cz, data("minecraft:soul_lantern"));
            r.setBlockData(cx - 1, floor + 1, cz, data("minecraft:soul_lantern"));
        }
    }
}
