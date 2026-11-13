package net.saturn.pumpkinfx.shader;

import net.saturn.pumpkinfx.graph.GraphDefinition;
import net.saturn.pumpkinfx.graph.GraphNode;
import net.saturn.pumpkinfx.graph.NodeType;
import net.saturn.pumpkinfx.graph.Slot;
import net.saturn.pumpkinfx.model.ColorStop;
import net.saturn.pumpkinfx.model.Gradient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the shader part of an emitter graph into GLSL. Every graph value is a vec3 (the same
 * three-numbers-per-value model the CPU evaluator uses), so generated code mirrors
 * {@code CompiledGraph} operation for operation.
 *
 * <p>The output is a block of helper functions plus {@code vec4 pfx_shade(vec4 base, vec2 uv)}.
 * It deliberately knows nothing about the vanilla shader it will be appended to; see
 * {@link ParticleShaderInjector}.
 */
public final class ShaderGraphCompiler {
    public static final String ENTRY_POINT = "pfx_shade";

    public record Result(String glsl, List<String> warnings) {
    }

    private static final String HELPERS = """
            float pfx_div1(float a, float b) { return b == 0.0 ? 0.0 : a / b; }
            vec3 pfx_div(vec3 a, vec3 b) { return vec3(pfx_div1(a.x, b.x), pfx_div1(a.y, b.y), pfx_div1(a.z, b.z)); }
            float pfx_pow1(float a, float b) {
                if (a == 0.0) { return b == 0.0 ? 1.0 : 0.0; }
                if (a > 0.0) { return pow(a, b); }
                if (b != floor(b)) { return 0.0; }
                float r = pow(-a, b);
                return mod(b, 2.0) == 0.0 ? r : -r;
            }
            vec3 pfx_pow(vec3 a, vec3 b) { return vec3(pfx_pow1(a.x, b.x), pfx_pow1(a.y, b.y), pfx_pow1(a.z, b.z)); }
            float pfx_mod1(float a, float b) { return b == 0.0 ? 0.0 : a - b * floor(a / b); }
            vec3 pfx_mod(vec3 a, vec3 b) { return vec3(pfx_mod1(a.x, b.x), pfx_mod1(a.y, b.y), pfx_mod1(a.z, b.z)); }
            float pfx_remap1(float x, float i0, float i1, float o0, float o1) {
                float span = i1 - i0;
                float t = span == 0.0 ? 0.0 : (x - i0) / span;
                return o0 + (o1 - o0) * t;
            }
            vec3 pfx_remap(vec3 x, vec3 i0, vec3 i1, vec3 o0, vec3 o1) {
                return vec3(pfx_remap1(x.x, i0.x, i1.x, o0.x, o1.x), pfx_remap1(x.y, i0.y, i1.y, o0.y, o1.y), pfx_remap1(x.z, i0.z, i1.z, o0.z, o1.z));
            }
            float pfx_smooth1(float x, float e0, float e1) {
                float span = e1 - e0;
                float t = span == 0.0 ? 0.0 : clamp((x - e0) / span, 0.0, 1.0);
                return t * t * (3.0 - 2.0 * t);
            }
            vec3 pfx_smooth(vec3 x, vec3 e0, vec3 e1) { return vec3(pfx_smooth1(x.x, e0.x, e1.x), pfx_smooth1(x.y, e0.y, e1.y), pfx_smooth1(x.z, e0.z, e1.z)); }
            vec3 pfx_norm(vec3 v) { float l = length(v); return l < 1.0e-9 ? vec3(0.0) : v / l; }
            vec3 pfx_roty(vec3 v, float a) { float c = cos(a); float s = sin(a); return vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c); }
            """;

    private final GraphDefinition graph;
    private final List<String> warnings = new ArrayList<>();
    private final StringBuilder body = new StringBuilder();
    private final Set<Integer> emitted = new HashSet<>();
    private final Set<Integer> visiting = new HashSet<>();

    private ShaderGraphCompiler(GraphDefinition graph) {
        this.graph = graph;
    }

    public static boolean hasShaderOutput(GraphDefinition graph) {
        return graph != null && graph.nodes != null
                && graph.nodes.stream().anyMatch(n -> n != null && n.type != null && n.type.isShaderOutput());
    }

