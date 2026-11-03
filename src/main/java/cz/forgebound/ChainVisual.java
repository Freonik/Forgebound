package cz.forgebound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Real chains instead of grey particle lines (grappling hook, pinning bolt): a row of iron chain blocks
 * (block displays, one per block of length) turned along the line. Each chain is drawn again every tick it should
 * exist; one that isn't redrawn for a few ticks removes itself.
 */
final class ChainVisual {

    private static final Map<Object, ChainVisual> CHAINS = new HashMap<>();
    private static boolean started;
    private static final BlockData CHAIN = Material.IRON_CHAIN.createBlockData();

    private final List<BlockDisplay> links = new ArrayList<>();
    private long lastDrawn;
    private long tick;

    private ChainVisual() {
    }

    /** Draws (or moves) the chain with this key from a to b. */
    static void draw(Plugin plugin, Object key, Location a, Location b) {
        if (a.getWorld() == null || a.getWorld() != b.getWorld()) {
            remove(key);
            return;
        }
        start(plugin);
        ChainVisual c = CHAINS.computeIfAbsent(key, k -> new ChainVisual());
        c.lastDrawn = c.tick;
        c.update(a, b);
    }

    static void remove(Object key) {
        ChainVisual c = CHAINS.remove(key);
        if (c != null) {
            c.links.forEach(BlockDisplay::remove);
        }
    }

    private static void start(Plugin plugin) {
        if (started) {
            return;
        }
        started = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Iterator<ChainVisual> it = CHAINS.values().iterator(); it.hasNext(); ) {
                ChainVisual c = it.next();
                c.tick++;
                if (c.tick - c.lastDrawn > 4) {                  // nobody draws it any more
                    c.links.forEach(BlockDisplay::remove);
                    it.remove();
                }
            }
        }, 1L, 1L);
    }

    /** Removes every chain (plugin disable). */
    static void clear() {
        CHAINS.values().forEach(c -> c.links.forEach(BlockDisplay::remove));
        CHAINS.clear();
    }

    private void update(Location a, Location b) {
        World world = a.getWorld();
        Vector dir = b.toVector().subtract(a.toVector());
        double length = dir.length();
        if (length < 0.05) {
            return;
        }
        int count = Math.max(1, (int) Math.ceil(length));
        double each = length / count;                            // every link the same length, a bit stretched
        Vector3f d = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()).normalize();
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d);
        Vector3f centre = rot.transform(new Vector3f(-0.5f, 0, -0.5f));      // turn around the chain's middle
        while (links.size() > count) {
            links.remove(links.size() - 1).remove();
        }
        for (int i = 0; i < count; i++) {
            Location at = a.clone().add(dir.clone().multiply(i * each / length));
            at.setYaw(0);
            at.setPitch(0);
            Transformation t = new Transformation(centre, rot, new Vector3f(1, (float) each, 1), new Quaternionf());
            if (i < links.size() && links.get(i).isValid()) {
                BlockDisplay link = links.get(i);
                link.teleport(at);
                link.setTransformation(t);
            } else {
                BlockDisplay link = world.spawn(at, BlockDisplay.class, e -> {
                    e.setBlock(CHAIN);
                    e.setPersistent(false);
                    e.setTransformation(t);
                    e.setTeleportDuration(2);
                    e.setBrightness(new Display.Brightness(12, 12));
                });
                if (i < links.size()) {
                    links.set(i, link);
                } else {
                    links.add(link);
                }
            }
        }
    }
}
