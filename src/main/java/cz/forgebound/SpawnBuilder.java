package cz.forgebound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * /forge spawnbuild: builds the Forge Citadel (spawn_build.txt.gz, made by art/spawn_build.py) around the admin:
 * the floor goes under their feet, the air above the plaza is cleared, the ground under it filled. A few thousand
 * blocks per tick, so the server keeps running. Everything it changed is saved, /forge spawnbuild undo puts it back.
 */
public final class SpawnBuilder {

    private static final int RADIUS = 36;          // cleared / filled around the center
    private static final int CLEAR_HEIGHT = 34;
    private static final int FILL_DEPTH = 24;
    private static final int PER_TICK = 3000;

    private final Forgebound plugin;
    private final File undoFile;
    private BukkitTask task;
    /** changes of the build running now (saved when it ends, is cancelled or the server stops) */
    private List<String> running;
    private final Map<UUID, Long> confirm = new HashMap<>();

    public SpawnBuilder(Forgebound plugin) {
        this.plugin = plugin;
        this.undoFile = new File(plugin.getDataFolder(), "spawn-build-undo.txt.gz");
    }

    private record Plan(Map<Long, BlockData> blocks, Map<Long, String[]> signs) {
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    private static int kx(long k) {
        return (int) (k >> 40) - 512;
    }

    private static int ky(long k) {
        return (int) ((k >> 20) & 0xFFFFF) - 512;
    }

    private static int kz(long k) {
        return (int) (k & 0xFFFFF) - 512;
    }

