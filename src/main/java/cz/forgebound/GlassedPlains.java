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
import org.bukkit.block.data.Directional;
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
 * Glassed Plains and the Crystal Hollows under them: a rare 3D biome pair painted onto NEW overworld chunks.
 * - Glassed Plains (surface): ground fused by an old meteor storm - calcite, sand turned to glass, tinted glass
 *   shards and amethyst growing out of the ground, a few small craters with an obsidian heart.
 * - Crystal Hollows (underground): the caves below get geode walls (calcite, smooth basalt, amethyst) with
 *   crystals on floors, walls and ceilings, and now and then a huge open geode with a chest in the middle.
 * Runs on the world generation threads: only the LimitedRegion it's given is touched.
 */
public final class GlassedPlains implements Listener {

    static final Key PLAINS = Key.key("forgebound", "glassed_plains");
    static final Key HOLLOWS = Key.key("forgebound", "crystal_hollows");
    private static final long SALT = 0x61A55EDL;

    private final Forgebound plugin;
    private final Populator populator = new Populator();
    private volatile boolean enabled = true;
    private volatile double rarity = 0.02;
    private volatile double zoneSize = 900;
    private volatile double geodeChance = 0.5;
    private volatile Biome plains;
    private volatile Biome hollows;
    private volatile LootTable loot;
    private final java.util.Map<Long, ScorchedNoise> noises = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, BlockData> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public GlassedPlains(Forgebound plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("glassed-plains.enabled", true);
        rarity = c.getDouble("glassed-plains.rarity", 0.02);
        zoneSize = c.getDouble("glassed-plains.zone-size", 900);
        geodeChance = c.getDouble("glassed-plains.geode-chance", 0.5);
        noises.clear();
        var reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        plains = reg.get(PLAINS);
        hollows = reg.get(HOLLOWS);
        if (plains == null || hollows == null) {
            plugin.getLogger().warning("Glassed Plains: the biomes from the Forgebound datapack are missing, nothing will generate.");
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

    ScorchedNoise noise(long seed) {
        return noises.computeIfAbsent(seed, k -> new ScorchedNoise(k ^ SALT, zoneSize, rarity));
    }

    private BlockData data(String s) {
        return cache.computeIfAbsent(s, Bukkit::createBlockData);
    }

    private final class Populator extends BlockPopulator {
        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            if (!enabled || plains == null || hollows == null) {
                return;
            }
            try {
                generate(info, chunkX, chunkZ, region);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Glassed Plains at chunk " + chunkX + "," + chunkZ + ": " + ex);
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
        // the Scorched Lands and the Ember Crags win where they overlap
        ScorchedNoise scorch = plugin.scorched() != null ? plugin.scorched().noise(info.getSeed()) : null;
        ScorchedNoise crags = plugin.emberCrags() != null ? plugin.emberCrags().noise(info.getSeed()) : null;
        SplittableRandom rnd = new SplittableRandom(info.getSeed() ^ SALT ^ (chunkX * 341873128712L + chunkZ * 132897987541L));
        int[][] surface = new int[16][16];
        double[][] strength = new double[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                surface[x][z] = Integer.MIN_VALUE;
                double s = noise.strength(bx + x, bz + z);
                strength[x][z] = s;
                if (s <= 0 || s < 1 && rnd.nextDouble() > s
                        || scorch != null && scorch.inside(bx + x, bz + z) || crags != null && crags.inside(bx + x, bz + z)) {
                    continue;
                }
                surface[x][z] = surfaceColumn(info, r, bx + x, bz + z, rnd, s);
                if (surface[x][z] != Integer.MIN_VALUE && s > 0.35) {
                    hollowsColumn(info, r, bx + x, bz + z, surface[x][z]);
                }
            }
        }
        crater(info, r, bx, bz, surface, rnd);
        geode(info, r, chunkX, chunkZ, strength, surface);
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
        if (top == Integer.MIN_VALUE || m == Material.WATER || m == Material.LAVA || m == Material.ICE) {
            return Integer.MIN_VALUE;
        }
        // the storm left nothing standing
        for (int y = top + 1; y < Math.min(info.getMaxHeight(), top + 40); y++) {
            Material a = r.getType(x, y, z);
            if (!a.isAir() && (Tag.LOGS.isTagged(a) || Tag.LEAVES.isTagged(a) || !a.isSolid() && a != Material.WATER && a != Material.LAVA)) {
                r.setType(x, y, z, Material.AIR);
            }
        }
        boolean natural = Tag.DIRT.isTagged(m) || Tag.SAND.isTagged(m) || Tag.BASE_STONE_OVERWORLD.isTagged(m)
                || m == Material.GRAVEL || m == Material.SNOW_BLOCK || m == Material.MUD || m == Material.CLAY
                || m == Material.MOSS_BLOCK || m == Material.TERRACOTTA;
        if (natural) {
            double p = hash(info.getSeed(), x >> 2, 3, z >> 2);         // patches of 4x4
            double h = hash(info.getSeed(), x, 5, z);
            Material ground = p < 0.35 ? Material.CALCITE : p < 0.6 ? Material.SMOOTH_SANDSTONE
                    : p < 0.8 ? Material.SAND : p < 0.92 ? Material.SMOOTH_QUARTZ : Material.TUFF;
            if (h < 0.07) {
                ground = Material.GLASS;                                  // sand fused into glass
            } else if (h < 0.09) {
                ground = Material.TINTED_GLASS;
            }
            r.setType(x, top, z, ground);
            for (int y = top - 1; y >= top - 3 && y > min; y--) {
                Material below = r.getType(x, y, z);
                if (Tag.DIRT.isTagged(below) || below == Material.GRAVEL || below == Material.CLAY) {
                    r.setType(x, y, z, Material.SANDSTONE);
                }
            }
            // crystals growing out of the ground, glass shards standing up
            if (r.getType(x, top + 1, z).isAir() && ground != Material.GLASS && ground != Material.TINTED_GLASS) {
                double c = hash(info.getSeed(), x, top + 1, z);
                if (c < 0.02) {
                    r.setBlockData(x, top + 1, z, data("minecraft:amethyst_cluster[facing=up]"));
                } else if (c < 0.035) {
                    r.setBlockData(x, top + 1, z, data("minecraft:medium_amethyst_bud[facing=up]"));
                } else if (s > 0.7 && c < 0.039) {
                    int hgt = 2 + rnd.nextInt(4);
                    for (int y = top + 1; y <= top + hgt; y++) {
                        r.setType(x, y, z, y == top + hgt ? Material.AMETHYST_BLOCK : Material.TINTED_GLASS);
                    }
                }
            }
        }
        for (int y = Math.max(min, top - 8); y < Math.min(info.getMaxHeight(), top + 48); y += 4) {
            r.setBiome(x, y, z, plains);
        }
        return top;
    }

    /** Maybe a small impact crater in this chunk: a bowl of glass and calcite around an obsidian heart. */
    private void crater(WorldInfo info, LimitedRegion r, int bx, int bz, int[][] surface, SplittableRandom rnd) {
        if (rnd.nextInt(5) != 0) {
            return;
        }
        int cx = 5 + rnd.nextInt(6);
        int cz = 5 + rnd.nextInt(6);
        int y = surface[cx][cz];
        if (y == Integer.MIN_VALUE) {
            return;
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int s = surface[cx + dx][cz + dz];
                if (s == Integer.MIN_VALUE || Math.abs(s - y) > 2) {
                    return;
                }
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 4.3) {
                    continue;
                }
                int x = bx + cx + dx;
                int z = bz + cz + dz;
                int depth = (int) Math.round(3 * (1 - d / 4.3));
                for (int up = 0; up <= 4; up++) {
                    r.setType(x, y + up - depth + 1, z, Material.AIR);
                }
                int floor = y - depth;
                Material mat = d < 1 ? Material.CRYING_OBSIDIAN : d < 1.8 ? Material.OBSIDIAN
                        : hash(info.getSeed(), x, floor, z) < 0.5 ? Material.GLASS : Material.CALCITE;
                r.setType(x, floor, z, mat);
                r.setType(x, floor - 1, z, Material.CALCITE);
            }
        }
        r.setBlockData(bx + cx, y - 2, bz + cz, data("minecraft:amethyst_cluster[facing=up]"));
    }

