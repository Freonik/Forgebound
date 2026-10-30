package cz.forgebound;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Scorched Lands: a rare burnt biome (from the bundled datapack) painted onto NEW chunks of the overworld,
 * with ruined forges, workshops, slag heaps and watchtowers in it. Already generated chunks are never touched.
 * Runs as a block populator, so it can run on the server's world generation threads: no Bukkit main-thread calls
 * in there, only the LimitedRegion it's given.
 */
public final class ScorchedLands implements Listener {

    static final Key BIOME = Key.key("forgebound", "scorched_lands");
    static final NamespacedKey LOOT_BIG = new NamespacedKey("forgebound", "chests/forge_ruin");
    static final NamespacedKey LOOT_SMALL = new NamespacedKey("forgebound", "chests/forge_ruin_small");

    private final Forgebound plugin;
    private final NamespacedKey guardKey;
    private final Populator populator = new Populator();
    private final NetherPopulator netherPopulator = new NetherPopulator();
    private final EndPopulator endPopulator = new EndPopulator();
    private final Map<Long, ScorchedNoise> noises = new ConcurrentHashMap<>();
    private final Map<String, BlockData> blockCache = new ConcurrentHashMap<>();
    private volatile Settings settings;
    private volatile Biome biome;
    private volatile LootTable lootBig;
    private volatile LootTable lootSmall;

    record Settings(boolean enabled, List<String> worlds, double rarity, double zoneSize, boolean ruins,
                    double ruinChance, boolean guards, double ancientChance, boolean nether, int netherCell,
                    double netherChance, int sureCount, int sureMin, int sureMax, int sureRadius, int centerX,
                    int centerZ, int netherSureCount, int netherSureMin, int netherSureMax, int sureForges) { }

    static final long OVERWORLD_SALT = 0x5EED1L;
    static final long NETHER_SALT = 0x4E7E2L;

    public ScorchedLands(Forgebound plugin) {
        this.plugin = plugin;
        this.guardKey = Forgebound.key("ruin_guard");
        load();
    }

