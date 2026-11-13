package net.saturn.pumpkinfx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.runtime.EffectRunner;
import net.saturn.pumpkinfx.runtime.Spawn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

public final class EffectPreview {
    private static final List<Instance> ACTIVE = new ArrayList<>();
    private static final RandomGenerator RNG = RandomGenerator.getDefault();
    private static final double FALLBACK_DISTANCE = 4.0;
    private static final int MAX_INSTANCES = 16;

    private static EffectDefinition last;

    private record Instance(EffectRunner runner, Vec3 origin) {
    }

    private EffectPreview() {
    }

    public static EffectDefinition last() {
        return last;
    }

    public static boolean isPlaying() {
        return !ACTIVE.isEmpty();
    }

    public static void play(EffectDefinition effect) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        last = effect;
        if (ACTIVE.size() >= MAX_INSTANCES) {
            ACTIVE.remove(0);
        }
        EffectDefinition snapshot = effect.copy();
        EffectRunner runner = snapshot.seed != 0L
                ? EffectRunner.seeded(snapshot, snapshot.seed)
                : new EffectRunner(snapshot, RNG);
        ACTIVE.add(new Instance(runner, anchor(mc)));
    }

    public static void replay() {
        if (last != null) {
            play(last);
        }
    }

    public static void stop() {
        ACTIVE.clear();
    }

    public static void tick(Minecraft mc) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Iterator<Instance> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Instance instance = it.next();
            for (Spawn spawn : instance.runner().step()) {
                ParticleOptions options = ParticleResolver.resolve(spawn);
                if (options == null) {
                    continue;
                }
                mc.level.addParticle(options,
                        instance.origin().x + spawn.x(),
                        instance.origin().y + spawn.y(),
                        instance.origin().z + spawn.z(),
                        spawn.vx(), spawn.vy(), spawn.vz());
            }
            if (instance.runner().finished()) {
                it.remove();
            }
        }
    }

    private static Vec3 anchor(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return mc.player.getEyePosition().add(mc.player.getLookAngle().scale(FALLBACK_DISTANCE));
    }
}
