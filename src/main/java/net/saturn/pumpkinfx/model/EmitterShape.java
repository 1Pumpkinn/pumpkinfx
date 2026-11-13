package net.saturn.pumpkinfx.model;

public enum EmitterShape {
    POINT("Point"),
    SPHERE("Sphere"),
    HEMISPHERE("Hemisphere"),
    CONE("Cone"),
    BOX("Box"),
    RING("Ring"),
    LINE("Line");

    private final String displayName;

    EmitterShape(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public EmitterShape next() {
        EmitterShape[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public EmitterShape previous() {
        EmitterShape[] all = values();
        return all[(ordinal() + all.length - 1) % all.length];
    }
}
