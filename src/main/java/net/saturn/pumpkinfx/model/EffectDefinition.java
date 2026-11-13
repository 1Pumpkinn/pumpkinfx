package net.saturn.pumpkinfx.model;

import java.util.ArrayList;
import java.util.List;

public final class EffectDefinition {
    public static final int FORMAT_VERSION = 2;

    public int formatVersion = FORMAT_VERSION;
    public String id = "new_effect";
    public String displayName = "New Effect";
    public int duration = 60;
    public boolean loop = false;
    /** 0 means a fresh random seed every play; any other value makes playback repeatable. */
    public long seed = 0L;
    public List<EmitterDefinition> emitters = new ArrayList<>();

    public EffectDefinition() {
    }

    public EffectDefinition(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static EffectDefinition createDefault(String id) {
        EffectDefinition effect = new EffectDefinition(id, id);
        effect.emitters.add(new EmitterDefinition("Emitter 1"));
        return effect;
    }

    public void normalize() {
        formatVersion = FORMAT_VERSION;
        id = EffectIds.sanitize(id);
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        duration = Math.max(1, duration);
        if (emitters == null) {
            emitters = new ArrayList<>();
        }
        for (EmitterDefinition emitter : emitters) {
            emitter.normalize();
        }
    }

    public EmitterDefinition addEmitter() {
        EmitterDefinition emitter = new EmitterDefinition("Emitter " + (emitters.size() + 1));
        emitters.add(emitter);
        return emitter;
    }

    public void removeEmitter(int index) {
        if (index >= 0 && index < emitters.size()) {
            emitters.remove(index);
        }
    }

    /** Moves an emitter to a new slot in the list. Returns false if either index is out of range. */
    public boolean moveEmitter(int from, int to) {
        if (from < 0 || from >= emitters.size() || to < 0 || to >= emitters.size()) {
            return false;
        }
        if (from != to) {
            emitters.add(to, emitters.remove(from));
        }
        return true;
    }

    public EmitterDefinition duplicateEmitter(int index) {
        if (index < 0 || index >= emitters.size()) {
            return null;
        }
        EmitterDefinition copy = emitters.get(index).copy();
        copy.name = copy.name + " copy";
        emitters.add(index + 1, copy);
        return copy;
    }

    public EffectDefinition copy() {
        EffectDefinition e = new EffectDefinition(id, displayName);
        e.formatVersion = formatVersion;
        e.duration = duration;
        e.loop = loop;
        e.seed = seed;
        for (EmitterDefinition emitter : emitters) {
            e.emitters.add(emitter.copy());
        }
        return e;
    }
}
