package net.saturn.pumpkinfx.model;

import net.saturn.pumpkinfx.graph.GraphDefinition;

public final class EmitterDefinition {
    public String name = "Emitter";
    public boolean enabled = true;

    public String particle = "minecraft:flame";

    public int startDelay = 0;
    public int duration = 40;
    public boolean loop = false;

    public EmitterShape shape = EmitterShape.POINT;
    public float shapeX = 1f;
    public float shapeY = 1f;
    public float shapeZ = 1f;
    public float coneAngle = 25f;
    public boolean surfaceOnly = false;

    public float offsetX = 0f;
    public float offsetY = 0f;
    public float offsetZ = 0f;

    public DirectionMode direction = DirectionMode.SHAPE;
    public float directionX = 0f;
    public float directionY = 1f;
    public float directionZ = 0f;

    public Curve rate = Curve.constant(2f);
    public Curve speed = Curve.constant(0.05f);
    public float speedRandom = 0.2f;

    public int burstCount = 0;
    public int burstTick = 0;

    public boolean useColor = false;
    public Gradient color = Gradient.solid(0xFFFFFF);
    public Curve size = Curve.constant(1f);

    public GraphDefinition graph = new GraphDefinition();

    public EmitterDefinition() {
    }

    public EmitterDefinition(String name) {
        this.name = name;
    }

    public void normalize() {
        if (name == null || name.isBlank()) {
            name = "Emitter";
        }
        if (particle == null || particle.isBlank()) {
            particle = "minecraft:flame";
        }
        if (shape == null) {
            shape = EmitterShape.POINT;
        }
        if (direction == null) {
            direction = DirectionMode.SHAPE;
        }
        if (rate == null || rate.keys == null || rate.keys.isEmpty()) {
            rate = Curve.constant(2f);
        }
        if (speed == null || speed.keys == null || speed.keys.isEmpty()) {
            speed = Curve.constant(0.05f);
        }
        if (size == null || size.keys == null || size.keys.isEmpty()) {
            size = Curve.constant(1f);
        }
        if (color == null || color.stops == null || color.stops.isEmpty()) {
            color = Gradient.solid(0xFFFFFF);
        }
        if (graph == null) {
            graph = new GraphDefinition();
        }
        graph.normalize();
        rate.sort();
        speed.sort();
        size.sort();
        color.sort();
        duration = Math.max(1, duration);
        startDelay = Math.max(0, startDelay);
        burstCount = Math.max(0, burstCount);
        burstTick = Math.max(0, burstTick);
        speedRandom = Math.max(0f, Math.min(1f, speedRandom));
        coneAngle = Math.max(0f, Math.min(90f, coneAngle));
    }

    public EmitterDefinition copy() {
        EmitterDefinition e = new EmitterDefinition(name);
        e.enabled = enabled;
        e.particle = particle;
        e.startDelay = startDelay;
        e.duration = duration;
        e.loop = loop;
        e.shape = shape;
        e.shapeX = shapeX;
        e.shapeY = shapeY;
        e.shapeZ = shapeZ;
        e.coneAngle = coneAngle;
        e.surfaceOnly = surfaceOnly;
        e.offsetX = offsetX;
        e.offsetY = offsetY;
        e.offsetZ = offsetZ;
        e.direction = direction;
        e.directionX = directionX;
        e.directionY = directionY;
        e.directionZ = directionZ;
        e.rate = rate.copy();
        e.speed = speed.copy();
        e.speedRandom = speedRandom;
        e.burstCount = burstCount;
        e.burstTick = burstTick;
        e.useColor = useColor;
        e.color = color.copy();
        e.size = size.copy();
        e.graph = graph.copy();
        return e;
    }
}