    /** Returns null when the graph has no shader output node. The graph is normalised in place. */
    public static Result compile(GraphDefinition graph) {
        if (!hasShaderOutput(graph)) {
            return null;
        }
        graph.normalize();
        return new ShaderGraphCompiler(graph).run();
    }

    private Result run() {
        GraphNode color = firstOf(NodeType.SH_OUT_COLOR);
        GraphNode alpha = firstOf(NodeType.SH_OUT_ALPHA);
        if (color != null) {
            emit(color);
        }
        if (alpha != null) {
            emit(alpha);
        }
        for (NodeType type : new NodeType[]{NodeType.SH_OUT_COLOR, NodeType.SH_OUT_ALPHA}) {
            if (graph.nodes.stream().filter(n -> n.type == type).count() > 1) {
                warnings.add("More than one '" + type.displayName() + "' node, only the first is used");
            }
        }

        StringBuilder out = new StringBuilder();
        out.append(HELPERS);
        out.append("vec4 ").append(ENTRY_POINT).append("(vec4 base, vec2 uv) {\n");
        out.append(body);
        out.append("    vec3 pfx_rgb = base.rgb;\n");
        out.append("    float pfx_a = base.a;\n");
        if (color != null) {
            out.append("    pfx_rgb = clamp(").append(name(color)).append(", 0.0, 1.0);\n");
        }
        if (alpha != null) {
            out.append("    pfx_a = clamp(").append(name(alpha)).append(".x, 0.0, 1.0);\n");
        }
        out.append("    return vec4(pfx_rgb, pfx_a);\n}\n");
        return new Result(out.toString(), warnings);
    }

    private GraphNode firstOf(NodeType type) {
        for (GraphNode node : graph.nodes) {
            if (node.type == type) {
                return node;
            }
        }
        return null;
    }

    private static String name(GraphNode node) {
        return "pfx_v" + node.id;
    }

    private void emit(GraphNode node) {
        if (emitted.contains(node.id)) {
            return;
        }
        visiting.add(node.id);
        for (Slot slot : node.inputs) {
            if (slot.from < 0) {
                continue;
            }
            GraphNode upstream = graph.get(slot.from);
            if (upstream == null) {
                continue;
            }
            if (visiting.contains(upstream.id)) {
                warnings.add("Cycle detected at node " + node.id + ", that link is ignored");
                slot.from = -1;
            } else {
                emit(upstream);
            }
        }
        visiting.remove(node.id);
        emitted.add(node.id);
        line(node);
    }

    private String in(GraphNode node, int slot) {
        Slot s = node.inputs.get(slot);
        if (s.from >= 0) {
            GraphNode upstream = graph.get(s.from);
            if (upstream != null) {
                return name(upstream);
            }
        }
        return vec(s.x, s.y, s.z);
    }

