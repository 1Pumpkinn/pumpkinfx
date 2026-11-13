package net.saturn.pumpkinfx.graph;

public enum NodeType {
    TIME("Time", Category.SOURCE),
    TICK("Tick", Category.SOURCE),
    INDEX("Spawn index", Category.SOURCE),
    RANDOM("Random", Category.SOURCE),
    RANDOM_DIRECTION("Random direction", Category.SOURCE),
    SHAPE_POSITION("Shape position", Category.SOURCE),
    SHAPE_DIRECTION("Shape direction", Category.SOURCE),
    CONSTANT("Constant", Category.SOURCE),
    VECTOR("Vector", Category.SOURCE),
    COLOR("Colour", Category.SOURCE),

    ADD("Add", Category.MATH, "A", "B"),
    SUBTRACT("Subtract", Category.MATH, "A", "B"),
    MULTIPLY("Multiply", Category.MATH, new double[]{1, 1}, "A", "B"),
    DIVIDE("Divide", Category.MATH, new double[]{1, 1}, "A", "B"),
    MIN("Min", Category.MATH, "A", "B"),
    MAX("Max", Category.MATH, "A", "B"),
    POWER("Power", Category.MATH, new double[]{1, 1}, "Base", "Exp"),
    SIN("Sin", Category.MATH, "X"),
    COS("Cos", Category.MATH, "X"),
    ABS("Abs", Category.MATH, "X"),
    FRACT("Fract", Category.MATH, "X"),
    NEGATE("Negate", Category.MATH, "X"),
    CLAMP("Clamp", Category.MATH, new double[]{0, 0, 1}, "X", "Min", "Max"),
    LERP("Lerp", Category.MATH, new double[]{0, 1, 0.5}, "A", "B", "T"),
    REMAP("Remap", Category.MATH, new double[]{0, 0, 1, 0, 1}, "X", "In min", "In max", "Out min", "Out max"),
    SMOOTHSTEP("Smoothstep", Category.MATH, new double[]{0, 0, 1}, "X", "Edge 0", "Edge 1"),

    COMBINE("Combine", Category.VECTOR, "X", "Y", "Z"),
    SPLIT_X("Split X", Category.VECTOR, "Vec"),
    SPLIT_Y("Split Y", Category.VECTOR, "Vec"),
    SPLIT_Z("Split Z", Category.VECTOR, "Vec"),
    NORMALIZE("Normalize", Category.VECTOR, "Vec"),
    LENGTH("Length", Category.VECTOR, "Vec"),
    ROTATE_Y("Rotate Y", Category.VECTOR, "Vec", "Radians"),

    NOISE("Noise", Category.UTILITY, new double[]{0, 1}, "Position", "Scale"),
    CURVE("Curve", Category.UTILITY, "X"),
    GRADIENT("Gradient", Category.UTILITY, "T"),

    OUT_RATE("Output: rate", Category.OUTPUT, "Rate"),
    OUT_SPEED("Output: speed", Category.OUTPUT, "Speed"),
    OUT_SIZE("Output: size", Category.OUTPUT, new double[]{1}, "Size"),
    OUT_COLOR("Output: colour", Category.OUTPUT, new double[]{1}, "Colour"),
    OUT_POSITION("Output: position", Category.OUTPUT, "Position"),
    OUT_DIRECTION("Output: direction", Category.OUTPUT, "Direction");

    public enum Category {
        SOURCE, MATH, VECTOR, UTILITY, OUTPUT
    }

    private final String displayName;
    private final Category category;
    private final double[] defaults;
    private final String[] inputLabels;

    NodeType(String displayName, Category category, String... inputLabels) {
        this(displayName, category, new double[inputLabels.length], inputLabels);
    }

    NodeType(String displayName, Category category, double[] defaults, String... inputLabels) {
        this.displayName = displayName;
        this.category = category;
        this.defaults = defaults;
        this.inputLabels = inputLabels;
    }

    public String displayName() {
        return displayName;
    }

    public Category category() {
        return category;
    }

    public int inputCount() {
        return inputLabels.length;
    }

    public String inputLabel(int slot) {
        return inputLabels[slot];
    }

    public double defaultInput(int slot) {
        return defaults[slot];
    }

    public boolean isOutput() {
        return category == Category.OUTPUT;
    }
}
