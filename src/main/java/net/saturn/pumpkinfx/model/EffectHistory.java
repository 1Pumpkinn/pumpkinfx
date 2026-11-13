package net.saturn.pumpkinfx.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo/redo for an {@link EffectDefinition}, stored as compact JSON snapshots.
 *
 * <p>Call {@link #commit} after each user edit (it ignores calls where nothing changed, so it
 * is safe to call on every widget change). {@link #undo} and {@link #redo} return a new
 * instance, so the editor must swap its session reference to the returned object.
 */
public final class EffectHistory {
    public static final int MAX_STEPS = 100;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private String current;

    public EffectHistory(EffectDefinition initial) {
        this.current = EffectSerializer.toCompactJson(initial);
    }

    /** Records the effect's present state. Returns true if it differed from the last snapshot. */
    public boolean commit(EffectDefinition effect) {
        String json = EffectSerializer.toCompactJson(effect);
        if (json.equals(current)) {
            return false;
        }
        undoStack.push(current);
        while (undoStack.size() > MAX_STEPS) {
            undoStack.removeLast();
        }
        redoStack.clear();
        current = json;
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Steps back one snapshot, or returns null if there is nothing to undo. */
    public EffectDefinition undo() {
        if (undoStack.isEmpty()) {
            return null;
        }
        redoStack.push(current);
        current = undoStack.pop();
        return EffectSerializer.fromJson(current);
    }

    /** Steps forward one snapshot, or returns null if there is nothing to redo. */
    public EffectDefinition redo() {
        if (redoStack.isEmpty()) {
            return null;
        }
        undoStack.push(current);
        current = redoStack.pop();
        return EffectSerializer.fromJson(current);
    }

    public void clear(EffectDefinition effect) {
        undoStack.clear();
        redoStack.clear();
        current = EffectSerializer.toCompactJson(effect);
    }
}