    private Plan load() throws IOException {
        Map<Integer, BlockData> palette = new HashMap<>();
        Map<Long, BlockData> blocks = new LinkedHashMap<>();
        Map<Long, String[]> signs = new LinkedHashMap<>();
        try (InputStream in = plugin.getResource("spawn_build.txt.gz")) {
            if (in == null) {
                throw new IOException("spawn_build.txt.gz is missing from the plugin");
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("P ")) {
                    String[] p = line.split(" ", 3);
                    palette.put(Integer.parseInt(p[1]), Bukkit.createBlockData(p[2]));
                } else if (line.startsWith("B ")) {
                    String[] p = line.split(" ");
                    blocks.put(key(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])),
                            palette.get(Integer.parseInt(p[4])));
                } else if (line.startsWith("S ")) {
                    String[] p = line.split(" ", 5);
                    signs.put(key(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])),
                            (p.length > 4 ? p[4] : "").split("\\|", -1));
                }
            }
        }
        return new Plan(blocks, signs);
    }

    /** /forge spawnbuild [confirm|undo|cancel] */
    public void command(CommandSender sender, String what) {
        switch (what) {
            case "undo" -> undo(sender);
            case "cancel" -> {
                if (task == null) {
                    sender.sendMessage(ItemManager.color("&7Nothing is being built."));
                } else {
                    task.cancel();
                    task = null;
                    if (running != null) {
                        saveUndo(running);
                        running = null;
                    }
                    sender.sendMessage(ItemManager.color("&eStopped. &7/forge spawnbuild undo puts back what was already changed."));
                }
            }
            case "confirm" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ItemManager.color("&cOnly players can use this command."));
                    return;
                }
                Long asked = confirm.remove(p.getUniqueId());
                if (asked == null || System.currentTimeMillis() - asked > 60_000) {
                    sender.sendMessage(ItemManager.color("&7Run &f/forge spawnbuild &7first."));
                    return;
                }
                build(p);
            }
            default -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ItemManager.color("&cOnly players can use this command."));
                    return;
                }
                confirm.put(p.getUniqueId(), System.currentTimeMillis());
                p.sendMessage(ItemManager.color("&6⚒ Forge Citadel &7- a round plaza about &f70×70&7 with the ceremony circle "
                        + "in the middle, centered on &fyour feet&7 (stand where the season should start)."));
                p.sendMessage(ItemManager.color("&7Everything within &f" + RADIUS + " &7blocks and &f" + CLEAR_HEIGHT
                        + " &7up is cleared and the ground below is filled. Chests in the area lose their items. "
                        + "&f/forge spawnbuild undo &7puts the old blocks back."));
                p.sendMessage(ItemManager.color("&eType &f/forge spawnbuild confirm &ewithin a minute."));
            }
        }
    }

    private void build(Player admin) {
        if (task != null) {
            admin.sendMessage(ItemManager.color("&7It's already building &8(/forge spawnbuild cancel)"));
            return;
        }
        Plan plan;
        try {
            plan = load();
        } catch (Exception ex) {
            admin.sendMessage(ItemManager.color("&cCouldn't load the build: &7" + ex.getMessage()));
            return;
        }
        World world = admin.getWorld();
        Location feet = admin.getLocation();
        int ox = feet.getBlockX();
        int oy = feet.getBlockY() - 1;              // the floor goes where the admin stands
        int oz = feet.getBlockZ();
        // the work list: clear above, fill below, then the build itself (bottom up)
        List<long[]> ops = new ArrayList<>();     // {key relative, kind} kind 0 = air, 1 = fill, 2 = plan
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (x * x + z * z > RADIUS * RADIUS) {
                    continue;
                }
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    if (!plan.blocks.containsKey(key(x, y, z))) {
                        ops.add(new long[]{key(x, y, z), 0});
                    }
                }
                for (int y = 0; y >= -FILL_DEPTH; y--) {
                    if (y == 0 && plan.blocks.containsKey(key(x, 0, z))) {
                        continue;
                    }
                    ops.add(new long[]{key(x, y, z), 1});
                }
            }
        }
        for (long k : plan.blocks.keySet()) {
            ops.add(new long[]{k, 2});
        }
        List<String> undo = new ArrayList<>();
        undo.add("W " + world.getName());
        running = undo;
        boolean[] filling = new boolean[1];
        int[] at = {0};
        admin.sendMessage(ItemManager.color("&6⚒ Building the Forge Citadel... &7(" + ops.size() / 1000 + "k steps)"));
        plugin.getLogger().info("[SpawnBuild] " + admin.getName() + " builds the citadel at " + world.getName() + " "
                + ox + " " + (oy + 1) + " " + oz);
        long[] lastColumn = {Long.MIN_VALUE};
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int done = 0;
            while (done < PER_TICK && at[0] < ops.size()) {
                long[] op = ops.get(at[0]++);
                long k = op[0];
                int x = kx(k), y = ky(k), z = kz(k);
                Block b = world.getBlockAt(ox + x, oy + y, oz + z);
                BlockData want;
                if (op[1] == 0) {
                    if (b.getType().isAir()) {
                        continue;
                    }
                    want = Material.AIR.createBlockData();
                } else if (op[1] == 1) {
                    // fill down until solid ground: a new column starts at y = 0
                    long column = ((long) x << 32) | (z & 0xFFFFFFFFL);
                    if (column != lastColumn[0]) {
                        lastColumn[0] = column;
                        filling[0] = true;
                    }
                    if (!filling[0]) {
                        continue;
                    }
                    if (b.getType().isSolid() && !b.getType().name().contains("LEAVES") && !b.getType().name().endsWith("_LOG")) {
                        filling[0] = false;
                        continue;
                    }
                    want = (y > -3 ? Material.DEEPSLATE_BRICKS : Material.COBBLED_DEEPSLATE).createBlockData();
                } else {
                    want = plan.blocks.get(k);
                }
                if (b.getBlockData().matches(want)) {
                    continue;
                }
                undo.add("B " + (ox + x) + " " + (oy + y) + " " + (oz + z) + " " + b.getBlockData().getAsString());
                b.setBlockData(want, false);
                done++;
            }
            if (at[0] >= ops.size()) {
                task.cancel();
                task = null;
                for (Map.Entry<Long, String[]> e : plan.signs.entrySet()) {
                    long k = e.getKey();
                    if (world.getBlockAt(ox + kx(k), oy + ky(k), oz + kz(k)).getState() instanceof Sign sign) {
                        String[] lines = e.getValue();
                        for (int i = 0; i < Math.min(4, lines.length); i++) {
                            sign.getSide(Side.FRONT).line(i, ItemManager.color(lines[i]));
                        }
                        sign.setWaxed(true);
                        sign.update(true, false);
                    }
                }
                saveUndo(undo);
                running = null;
                world.setSpawnLocation(ox, oy + 1, oz);
                if (plugin.protectedZones() != null) {
                    plugin.protectedZones().setSpawn(world, ox, oy, oz);
                }
                Player p = Bukkit.getPlayer(admin.getUniqueId());
                if (p != null) {
                    p.sendMessage(ItemManager.color("&a⚒ The Forge Citadel is done. &7World spawn = the center. "
                            + "Put the Questmaster in the east stall: stand at &f" + (ox + 24) + " " + (oy + 1) + " " + (oz - 10)
                            + "&7 and run &f/forge npc set&7."));
                }
                plugin.getLogger().info("[SpawnBuild] done, " + (undo.size() - 1) + " blocks changed");
            } else if (at[0] % (PER_TICK * 20) < PER_TICK) {
                Player p = Bukkit.getPlayer(admin.getUniqueId());
                if (p != null) {
                    p.sendActionBar(ItemManager.color("&6⚒ Building... &f" + (at[0] * 100L / ops.size()) + " %"));
                }
            }
        }, 1L, 1L);
    }

    private void saveUndo(List<String> lines) {
        try {
            plugin.getDataFolder().mkdirs();
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(undoFile)), StandardCharsets.UTF_8))) {
                for (String l : lines) {
                    w.write(l);
                    w.newLine();
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("[SpawnBuild] couldn't save the undo file: " + ex.getMessage());
        }
    }

    private void undo(CommandSender sender) {
        if (task != null) {
            sender.sendMessage(ItemManager.color("&7Wait until it's done or &f/forge spawnbuild cancel&7."));
            return;
        }
        if (!undoFile.isFile()) {
            sender.sendMessage(ItemManager.color("&7There's nothing to undo."));
            return;
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(undoFile)), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                lines.add(l);
            }
        } catch (IOException ex) {
            sender.sendMessage(ItemManager.color("&cCouldn't read the undo file: &7" + ex.getMessage()));
            return;
        }
        World world = lines.isEmpty() ? null : Bukkit.getWorld(lines.get(0).substring(2));
        if (world == null) {
            sender.sendMessage(ItemManager.color("&cThe world of the build doesn't exist."));
            return;
        }
        sender.sendMessage(ItemManager.color("&7Putting " + (lines.size() - 1) + " blocks back..."));
        int[] at = {lines.size() - 1};             // newest change first
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int done = 0;
            while (done++ < PER_TICK && at[0] >= 1) {
                String[] p = lines.get(at[0]--).split(" ", 5);
                world.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]))
                        .setBlockData(Bukkit.createBlockData(p[4]), false);
            }
            if (at[0] < 1) {
                task.cancel();
                task = null;
                if (plugin.protectedZones() != null) {
                    plugin.protectedZones().setSpawn(null, 0, 0, 0);
                }
                if (undoFile.delete()) {
                    sender.sendMessage(ItemManager.color("&aThe old blocks are back."));
                }
                plugin.getLogger().info("[SpawnBuild] undone");
            }
        }, 1L, 1L);
    }

    public void shutdown() {
        if (running != null) {
            saveUndo(running);
            running = null;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