    /** (Re)reads the config. Rarity / size changes only affect chunks generated afterwards. */
    public void load() {
        var c = plugin.getConfig();
        settings = new Settings(c.getBoolean("scorched-lands.enabled", true), c.getStringList("scorched-lands.worlds"),
                c.getDouble("scorched-lands.rarity", 0.03), c.getDouble("scorched-lands.zone-size", 800),
                c.getBoolean("scorched-lands.ruins", true), c.getDouble("scorched-lands.ruin-chance", 0.35),
                c.getBoolean("scorched-lands.guards", true),
                c.getDouble("ancient-forge.scorched-chance", 0), c.getBoolean("ancient-forge.nether", true),
                Math.max(4, c.getInt("ancient-forge.nether-cell-chunks", 16)), c.getDouble("ancient-forge.nether-chance", 0),
                c.getInt("scorched-lands.guaranteed.count", 2), c.getInt("scorched-lands.guaranteed.min-distance", 500),
                c.getInt("scorched-lands.guaranteed.max-distance", 1700), c.getInt("scorched-lands.guaranteed.radius", 110),
                c.getInt("scorched-lands.guaranteed.center-x", 0), c.getInt("scorched-lands.guaranteed.center-z", 0),
                c.getInt("ancient-forge.nether-guaranteed.count", 1), c.getInt("ancient-forge.nether-guaranteed.min-distance", 60),
                c.getInt("ancient-forge.nether-guaranteed.max-distance", 220),
                c.getInt("scorched-lands.guaranteed.forges", 1));
        noises.clear();
        biome = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(BIOME);
        if (biome == null) {
            plugin.getLogger().warning("Scorched Lands: the biome from the Forgebound datapack is missing, nothing will generate.");
        }
        lootBig = Bukkit.getLootTable(LOOT_BIG);
        lootSmall = Bukkit.getLootTable(LOOT_SMALL);
        debrisKeep = Math.max(0, Math.min(100, c.getDouble("ancient-debris.keep-percent", 50))) / 100.0;
        endOn = c.getBoolean("end-ruins.enabled", true);
        endCell = Math.max(4, c.getInt("end-ruins.cell-chunks", 10));
        endChance = c.getDouble("end-ruins.chance", 0.4);
        endMinDistance = c.getInt("end-ruins.min-distance", 1000);
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            attach(world);
        }
        // Scorched Earth: someone walked into the biome (checked every 5 s)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Biome b = biome;
            if (b == null || plugin.advancements() == null) {
                return;
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                Location l = p.getLocation();
                if (wanted(p.getWorld()) && l.getWorld().getBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ()).equals(b)) {
                    plugin.award(p, "scorched_earth");
                }
            }
        }, 200L, 100L);
    }

    /** Ruin Raider: a player killed a ruin guard. */
    @EventHandler
    public void onGuardDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && plugin.advancements() != null
                && event.getEntity().getPersistentDataContainer().has(guardKey, PersistentDataType.BYTE)) {
            plugin.award(killer, "ruin_raider");
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attach(event.getWorld());
    }

    private boolean wanted(World world) {
        Settings s = settings;
        return s.worlds().isEmpty() ? world.getEnvironment() == World.Environment.NORMAL : s.worlds().contains(world.getName());
    }

    private void attach(World world) {
        if (wanted(world) && !world.getPopulators().contains(populator)) {
            world.getPopulators().add(populator);
        }
        if (world.getEnvironment() == World.Environment.NETHER && !world.getPopulators().contains(netherPopulator)) {
            world.getPopulators().add(netherPopulator);
        }
        if (world.getEnvironment() == World.Environment.THE_END && !world.getPopulators().contains(endPopulator)) {
            world.getPopulators().add(endPopulator);
        }
    }

    /** The biome noise of a world, with the guaranteed areas forced in. */
    ScorchedNoise noise(long seed) {
        Settings s = settings;
        return noises.computeIfAbsent(seed, k -> {
            ScorchedNoise n = new ScorchedNoise(k, s.zoneSize(), s.rarity());
            n.forceSites(sureSites(k), s.sureRadius());
            return n;
        });
    }

    /** Plain noise without the guaranteed areas (for ground patches). */
    private ScorchedNoise plain(long seed) {
        Settings s = settings;
        return noises.computeIfAbsent(seed ^ 0x7A7C4L, k -> new ScorchedNoise(seed, s.zoneSize(), s.rarity()));
    }

    /** The guaranteed Scorched Lands areas (each with an Ancient Forge in the middle) of an overworld. */
    List<int[]> sureSites(long seed) {
        Settings s = settings;
        return ScorchedNoise.sites(seed, OVERWORLD_SALT, s.sureCount(), s.sureMin(), s.sureMax(), s.centerX(), s.centerZ());
    }

    /** Where this world's guaranteed Ancient Forges will be (x, z), also before anyone went there. */
    public List<int[]> plannedForges(World world) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            return settings.nether() ? netherSites(world.getSeed()) : List.of();
        }
        if (!wanted(world) || !settings.enabled() || !settings.ruins()) {
            return List.of();
        }
        List<int[]> sure = sureSites(world.getSeed());
        return sure.subList(0, Math.min(settings.sureForges(), sure.size()));
    }

    /** The guaranteed Ancient Forges of a nether world. */
    List<int[]> netherSites(long seed) {
        Settings s = settings;
        return ScorchedNoise.sites(seed, NETHER_SALT, s.netherSureCount(), s.netherSureMin(), s.netherSureMax(),
                s.centerX() / 8, s.centerZ() / 8);
    }

    private BlockData data(String block) {
        return blockCache.computeIfAbsent(block, Bukkit::createBlockData);
    }

    // ================================================================ generation

    private final class Populator extends BlockPopulator {
        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            Settings s = settings;
            Biome b = biome;
            if (!s.enabled() || b == null) {
                return;
            }
            try {
                scorch(info, chunkX, chunkZ, region, s, b);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Scorched Lands at chunk " + chunkX + "," + chunkZ + ": " + ex);
            }
        }
    }

    private void scorch(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region, Settings s, Biome b) {
        ScorchedNoise noise = noise(info.getSeed());
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        // quick skip: nothing near this chunk
        boolean near = false;
        for (int[] p : new int[][] {{0, 0}, {15, 0}, {0, 15}, {15, 15}, {8, 8}}) {
            if (noise.value(bx + p[0], bz + p[1]) > noise.threshold - 0.02) {
                near = true;
                break;
            }
        }
        if (!near) {
            return;
        }
        SplittableRandom rnd = new SplittableRandom(info.getSeed() ^ (chunkX * 341873128712L + chunkZ * 132897987541L));
        ScorchedNoise patches = plain(info.getSeed() + 7);         // small patches of gravel / blackstone / tuff
        int[][] surface = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double strength = noise.strength(bx + x, bz + z);
                surface[x][z] = Integer.MIN_VALUE;
                if (strength <= 0 || strength < 1 && rnd.nextDouble() > strength) {
                    continue;
                }
                surface[x][z] = column(info, region, bx + x, bz + z, rnd, patches, b);
            }
        }
        if (s.ruins()) {
            ruins(info, chunkX, chunkZ, region, s, noise, surface);
        }
    }

    /** Burns one block column. Returns the surface height (MIN_VALUE when it's water or nothing was found). */
    private int column(WorldInfo info, LimitedRegion r, int x, int z, SplittableRandom rnd, ScorchedNoise patches, Biome b) {
        int min = info.getMinHeight();
        int surface = Integer.MIN_VALUE;
        Material top = Material.AIR;
        for (int y = info.getMaxHeight() - 1; y > min; y--) {
            if (!r.isInRegion(x, y, z)) {
                return Integer.MIN_VALUE;
            }
            Material m = r.getType(x, y, z);
            if (m.isAir() || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || !m.isSolid() && m != Material.WATER && m != Material.LAVA) {
                continue;
            }
            surface = y;
            top = m;
            break;
        }
        if (surface == Integer.MIN_VALUE || top == Material.WATER || top == Material.LAVA || top == Material.ICE) {
            return Integer.MIN_VALUE;
        }
        // above the ground: no leaves, no plants, trees burnt down to stumps
        int stump = 1 + rnd.nextInt(4);
        for (int y = surface + 1; y < Math.min(info.getMaxHeight(), surface + 40); y++) {
            Material m = r.getType(x, y, z);
            if (m.isAir()) {
                continue;
            }
            if (Tag.LOGS.isTagged(m)) {
                if (y - surface <= stump) {
                    BlockData basalt = Material.BASALT.createBlockData();
                    if (r.getBlockData(x, y, z) instanceof Orientable o && basalt instanceof Orientable ob) {
                        ob.setAxis(o.getAxis());
                    }
                    r.setBlockData(x, y, z, basalt);
                } else {
                    r.setType(x, y, z, Material.AIR);
                }
            } else if (Tag.LEAVES.isTagged(m) || !m.isSolid() && m != Material.WATER && m != Material.LAVA) {
                r.setType(x, y, z, Material.AIR);
            }
        }
        // the ground itself
        if (Tag.DIRT.isTagged(top) || Tag.SAND.isTagged(top) || top == Material.SNOW_BLOCK || top == Material.MUD
                || top == Material.MOSS_BLOCK || top == Material.CLAY || top == Material.GRAVEL) {
            double v = patches.value(x * 18.0, z * 18.0);
            Material ground = v > 0.45 ? Material.BLACKSTONE : v > 0.22 ? Material.GRAVEL : v < -0.45 ? Material.TUFF : Material.COARSE_DIRT;
            if (rnd.nextInt(80) == 0) {
                ground = Material.MAGMA_BLOCK;
            }
            r.setType(x, surface, z, ground);
            for (int y = surface - 1; y >= surface - 3 && y > min; y--) {
                if (Tag.DIRT.isTagged(r.getType(x, y, z))) {
                    r.setType(x, y, z, Material.COARSE_DIRT);
                }
            }
            if (ground == Material.COARSE_DIRT && rnd.nextInt(30) == 0 && r.getType(x, surface + 1, z).isAir()) {
                r.setType(x, surface + 1, z, Material.DEAD_BUSH);
            }
        } else if (Tag.BASE_STONE_OVERWORLD.isTagged(top) && patches.value(x * 18.0, z * 18.0) > 0.4) {
            r.setType(x, surface, z, Material.BLACKSTONE);
        }
        // the biome, from a little underground up into the sky (biomes are stored per 4x4x4 cell)
        for (int y = Math.max(min, surface - 12); y < Math.min(info.getMaxHeight(), surface + 48); y += 4) {
            r.setBiome(x, y, z, b);
        }
        return surface;
    }

    /** One possible ruin per 4x4-chunk cell, in a random chunk of the cell, only deep inside the biome. */
    private void ruins(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region, Settings s, ScorchedNoise noise,
                       int[][] surface) {
        List<int[]> sure = sureSites(info.getSeed());
        for (int i = 0; i < Math.min(s.sureForges(), sure.size()); i++) {
            int[] site = sure.get(i);
            if (Math.floorDiv(site[0], 16) == chunkX && Math.floorDiv(site[1], 16) == chunkZ) {
                sureForge(info, chunkX, chunkZ, region, s, surface);
                return;
            }
        }
        int cellX = Math.floorDiv(chunkX, 4);
        int cellZ = Math.floorDiv(chunkZ, 4);
        SplittableRandom cell = new SplittableRandom(info.getSeed() * 31 + cellX * 73428767L + cellZ * 912931L);
        int pickX = cell.nextInt(4);
        int pickZ = cell.nextInt(4);
        if (Math.floorMod(chunkX, 4) != pickX || Math.floorMod(chunkZ, 4) != pickZ || cell.nextDouble() >= s.ruinChance()) {
            return;
        }
        if (noise.strength((chunkX << 4) + 8, (chunkZ << 4) + 8) < 1) {
            return;
        }
        if (cell.nextDouble() < s.ancientChance() && noise.strength((chunkX << 4) + 8, (chunkZ << 4) + 8) >= 1) {
            sureForge(info, chunkX, chunkZ, region, s, surface);       // a random extra Ancient Forge (off by default)
            return;
        }
        ForgeRuins.Type type = ForgeRuins.pick(cell);
        int ox = 1 + cell.nextInt(Math.max(1, 15 - type.width));
        int oz = 1 + cell.nextInt(Math.max(1, 15 - type.depth));
        // flat enough, and all of it burnt (no water)?
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        long sum = 0;
        for (int x = ox; x < ox + type.width; x++) {
            for (int z = oz; z < oz + type.depth; z++) {
                int h = surface[x][z];
                if (h == Integer.MIN_VALUE) {
                    return;
                }
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                sum += h;
            }
        }
        if (hi - lo > 4) {
            return;
        }
        int y0 = (int) Math.round((double) sum / (type.width * type.depth));
        int bx = (chunkX << 4) + ox;
        int bz = (chunkZ << 4) + oz;
        ForgeRuins.Result result = ForgeRuins.build(type, canvas(region), bx, y0, bz, cell);
        finish(info, region, result, s, cell, false);
    }

    private ForgeRuins.Canvas canvas(LimitedRegion region) {
        return new ForgeRuins.Canvas() {
            @Override
            public void set(int x, int y, int z, String block) {
                if (region.isInRegion(x, y, z)) {
                    region.setBlockData(x, y, z, data(block));
                }
            }

            @Override
            public String get(int x, int y, int z) {
                return region.isInRegion(x, y, z) ? region.getType(x, y, z).getKey().toString() : "minecraft:stone";
            }
        };
    }

    /** Loot, guards and (for an Ancient Forge) registering the forge. */
    private void finish(WorldInfo info, LimitedRegion region, ForgeRuins.Result result, Settings s, SplittableRandom cell,
                        boolean nether) {
        long seed = cell.nextLong();
        for (int[] p : result.bigChests) {
            loot(region, p, lootBig, seed++);
        }
        for (int[] p : result.smallChests) {
            loot(region, p, lootSmall, seed++);
        }
        if (s.guards()) {
            for (int[] p : result.guards) {
                guard(region, p, nether);
            }
        }
        if (result.core != null && plugin.stars() != null) {
            plugin.stars().found(info.getName(), result.core[0], result.core[1], result.core[2]);
        }
    }

    /** A guaranteed Ancient Forge: built whatever the ground looks like (on its average height). */
    private void sureForge(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region, Settings s, int[][] surface) {
        long sum = 0;
        int count = 0;
        for (int x = 0; x < 16; x++) {                       // the ground of this chunk (the forge is wider)
            for (int z = 0; z < 16; z++) {
                if (surface[x][z] != Integer.MIN_VALUE) {
                    sum += surface[x][z];
                    count++;
                }
            }
        }
        int y0 = count == 0 ? 63 : (int) Math.round((double) sum / count);
        SplittableRandom cell = new SplittableRandom(info.getSeed() ^ (chunkX * 7919L + chunkZ * 104729L));
        // centered on the chunk; it reaches into the neighbours (the populator may write 16 blocks around)
        ForgeRuins.Result result = ForgeRuins.build(ForgeRuins.Type.ANCIENT, canvas(region), (chunkX << 4) - 4, y0,
                (chunkZ << 4) - 4, cell);
        finish(info, region, result, s, cell, false);
    }

    // ================================================================ Ancient Forges in the Nether

    private final class NetherPopulator extends BlockPopulator {
        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            thinDebris(info, chunkX, chunkZ, region);
            Settings s = settings;
            if (!s.nether() || s.netherChance() <= 0) {
                return;
            }
            try {
                netherForge(info, chunkX, chunkZ, region, s);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Ancient Forge at nether chunk " + chunkX + "," + chunkZ + ": " + ex);
            }
        }
    }

    /** ancient-debris.keep-percent: how much of the nether's ancient debris stays (the rest becomes netherrack). */
    private volatile double debrisKeep = 0.5;

    /** Less netherite: part of the ancient debris in a new nether chunk turns into netherrack (the same blocks for a seed). */
    private void thinDebris(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region) {
        double keep = debrisKeep;
        if (keep >= 1) {
            return;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int min = Math.max(info.getMinHeight(), 0);
        int max = Math.min(info.getMaxHeight() - 1, 200);
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int y = min; y <= max; y++) {
                    if (region.getType(x, y, z) != Material.ANCIENT_DEBRIS) {
                        continue;
                    }
                    long h = info.getSeed() ^ (x * 341873128712L) ^ (z * 132897987541L) ^ (y * 0x9E3779B97F4A7C15L);
                    h ^= h >>> 29;
                    h *= 0xBF58476D1CE4E5B9L;
                    h ^= h >>> 32;
                    if ((h >>> 11) * 0x1.0p-53 >= keep) {
                        region.setType(x, y, z, Material.NETHERRACK);
                    }
                }
            }
        }
    }

    // ================================================================ the End: lost forges on the outer islands

    /** End ruins settings, read in load(). */
    private volatile boolean endOn = true;
    private volatile int endCell = 10;
    private volatile double endChance = 0.4;
    private volatile int endMinDistance = 1000;

    private final class EndPopulator extends BlockPopulator {
        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            if (!endOn) {
                return;
            }
            try {
                endRuin(info, chunkX, chunkZ, region);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("End ruin at chunk " + chunkX + "," + chunkZ + ": " + ex);
            }
        }
    }

    /** The overworld ruin palette turned into the End's: end stone, end stone bricks and purpur instead of dirt, stone and wood. */
    static String endBlock(String block) {
        int at = block.indexOf('[');
        String id = at < 0 ? block : block.substring(0, at);
        String props = at < 0 ? "" : block.substring(at);
        String name = id.substring(id.indexOf(':') + 1);
        String to = switch (name) {
            case "coarse_dirt", "dirt", "grass_block", "podzol", "gravel", "rooted_dirt", "mud", "sand", "red_sand" -> "end_stone";
            case "cobblestone", "mossy_cobblestone", "stone", "andesite", "tuff" -> "end_stone_bricks";
            case "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks" -> "end_stone_bricks";
            case "cobblestone_wall", "mossy_cobblestone_wall", "stone_brick_wall", "mossy_stone_brick_wall" -> "end_stone_brick_wall";
            case "cobblestone_slab", "stone_brick_slab", "mossy_stone_brick_slab", "stone_slab" -> "end_stone_brick_slab";
            case "cobblestone_stairs", "stone_brick_stairs", "mossy_stone_brick_stairs", "stone_stairs" -> "end_stone_brick_stairs";
            default -> null;
        };
        if (to == null) {
            if (name.endsWith("_planks")) {
                to = "purpur_block";
                props = "";
            } else if (name.endsWith("_log") || name.endsWith("_wood")) {
                to = "purpur_pillar";
                props = props.contains("axis=") ? props.replaceAll(".*(axis=[xyz]).*", "[$1]") : "";
            } else if (name.endsWith("_slab") && !name.contains("blackstone")) {
                to = "purpur_slab";
            } else if (name.endsWith("_stairs") && !name.contains("blackstone")) {
                to = "purpur_stairs";
            } else if (name.equals("torch") || name.equals("wall_torch")) {
                to = name.equals("torch") ? "end_rod" : "end_rod";
                props = "";
            } else if (name.equals("lantern")) {
                to = "soul_lantern";
            } else {
                return block;
            }
        }
        if (to.equals("end_stone_bricks") || to.equals("end_stone") || to.equals("purpur_block")) {
            props = "";
        }
        if (to.equals("end_stone_brick_wall")) {
            props = "";
        }
        return "minecraft:" + to + props;
    }

    /** One possible lost forge per cell of chunks, on an outer island (far from the dragon's island). */
    private void endRuin(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region) {
        int size = endCell;
        int cellX = Math.floorDiv(chunkX, size);
        int cellZ = Math.floorDiv(chunkZ, size);
        SplittableRandom cell = new SplittableRandom(info.getSeed() * 31 + cellX * 73856093L + cellZ * 19349663L + 7);
        int pickX = 1 + cell.nextInt(size - 2);
        int pickZ = 1 + cell.nextInt(size - 2);
        if (Math.floorMod(chunkX, size) != pickX || Math.floorMod(chunkZ, size) != pickZ || cell.nextDouble() >= endChance) {
            return;
        }
        int bx = (chunkX << 4) + 2;
        int bz = (chunkZ << 4) + 2;
        if ((long) bx * bx + (long) bz * bz < (long) endMinDistance * endMinDistance) {
            return;
        }
        ForgeRuins.Type type = switch (cell.nextInt(3)) {
            case 0 -> ForgeRuins.Type.FORGE;
            case 1 -> ForgeRuins.Type.WORKSHOP;
            default -> ForgeRuins.Type.WATCHTOWER;
        };
        int cx = bx + type.width / 2;
        int cz = bz + type.depth / 2;
        // the island's top in the middle of the footprint
        int floor = Integer.MIN_VALUE;
        for (int y = 110; y >= 20; y--) {
            if (region.isInRegion(cx, y, cz) && region.getType(cx, y, cz) == Material.END_STONE) {
                floor = y;
                break;
            }
        }
        if (floor == Integer.MIN_VALUE) {
            return;
        }
        // most of the footprint has to stand on the island
        int grounded = 0;
        int total = 0;
        for (int x = bx; x < bx + type.width; x += 2) {
            for (int z = bz; z < bz + type.depth; z += 2) {
                total++;
                for (int y = floor + 2; y >= floor - 3; y--) {
                    if (region.isInRegion(x, y, z) && region.getType(x, y, z).isSolid()) {
                        grounded++;
                        break;
                    }
                }
            }
        }
        if (grounded * 10 < total * 8) {
            return;
        }
        ForgeRuins.Canvas base = canvas(region);
        ForgeRuins.Canvas end = new ForgeRuins.Canvas() {
            @Override
            public void set(int x, int y, int z, String block) {
                base.set(x, y, z, endBlock(block));
            }

            @Override
            public String get(int x, int y, int z) {
                return base.get(x, y, z);
            }
        };
        ForgeRuins.Result result = ForgeRuins.build(type, end, bx, floor + 1, bz, cell);
        long seed = cell.nextLong();
        for (int[] p : result.bigChests) {
            loot(region, p, lootBig, seed++);
        }
        for (int[] p : result.smallChests) {
            loot(region, p, lootSmall, seed++);
        }
        // shulkers keep watch instead of husks
        int n = 0;
        for (int[] p : result.guards) {
            if (n++ >= 2 || !region.isInRegion(p[0], p[1], p[2]) || !region.getType(p[0], p[1], p[2]).isAir()) {
                continue;
            }
            region.spawn(new Location(region.getWorld(), p[0] + 0.5, p[1], p[2] + 0.5), org.bukkit.entity.Shulker.class, sh -> {
                sh.setRemoveWhenFarAway(false);
                sh.setPersistent(true);
                sh.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    /** One possible Ancient Forge per cell of chunks, on an open floor between 32 and 100. */
    private void netherForge(WorldInfo info, int chunkX, int chunkZ, LimitedRegion region, Settings s) {
        boolean sure = false;
        for (int[] site : netherSites(info.getSeed())) {
            sure |= Math.floorDiv(site[0], 16) == chunkX && Math.floorDiv(site[1], 16) == chunkZ;
        }
        int size = s.netherCell();
        int cellX = Math.floorDiv(chunkX, size);
        int cellZ = Math.floorDiv(chunkZ, size);
        SplittableRandom cell = new SplittableRandom(info.getSeed() * 17 + cellX * 50331653L + cellZ * 1610612741L + 99);
        int pickX = 1 + cell.nextInt(size - 2);
        int pickZ = 1 + cell.nextInt(size - 2);
        boolean random = Math.floorMod(chunkX, size) == pickX && Math.floorMod(chunkZ, size) == pickZ
                && cell.nextDouble() < s.netherChance();
        if (!sure && !random) {
            return;
        }
        int bx = (chunkX << 4) - 4;
        int bz = (chunkZ << 4) - 4;
        int n = ForgeRuins.Type.ANCIENT_NETHER.width;
        // the lowest open floor (solid, with 8 free blocks above) in the middle of the footprint
        int cx = bx + n / 2;
        int cz = bz + n / 2;
        int floor = Integer.MIN_VALUE;
        for (int y = 32; y <= 100; y++) {
            if (!region.isInRegion(cx, y, cz) || !region.getType(cx, y, cz).isSolid()) {
                continue;
            }
            boolean open = true;
            for (int up = 1; up <= 8 && open; up++) {
                open = region.isInRegion(cx, y + up, cz) && region.getType(cx, y + up, cz).isAir();
            }
            if (open) {
                floor = y;
                break;
            }
        }
        if (floor == Integer.MIN_VALUE) {
            if (!sure) {
                return;
            }
            floor = 64;                                      // guaranteed: carve a hall into the rock if there's no floor
        }
        // most of the footprint needs ground close below the floor (no forge hanging over a lava sea)
        int grounded = 0;
        int total = 0;
        for (int x = bx; x < bx + n; x += 2) {
            for (int z = bz; z < bz + n; z += 2) {
                total++;
                for (int y = floor; y >= floor - 4; y--) {
                    if (region.isInRegion(x, y, z) && region.getType(x, y, z).isSolid()) {
                        grounded++;
                        break;
                    }
                }
            }
        }
        if (grounded * 10 < total * 7 && !sure) {
            return;
        }
        ForgeRuins.Result result = ForgeRuins.build(ForgeRuins.Type.ANCIENT_NETHER, canvas(region), bx, floor, bz, cell);
        finish(info, region, result, s, cell, true);
    }

    private void loot(LimitedRegion region, int[] p, LootTable table, long seed) {
        if (table == null || !region.isInRegion(p[0], p[1], p[2])) {
            return;
        }
        BlockState state = region.getBlockState(p[0], p[1], p[2]);
        if (state instanceof Chest chest) {
            chest.setLootTable(table, seed);
            region.setBlockState(p[0], p[1], p[2], chest);
        }
    }

    /** A ruin guard: a husk in battered armor that doesn't despawn (a wither skeleton in the Nether). */
    private void guard(LimitedRegion region, int[] p, boolean nether) {
        if (!region.isInRegion(p[0], p[1], p[2]) || !region.getType(p[0], p[1], p[2]).isAir()) {
            return;
        }
        Location at = new Location(region.getWorld(), p[0] + 0.5, p[1], p[2] + 0.5);
        if (nether) {
            region.spawn(at, org.bukkit.entity.WitherSkeleton.class, ws -> {
                ws.setRemoveWhenFarAway(false);
                ws.setPersistent(true);
                ws.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);
            });
            return;
        }
        region.spawn(at, Husk.class, husk -> {
            husk.setRemoveWhenFarAway(false);
            husk.setPersistent(true);
            husk.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);
            var eq = husk.getEquipment();
            eq.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            eq.setItemInMainHand(new ItemStack(Material.IRON_AXE));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                eq.setDropChance(slot, 0.05f);
            }
        });
    }

    // ================================================================ extra loot

    /** Ruin chests can also hold Bounty Tokens and, rarely, a legendary material. */
    @EventHandler
    public void onLoot(LootGenerateEvent event) {
        if (event.getLootTable() == null || !"forgebound".equals(event.getLootTable().getKey().getNamespace())
                || plugin.bounty() == null) {
            return;
        }
        boolean big = event.getLootTable().getKey().equals(LOOT_BIG);
        var c = plugin.getConfig();
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        if (plugin.shop() != null && plugin.shop().enabled()
                && r.nextDouble() * 100 < c.getDouble("scorched-lands.loot.token-chance", big ? 35 : 15)) {
            int min = c.getInt("scorched-lands.loot.tokens-min", 1);
            int max = Math.max(min, c.getInt("scorched-lands.loot.tokens-max", big ? 3 : 2));
            event.getLoot().add(plugin.bounty().token(min + r.nextInt(max - min + 1)));
        }
        if (big && r.nextDouble() * 100 < c.getDouble("scorched-lands.loot.material-chance", 3)) {
            List<ItemManager.Entry> mats = plugin.bounty().enabledMaterials();
            if (!mats.isEmpty()) {
                event.getLoot().add(mats.get(r.nextInt(mats.size())).item.clone());
            }
        }
    }

    // ================================================================ /forge scorched

    /** Tells an admin where the nearest Scorched Lands are (also in chunks nobody has generated yet). */
    public void locate(CommandSender sender) {
        // from the console: searched from 0,0 of the main world
        World w = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
        if (!wanted(w) || !settings.enabled()) {
            sender.sendMessage(ItemManager.color("&7The Scorched Lands don't generate in this world."));
            return;
        }
        Location at = sender instanceof Player p ? p.getLocation() : new Location(w, 0, 0, 0);
        int[] found = noise(w.getSeed()).nearest(at.getBlockX(), at.getBlockZ(), 30_000);
        if (found == null) {
            sender.sendMessage(ItemManager.color("&7No Scorched Lands within 30 000 blocks."));
            return;
        }
        int dist = (int) Math.hypot(found[0] - at.getBlockX(), found[1] - at.getBlockZ());
        sender.sendMessage(ItemManager.color("&6☀ Scorched Lands &7at &f" + found[0] + " ~ " + found[1]
                        + " &8(" + dist + " blocks) &e[tp]")
                .clickEvent(ClickEvent.suggestCommand("/tp @s " + found[0] + " 200 " + found[1])));
        sender.sendMessage(ItemManager.color("&8Only chunks generated from now on are scorched; explored land stays as it is."));
    }
}
