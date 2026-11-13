package net.saturn.pumpkinfx.shader;

import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EmitterDefinition;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Decides what shader file, if any, goes into an exported pack. */
public final class ShaderExport {
    public record Entry(String path, byte[] data, String source) {
    }

    private ShaderExport() {
    }

    /**
     * particle.fsh is shared by every particle in the game, so a pack can carry only one patched
     * copy. The first enabled emitter with a shader output wins; any others are reported.
     *
     * @return the file to write, or null when nothing needs exporting or it could not be built
     */
    public static Entry plan(List<EffectDefinition> effects, Supplier<Optional<String>> vanilla, List<String> warnings) {
        String owner = null;
        EmitterDefinition chosen = null;
        for (EffectDefinition effect : effects) {
            for (EmitterDefinition emitter : effect.emitters) {
                if (!emitter.enabled || !ShaderGraphCompiler.hasShaderOutput(emitter.graph)) {
                    continue;
                }
                String label = effect.id + "/" + emitter.name;
                if (chosen == null) {
                    chosen = emitter;
                    owner = label;
                } else {
                    warnings.add("Shader of '" + label + "' skipped: a pack has one particle shader and '"
                            + owner + "' already uses it");
                }
            }
        }
        if (chosen == null) {
            return null;
        }

        final String label = owner;
        ShaderGraphCompiler.Result generated = ShaderGraphCompiler.compile(chosen.graph.copy());
        if (generated == null) {
            return null;
        }
        for (String w : generated.warnings()) {
            warnings.add("[" + label + "] shader: " + w);
        }

        Optional<String> source = vanilla.get();
        if (source.isEmpty()) {
            warnings.add("Could not read the game's particle.fsh, so the shader of '" + label + "' was not exported");
            return null;
        }
        try {
            ParticleShaderInjector.Result injected = ParticleShaderInjector.inject(source.get(), generated.glsl());
            injected.warnings().forEach(w -> warnings.add("[" + label + "] shader: " + w));
            return new Entry(VanillaShaders.PARTICLE_FSH, injected.source().getBytes(StandardCharsets.UTF_8), injected.source());
        } catch (IllegalArgumentException e) {
            warnings.add("Shader of '" + label + "' not exported: " + e.getMessage());
            return null;
        }
    }
}
