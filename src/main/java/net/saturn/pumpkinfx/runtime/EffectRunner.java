package net.saturn.pumpkinfx.runtime;

import net.saturn.pumpkinfx.model.DirectionMode;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EmitterDefinition;
import net.saturn.pumpkinfx.model.ShapeSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class EffectRunner {
    private static final int MAX_PER_EMITTER = 200;
    private static final int MAX_PER_TICK = 400;

    private final EffectDefinition effect;
    private final RandomGenerator rng;
    private final double[] accumulators;
    private int tick;

    public EffectRunner(EffectDefinition effect, RandomGenerator rng) {
        this.effect = effect;
        this.rng = rng;
        this.accumulators = new double[effect.emitters.size()];
    }

    public int tick() {
        return tick;
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

        accumulators[index] += Math.max(0f, emitter.rate.evaluate(progress));
        int count = (int) Math.floor(accumulators[index]);
        accumulators[index] -= count;

        if (emitter.burstCount > 0 && inCycle == emitter.burstTick) {
            count += emitter.burstCount;
        }
        count = Math.min(count, MAX_PER_EMITTER);

        for (int n = 0; n < count && out.size() < MAX_PER_TICK; n++) {
            out.add(spawnOne(emitter, progress));
        }
    }

    private Spawn spawnOne(EmitterDefinition emitter, float progress) {
        ShapeSampler.Sample sample = ShapeSampler.sample(emitter, rng);

        double dx = sample.dx();
        double dy = sample.dy();
        double dz = sample.dz();
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
        speed *= 1.0 + (rng.nextDouble() * 2.0 - 1.0) * emitter.speedRandom;

        int rgb = emitter.useColor ? emitter.color.evaluate(progress) : 0xFFFFFF;
        float size = emitter.useColor ? emitter.size.evaluate(progress) : 1f;

        return new Spawn(emitter.particle,
                sample.x() + emitter.offsetX,
                sample.y() + emitter.offsetY,
                sample.z() + emitter.offsetZ,
                dx * speed, dy * speed, dz * speed,
                emitter.useColor, rgb, size);
    }
}
