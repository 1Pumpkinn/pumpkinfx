package net.saturn.pumpkinfx.model;

import net.saturn.pumpkinfx.graph.CompiledGraph;
import net.saturn.pumpkinfx.graph.GraphDefinition;
import net.saturn.pumpkinfx.graph.NodeType;
import net.saturn.pumpkinfx.runtime.EffectRunner;
import net.saturn.pumpkinfx.shader.ShaderGraphCompiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Checks an effect for things that will silently not do what the author expects. Used by
 * export, the editor and {@code /pfx validate}, so every place reports problems the same way.
 */
public final class EffectValidator {
    private static final String DUST = "minecraft:dust";

    public enum Severity {
        ERROR, WARNING, INFO
    }

    /** {@code where} is the emitter name, or empty for effect-level issues. */
    public record Issue(Severity severity, String where, String message) {
        @Override
        public String toString() {
            return where.isEmpty() ? message : where + ": " + message;
        }
    }

    private EffectValidator() {
    }

    /**
     * @param particleKnown tells whether a particle id exists in the running game; pass null
     *                      where the registry is unavailable (the check is then skipped)
     */
    public static List<Issue> validate(EffectDefinition effect, Predicate<String> particleKnown) {
        List<Issue> issues = new ArrayList<>();
        if (effect.emitters == null || effect.emitters.isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "", "Effect has no emitters"));
            return issues;
        }

        boolean anyEnabled = false;
        Set<String> names = new HashSet<>();
        for (EmitterDefinition e : effect.emitters) {
            anyEnabled |= e.enabled;
            String where = e.name == null ? "" : e.name;
            if (!names.add(where)) {
                issues.add(new Issue(Severity.INFO, where, "Another emitter has the same name"));
            }
            if (e.enabled) {
                checkEmitter(effect, e, where, particleKnown, issues);
            }
        }
        if (!anyEnabled) {
            issues.add(new Issue(Severity.WARNING, "", "Every emitter is disabled, nothing will play"));
        }
        return issues;
    }

    public static boolean hasErrors(List<Issue> issues) {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    private static void checkEmitter(EffectDefinition effect, EmitterDefinition e, String where,
                                     Predicate<String> particleKnown, List<Issue> out) {
        if (e.particle == null || e.particle.isBlank()) {
            out.add(new Issue(Severity.ERROR, where, "No particle selected"));
        } else if (particleKnown != null && !particleKnown.test(e.particle)) {
            out.add(new Issue(Severity.ERROR, where, "Unknown or unsupported particle '" + e.particle + "'"));
        }

        boolean dust = DUST.equals(e.particle);
        GraphDefinition graph = e.graph;
        boolean graphColour = graph != null && graph.has(NodeType.OUT_COLOR);
        boolean graphSize = graph != null && graph.has(NodeType.OUT_SIZE);
        if (!dust && (e.useColor || graphColour || graphSize)) {
            out.add(new Issue(Severity.WARNING, where,
                    "Colour and size only apply to minecraft:dust, they are ignored for " + e.particle));
        }

        if (!effect.loop) {
            if (e.startDelay >= effect.duration) {
                out.add(new Issue(Severity.WARNING, where,
                        "Starts at tick " + e.startDelay + " but the effect ends at tick " + effect.duration));
            } else if (!e.loop && e.startDelay + e.duration > effect.duration) {
                out.add(new Issue(Severity.INFO, where, "Runs past the end of the effect and will be cut off"));
            }
        }
        if (e.burstCount > 0 && e.burstTick >= e.duration) {
            out.add(new Issue(Severity.WARNING, where,
                    "Burst tick " + e.burstTick + " is outside the emitter's " + e.duration + " tick duration"));
        }

        float peak = Math.max(0f, e.rate.bounds()[1]) + e.burstCount;
        if (peak > EffectRunner.MAX_PER_EMITTER) {
            out.add(new Issue(Severity.WARNING, where,
                    "Wants up to " + Math.round(peak) + " particles a tick, capped at " + EffectRunner.MAX_PER_EMITTER));
        }

        if (e.shape != EmitterShape.POINT && e.shapeX == 0f && e.shapeY == 0f && e.shapeZ == 0f) {
            out.add(new Issue(Severity.WARNING, where, "All shape sizes are 0, so it behaves like a point"));
        }
        if (e.direction == DirectionMode.FIXED && e.directionX == 0f && e.directionY == 0f && e.directionZ == 0f) {
            out.add(new Issue(Severity.INFO, where, "Fixed direction is (0, 0, 0), falling back to up"));
        }

        if (graph != null && !graph.isEmpty()) {
            CompiledGraph compiled = CompiledGraph.compile(graph.copy());
            if (compiled == null) {
                out.add(new Issue(Severity.INFO, where, "Graph has no output node, so it has no effect"));
            } else {
                compiled.warnings().forEach(w -> out.add(new Issue(Severity.WARNING, where, w)));
            }
            if (ShaderGraphCompiler.hasShaderOutput(graph)) {
                out.add(new Issue(Severity.INFO, where,
                        "Shader nodes only show up in the exported resource pack, and the particle shader affects every particle"));
                ShaderGraphCompiler.Result shader = ShaderGraphCompiler.compile(graph.copy());
                if (shader != null) {
                    shader.warnings().forEach(w -> out.add(new Issue(Severity.WARNING, where, "Shader: " + w)));
                }
            }
        }
    }
}
