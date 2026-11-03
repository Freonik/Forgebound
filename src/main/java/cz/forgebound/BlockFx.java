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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ability visuals made of real blocks (block displays) instead of particles:
 * - rope: one thin stretched block from a to b (tongues, silk threads), redrawn every tick while it exists
 * - beam: the same, but it shows for a few ticks and goes (a golden shot)
 * - spike: a block that grows out of the ground and sinks back (ice spikes)
 * - debris: a small chunk of a block that flies, falls and disappears (rocks torn out of the ground)
 * Nothing here is saved with the world; whatever isn't redrawn or has lived its time removes itself.
 */
final class BlockFx {

    private static final Map<Object, Rope> ROPES = new HashMap<>();
    private static boolean started;
    private static long tick;

    private BlockFx() {
    }

    private static final class Rope {
        BlockDisplay display;
        long lastDrawn;
    }

    private static void start(Plugin plugin) {
        if (started) {
            return;
        }
        started = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            for (Iterator<Rope> it = ROPES.values().iterator(); it.hasNext(); ) {
                Rope r = it.next();
                if (tick - r.lastDrawn > 3 || r.display == null || !r.display.isValid()) {
                    if (r.display != null) {
                        r.display.remove();
                    }
                    it.remove();
                }
            }
        }, 1L, 1L);
    }

    static void clear() {
        ROPES.values().forEach(r -> {
            if (r.display != null) {
                r.display.remove();
            }
        });
        ROPES.clear();
    }

    /** The transformation that stretches a block into a bar of this thickness from the display's spot along dir. */
    private static Transformation bar(Vector dir, float thickness) {
        float length = (float) dir.length();
        Vector3f d = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()).normalize();
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d);
        Vector3f offset = rot.transform(new Vector3f(-thickness / 2, 0, -thickness / 2));
        return new Transformation(offset, rot, new Vector3f(thickness, length, thickness), new Quaternionf());
    }

    private static BlockDisplay spawn(Location at, BlockData block, Transformation t, int brightness) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        return at.getWorld().spawn(l, BlockDisplay.class, e -> {
            e.setBlock(block);
            e.setPersistent(false);
            e.setTransformation(t);
            e.setTeleportDuration(1);
            if (brightness >= 0) {
                e.setBrightness(new Display.Brightness(brightness, brightness));
            }
        });
    }

    /** A thin bar from a to b; call it again every tick it should stay. */
    static void rope(Plugin plugin, Object key, Location a, Location b, BlockData block, float thickness) {
        if (a.getWorld() == null || a.getWorld() != b.getWorld()) {
            remove(key);
            return;
        }
        Vector dir = b.toVector().subtract(a.toVector());
        if (dir.lengthSquared() < 0.0025) {
            return;
        }
        start(plugin);
        Rope r = ROPES.computeIfAbsent(key, k -> new Rope());
        r.lastDrawn = tick;
        Transformation t = bar(dir, thickness);
        if (r.display == null || !r.display.isValid()) {
            r.display = spawn(a, block, t, 13);
        } else {
            Location l = a.clone();
            l.setYaw(0);
            l.setPitch(0);
            r.display.teleport(l);
            r.display.setTransformation(t);
        }
    }

    static void remove(Object key) {
        Rope r = ROPES.remove(key);
        if (r != null && r.display != null) {
            r.display.remove();
        }
    }

    /** A bar from a to b that shows for a few ticks and fades (gets thinner) away. */
    static void beam(Plugin plugin, Location a, Location b, BlockData block, float thickness, int ticks) {
        Vector dir = b.toVector().subtract(a.toVector());
        if (a.getWorld() == null || dir.lengthSquared() < 0.0025) {
            return;
        }
        BlockDisplay d = spawn(a, block, bar(dir, thickness), 15);
        d.setInterpolationDuration(ticks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) {
                d.setInterpolationDelay(0);
                d.setTransformation(bar(dir, 0.001f));
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, ticks + 1L);
    }

    /** A block that grows out of the ground at base (its bottom), stays and sinks back. */
    static void spike(Plugin plugin, Location base, BlockData block, float width, float height, int lifeTicks) {
        if (base.getWorld() == null) {
            return;
        }
        float tilt = (float) Math.toRadians(ThreadLocalRandom.current().nextDouble(-14, 14));
        Quaternionf rot = new Quaternionf().rotateY((float) (ThreadLocalRandom.current().nextDouble() * Math.PI * 2)).rotateX(tilt);
        Vector3f low = new Vector3f(width, 0.01f, width);
        Vector3f off = rot.transform(new Vector3f(-width / 2, 0, -width / 2));
        BlockDisplay d = spawn(base.clone().add(0, -0.05, 0), block, new Transformation(off, rot, low, new Quaternionf()), -1);
        d.setInterpolationDuration(3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) {
                d.setInterpolationDelay(0);
                d.setTransformation(new Transformation(off, rot, new Vector3f(width, height, width), new Quaternionf()));
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) {
                d.setInterpolationDuration(6);
                d.setInterpolationDelay(0);
                d.setTransformation(new Transformation(off, rot, low, new Quaternionf()));
            }
        }, Math.max(5, lifeTicks - 6));
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, lifeTicks + 1L);
    }

    /** A small chunk of a block thrown with this velocity (blocks per tick); it falls, tumbles and goes. */
    static void debris(Plugin plugin, Location from, BlockData block, Vector velocity, int lifeTicks) {
        World world = from.getWorld();
        if (world == null || block.getMaterial().isAir()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float size = (float) r.nextDouble(0.25, 0.45);
        BlockDisplay d = spawn(from, block, new Transformation(new Vector3f(-size / 2), new Quaternionf(),
                new Vector3f(size), new Quaternionf()), -1);
        Location pos = from.clone();
        pos.setYaw(0);
        pos.setPitch(0);
        Vector v = velocity.clone();
        float spinX = (float) r.nextDouble(-0.4, 0.4);
        float spinZ = (float) r.nextDouble(-0.4, 0.4);
        Quaternionf spin = new Quaternionf();
        int[] age = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            age[0]++;
            if (!d.isValid() || age[0] > lifeTicks) {
                d.remove();
                task.cancel();
                return;
            }
            pos.add(v);
            v.setY(v.getY() - 0.06);
            v.multiply(0.97);
            if (pos.getBlock().getType().isSolid()) {            // landed
                v.zero();
                pos.setY(Math.floor(pos.getY()) + 1 + size / 2);
            }
            spin.rotateX(spinX).rotateZ(spinZ);
            float s = age[0] > lifeTicks - 5 ? size * (lifeTicks - age[0]) / 5f : size;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(1);
            d.setTransformation(new Transformation(new Vector3f(-s / 2), new Quaternionf(spin), new Vector3f(Math.max(0.01f, s)),
                    new Quaternionf()));
            d.teleport(pos);
        }, 1L, 1L);
    }

    static BlockData data(Material m) {
        return m.createBlockData();
    }
}
