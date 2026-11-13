package net.saturn.pumpkinfx.runtime;

public record Spawn(String particle, double x, double y, double z,
                    double vx, double vy, double vz,
                    boolean colored, int rgb, float size) {
}
