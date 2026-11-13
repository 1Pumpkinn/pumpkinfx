package net.saturn.pumpkinfx.graph;

import net.saturn.pumpkinfx.model.Curve;
import net.saturn.pumpkinfx.model.Gradient;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CompiledGraph {
    private static final class Compiled {
        final NodeType type;
        final int id;
        final int[] from;
        final double[][] defaults;
        final double vx;
        final double vy;
        final double vz;
        final Curve curve;
        final Gradient gradient;

        Compiled(GraphNode node, Map<Integer, Integer> indexById) {
            this.type = node.type;
            this.id = node.id;
            this.from = new int[node.inputs.size()];
            this.defaults = new double[node.inputs.size()][];
            for (int i = 0; i < from.length; i++) {
                Slot slot = node.inputs.get(i);
                Integer index = slot.from < 0 ? null : indexById.get(slot.from);
                from[i] = index == null ? -1 : index;
                defaults[i] = new double[]{slot.x, slot.y, slot.z};
            }
            this.vx = node.vx;
            this.vy = node.vy;
            this.vz = node.vz;
            this.curve = node.curve;
            this.gradient = node.gradient;
        }
    }

    private final Compiled[] nodes;
    private final Map<NodeType, int[]> orders = new EnumMap<>(NodeType.class);
    private final Map<NodeType, Integer> outputIndex = new EnumMap<>(NodeType.class);
    private final double[][] values;
    private final List<String> warnings = new ArrayList<>();

    private CompiledGraph(GraphDefinition graph) {
        Map<Integer, Integer> indexById = new HashMap<>();
        for (int i = 0; i < graph.nodes.size(); i++) {
            indexById.put(graph.nodes.get(i).id, i);
        }
        nodes = new Compiled[graph.nodes.size()];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Compiled(graph.nodes.get(i), indexById);
        }
        values = new double[nodes.length][3];

        for (int i = 0; i < nodes.length; i++) {
            NodeType type = nodes[i].type;
            if (!type.isOutput()) {
                continue;
            }
            if (outputIndex.containsKey(type)) {
                warnings.add("More than one '" + type.displayName() + "' node, only the first is used");
                continue;
            }
            outputIndex.put(type, i);
            orders.put(type, order(i));
        }
    }

    public static CompiledGraph compile(GraphDefinition graph) {
        if (graph == null || graph.isEmpty()) {
            return null;
        }
        graph.normalize();
        CompiledGraph compiled = new CompiledGraph(graph);
        return compiled.outputIndex.isEmpty() ? null : compiled;
    }

    public List<String> warnings() {
        return warnings;
    }

    public boolean has(NodeType output) {
        return outputIndex.containsKey(output);
    }

    public double[] eval(NodeType output, EvalContext ctx) {
        int[] order = orders.get(output);
        if (order == null) {
            return new double[3];
        }
        for (int index : order) {
            evalNode(nodes[index], values[index], ctx);
        }
        return values[outputIndex.get(output)].clone();
    }

    public double evalScalar(NodeType output, EvalContext ctx) {
        return eval(output, ctx)[0];
    }

    private int[] order(int root) {
        List<Integer> result = new ArrayList<>();
        int[] state = new int[nodes.length];
        visit(root, state, result);
        int[] order = new int[result.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = result.get(i);
        }
        return order;
    }

    private void visit(int index, int[] state, List<Integer> result) {
        state[index] = 1;
        Compiled node = nodes[index];
        for (int slot = 0; slot < node.from.length; slot++) {
            int upstream = node.from[slot];
            if (upstream < 0) {
                continue;
            }
            if (state[upstream] == 1) {
                warnings.add("Cycle detected at node " + node.id + ", that link is ignored");
                node.from[slot] = -1;
            } else if (state[upstream] == 0) {
                visit(upstream, state, result);
            }
        }
        state[index] = 2;
        result.add(index);
    }

    private double[] input(Compiled node, int slot) {
        int upstream = node.from[slot];
        return upstream >= 0 ? values[upstream] : node.defaults[slot];
    }

    private void evalNode(Compiled n, double[] out, EvalContext ctx) {
        switch (n.type) {
            case TIME -> fill(out, ctx.progress);
            case TICK -> fill(out, ctx.tick);
            case INDEX -> fill(out, ctx.index);
            case RANDOM -> fill(out, Noise.hash01(ctx.seed, n.id));
            case RANDOM_DIRECTION -> randomDirection(out, ctx.seed, n.id);
            case SHAPE_POSITION -> System.arraycopy(ctx.shapePosition, 0, out, 0, 3);
            case SHAPE_DIRECTION -> System.arraycopy(ctx.shapeDirection, 0, out, 0, 3);
            case CONSTANT -> fill(out, n.vx);
            case VECTOR, COLOR -> set(out, n.vx, n.vy, n.vz);
            case ADD -> binary(out, input(n, 0), input(n, 1), 0);
            case SUBTRACT -> binary(out, input(n, 0), input(n, 1), 1);
            case MULTIPLY -> binary(out, input(n, 0), input(n, 1), 2);
            case DIVIDE -> binary(out, input(n, 0), input(n, 1), 3);
            case MIN -> binary(out, input(n, 0), input(n, 1), 4);
            case MAX -> binary(out, input(n, 0), input(n, 1), 5);
            case POWER -> binary(out, input(n, 0), input(n, 1), 6);
            case SIN -> unary(out, input(n, 0), 0);
            case COS -> unary(out, input(n, 0), 1);
            case ABS -> unary(out, input(n, 0), 2);
            case FRACT -> unary(out, input(n, 0), 3);
            case NEGATE -> unary(out, input(n, 0), 4);
            case CLAMP -> clamp(out, input(n, 0), input(n, 1), input(n, 2));
            case LERP -> lerp(out, input(n, 0), input(n, 1), input(n, 2));
            case REMAP -> remap(out, input(n, 0), input(n, 1), input(n, 2), input(n, 3), input(n, 4));
            case SMOOTHSTEP -> smoothstep(out, input(n, 0), input(n, 1), input(n, 2));
            case FLOOR -> unary(out, input(n, 0), 5);
            case SQRT -> unary(out, input(n, 0), 6);
            case SATURATE -> unary(out, input(n, 0), 7);
            case STEP -> step(out, input(n, 0), input(n, 1));
            case MOD -> mod(out, input(n, 0), input(n, 1));
            case COMBINE -> set(out, input(n, 0)[0], input(n, 1)[0], input(n, 2)[0]);
            case SPLIT_X -> fill(out, input(n, 0)[0]);
            case SPLIT_Y -> fill(out, input(n, 0)[1]);
            case SPLIT_Z -> fill(out, input(n, 0)[2]);
            case NORMALIZE -> normalize(out, input(n, 0));
            case LENGTH -> fill(out, length(input(n, 0)));
            case ROTATE_Y -> rotateY(out, input(n, 0), input(n, 1)[0]);
            case SCALE -> {
                double[] v = input(n, 0);
                double f = input(n, 1)[0];
                set(out, v[0] * f, v[1] * f, v[2] * f);
            }
            case DOT -> {
                double[] a = input(n, 0);
                double[] b = input(n, 1);
                fill(out, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]);
            }
            case CROSS -> {
                double[] a = input(n, 0);
                double[] b = input(n, 1);
                set(out, a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
            }
            case DISTANCE -> {
                double[] a = input(n, 0);
                double[] b = input(n, 1);
                double dx = a[0] - b[0];
                double dy = a[1] - b[1];
                double dz = a[2] - b[2];
                fill(out, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            case NOISE -> {
                double[] p = input(n, 0);
                double s = input(n, 1)[0];
                fill(out, Noise.value3(p[0] * s, p[1] * s, p[2] * s));
            }
            case FBM -> {
                double[] p = input(n, 0);
                double s = input(n, 1)[0];
                fill(out, Noise.fbm3(p[0] * s, p[1] * s, p[2] * s, (int) Math.round(input(n, 2)[0])));
            }
            case CURVE -> {
                double[] x = input(n, 0);
                for (int k = 0; k < 3; k++) {
                    out[k] = n.curve.evaluate((float) x[k]);
                }
            }
            case GRADIENT -> {
                int rgb = n.gradient.evaluate((float) input(n, 0)[0]);
                set(out, ((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0);
            }
            // Shader-only sources have no CPU meaning; these neutral values just keep the preview
            // and the validator from tripping over a graph that also drives the shader.
            case SH_UV, SH_FRAG_POS -> set(out, 0.5, 0.5, 0.0);
            case SH_BASE_COLOR -> fill(out, 1.0);
            case SH_BASE_ALPHA -> fill(out, 1.0);
            case OUT_RATE, OUT_SPEED, OUT_SIZE, OUT_COLOR, OUT_POSITION, OUT_DIRECTION,
                 SH_OUT_COLOR, SH_OUT_ALPHA -> System.arraycopy(input(n, 0), 0, out, 0, 3);
        }
    }

    private static void fill(double[] out, double v) {
        out[0] = v;
        out[1] = v;
        out[2] = v;
    }

    private static void set(double[] out, double x, double y, double z) {
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    private static void binary(double[] out, double[] a, double[] b, int op) {
        for (int k = 0; k < 3; k++) {
            double r = switch (op) {
                case 0 -> a[k] + b[k];
                case 1 -> a[k] - b[k];
                case 2 -> a[k] * b[k];
                case 3 -> b[k] == 0 ? 0 : a[k] / b[k];
                case 4 -> Math.min(a[k], b[k]);
                case 5 -> Math.max(a[k], b[k]);
                default -> Math.pow(a[k], b[k]);
            };
            out[k] = Double.isNaN(r) || Double.isInfinite(r) ? 0 : r;
        }
    }

    private static void unary(double[] out, double[] a, int op) {
        for (int k = 0; k < 3; k++) {
            out[k] = switch (op) {
                case 0 -> Math.sin(a[k]);
                case 1 -> Math.cos(a[k]);
                case 2 -> Math.abs(a[k]);
                case 3 -> a[k] - Math.floor(a[k]);
                case 4 -> -a[k];
                case 5 -> Math.floor(a[k]);
                case 6 -> Math.sqrt(Math.max(0.0, a[k]));
                default -> Math.max(0.0, Math.min(1.0, a[k]));
            };
        }
    }

    private static void step(double[] out, double[] edge, double[] x) {
        for (int k = 0; k < 3; k++) {
            out[k] = x[k] < edge[k] ? 0.0 : 1.0;
        }
    }

    private static void mod(double[] out, double[] a, double[] b) {
        for (int k = 0; k < 3; k++) {
            out[k] = b[k] == 0 ? 0 : a[k] - b[k] * Math.floor(a[k] / b[k]);
        }
    }

    private static void clamp(double[] out, double[] x, double[] min, double[] max) {
        for (int k = 0; k < 3; k++) {
            double lo = Math.min(min[k], max[k]);
            double hi = Math.max(min[k], max[k]);
            out[k] = Math.max(lo, Math.min(hi, x[k]));
        }
    }

    private static void lerp(double[] out, double[] a, double[] b, double[] t) {
        for (int k = 0; k < 3; k++) {
            out[k] = a[k] + (b[k] - a[k]) * t[k];
        }
    }

    private static void remap(double[] out, double[] x, double[] inMin, double[] inMax,
                              double[] outMin, double[] outMax) {
        for (int k = 0; k < 3; k++) {
            double span = inMax[k] - inMin[k];
            double t = span == 0 ? 0 : (x[k] - inMin[k]) / span;
            out[k] = outMin[k] + (outMax[k] - outMin[k]) * t;
        }
    }

    private static void smoothstep(double[] out, double[] x, double[] e0, double[] e1) {
        for (int k = 0; k < 3; k++) {
            double span = e1[k] - e0[k];
            double t = span == 0 ? 0 : Math.max(0, Math.min(1, (x[k] - e0[k]) / span));
            out[k] = t * t * (3 - 2 * t);
        }
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static void normalize(double[] out, double[] v) {
        double len = length(v);
        if (len < 1.0e-9) {
            fill(out, 0);
        } else {
            set(out, v[0] / len, v[1] / len, v[2] / len);
        }
    }

    private static void rotateY(double[] out, double[] v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        set(out, v[0] * cos + v[2] * sin, v[1], -v[0] * sin + v[2] * cos);
    }

    private static void randomDirection(double[] out, long seed, int id) {
        double y = Noise.hash01(seed, id * 2L + 1) * 2.0 - 1.0;
        double phi = Noise.hash01(seed, id * 2L + 2) * Math.PI * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        set(out, r * Math.cos(phi), y, r * Math.sin(phi));
    }
}
