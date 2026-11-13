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
    FLOOR("Floor", Category.MATH, "X"),
    SATURATE("Saturate", Category.MATH, "X"),
    SQRT("Square root", Category.MATH, "X"),
    STEP("Step", Category.MATH, new double[]{0.5, 0}, "Edge", "X"),
    MOD("Modulo", Category.MATH, new double[]{0, 1}, "A", "B"),

    COMBINE("Combine", Category.VECTOR, "X", "Y", "Z"),
    SPLIT_X("Split X", Category.VECTOR, "Vec"),
    SPLIT_Y("Split Y", Category.VECTOR, "Vec"),
    SPLIT_Z("Split Z", Category.VECTOR, "Vec"),
    NORMALIZE("Normalize", Category.VECTOR, "Vec"),
    LENGTH("Length", Category.VECTOR, "Vec"),
    ROTATE_Y("Rotate Y", Category.VECTOR, "Vec", "Radians"),
    SCALE("Scale vector", Category.VECTOR, new double[]{0, 1}, "Vec", "Factor"),
    DOT("Dot", Category.VECTOR, "A", "B"),
    CROSS("Cross", Category.VECTOR, "A", "B"),
    DISTANCE("Distance", Category.VECTOR, "A", "B"),

    NOISE("Noise", Category.UTILITY, new double[]{0, 1}, "Position", "Scale"),
    FBM("Fractal noise", Category.UTILITY, new double[]{0, 1, 4}, "Position", "Scale", "Octaves"),
    CURVE("Curve", Category.UTILITY, "X"),
    GRADIENT("Gradient", Category.UTILITY, "T"),

    OUT_RATE("Output: rate", Category.OUTPUT, "Rate"),
    OUT_SPEED("Output: speed", Category.OUTPUT, "Speed"),
    OUT_SIZE("Output: size", Category.OUTPUT, new double[]{1}, "Size"),
    OUT_COLOR("Output: colour", Category.OUTPUT, new double[]{1}, "Colour"),
    OUT_POSITION("Output: position", Category.OUTPUT, "Position"),
    OUT_DIRECTION("Output: direction", Category.OUTPUT, "Direction"),

    SH_UV("Shader: atlas UV", Category.SHADER),
    SH_FRAG_POS("Shader: screen position", Category.SHADER),
    SH_BASE_COLOR("Shader: vanilla colour", Category.SHADER),
    SH_BASE_ALPHA("Shader: vanilla alpha", Category.SHADER),
    SH_OUT_COLOR("Shader output: colour", Category.OUTPUT, new double[]{1}, "Colour"),
    SH_OUT_ALPHA("Shader output: alpha", Category.OUTPUT, new double[]{1}, "Alpha");

    public enum Category {
        SOURCE, MATH, VECTOR, UTILITY, SHADER, OUTPUT
    }

    /**
     * What a node produces. Every value in the graph is stored as three numbers; this says how
     * to read them. ANY means component-wise maths that follows whatever is plugged in.
     * The editor uses it to colour ports, and shader code generation will use it to pick types.
     */
    public enum ValueKind {
        SCALAR, VECTOR, COLOR, ANY
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

    /** Outputs that feed the generated particle fragment shader rather than the CPU spawner. */
    public boolean isShaderOutput() {
        return this == SH_OUT_COLOR || this == SH_OUT_ALPHA;
    }

    public ValueKind outputKind() {
        return switch (this) {
            case RANDOM_DIRECTION, SHAPE_POSITION, SHAPE_DIRECTION, VECTOR, COMBINE, NORMALIZE,
                 ROTATE_Y, SCALE, CROSS, OUT_POSITION, OUT_DIRECTION, SH_UV, SH_FRAG_POS -> ValueKind.VECTOR;
            case COLOR, GRADIENT, OUT_COLOR, SH_BASE_COLOR, SH_OUT_COLOR -> ValueKind.COLOR;
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MIN, MAX, POWER, SIN, COS, ABS, FRACT, NEGATE,
                 CLAMP, LERP, REMAP, SMOOTHSTEP, FLOOR, SATURATE, SQRT, STEP, MOD, CURVE -> ValueKind.ANY;
            default -> ValueKind.SCALAR;
        };
    }
}
