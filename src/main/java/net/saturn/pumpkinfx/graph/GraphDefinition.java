package net.saturn.pumpkinfx.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Copies the given nodes (output nodes are skipped, there can only be one of each). Links
     * between copied nodes are kept pointing at the copies; links to nodes that were not copied
     * keep pointing at the originals, like duplicating in Blender. Returns the new nodes.
     */
    public List<GraphNode> duplicate(Collection<Integer> ids, float dx, float dy) {
        Map<Integer, Integer> remap = new LinkedHashMap<>();
        List<GraphNode> created = new ArrayList<>();
        for (GraphNode node : nodes) {
            if (!ids.contains(node.id) || node.type.isOutput()) {
                continue;
            }
            GraphNode copy = node.copy();
            copy.id = nextId++;
            copy.x += dx;
            copy.y += dy;
            remap.put(node.id, copy.id);
            created.add(copy);
        }
        for (GraphNode copy : created) {
            for (Slot slot : copy.inputs) {
                Integer mapped = slot.from < 0 ? null : remap.get(slot.from);
                if (mapped != null) {
                    slot.from = mapped;
                }
            }
        }
        nodes.addAll(created);
        return created;
    }

    /** Arranges nodes in columns by how far they are from the inputs, so data flows left to right. */
    public void autoLayout(float columnWidth, float rowHeight) {
        Map<Integer, Integer> depth = new HashMap<>();
        for (GraphNode node : nodes) {
            depthOf(node, depth, new HashSet<>());
        }
        Map<Integer, Integer> rows = new HashMap<>();
        for (GraphNode node : nodes) {
            int column = depth.get(node.id);
            int row = rows.merge(column, 1, Integer::sum) - 1;
            node.x = column * columnWidth;
            node.y = row * rowHeight;
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

    private int depthOf(GraphNode node, Map<Integer, Integer> memo, Set<Integer> path) {
        Integer known = memo.get(node.id);
        if (known != null) {
            return known;
        }
        if (!path.add(node.id)) {
            return 0;
        }
        int best = 0;
        for (Slot slot : node.inputs) {
            GraphNode upstream = slot.from < 0 ? null : get(slot.from);
            if (upstream != null) {
                best = Math.max(best, depthOf(upstream, memo, path) + 1);
            }
        }
        path.remove(node.id);
        memo.put(node.id, best);
        return best;
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
