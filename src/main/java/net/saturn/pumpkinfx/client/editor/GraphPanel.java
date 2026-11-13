package net.saturn.pumpkinfx.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.saturn.pumpkinfx.graph.GraphDefinition;
import net.saturn.pumpkinfx.graph.GraphNode;
import net.saturn.pumpkinfx.graph.GraphPresets;
import net.saturn.pumpkinfx.graph.NodeType;
import net.saturn.pumpkinfx.graph.Slot;
import net.saturn.pumpkinfx.model.EmitterDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * The Graph tab: a node list on the left and the selected node's type, inputs and values on the
 * right. It is built from ordinary buttons and boxes (no custom mouse handling), so links are made
 * by picking a source for each input rather than dragging wires.
 */
final class GraphPanel {
    interface Host {
        void rebuild();

        void commit();

        void status(String text);
    }

    private static final int LIST_W = 100;
    private static final int LIST_ROW = 18;
    private static final int ROW = 20;
    private static final int GAP = 4;
    private static final int LABEL_W = 46;

    private static int selectedId = -1;
    private static int listOffset;

    private GraphPanel() {
    }

    /** Builds the tab and returns the form that the screen should render for its labels and plots. */
    static FormBuilder build(Font font, Consumer<AbstractWidget> adder, EmitterDefinition emitter,
                             int left, int top, int bottom, int rightEdge, Host host) {
        GraphDefinition graph = emitter.graph;
        graph.normalize();
        if (graph.get(selectedId) == null) {
            selectedId = graph.nodes.isEmpty() ? -1 : graph.nodes.get(0).id;
        }

        buildList(adder, graph, left, top, bottom, host);

        int rx = left + LIST_W + 8;
        int rw = Math.max(120, rightEdge - rx);
        FormBuilder form = new FormBuilder(font, adder, rx, top, rw);

        GraphNode node = graph.get(selectedId);
        if (graph.nodes.isEmpty() || node == null) {
            buildEmpty(adder, emitter, form, rx, rw, host);
            return form;
        }
        buildNode(font, adder, graph, node, form, rx, rw, host);
        return form;
    }

    private static void buildList(Consumer<AbstractWidget> adder, GraphDefinition graph,
                                  int left, int top, int bottom, Host host) {
        int controlsH = (ROW + GAP) * 2;
        int visible = Math.max(1, (bottom - top - controlsH) / LIST_ROW);
        int count = graph.nodes.size();
        listOffset = Math.max(0, Math.min(listOffset, Math.max(0, count - visible)));

        for (int row = 0; row < visible && listOffset + row < count; row++) {
            GraphNode n = graph.nodes.get(listOffset + row);
            String text = (n.id == selectedId ? "> " : "") + "#" + n.id + " " + n.type.displayName();
            adder.accept(Button.builder(Component.literal(trim(text, 15)), b -> {
                selectedId = n.id;
                host.rebuild();
            }).bounds(left, top + row * LIST_ROW, LIST_W, LIST_ROW - 2).build());
        }

        int y = top + visible * LIST_ROW + GAP;
        Button up = Button.builder(Component.literal("Up"), b -> {
            listOffset--;
            host.rebuild();
        }).bounds(left, y, 48, ROW).build();
        up.active = listOffset > 0;
        adder.accept(up);
        Button down = Button.builder(Component.literal("Down"), b -> {
            listOffset++;
            host.rebuild();
        }).bounds(left + 52, y, 48, ROW).build();
        down.active = listOffset + visible < count;
        adder.accept(down);

        y += ROW + GAP;
        adder.accept(Button.builder(Component.literal("+"), b -> {
            host.commit();
            GraphNode created = graph.add(NodeType.CONSTANT, graph.nodes.size() * 20f, 0);
            selectedId = created.id;
            listOffset = Integer.MAX_VALUE;
            host.commit();
            host.rebuild();
        }).bounds(left, y, 28, ROW).build());

        adder.accept(Button.builder(Component.literal("Dup"), b -> {
            host.commit();
            List<GraphNode> copies = graph.duplicate(Set.of(selectedId), 24f, 24f);
            if (copies.isEmpty()) {
                host.status("Output nodes can't be duplicated");
            } else {
                selectedId = copies.get(0).id;
                listOffset = Integer.MAX_VALUE;
            }
            host.commit();
            host.rebuild();
        }).bounds(left + 32, y, 34, ROW).build());

        adder.accept(Button.builder(Component.literal("Del"), b -> {
            host.commit();
            graph.remove(selectedId);
            selectedId = graph.nodes.isEmpty() ? -1 : graph.nodes.get(0).id;
            host.commit();
            host.rebuild();
        }).bounds(left + 70, y, 30, ROW).build());
    }