    private void line(GraphNode n) {
        String id = name(n);
        String expr;
        switch (n.type) {
            case SH_UV -> expr = "vec3(uv, 0.0)";
            case SH_FRAG_POS -> expr = "gl_FragCoord.xyz";
            case SH_BASE_COLOR -> expr = "base.rgb";
            case SH_BASE_ALPHA -> expr = "vec3(base.a)";
            case CONSTANT -> expr = "vec3(" + f(n.vx) + ")";
            case VECTOR, COLOR -> expr = vec(n.vx, n.vy, n.vz);
            case ADD -> expr = in(n, 0) + " + " + in(n, 1);
            case SUBTRACT -> expr = in(n, 0) + " - " + in(n, 1);
            case MULTIPLY -> expr = in(n, 0) + " * " + in(n, 1);
            case DIVIDE -> expr = "pfx_div(" + in(n, 0) + ", " + in(n, 1) + ")";
            case MIN -> expr = "min(" + in(n, 0) + ", " + in(n, 1) + ")";
            case MAX -> expr = "max(" + in(n, 0) + ", " + in(n, 1) + ")";
            case POWER -> expr = "pfx_pow(" + in(n, 0) + ", " + in(n, 1) + ")";
            case SIN -> expr = "sin(" + in(n, 0) + ")";
            case COS -> expr = "cos(" + in(n, 0) + ")";
            case ABS -> expr = "abs(" + in(n, 0) + ")";
            case FRACT -> expr = "fract(" + in(n, 0) + ")";
            case NEGATE -> expr = "-" + in(n, 0);
            case FLOOR -> expr = "floor(" + in(n, 0) + ")";
            case SQRT -> expr = "sqrt(max(" + in(n, 0) + ", vec3(0.0)))";
            case SATURATE -> expr = "clamp(" + in(n, 0) + ", 0.0, 1.0)";
            case CLAMP -> expr = "clamp(" + in(n, 0) + ", min(" + in(n, 1) + ", " + in(n, 2)
                    + "), max(" + in(n, 1) + ", " + in(n, 2) + "))";
            case LERP -> expr = "mix(" + in(n, 0) + ", " + in(n, 1) + ", " + in(n, 2) + ")";
            case REMAP -> expr = "pfx_remap(" + in(n, 0) + ", " + in(n, 1) + ", " + in(n, 2) + ", "
                    + in(n, 3) + ", " + in(n, 4) + ")";
            case SMOOTHSTEP -> expr = "pfx_smooth(" + in(n, 0) + ", " + in(n, 1) + ", " + in(n, 2) + ")";
            case STEP -> expr = "step(" + in(n, 0) + ", " + in(n, 1) + ")";
            case MOD -> expr = "pfx_mod(" + in(n, 0) + ", " + in(n, 1) + ")";
            case COMBINE -> expr = "vec3(" + in(n, 0) + ".x, " + in(n, 1) + ".x, " + in(n, 2) + ".x)";
            case SPLIT_X -> expr = "vec3(" + in(n, 0) + ".x)";
            case SPLIT_Y -> expr = "vec3(" + in(n, 0) + ".y)";
            case SPLIT_Z -> expr = "vec3(" + in(n, 0) + ".z)";
            case NORMALIZE -> expr = "pfx_norm(" + in(n, 0) + ")";
            case LENGTH -> expr = "vec3(length(" + in(n, 0) + "))";
            case ROTATE_Y -> expr = "pfx_roty(" + in(n, 0) + ", " + in(n, 1) + ".x)";
            case SCALE -> expr = in(n, 0) + " * " + in(n, 1) + ".x";
            case DOT -> expr = "vec3(dot(" + in(n, 0) + ", " + in(n, 1) + "))";
            case CROSS -> expr = "cross(" + in(n, 0) + ", " + in(n, 1) + ")";
            case DISTANCE -> expr = "vec3(distance(" + in(n, 0) + ", " + in(n, 1) + "))";
            case GRADIENT -> {
                gradient(n);
                return;
            }
            case SH_OUT_COLOR, SH_OUT_ALPHA -> expr = in(n, 0);
            default -> {
                warnings.add("'" + n.type.displayName() + "' (node " + n.id + ") is worked out per particle on the CPU "
                        + "and has no shader equivalent, so the shader sees 0 for it");
                expr = "vec3(0.0)";
            }
        }
        body.append("    vec3 ").append(id).append(" = ").append(expr).append(";\n");
    }

    private void gradient(GraphNode n) {
        Gradient g = n.gradient;
        String id = name(n);
        String t = "pfx_t" + n.id;
        body.append("    float ").append(t).append(" = ").append(in(n, 0)).append(".x;\n");
        List<ColorStop> stops = new ArrayList<>(g.stops);
        stops.sort((a, b) -> Float.compare(a.time(), b.time()));
        body.append("    vec3 ").append(id).append(" = ").append(rgb(stops.get(0).rgb())).append(";\n");
        for (int i = 1; i < stops.size(); i++) {
            float p0 = stops.get(i - 1).time();
            float p1 = stops.get(i).time();
            String factor = p1 > p0
                    ? "clamp((" + t + " - " + f(p0) + ") / " + f(p1 - p0) + ", 0.0, 1.0)"
                    : "step(" + f(p1) + ", " + t + ")";
            body.append("    ").append(id).append(" = mix(").append(id).append(", ")
                    .append(rgb(stops.get(i).rgb())).append(", ").append(factor).append(");\n");
        }
    }

    private static String rgb(int rgb) {
        return vec(((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0);
    }

    private static String vec(double x, double y, double z) {
        return "vec3(" + f(x) + ", " + f(y) + ", " + f(z) + ")";
    }

    /** A GLSL float literal that is always valid (never "NaN", "1e5" without a dot, or locale-formatted). */
    static String f(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.6f", v);
    }
}
