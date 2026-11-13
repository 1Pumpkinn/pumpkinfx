package net.saturn.pumpkinfx.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GraphDefinition {
    public List<GraphNode> nodes = new ArrayList<>();
    public int nextId = 1;

    public boolean isEmpty() {
        return nodes == null || nodes.isEmpty();
    }

    public GraphNode get(int id) {
        for (GraphNode node : nodes) {
            if (node.id == id) {
                return node;
            }
        }
        return null;
    }

    public GraphNode add(NodeType type, float x, float y) {
        GraphNode node = new GraphNode(nextId++, type, x, y);
        nodes.add(node);
        return node;
    }

    public void remove(int id) {
        nodes.removeIf(n -> n.id == id);
        for (GraphNode node : nodes) {
            for (Slot slot : node.inputs) {
                if (slot.from == id) {
                    slot.from = -1;
                }
            }
        }
    }

    public boolean link(int toId, int slotIndex, int fromId) {
        GraphNode to = get(toId);
        GraphNode from = get(fromId);
        if (to == null || from == null || toId == fromId || from.type.isOutput()) {
            return false;
        }
        if (slotIndex < 0 || slotIndex >= to.inputs.size()) {
            return false;
        }
        if (dependsOn(from, toId, new HashSet<>())) {
            return false;
        }
        to.inputs.get(slotIndex).from = fromId;
        return true;
    }

    public void unlink(int toId, int slotIndex) {
        GraphNode to = get(toId);
        if (to != null && slotIndex >= 0 && slotIndex < to.inputs.size()) {
            to.inputs.get(slotIndex).from = -1;
        }
    }

    public boolean has(NodeType type) {
        for (GraphNode node : nodes) {
            if (node.type == type) {
                return true;
            }
        }
        return false;
    }

    public void normalize() {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        Set<Integer> seen = new HashSet<>();
        int maxId = 0;
        List<GraphNode> kept = new ArrayList<>();
        for (GraphNode node : nodes) {
            if (node == null || node.type == null || !seen.add(node.id)) {
                continue;
            }
            node.applyTypeDefaults();
            maxId = Math.max(maxId, node.id);
            kept.add(node);
        }
        nodes = kept;
        nextId = Math.max(nextId, maxId + 1);
        for (GraphNode node : nodes) {
            for (Slot slot : node.inputs) {
                GraphNode source = slot.from < 0 ? null : get(slot.from);
                if (source == null || source.id == node.id || source.type.isOutput()) {
                    slot.from = -1;
                }
            }
        }
    }

    public GraphDefinition copy() {
        GraphDefinition graph = new GraphDefinition();
        graph.nextId = nextId;
        for (GraphNode node : nodes) {
            graph.nodes.add(node.copy());
        }
        return graph;
    }

    private boolean dependsOn(GraphNode node, int targetId, Set<Integer> visited) {
        if (node.id == targetId) {
            return true;
        }
        if (!visited.add(node.id)) {
            return false;
        }
        for (Slot slot : node.inputs) {
            if (slot.from >= 0) {
                GraphNode upstream = get(slot.from);
                if (upstream != null && dependsOn(upstream, targetId, visited)) {
                    return true;
                }
            }
        }
        return false;
    }
}