    private static void buildEmpty(Consumer<AbstractWidget> adder, EmitterDefinition emitter,
                                   FormBuilder form, int rx, int rw, Host host) {
        form.note("This emitter has no graph yet.");
        form.note("Graphs can drive position, speed, colour and the particle shader.");
        form.note("Use + to add a node, or start from a template:");
        form.gap(4);
        int y = form.y();
        int half = (rw - GAP) / 2;
        adder.accept(Button.builder(Component.literal("Helix"), b -> {
            host.commit();
            emitter.graph = GraphPresets.helix("template").emitters.get(0).graph.copy();
            selectedId = -1;
            listOffset = 0;
            host.commit();
            host.rebuild();
        }).bounds(rx, y, half, ROW).build());
        adder.accept(Button.builder(Component.literal("Shader tint"), b -> {
            host.commit();
            emitter.graph = GraphPresets.shaderTint("template").emitters.get(0).graph.copy();
            selectedId = -1;
            listOffset = 0;
            host.commit();
            host.rebuild();
        }).bounds(rx + half + GAP, y, half, ROW).build());
        form.gap(ROW + GAP);
        form.note("Shader nodes only show up in an exported pack.", FormBuilder.NOTE_COLOR);
    }

    private static void buildNode(Font font, Consumer<AbstractWidget> adder, GraphDefinition graph,
                                  GraphNode node, FormBuilder form, int rx, int rw, Host host) {
        buildTypeRow(adder, graph, node, form, rx, rw, host);

        if (node.type.category() == NodeType.Category.SHADER || node.type.isShaderOutput()) {
            form.note("Shader node: only visible in an exported pack.", FormBuilder.NOTE_COLOR);
        }

        for (int i = 0; i < node.inputs.size(); i++) {
            buildInput(adder, font, graph, node, i, form, rx, rw, host);
        }

        switch (node.type) {
            case CONSTANT -> {
                int y = form.y();
                form.labelAt("Value", rx, y + 6, FormBuilder.LABEL_COLOR);
                adder.accept(numberBox(font, rx + LABEL_W, y, rw - LABEL_W, node.vx, v -> node.vx = v));
                form.gap(ROW + GAP);
            }
            case VECTOR -> triple(adder, font, form, rx, rw, new String[]{"X", "Y", "Z"},
                    new double[]{node.vx, node.vy, node.vz},
                    v -> node.vx = v, v -> node.vy = v, v -> node.vz = v);
            case COLOR -> {
                form.note("Colour channels are 0 to 1.", FormBuilder.NOTE_COLOR);
                triple(adder, font, form, rx, rw, new String[]{"R", "G", "B"},
                        new double[]{node.vx, node.vy, node.vz},
                        v -> node.vx = v, v -> node.vy = v, v -> node.vz = v);
            }
            case CURVE -> form.curve(node.curve, host::rebuild);
            case GRADIENT -> form.gradient(node.gradient, host::rebuild);
            default -> {
            }
        }
    }

    private static void buildTypeRow(Consumer<AbstractWidget> adder, GraphDefinition graph, GraphNode node,
                                     FormBuilder form, int rx, int rw, Host host) {
        int y = form.y();
        int catW = 54;
        int arrowW = 18;
        int nameW = rw - catW - arrowW * 2 - GAP * 3;

        adder.accept(Button.builder(Component.literal(trim(node.type.category().name(), 8)), b -> {
            NodeType.Category[] all = NodeType.Category.values();
            NodeType.Category next = all[(node.type.category().ordinal() + 1) % all.length];
            for (NodeType type : NodeType.values()) {
                if (type.category() == next) {
                    retype(graph, node, type);
                    break;
                }
            }
            host.commit();
            host.rebuild();
        }).bounds(rx, y, catW, ROW).build());

        adder.accept(Button.builder(Component.literal("<"), b -> {
            retype(graph, node, step(node.type, -1));
            host.commit();
            host.rebuild();
        }).bounds(rx + catW + GAP, y, arrowW, ROW).build());

        Button name = Button.builder(Component.literal(trim(node.type.displayName(), Math.max(6, nameW / 6))), b -> {
        }).bounds(rx + catW + GAP + arrowW + GAP, y, nameW, ROW).build();
        name.active = false;
        adder.accept(name);

        adder.accept(Button.builder(Component.literal(">"), b -> {
            retype(graph, node, step(node.type, 1));
            host.commit();
            host.rebuild();
        }).bounds(rx + rw - arrowW, y, arrowW, ROW).build());

        form.gap(ROW + GAP);
    }

