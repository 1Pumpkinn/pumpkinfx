package net.saturn.pumpkinfx.runtime;

import net.saturn.pumpkinfx.graph.CompiledGraph;
import net.saturn.pumpkinfx.graph.EvalContext;
import net.saturn.pumpkinfx.graph.NodeType;
import net.saturn.pumpkinfx.model.DirectionMode;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EmitterDefinition;
import net.saturn.pumpkinfx.model.ShapeSampler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

public final class EffectRunner {
    public static final int MAX_PER_EMITTER = 200;
    public static final int MAX_PER_TICK = 400;

    private final EffectDefinition effect;
    private final Long seed;
    private RandomGenerator rng;
    private final double[] accumulators;
    private final CompiledGraph[] graphs;
    private final List<String> warnings = new ArrayList<>();
    private int tick;

    public EffectRunner(EffectDefinition effect, RandomGenerator rng) {
        this(effect, rng, null);
    }

    /** A runner whose output is identical every time it is played or reset with the same seed. */
    public static EffectRunner seeded(EffectDefinition effect, long seed) {
        return new EffectRunner(effect, new SplittableRandom(seed), seed);
    }

    private EffectRunner(EffectDefinition effect, RandomGenerator rng, Long seed) {
        this.effect = effect;
        this.rng = rng;
        this.seed = seed;
        int count = effect.emitters.size();
        this.accumulators = new double[count];
        this.graphs = new CompiledGraph[count];
        for (int i = 0; i < count; i++) {
            CompiledGraph compiled = CompiledGraph.compile(effect.emitters.get(i).graph);
            graphs[i] = compiled;
            if (compiled != null) {
                String name = effect.emitters.get(i).name;
                compiled.warnings().forEach(w -> warnings.add(name + ": " + w));
            }
        }
    }

    public int tick() {
        return tick;
    }

    /** How far through the effect we are, 0-1. Loops wrap around. */
    public float progress() {
        int d = Math.max(1, effect.duration);
        return effect.loop ? (tick % d) / (float) d : Math.min(1f, tick / (float) d);
    }

    public boolean isSeeded() {
        return seed != null;
    }

    /** Rewinds to tick 0. Seeded runners then replay exactly as before; unseeded ones will differ. */
    public void reset() {
        tick = 0;
        Arrays.fill(accumulators, 0.0);
        if (seed != null) {
            rng = new SplittableRandom(seed);
        }
    }

    /**
     * Jumps to {@code target} ticks by silently simulating from the start (or from the current
     * tick when moving forward). Only reproducible for seeded runners.
     */
    public void seek(int target) {
        target = Math.max(0, target);
        if (target < tick) {
            reset();
        }
        while (tick < target && !finished()) {
            step();
        }
    }

    public List<String> warnings() {
        return warnings;
    }

    public boolean finished() {
        return !effect.loop && tick >= effect.duration;
    }

    public List<Spawn> step() {
        List<Spawn> out = new ArrayList<>();
        if (finished()) {
            return out;
        }
        int t = effect.loop ? tick % effect.duration : tick;
        for (int i = 0; i < effect.emitters.size(); i++) {
            EmitterDefinition emitter = effect.emitters.get(i);
            if (!emitter.enabled) {
                continue;
            }
            stepEmitter(i, emitter, t, out);
            if (out.size() >= MAX_PER_TICK) {
                break;
            }
        }
        tick++;
        return out;
    }

    private void stepEmitter(int index, EmitterDefinition emitter, int t, List<Spawn> out) {
        int local = t - emitter.startDelay;
        if (local < 0) {
            return;
        }
        int cycle = local / emitter.duration;
        if (cycle > 0 && !emitter.loop) {
            return;
        }
        int inCycle = local % emitter.duration;
        float progress = inCycle / (float) emitter.duration;
        CompiledGraph graph = graphs[index];

        double rate = emitter.rate.evaluate(progress);
        if (graph != null && graph.has(NodeType.OUT_RATE)) {
            rate = graph.evalScalar(NodeType.OUT_RATE, new EvalContext(progress, inCycle, 0, rng.nextLong()));
        }
        accumulators[index] += Math.max(0.0, rate);
        int count = (int) Math.floor(accumulators[index]);
        accumulators[index] -= count;

        if (emitter.burstCount > 0 && inCycle == emitter.burstTick) {
            count += emitter.burstCount;
        }
        count = Math.min(count, MAX_PER_EMITTER);

        for (int n = 0; n < count && out.size() < MAX_PER_TICK; n++) {
            out.add(spawnOne(emitter, graph, progress, inCycle, n));
        }
    }

    private Spawn spawnOne(EmitterDefinition emitter, CompiledGraph graph, float progress, int tickInCycle, int n) {
        ShapeSampler.Sample sample = ShapeSampler.sample(emitter, rng);

        double px = sample.x();
        double py = sample.y();
        double pz = sample.z();
        double dx = sample.dx();
        double dy = sample.dy();
        double dz = sample.dz();

        EvalContext ctx = null;
        if (graph != null) {
            ctx = new EvalContext(progress, tickInCycle, n, rng.nextLong());
            ctx.setShape(px, py, pz, dx, dy, dz);
        }

        if (emitter.direction == DirectionMode.UP) {
            dx = 0;
            dy = 1;
            dz = 0;
        } else if (emitter.direction == DirectionMode.FIXED) {
            double len = Math.sqrt(emitter.directionX * emitter.directionX
                    + emitter.directionY * emitter.directionY
                    + emitter.directionZ * emitter.directionZ);
            if (len < 1.0e-6) {
                dx = 0;
                dy = 1;
                dz = 0;
            } else {
                dx = emitter.directionX / len;
                dy = emitter.directionY / len;
                dz = emitter.directionZ / len;
            }
        }

        double speed = emitter.speed.evaluate(progress);
        double size = emitter.size.evaluate(progress);
        int rgb = emitter.useColor ? emitter.color.evaluate(progress) : 0xFFFFFF;
        boolean colored = emitter.useColor;

        if (graph != null) {
            if (graph.has(NodeType.OUT_POSITION)) {
                double[] v = graph.eval(NodeType.OUT_POSITION, ctx);
                px = v[0];
                py = v[1];
                pz = v[2];
            }
            if (graph.has(NodeType.OUT_DIRECTION)) {
                double[] v = graph.eval(NodeType.OUT_DIRECTION, ctx);
                double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                if (len < 1.0e-9) {
                    dx = 0;
                    dy = 1;
                    dz = 0;
                } else {
                    dx = v[0] / len;
                    dy = v[1] / len;
                    dz = v[2] / len;
                }
            }
            if (graph.has(NodeType.OUT_SPEED)) {
                speed = graph.evalScalar(NodeType.OUT_SPEED, ctx);
            }
            if (graph.has(NodeType.OUT_SIZE)) {
                size = graph.evalScalar(NodeType.OUT_SIZE, ctx);
                colored = true;
            }
            if (graph.has(NodeType.OUT_COLOR)) {
                double[] v = graph.eval(NodeType.OUT_COLOR, ctx);
                rgb = (channel(v[0]) << 16) | (channel(v[1]) << 8) | channel(v[2]);
                colored = true;
            }
        }

        speed *= 1.0 + (rng.nextDouble() * 2.0 - 1.0) * emitter.speedRandom;

        return new Spawn(emitter.particle,
                px + emitter.offsetX,
                py + emitter.offsetY,
                pz + emitter.offsetZ,
                dx * speed, dy * speed, dz * speed,
                colored, rgb, (float) size);
    }

    private static int channel(double v) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, v)) * 255.0);
    }
}
