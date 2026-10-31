package cz.forgebound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Where the Scorched Lands are: smooth 2D noise from the world seed. A spot belongs to the biome when the noise
 * there is above a threshold; the threshold is picked so that about {@code rarity} of the world is scorched.
 * Pure Java (no server), so it can be tested and so /forge scorched can find areas that aren't generated yet.
 */
final class ScorchedNoise {

    private final int[] perm = new int[512];
    private final double scale;
    final double threshold;
    /** Guaranteed areas (centers) that are always scorched, whatever the noise says. */
    private List<int[]> sites = List.of();
    private double siteRadius;

    ScorchedNoise(long seed, double zoneSize, double rarity) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        SplittableRandom random = new SplittableRandom(seed ^ 0x5C0FC4EDL);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
        this.scale = Math.max(64, zoneSize);
        this.threshold = thresholdFor(Math.max(0.001, Math.min(0.5, rarity)));
    }

    /** Classic 2D Perlin noise, about -1..1. */
    private double perlin(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double xf = x - xi;
        double yf = y - yi;
        int X = xi & 255;
        int Y = yi & 255;
        double u = fade(xf);
        double v = fade(yf);
        int aa = perm[perm[X] + Y];
        int ab = perm[perm[X] + Y + 1];
        int ba = perm[perm[X + 1] + Y];
        int bb = perm[perm[X + 1] + Y + 1];
        double x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf));
        double x2 = lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1));
        return lerp(v, x1, x2) * 1.41;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        return switch (hash & 7) {
            case 0 -> x + y;
            case 1 -> -x + y;
            case 2 -> x - y;
            case 3 -> -x - y;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> y;
            default -> -y;
        };
    }

    /** The "scorchedness" of a block column. */
    double value(double blockX, double blockZ) {
        double x = blockX / scale;
        double z = blockZ / scale;
        double v = perlin(x, z) * 0.7 + perlin(x * 2.3 + 41.7, z * 2.3 - 19.3) * 0.3;
        for (int[] site : sites) {
            double dx = blockX - site[0];
            double dz = blockZ - site[1];
            double d2 = dx * dx + dz * dz;
            double reach = siteRadius * 1.3;
            if (d2 < reach * reach) {
                // a round-ish blob with a wobbly edge
                double r = siteRadius * (1 + 0.22 * perlin(blockX / 37.0 + 300, blockZ / 37.0 - 300));
                double d = Math.sqrt(d2);
                if (d < r) {
                    v = Math.max(v, threshold + 0.03 + 0.12 * (1 - (d / r) * (d / r)));
                }
            }
        }
        return v;
    }

    /** Makes these areas always scorched (radius in blocks). */
    void forceSites(List<int[]> centers, double radius) {
        this.sites = List.copyOf(centers);
        this.siteRadius = Math.max(24, radius);
    }

    /**
     * Places {@code count} spots between minDist and maxDist from (cx, cz), spread around the circle, from the seed
     * (the same seed always gives the same spots). salt keeps the overworld and nether spots apart.
     */
    static List<int[]> sites(long seed, long salt, int count, int minDist, int maxDist, int cx, int cz) {
        List<int[]> out = new ArrayList<>();
        if (count <= 0 || maxDist <= 0) {
            return out;
        }
        SplittableRandom r = new SplittableRandom(seed * 31 + salt);
        double base = r.nextDouble() * Math.PI * 2;
        int lo = Math.max(0, Math.min(minDist, maxDist));
        for (int i = 0; i < count; i++) {
            double a = base + i * Math.PI * 2 / count + (r.nextDouble() - 0.5) * Math.PI / count;
            double d = lo + r.nextDouble() * (maxDist - lo);
            out.add(new int[] {cx + (int) Math.round(Math.cos(a) * d), cz + (int) Math.round(Math.sin(a) * d)});
        }
        return out;
    }

    /** 0 = outside, 1 = deep inside; the edge (a thin band above the threshold) fades from 0 to 1. */
    double strength(double blockX, double blockZ) {
        double v = value(blockX, blockZ) - threshold;
        return v <= 0 ? 0 : Math.min(1, v / 0.03);
    }

    boolean inside(double blockX, double blockZ) {
        return value(blockX, blockZ) > threshold;
    }

    /** The noise value above which `rarity` of all points lie (sampled on a big grid). */
    private double thresholdFor(double rarity) {
        int n = 160;
        double[] samples = new double[n * n];
        int i = 0;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                samples[i++] = value(a * scale * 0.37 + 1234, b * scale * 0.37 - 5678);
            }
        }
        Arrays.sort(samples);
        return samples[(int) Math.min(samples.length - 1, Math.round((1 - rarity) * samples.length))];
    }

    /** The nearest scorched spot from (x, z), searching outwards in rings (null if none within maxRadius). */
    int[] nearest(int x, int z, int maxRadius) {
        return nearest(x, z, maxRadius, null);
    }

    /** Like {@link #nearest(int, int, int)}, skipping spots where {@code blocked} says another biome wins. */
    int[] nearest(int x, int z, int maxRadius, java.util.function.BiPredicate<Integer, Integer> blocked) {
        int step = (int) Math.max(16, scale / 16);
        for (int r = 0; r <= maxRadius; r += step) {
            int[] best = null;
            double bestDist = Double.MAX_VALUE;
            int points = Math.max(8, (int) (2 * Math.PI * r / step));
            for (int k = 0; k < points; k++) {
                double a = 2 * Math.PI * k / points;
                int px = x + (int) Math.round(Math.cos(a) * r);
                int pz = z + (int) Math.round(Math.sin(a) * r);
                if (value(px, pz) > threshold + 0.03 && (blocked == null || !blocked.test(px, pz))) {           // a bit inside, not on the very edge
                    double d = (double) (px - x) * (px - x) + (double) (pz - z) * (pz - z);
                    if (d < bestDist) {
                        bestDist = d;
                        best = new int[] {px, pz};
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }
}
