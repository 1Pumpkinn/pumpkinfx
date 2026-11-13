package net.saturn.pumpkinfx.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps the game's own {@code particle.fsh} instead of replacing it. The vanilla {@code main}
 * is renamed and still runs first; a new {@code main} then hands the colour it produced to the
 * generated {@code pfx_shade}. Nothing here assumes a particular vanilla uniform or helper: only
 * the {@code out vec4}, one {@code in vec2} (texture coordinates) and {@code void main()} are
 * looked up, so the result keeps working when Mojang changes the rest of the shader.
 */
public final class ParticleShaderInjector {
    public static final String MARKER = "PUMPKINFX_INJECTED";
    public static final String RENAMED_MAIN = "pfx_vanilla_main";

    private static final Pattern OUT_VEC4 =
            Pattern.compile("(?m)^[ \\t]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?:(?:highp|mediump|lowp)\\s+)?vec4\\s+(\\w+)\\s*;");
    private static final Pattern IN_VEC2 =
            Pattern.compile("(?m)^[ \\t]*(?:(?:flat|smooth|noperspective)\\s+)?in\\s+(?:(?:highp|mediump|lowp)\\s+)?vec2\\s+(\\w+)\\s*;");
    private static final Pattern MAIN =
            Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");

    public record Result(String source, List<String> warnings) {
    }

    private ParticleShaderInjector() {
    }

    /** @throws IllegalArgumentException if the vanilla source does not look like a fragment shader we can wrap */
    public static Result inject(String vanilla, String generated) {
        if (vanilla.contains(MARKER)) {
            throw new IllegalArgumentException("The shader was already patched by PumpkinFX");
        }
        List<String> warnings = new ArrayList<>();
        // Search a copy with comments blanked out so a commented-out main() or output is not matched.
        String code = maskComments(vanilla);

        Matcher out = OUT_VEC4.matcher(code);
        if (!out.find()) {
            throw new IllegalArgumentException("Could not find the 'out vec4' colour output in particle.fsh");
        }
        String colorOut = out.group(1);

        String uv = null;
        Matcher in = IN_VEC2.matcher(code);
        while (in.find()) {
            String candidate = in.group(1);
            if (uv == null || candidate.toLowerCase(java.util.Locale.ROOT).contains("texcoord")) {
                uv = candidate;
                if (candidate.toLowerCase(java.util.Locale.ROOT).contains("texcoord")) {
                    break;
                }
            }
        }
        String uvExpr = uv;
        if (uvExpr == null) {
            uvExpr = "vec2(0.5)";
            warnings.add("particle.fsh has no vec2 input, so the shader's UV node always reads (0.5, 0.5)");
        }

        Matcher main = MAIN.matcher(code);
        if (!main.find()) {
            throw new IllegalArgumentException("Could not find 'void main()' in particle.fsh");
        }
        int start = main.start();
        int end = main.end();
        if (main.find()) {
            throw new IllegalArgumentException("Found more than one 'void main()' in particle.fsh");
        }

        StringBuilder result = new StringBuilder(vanilla.length() + generated.length() + 256);
        result.append(vanilla, 0, start);
        result.append("void ").append(RENAMED_MAIN).append("()");
        result.append(vanilla, end, vanilla.length());
        if (result.charAt(result.length() - 1) != '\n') {
            result.append('\n');
        }
        result.append("\n// ---- PumpkinFX generated shader (").append(MARKER).append(") ----\n");
        result.append(generated);
        result.append("\nvoid main() {\n");
        result.append("    ").append(RENAMED_MAIN).append("();\n");
        result.append("    ").append(colorOut).append(" = ").append(ShaderGraphCompiler.ENTRY_POINT)
                .append("(").append(colorOut).append(", ").append(uvExpr).append(");\n");
        result.append("}\n");
        return new Result(result.toString(), warnings);
    }

    /** Same length as the input, with comment text replaced by spaces (newlines kept). */
    static String maskComments(String source) {
        char[] chars = source.toCharArray();
        int i = 0;
        while (i < chars.length) {
            if (chars[i] == '/' && i + 1 < chars.length && chars[i + 1] == '/') {
                while (i < chars.length && chars[i] != '\n') {
                    chars[i++] = ' ';
                }
            } else if (chars[i] == '/' && i + 1 < chars.length && chars[i + 1] == '*') {
                chars[i++] = ' ';
                chars[i++] = ' ';
                while (i < chars.length && !(chars[i] == '*' && i + 1 < chars.length && chars[i + 1] == '/')) {
                    if (chars[i] != '\n') {
                        chars[i] = ' ';
                    }
                    i++;
                }
                if (i < chars.length) {
                    chars[i++] = ' ';
                    if (i < chars.length) {
                        chars[i++] = ' ';
                    }
                }
            } else {
                i++;
            }
        }
        return new String(chars);
    }
}
