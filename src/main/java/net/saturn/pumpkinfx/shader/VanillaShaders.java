package net.saturn.pumpkinfx.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Reads the game's own shader sources from the client jar, so they can be wrapped rather than guessed. */
public final class VanillaShaders {
    /** Where particle.fsh lives inside the game jar, and where the patched copy goes in the pack. */
    public static final String PARTICLE_FSH = "assets/minecraft/shaders/core/particle.fsh";

    private VanillaShaders() {
    }

    public static Optional<String> particleFragment() {
        return read(PARTICLE_FSH);
    }

    static Optional<String> read(String path) {
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                VanillaShaders.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(path)) {
                if (in != null) {
                    return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        }
        return Optional.empty();
    }
}
