package net.saturn.pumpkinfx.graph;

public final class Noise {
    private Noise() {
    }

    public static double value3(double x, double y, double z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double u = fade(x - xi);
        double v = fade(y - yi);
        double w = fade(z - zi);

        double c000 = lattice(xi, yi, zi);
        double c100 = lattice(xi + 1, yi, zi);
        double c010 = lattice(xi, yi + 1, zi);
        double c110 = lattice(xi + 1, yi + 1, zi);
        double c001 = lattice(xi, yi, zi + 1);
        double c101 = lattice(xi + 1, yi, zi + 1);
        double c011 = lattice(xi, yi + 1, zi + 1);
        double c111 = lattice(xi + 1, yi + 1, zi + 1);

        double x00 = c000 + (c100 - c000) * u;
        double x10 = c010 + (c110 - c010) * u;
        double x01 = c001 + (c101 - c001) * u;
        double x11 = c011 + (c111 - c011) * u;
        double y0 = x00 + (x10 - x00) * v;
        double y1 = x01 + (x11 - x01) * v;
        return y0 + (y1 - y0) * w;
    }

    /** Layered value noise, normalised so the result stays in -1..1. Octaves are clamped to 1-8. */
    public static double fbm3(double x, double y, double z, int octaves) {
        int count = Math.max(1, Math.min(8, octaves));
        double sum = 0;
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        for (int i = 0; i < count; i++) {
            sum += amplitude * value3(x * frequency, y * frequency, z * frequency);
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return sum / total;
    }

    public static double hash01(long seed, long salt) {
        long z = seed + salt * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lattice(int i, int j, int k) {
        // The constant keeps (0,0,0) from hashing to 0, which made noise at the origin always -1.
        long h = i * 374761393L + j * 668265263L + k * 2147483647L + 0x632BE59BD9B4E019L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return ((h & 0xFFFFFFL) / (double) 0xFFFFFFL) * 2.0 - 1.0;
    }
}
