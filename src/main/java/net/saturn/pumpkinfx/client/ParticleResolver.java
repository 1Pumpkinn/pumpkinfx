package net.saturn.pumpkinfx.client;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.saturn.pumpkinfx.PumpkinFxClient;
import net.saturn.pumpkinfx.runtime.Spawn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ParticleResolver {
    private static final String DUST = "minecraft:dust";
    private static final Map<String, Optional<ParticleOptions>> CACHE = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private ParticleResolver() {
    }

    public static ParticleOptions resolve(Spawn spawn) {
        if (spawn.colored() && DUST.equals(spawn.particle())) {
            float scale = Math.max(0.01f, Math.min(4f, spawn.size()));
            return new DustParticleOptions(spawn.rgb() & 0xFFFFFF, scale);
        }
        Optional<ParticleOptions> found = CACHE.computeIfAbsent(spawn.particle(), ParticleResolver::lookup);
        if (found.isEmpty() && WARNED.add(spawn.particle())) {
            PumpkinFxClient.LOGGER.warn("Particle '{}' is unknown or needs extra options; skipped", spawn.particle());
        }
        return found.orElse(null);
    }

    public static boolean isKnown(String id) {
        return lookup(id).isPresent() || DUST.equals(id);
    }

    private static Optional<ParticleOptions> lookup(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return Optional.empty();
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(parsed);
        if (type instanceof SimpleParticleType simple) {
            return Optional.of(simple);
        }
        return Optional.empty();
    }
}
