package net.saturn.pumpkinfx.model;

/**
 * One point on a {@link Curve}. Files written before interpolation existed have no "interp"
 * field, so a missing value reads as linear.
 */
public record Keyframe(float time, float value, Interp interp) {
    public Keyframe {
        if (interp == null) {
            interp = Interp.LINEAR;
        }
    }

    public Keyframe(float time, float value) {
        this(time, value, Interp.LINEAR);
    }

    public Keyframe withTime(float newTime) {
        return new Keyframe(newTime, value, interp);
    }

    public Keyframe withValue(float newValue) {
        return new Keyframe(time, newValue, interp);
    }

    public Keyframe withInterp(Interp newInterp) {
        return new Keyframe(time, value, newInterp);
    }
}