    private static void buildInput(Consumer<AbstractWidget> adder, Font font, GraphDefinition graph, GraphNode node,
                                   int slotIndex, FormBuilder form, int rx, int rw, Host host) {
        Slot slot = node.inputs.get(slotIndex);
        int y = form.y();
        form.labelAt(trim(node.type.inputLabel(slotIndex), 7), rx, y + 6, FormBuilder.LABEL_COLOR);

        GraphNode source = slot.from < 0 ? null : graph.get(slot.from);
        boolean linked = source != null;
        int buttonX = rx + LABEL_W;
        int buttonW = linked ? rw - LABEL_W : Math.min(84, rw - LABEL_W - 40);
        String text = linked ? "#" + source.id + " " + source.type.displayName() : "(value)";
        adder.accept(Button.builder(Component.literal(trim(text, Math.max(6, buttonW / 6))), b -> {
            cycleSource(graph, node, slotIndex);
            host.commit();
            host.rebuild();
        }).bounds(buttonX, y, buttonW, ROW).build());

        if (!linked) {
            int boxX = buttonX + buttonW + GAP;
            adder.accept(numberBox(font, boxX, y, rx + rw - boxX, slot.x, v -> {
                slot.x = v;
                slot.y = v;
                slot.z = v;
            }));
        }
        form.gap(ROW + GAP);
    }

    private static void triple(Consumer<AbstractWidget> adder, Font font, FormBuilder form, int rx, int rw,
                               String[] names, double[] values,
                               DoubleConsumer sx, DoubleConsumer sy, DoubleConsumer sz) {
        DoubleConsumer[] setters = {sx, sy, sz};
        int y = form.y();
        int cell = (rw - GAP * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int x = rx + i * (cell + GAP);
            form.labelAt(names[i], x, y + 6, FormBuilder.LABEL_COLOR);
            adder.accept(numberBox(font, x + 10, y, cell - 10, values[i], setters[i]));
        }
        form.gap(ROW + GAP);
    }

    private static EditBox numberBox(Font font, int x, int y, int w, double value, DoubleConsumer setter) {
        EditBox box = new EditBox(font, x, y, w, ROW, Component.empty());
        box.setMaxLength(12);
        box.setValue(format(value));
        FormBuilder.bind(box, s -> s.matches(FormBuilder.SIGNED_FLOAT), s -> {
            try {
                setter.accept(Double.parseDouble(s));
            } catch (NumberFormatException ignored) {
            }
        });
        return box;
    }

    /** Moves an input to the next valid source: its own value, then each node that would not make a loop. */
    private static void cycleSource(GraphDefinition graph, GraphNode to, int slot) {
        List<GraphNode> candidates = new ArrayList<>();
        for (GraphNode c : graph.nodes) {
            if (c.id != to.id && !c.type.isOutput()) {
                candidates.add(c);
            }
        }
        int current = -1;
        int from = to.inputs.get(slot).from;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).id == from) {
                current = i;
            }
        }
        int n = candidates.size();
        for (int step = 1; step <= n + 1; step++) {
            int index = Math.floorMod(current + step + 1, n + 1) - 1;
            if (index < 0) {
                graph.unlink(to.id, slot);
                return;
            }
            if (graph.link(to.id, slot, candidates.get(index).id)) {
                return;
            }
        }
    }

    private static NodeType step(NodeType type, int direction) {
        NodeType[] all = NodeType.values();
        return all[Math.floorMod(type.ordinal() + direction, all.length)];
    }

    /** Changes a node's type, keeping its links, and drops links out of it if it became an output. */
    private static void retype(GraphDefinition graph, GraphNode node, NodeType type) {
        node.type = type;
        node.applyTypeDefaults();
        if (type.isOutput()) {
            for (GraphNode other : graph.nodes) {
                for (Slot slot : other.inputs) {
                    if (slot.from == node.id) {
                        slot.from = -1;
                    }
                }
            }
        }
    }

    private static String trim(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, Math.max(1, maxChars - 1)) + ".";
    }

    private static String format(double value) {
        String s = String.format(Locale.ROOT, "%.4f", value);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() || s.equals("-") ? "0" : s;
    }
}