    // ---------------------------------------------------------------- the Crystal Hollows

    private static boolean rock(Material m) {
        return Tag.BASE_STONE_OVERWORLD.isTagged(m) || m == Material.DIRT || m == Material.GRAVEL || m == Material.CLAY
                || m == Material.COBBLED_DEEPSLATE || m == Material.DRIPSTONE_BLOCK;
    }

    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /** The caves under the plains: geode walls and crystals. */
    private void hollowsColumn(WorldInfo info, LimitedRegion r, int x, int z, int surface) {
        int min = info.getMinHeight();
        int bottom = Math.max(min + 6, -56);
        int top = surface - 14;
        long seed = info.getSeed();
        for (int y = bottom; y <= top; y++) {
            Material m = r.getType(x, y, z);
            if (!rock(m)) {
                continue;
            }
            BlockFace open = openFace(r, x, y, z);
            if (open == null) {
                continue;
            }
            double h = hash(seed, x, y, z);
            double patch = hash(seed, x >> 2, y >> 2, z >> 2);
            Material wall = h < 0.05 ? Material.BUDDING_AMETHYST
                    : patch < 0.45 ? Material.AMETHYST_BLOCK : patch < 0.8 ? Material.CALCITE : Material.SMOOTH_BASALT;
            if (wall == Material.BUDDING_AMETHYST && !plugin.getConfig().getBoolean("glassed-plains.budding-amethyst", false)) {
                wall = Material.AMETHYST_BLOCK;                          // no free crystal farms by default
            }
            r.setType(x, y, z, wall);
            // a crystal on the open side
            int cx = x + open.getModX(), cy = y + open.getModY(), cz = z + open.getModZ();
            if (h > 0.9 && r.isInRegion(cx, cy, cz) && r.getType(cx, cy, cz).isAir()) {
                String kind = h > 0.97 ? "amethyst_cluster" : h > 0.94 ? "large_amethyst_bud" : "medium_amethyst_bud";
                BlockData crystal = data("minecraft:" + kind);
                if (crystal instanceof Directional dir) {
                    crystal = crystal.clone();
                    ((Directional) crystal).setFacing(open);
                }
                r.setBlockData(cx, cy, cz, crystal);
            }
        }
        for (int y = bottom - (bottom & 3); y <= top; y += 4) {
            r.setBiome(x, y, z, hollows);
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

    /** One possible giant geode per 6x6-chunk cell, deep inside: a hollow crystal sphere with a chest. */
    private void geode(WorldInfo info, LimitedRegion r, int chunkX, int chunkZ, double[][] strength, int[][] surface) {
        if (strength[8][8] < 0.8 || surface[8][8] == Integer.MIN_VALUE) {
            return;
        }
        int cellX = Math.floorDiv(chunkX, 6);
        int cellZ = Math.floorDiv(chunkZ, 6);
        SplittableRandom cell = new SplittableRandom(info.getSeed() ^ SALT ^ (cellX * 73856093L + cellZ * 19349663L));
        if (Math.floorMod(chunkX, 6) != 1 + cell.nextInt(4) || Math.floorMod(chunkZ, 6) != 1 + cell.nextInt(4)
                || cell.nextDouble() >= geodeChance) {
            return;
        }
        int cx = (chunkX << 4) + 8;
        int cz = (chunkZ << 4) + 8;
        int cy = Math.max(info.getMinHeight() + 16, Math.min(-10, surface[8][8] - 40));
        double rad = 10;
        long seed = info.getSeed();
        for (int x = cx - 13; x <= cx + 13; x++) {
            for (int z = cz - 13; z <= cz + 13; z++) {
                for (int y = cy - 13; y <= cy + 13; y++) {
                    if (!r.isInRegion(x, y, z)) {
                        continue;
                    }
                    double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) * 1.2 + (z - cz) * (z - cz))
                            + 0.8 * (hash(seed, x >> 1, y >> 1, z >> 1) - 0.5);
                    if (d < rad - 2) {
                        r.setType(x, y, z, Material.AIR);
                    } else if (d < rad - 1) {
                        r.setType(x, y, z, hash(seed, x, y, z) < 0.12 ? Material.CALCITE : Material.AMETHYST_BLOCK);
                    } else if (d < rad) {
                        r.setType(x, y, z, Material.CALCITE);
                    } else if (d < rad + 1) {
                        r.setType(x, y, z, Material.SMOOTH_BASALT);
                    } else {
                        continue;
                    }
                    r.setBiome(x, y, z, hollows);
                }
            }
        }
        // crystals on the inside of the shell
        for (int x = cx - 9; x <= cx + 9; x++) {
            for (int z = cz - 9; z <= cz + 9; z++) {
                for (int y = cy - 9; y <= cy + 9; y++) {
                    if (!r.isInRegion(x, y, z) || !r.getType(x, y, z).isAir() || hash(seed, x, y + 1, z) > 0.18) {
                        continue;
                    }
                    for (BlockFace f : FACES) {
                        int ax = x - f.getModX(), ay = y - f.getModY(), az = z - f.getModZ();
                        if (r.isInRegion(ax, ay, az) && r.getType(ax, ay, az) == Material.AMETHYST_BLOCK) {
                            BlockData c = data("minecraft:amethyst_cluster").clone();
                            ((Directional) c).setFacing(f);
                            r.setBlockData(x, y, z, c);
                            break;
                        }
                    }
                }
            }
        }
        // a calcite platform with a chest in the middle
        int floor = cy - 7;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = floor - 3; y <= floor; y++) {
                    if (dx * dx + dz * dz <= 5 && r.isInRegion(cx + dx, y, cz + dz)) {
                        r.setType(cx + dx, y, cz + dz, y == floor ? Material.POLISHED_DIORITE : Material.CALCITE);
                    }
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
        }
    }
}
