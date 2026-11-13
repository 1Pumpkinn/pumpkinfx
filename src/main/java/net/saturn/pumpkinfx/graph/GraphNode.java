package net.saturn.pumpkinfx.graph;

import net.saturn.pumpkinfx.model.Curve;
import net.saturn.pumpkinfx.model.Gradient;

import java.util.ArrayList;
import java.util.List;

public final class GraphNode {
    public int id;
    public NodeType type = NodeType.CONSTANT;
    public float x;
    public float y;

    public double vx = 1;
    public double vy = 1;
    public double vz = 1;

    public List<Slot> inputs = new ArrayList<>();
    public Curve curve;
    public Gradient gradient;

    public GraphNode() {
    }

    public GraphNode(int id, NodeType type, float x, float y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        applyTypeDefaults();
    }

    public void applyTypeDefaults() {
        while (inputs.size() < type.inputCount()) {
            inputs.add(new Slot(type.defaultInput(inputs.size())));
        }
        while (inputs.size() > type.inputCount()) {
            inputs.remove(inputs.size() - 1);
        }
        if (type == NodeType.CURVE && (curve == null || curve.keys == null || curve.keys.isEmpty())) {
            curve = Curve.linear(0f, 1f);
        }
        if (type == NodeType.GRADIENT && (gradient == null || gradient.stops == null || gradient.stops.isEmpty())) {
            gradient = Gradient.between(0x000000, 0xFFFFFF);
        }
        if (type == NodeType.VECTOR && vx == 1 && vy == 1 && vz == 1) {
            vx = 0;
            vz = 0;
        }
    }

    public GraphNode copy() {
        GraphNode node = new GraphNode();
        node.id = id;
        node.type = type;
        node.x = x;
        node.y = y;
        node.vx = vx;
        node.vy = vy;
        node.vz = vz;
        for (Slot slot : inputs) {
            node.inputs.add(slot.copy());
        }
        node.curve = curve == null ? null : curve.copy();
        node.gradient = gradient == null ? null : gradient.copy();
        return node;
    }
}
