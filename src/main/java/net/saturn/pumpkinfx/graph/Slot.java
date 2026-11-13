package net.saturn.pumpkinfx.graph;

public final class Slot {
    public int from = -1;
    public double x;
    public double y;
    public double z;

    public Slot() {
    }

    public Slot(double value) {
        this.x = value;
        this.y = value;
        this.z = value;
    }

    public Slot copy() {
        Slot slot = new Slot();
        slot.from = from;
        slot.x = x;
        slot.y = y;
        slot.z = z;
        return slot;
    }
}
