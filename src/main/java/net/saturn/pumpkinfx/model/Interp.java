package net.saturn.pumpkinfx.model;

/**
 * How a curve moves from one keyframe to the next. The mode is stored on the keyframe the
 * segment starts at, so "key 2 = Smooth" shapes the stretch between key 2 and key 3.
 */
public enum Interp {
    LINEAR("Linear"),
    SMOOTH("Smooth"),
    STEP("Step");

    private final String displayName;

    Interp(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public Interp next() {
        Interp[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Maps a 0-1 position inside a segment to the 0-1 blend between the two key values. */
    public float apply(float f) {
        return switch (this) {
            case LINEAR -> f;
            case SMOOTH -> f * f * (3f - 2f * f);
            case STEP -> 0f;
        };
    }
}
