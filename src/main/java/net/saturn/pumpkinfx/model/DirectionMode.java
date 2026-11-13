package net.saturn.pumpkinfx.model;

public enum DirectionMode {
    SHAPE("From shape"),
    UP("Up"),
    FIXED("Fixed");

    private final String displayName;

    DirectionMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public DirectionMode next() {
        DirectionMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
