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
 * Ashen Forest and the Hollow Roots under it: a rare 3D biome pair painted onto NEW overworld chunks.
 * - Ashen Forest (surface): the trees turned white and grey (stripped birch trunks, pale oak leaves thinned out,
 *   pale moss hanging down), pale moss and coarse ground, ashen trees planted where there were none.
 * - Hollow Roots (underground): caves wrapped in roots - rooted dirt and mud walls, hanging roots, mangrove root
 *   pillars, glow lichen, cobwebs - and now and then a spider nest hall with a cave spider spawner and a chest.
 * Runs on the world generation threads: only the LimitedRegion it's given is touched.
 */
public final class AshenForest implements Listener {

    static final Key FOREST = Key.key("forgebound", "ashen_forest");
    static final Key ROOTS = Key.key("forgebound", "hollow_roots");
    private static final long SALT = 0xA5E7F0L;

    private final Forgebound plugin;
    private final Populator populator = new Populator();
    private volatile boolean enabled = true;
    private volatile double rarity = 0.02;
    private volatile double zoneSize = 900;
    private volatile double nestChance = 0.5;
    private volatile Biome forest;
    private volatile Biome roots;
    private volatile LootTable loot;
    private final java.util.Map<Long, ScorchedNoise> noises = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, BlockData> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public AshenForest(Forgebound plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("ashen-forest.enabled", true);
        rarity = c.getDouble("ashen-forest.rarity", 0.025);
        zoneSize = c.getDouble("ashen-forest.zone-size", 900);
        nestChance = c.getDouble("ashen-forest.nest-chance", 0.5);
        noises.clear();
        var reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        forest = reg.get(FOREST);
        roots = reg.get(ROOTS);
        if (forest == null || roots == null) {
            plugin.getLogger().warning("Ashen Forest: the biomes from the Forgebound datapack are missing, nothing will generate.");
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
            if (!enabled || forest == null || roots == null) {
                return;
            }
            try {
                generate(info, chunkX, chunkZ, region);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Ashen Forest at chunk " + chunkX + "," + chunkZ + ": " + ex);
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
        ScorchedNoise glass = plugin.glassedPlains() != null ? plugin.glassedPlains().noise(info.getSeed()) : null;
        SplittableRandom rnd = new SplittableRandom(info.getSeed() ^ SALT ^ (chunkX * 341873128712L + chunkZ * 132897987541L));
        int[][] surface = new int[16][16];
        double[][] strength = new double[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                surface[x][z] = Integer.MIN_VALUE;
                double s = noise.strength(bx + x, bz + z);
                strength[x][z] = s;
                if (s <= 0 || s < 1 && rnd.nextDouble() > s
                        || scorch != null && scorch.inside(bx + x, bz + z) || crags != null && crags.inside(bx + x, bz + z) || glass != null && glass.inside(bx + x, bz + z)) {
                    continue;
                }
                surface[x][z] = surfaceColumn(info, r, bx + x, bz + z, rnd, s);
                if (surface[x][z] != Integer.MIN_VALUE && s > 0.35) {
                    rootsColumn(info, r, bx + x, bz + z, surface[x][z]);
                }
            }
        }
        
        nest(info, r, chunkX, chunkZ, strength, surface);
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
        long seed = info.getSeed();
        // the trees above turn ashen: white trunks, fewer grey leaves, pale moss hanging from them
        boolean hadTree = false;
        for (int y = top + 1; y < Math.min(info.getMaxHeight(), top + 40); y++) {
            Material a = r.getType(x, y, z);
            if (a.isAir()) {
                continue;
            }
            if (Tag.LOGS.isTagged(a)) {
                hadTree = true;
                BlockData log = data("minecraft:stripped_birch_log").clone();
                if (r.getBlockData(x, y, z) instanceof org.bukkit.block.data.Orientable o && log instanceof org.bukkit.block.data.Orientable ol) {
                    ol.setAxis(o.getAxis());
                }
                r.setBlockData(x, y, z, log);
            } else if (Tag.LEAVES.isTagged(a)) {
                if (hash(seed, x, y, z) < 0.45) {
                    r.setType(x, y, z, Material.AIR);
                } else {
                    r.setBlockData(x, y, z, data("minecraft:pale_oak_leaves[persistent=true]"));
                    if (r.getType(x, y - 1, z).isAir() && hash(seed, x, y - 1, z) < 0.12) {
                        r.setBlockData(x, y - 1, z, data("minecraft:pale_hanging_moss[tip=true]"));
                    }
                }
            } else if (!a.isSolid() && a != Material.WATER && a != Material.LAVA && a != Material.PALE_HANGING_MOSS) {
                r.setType(x, y, z, Material.AIR);                       // flowers and grass wither
            }
        }
        boolean natural = Tag.DIRT.isTagged(m) || m == Material.MOSS_BLOCK || m == Material.SNOW_BLOCK || m == Material.MUD;
        if (natural) {
            double p = hash(seed, x >> 2, 3, z >> 2);
            Material ground = p < 0.35 ? Material.PALE_MOSS_BLOCK : p < 0.55 ? Material.COARSE_DIRT : p < 0.7 ? Material.PODZOL
                    : Material.GRASS_BLOCK;
            r.setType(x, top, z, ground);
            double c = hash(seed, x, top + 1, z);
            if (r.getType(x, top + 1, z).isAir()) {
                if (ground == Material.PALE_MOSS_BLOCK && c < 0.35) {
                    r.setType(x, top + 1, z, Material.PALE_MOSS_CARPET);
                } else if (c < 0.03) {
                    r.setType(x, top + 1, z, Material.DEAD_BUSH);
                }
            }
            // ashen trees where there are none
            if (!hadTree && s > 0.5 && rnd.nextInt(70) == 0) {
                ashenTree(r, x, top, z, rnd);
            }
        }
        for (int y = Math.max(min, top - 8); y < Math.min(info.getMaxHeight(), top + 48); y += 4) {
            r.setBiome(x, y, z, forest);
        }
        return top;
    }

    /** A simple ashen tree: a white trunk with a thin grey crown and some hanging moss. */
    private void ashenTree(LimitedRegion r, int x, int ground, int z, SplittableRandom rnd) {
        int h = 5 + rnd.nextInt(4);
        for (int y = ground + 1; y <= ground + h; y++) {
            if (!r.isInRegion(x, y, z) || !r.getType(x, y, z).isAir()) {
                return;
            }
        }
        for (int y = ground + 1; y <= ground + h; y++) {
            r.setBlockData(x, y, z, data("minecraft:stripped_birch_log[axis=y]"));
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int lx = x + dx, ly = ground + h + dy, lz = z + dz;
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) > 3 || !r.isInRegion(lx, ly, lz) || !r.getType(lx, ly, lz).isAir()
                            || rnd.nextInt(3) == 0) {
                        continue;
                    }
                    r.setBlockData(lx, ly, lz, data("minecraft:pale_oak_leaves[persistent=true]"));
                    if (dy == -1 && rnd.nextInt(4) == 0 && r.isInRegion(lx, ly - 1, lz) && r.getType(lx, ly - 1, lz).isAir()) {
                        r.setBlockData(lx, ly - 1, lz, data("minecraft:pale_hanging_moss[tip=true]"));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- the Hollow Roots

    private static boolean rock(Material m) {
        return Tag.BASE_STONE_OVERWORLD.isTagged(m) || m == Material.DIRT || m == Material.GRAVEL || m == Material.CLAY
                || m == Material.COBBLED_DEEPSLATE || m == Material.DRIPSTONE_BLOCK;
    }

    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /** The caves under the forest: earthy walls, roots, lichen and webs. */
    private void rootsColumn(WorldInfo info, LimitedRegion r, int x, int z, int surface) {
        int min = info.getMinHeight();
        int bottom = Math.max(min + 6, -40);
        int top = surface - 8;
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
            r.setType(x, y, z, patch < 0.45 ? Material.ROOTED_DIRT : patch < 0.7 ? Material.MUD
                    : patch < 0.85 ? Material.PACKED_MUD : Material.MOSS_BLOCK);
            int ox = x + open.getModX(), oy = y + open.getModY(), oz = z + open.getModZ();
            if (!r.isInRegion(ox, oy, oz) || !r.getType(ox, oy, oz).isAir()) {
                continue;
            }
            if (open == BlockFace.DOWN && h < 0.25) {
                r.setType(ox, oy, oz, Material.HANGING_ROOTS);           // roots hanging from the ceiling
            } else if (open != BlockFace.DOWN && open != BlockFace.UP && h < 0.12) {
                // glow lichen on the wall (it sticks to the side facing the wall)
                r.setBlockData(ox, oy, oz, data("minecraft:glow_lichen[" + open.getOppositeFace().name().toLowerCase() + "=true]"));
            } else if (h > 0.985) {
                r.setType(ox, oy, oz, Material.COBWEB);
            } else if (open == BlockFace.DOWN && h > 0.965) {
                // a root pillar from the ceiling down to the floor
                for (int py = oy; py > bottom && r.isInRegion(ox, py, oz) && r.getType(ox, py, oz).isAir(); py--) {
                    r.setType(ox, py, oz, Material.MANGROVE_ROOTS);
                }
            }
        }
        for (int y = bottom - (bottom & 3); y <= top; y += 4) {
            r.setBiome(x, y, z, roots);
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

    /** One possible spider nest per 6x6-chunk cell, deep inside: a root-wrapped hall full of webs, a cave spider spawner and a chest. */
    private void nest(WorldInfo info, LimitedRegion r, int chunkX, int chunkZ, double[][] strength, int[][] surface) {
        if (strength[8][8] < 0.8 || surface[8][8] == Integer.MIN_VALUE) {
            return;
        }
        int cellX = Math.floorDiv(chunkX, 6);
        int cellZ = Math.floorDiv(chunkZ, 6);
        SplittableRandom cell = new SplittableRandom(info.getSeed() ^ SALT ^ (cellX * 73856093L + cellZ * 19349663L));
        if (Math.floorMod(chunkX, 6) != 1 + cell.nextInt(4) || Math.floorMod(chunkZ, 6) != 1 + cell.nextInt(4)
                || cell.nextDouble() >= nestChance) {
            return;
        }
        int cx = (chunkX << 4) + 8;
        int cz = (chunkZ << 4) + 8;
        int cy = Math.max(info.getMinHeight() + 14, Math.min(0, surface[8][8] - 30));
        long seed = info.getSeed();
        int rx = 9, ry = 5, rz = 9;
        for (int x = cx - rx - 1; x <= cx + rx + 1; x++) {
            for (int z = cz - rz - 1; z <= cz + rz + 1; z++) {
                for (int y = cy - ry - 1; y <= cy + ry + 1; y++) {
                    if (!r.isInRegion(x, y, z)) {
                        continue;
                    }
                    double dx = (x - cx) / (double) rx, dy = (y - cy) / (double) ry, dz = (z - cz) / (double) rz;
                    double d = dx * dx + dy * dy + dz * dz + 0.15 * (hash(seed, x >> 1, y >> 1, z >> 1) - 0.5);
                    if (d <= 1) {
                        double h = hash(seed, x, y, z);
                        r.setType(x, y, z, y <= cy - ry + 1 ? Material.ROOTED_DIRT : h < 0.12 ? Material.COBWEB : Material.AIR);
                    } else if (d <= 1.4 && rock(r.getType(x, y, z))) {
                        r.setType(x, y, z, hash(seed, x, y, z) < 0.5 ? Material.ROOTED_DIRT : Material.MUD);
                    }
                    if (r.isInRegion(x, y, z)) {
                        r.setBiome(x, y, z, roots);
                    }
                }
            }
        }
        // root pillars
        for (int[] p : new int[][]{{-5, -4}, {4, -5}, {-4, 5}, {5, 4}}) {
            for (int y = cy - ry + 2; y <= cy + ry; y++) {
                if (r.isInRegion(cx + p[0], y, cz + p[1])) {
                    r.setType(cx + p[0], y, cz + p[1], Material.MANGROVE_ROOTS);
                }
            }
        }
        int floor = cy - ry + 2;
        // the spawner in the middle, the chest next to it, webs around
        if (r.isInRegion(cx, floor, cz)) {
            r.setType(cx, floor, cz, Material.SPAWNER);
            if (r.getBlockState(cx, floor, cz) instanceof org.bukkit.block.CreatureSpawner sp) {
                sp.setSpawnedType(org.bukkit.entity.EntityType.CAVE_SPIDER);
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
