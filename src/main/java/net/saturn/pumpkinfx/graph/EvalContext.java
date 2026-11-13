package net.saturn.pumpkinfx.graph;

public final class EvalContext {
    public final double progress;
    public final double tick;
    public final int index;
    public final long seed;

    public final double[] shapePosition = new double[3];
    public final double[] shapeDirection = new double[]{0, 1, 0};

    public EvalContext(double progress, double tick, int index, long seed) {
        this.progress = progress;
        this.tick = tick;
        this.index = index;
        this.seed = seed;
    }

    public void setShape(double px, double py, double pz, double dx, double dy, double dz) {
        shapePosition[0] = px;
        shapePosition[1] = py;
        shapePosition[2] = pz;
        shapeDirection[0] = dx;
        shapeDirection[1] = dy;
        shapeDirection[2] = dz;
    }
}
