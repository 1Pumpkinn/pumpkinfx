package net.saturn.pumpkinfx.model;

import java.util.random.RandomGenerator;

public final class ShapeSampler {
    public record Sample(double x, double y, double z, double dx, double dy, double dz) {
    }

    private ShapeSampler() {
    }

    public static Sample sample(EmitterDefinition emitter, RandomGenerator rng) {
        return sample(emitter.shape, emitter.shapeX, emitter.shapeY, emitter.shapeZ,
                emitter.coneAngle, emitter.surfaceOnly, rng);
    }

    public static Sample sample(EmitterShape shape, double sx, double sy, double sz,
                                double coneAngleDeg, boolean surfaceOnly, RandomGenerator rng) {
        return switch (shape) {
            case POINT -> {
                double[] d = randomUnit(rng);
                yield new Sample(0, 0, 0, d[0], d[1], d[2]);
            }
            case SPHERE -> sphere(sx, sy, sz, surfaceOnly, false, rng);
            case HEMISPHERE -> sphere(sx, sy, sz, surfaceOnly, true, rng);
            case CONE -> cone(sx, coneAngleDeg, surfaceOnly, rng);
            case BOX -> box(sx, sy, sz, surfaceOnly, rng);
            case RING -> {
                double a = rng.nextDouble() * Math.PI * 2.0;
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                yield new Sample(cos * sx, 0, sin * sz, cos, 0, sin);
            }
            case LINE -> new Sample((rng.nextDouble() * 2.0 - 1.0) * sx, 0, 0, 0, 1, 0);
        };
    }

    private static Sample sphere(double sx, double sy, double sz, boolean surfaceOnly,
                                 boolean hemisphere, RandomGenerator rng) {
        double[] d = randomUnit(rng);
        if (hemisphere) {
            d[1] = Math.abs(d[1]);
        }
        double r = surfaceOnly ? 1.0 : Math.cbrt(rng.nextDouble());
        return new Sample(d[0] * r * sx, d[1] * r * sy, d[2] * r * sz, d[0], d[1], d[2]);
    }

    private static Sample cone(double baseRadius, double angleDeg, boolean surfaceOnly, RandomGenerator rng) {
        double angle = Math.toRadians(Math.max(0.0, Math.min(90.0, angleDeg)));
        double cosTheta = 1.0 - rng.nextDouble() * (1.0 - Math.cos(angle));
        double sinTheta = Math.sqrt(Math.max(0.0, 1.0 - cosTheta * cosTheta));
        double phi = rng.nextDouble() * Math.PI * 2.0;
        double dx = sinTheta * Math.cos(phi);
        double dz = sinTheta * Math.sin(phi);

        double baseAngle = rng.nextDouble() * Math.PI * 2.0;
        double r = surfaceOnly ? baseRadius : baseRadius * Math.sqrt(rng.nextDouble());
        return new Sample(Math.cos(baseAngle) * r, 0, Math.sin(baseAngle) * r, dx, cosTheta, dz);
    }

    private static Sample box(double sx, double sy, double sz, boolean surfaceOnly, RandomGenerator rng) {
        double x = (rng.nextDouble() * 2.0 - 1.0) * sx;
        double y = (rng.nextDouble() * 2.0 - 1.0) * sy;
        double z = (rng.nextDouble() * 2.0 - 1.0) * sz;
        if (surfaceOnly) {
            double sign = rng.nextBoolean() ? 1.0 : -1.0;
            switch (rng.nextInt(3)) {
                case 0 -> x = sign * sx;
                case 1 -> y = sign * sy;
                default -> z = sign * sz;
            }
        }
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-6) {
            return new Sample(x, y, z, 0, 1, 0);
        }
        return new Sample(x, y, z, x / len, y / len, z / len);
    }

    private static double[] randomUnit(RandomGenerator rng) {
        double y = rng.nextDouble() * 2.0 - 1.0;
        double phi = rng.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new double[]{r * Math.cos(phi), y, r * Math.sin(phi)};
    }
}
