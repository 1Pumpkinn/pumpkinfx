package net.saturn.pumpkinfx.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.saturn.pumpkinfx.client.ParticleResolver;
import net.saturn.pumpkinfx.model.ColorStop;
import net.saturn.pumpkinfx.model.Curve;
import net.saturn.pumpkinfx.model.Gradient;
import net.saturn.pumpkinfx.model.Keyframe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class FormBuilder {
    public static final int HEADER_COLOR = 0xFFFFD27F;
    public static final int LABEL_COLOR = 0xFFAAAAAA;
    public static final int NOTE_COLOR = 0xFF888888;

    private static final int ROW = 20;
    private static final int GAP = 4;
    private static final int LABEL_W = 64;
    private static final int MAX_KEYS = 8;
    private static final int TEXT_OK = 0xFFE0E0E0;
    private static final int TEXT_BAD = 0xFFFF5555;

    public static final String SIGNED_FLOAT = "-?\\d*\\.?\\d*";
    public static final String UNSIGNED_FLOAT = "\\d*\\.?\\d*";
    public static final String SIGNED_INT = "-?\\d*";
    public static final String UNSIGNED_INT = "\\d*";
    public static final String HEX = "[0-9a-fA-F]{0,6}";

    private record Label(String text, int x, int y, int color) {
    }

    private record Swatch(int x, int y, int w, int h, IntSupplier color) {
    }

    private record Plot(int x, int y, int w, int h, Curve curve) {
    }

    private record GradientBar(int x, int y, int w, int h, Gradient gradient) {
    }

    private final Font font;
    private final Consumer<AbstractWidget> adder;
    private final int left;
    private final int width;
    private final List<Label> labels = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<Plot> plots = new ArrayList<>();
    private final List<GradientBar> bars = new ArrayList<>();
    private int y;
    private int column;

    public FormBuilder(Font font, Consumer<AbstractWidget> adder, int left, int top, int width) {
        this.font = font;
        this.adder = adder;
        this.left = left;
        this.y = top;
        this.width = width;
    }

    public void render(GuiGraphicsExtractor graphics) {
        for (Label label : labels) {
            graphics.text(font, label.text(), label.x(), label.y(), label.color(), false);
        }
        for (Swatch swatch : swatches) {
            int x2 = swatch.x() + swatch.w();
            int y2 = swatch.y() + swatch.h();
            graphics.fill(swatch.x() - 1, swatch.y() - 1, x2 + 1, y2 + 1, 0xFF000000);
            graphics.fill(swatch.x(), swatch.y(), x2, y2, 0xFF000000 | swatch.color().getAsInt());
        }
        for (Plot plot : plots) {
            renderPlot(graphics, plot);
        }
        for (GradientBar bar : bars) {
            graphics.fill(bar.x() - 1, bar.y() - 1, bar.x() + bar.w() + 1, bar.y() + bar.h() + 1, 0xFF000000);
            for (int i = 0; i < bar.w(); i++) {
                int rgb = bar.gradient().evaluate(i / (float) Math.max(1, bar.w() - 1));
                graphics.fill(bar.x() + i, bar.y(), bar.x() + i + 1, bar.y() + bar.h(), 0xFF000000 | rgb);
            }
        }
    }

    private static int plotY(Plot p, float value, float lo, float hi) {
        float t = (value - lo) / (hi - lo);
        int py = p.y() + Math.round((1f - t) * (p.h() - 2));
        return Math.max(p.y(), Math.min(p.y() + p.h() - 2, py));
    }

    private void renderPlot(GuiGraphicsExtractor graphics, Plot p) {
        graphics.fill(p.x() - 1, p.y() - 1, p.x() + p.w() + 1, p.y() + p.h() + 1, 0xFF000000);
        graphics.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), 0xFF1E1E1E);
        float[] bounds = p.curve().bounds();
        float lo = Math.min(0f, bounds[0]);
        float hi = Math.max(lo + 0.0001f, bounds[1]);
        int zero = plotY(p, 0f, lo, hi);
        graphics.fill(p.x(), zero, p.x() + p.w(), zero + 1, 0xFF444444);
        float[] samples = p.curve().sample(p.w());
        for (int i = 0; i < samples.length; i++) {
            int py = plotY(p, samples[i], lo, hi);
            graphics.fill(p.x() + i, py, p.x() + i + 1, py + 2, 0xFF55AAFF);
        }
        for (Keyframe key : p.curve().keys) {
            int kx = p.x() + Math.round(key.time() * (p.w() - 1));
            int ky = plotY(p, key.value(), lo, hi);
            graphics.fill(kx - 2, ky - 1, kx + 3, ky + 3, 0xFFFFD27F);
        }
        graphics.text(font, formatFloat(hi), p.x() + 3, p.y() + 2, NOTE_COLOR, false);
        if (lo != 0f) {
            graphics.text(font, formatFloat(lo), p.x() + 3, p.y() + p.h() - 10, NOTE_COLOR, false);
        }
    }

    public void labelAt(String text, int x, int labelY, int color) {
        labels.add(new Label(text, x, labelY, color));
    }

    public int y() {
        endRow();
        return y;
    }

    public void section(String title) {
        endRow();
        labels.add(new Label(title, left, y + 6, HEADER_COLOR));
        y += ROW + GAP;
    }

    public void note(String text) {
        endRow();
        labels.add(new Label(text, left, y + 2, NOTE_COLOR));
        y += 12;
    }

    public void note(String text, int color) {
        endRow();
        labels.add(new Label(text, left, y + 2, color));
        y += 12;
    }

    public void endRow() {
        if (column != 0) {
            column = 0;
            y += ROW + GAP;
        }
    }

    public void gap(int pixels) {
        endRow();
        y += pixels;
    }

    public void number(String label, float value, Consumer<Float> setter) {
        EditBox box = cellBox(label, formatFloat(value), 12);
        bind(box, s -> s.matches(SIGNED_FLOAT), s -> parseFloat(s, setter));
        adder.accept(box);
        advance();
    }

    public void integer(String label, int value, Consumer<Integer> setter) {
        EditBox box = cellBox(label, Integer.toString(value), 9);
        bind(box, s -> s.matches(SIGNED_INT), s -> parseInt(s, setter));
        adder.accept(box);
        advance();
    }

    public void text(String label, String value, Consumer<String> setter, int maxLength) {
        EditBox box = cellBox(label, value, maxLength);
        box.setResponder(setter);
        adder.accept(box);
        advance();
    }

    public void toggle(String label, boolean value, Consumer<Boolean> setter, Runnable afterChange) {
        boolean[] state = {value};
        addCellLabel(label);
        Button button = Button.builder(Component.literal(onOff(state[0])), btn -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            btn.setMessage(Component.literal(onOff(state[0])));
            if (afterChange != null) {
                afterChange.run();
            }
        }).bounds(cellX() + LABEL_W, y, cellWidth() - LABEL_W, ROW).build();
        adder.accept(button);
        advance();
    }

    public void cycle(String label, Supplier<String> text, Runnable onClick) {
        addCellLabel(label);
        Button button = Button.builder(Component.literal(text.get()), btn -> {
            onClick.run();
            btn.setMessage(Component.literal(text.get()));
        }).bounds(cellX() + LABEL_W, y, cellWidth() - LABEL_W, ROW).build();
        adder.accept(button);
        advance();
    }

    public void particle(String value, Consumer<String> setter) {
        endRow();
        labels.add(new Label("Particle", left, y + 6, LABEL_COLOR));
        int boxX = left + LABEL_W;
        int arrow = 20;
        int boxW = width - LABEL_W - (arrow + 2) * 2;
        EditBox box = new EditBox(font, boxX, y, boxW, ROW, Component.literal("Particle"));
        box.setMaxLength(64);
        box.setValue(value);
        box.setTextColor(ParticleResolver.isKnown(value) ? TEXT_OK : TEXT_BAD);
        box.setResponder(s -> {
            setter.accept(s);
            box.setTextColor(ParticleResolver.isKnown(s) ? TEXT_OK : TEXT_BAD);
        });
        adder.accept(box);
        adder.accept(Button.builder(Component.literal("<"), b -> stepParticle(box, -1))
                .bounds(boxX + boxW + 2, y, arrow, ROW).build());
        adder.accept(Button.builder(Component.literal(">"), b -> stepParticle(box, 1))
                .bounds(boxX + boxW + 2 + arrow + 2, y, arrow, ROW).build());
        y += ROW + GAP;
    }

    public void curve(Curve curve, Runnable rebuild) {
        endRow();
        labels.add(new Label("Time (0-1)", left, y + 2, NOTE_COLOR));
        labels.add(new Label("Value", left + 68, y + 2, NOTE_COLOR));
        labels.add(new Label("Blend to next", left + 176, y + 2, NOTE_COLOR));
        y += 12;
        for (int i = 0; i < curve.keys.size(); i++) {
            int index = i;
            Keyframe key = curve.keys.get(i);
            EditBox time = rawBox(left, 64, formatFloat(key.time()), 10);
            bind(time, s -> s.matches(UNSIGNED_FLOAT), s -> parseFloat(s, v -> {
                curve.keys.set(index, curve.keys.get(index).withTime(Math.max(0f, Math.min(1f, v))));
            }));
            EditBox value = rawBox(left + 68, 80, formatFloat(key.value()), 12);
            bind(value, s -> s.matches(SIGNED_FLOAT), s -> parseFloat(s, v -> {
                curve.keys.set(index, curve.keys.get(index).withValue(v));
            }));
            adder.accept(time);
            adder.accept(value);
            adder.accept(removeButton(left + 152, () -> {
                curve.removeAt(index);
                rebuild.run();
            }));
            adder.accept(Button.builder(Component.literal(key.interp().displayName()), b -> {
                if (index < curve.keys.size()) {
                    curve.setInterp(index, curve.keys.get(index).interp().next());
                    b.setMessage(Component.literal(curve.keys.get(index).interp().displayName()));
                }
            }).bounds(left + 176, y, 56, ROW).build());
            y += ROW + GAP;
        }
        Button add = Button.builder(Component.literal("+ Key"), b -> {
            curve.addMidpoint();
            rebuild.run();
        }).bounds(left, y, 64, ROW).build();
        add.active = curve.keys.size() < MAX_KEYS;
        adder.accept(add);
        y += ROW + GAP;
        plots.add(new Plot(left, y + 2, Math.min(232, width), 60, curve));
        y += 60 + GAP + 4;
    }

    public void gradient(Gradient gradient, Runnable rebuild) {
        endRow();
        labels.add(new Label("Time (0-1)", left, y + 2, NOTE_COLOR));
        labels.add(new Label("Hex RGB", left + 68, y + 2, NOTE_COLOR));
        y += 12;
        for (int i = 0; i < gradient.stops.size(); i++) {
            int index = i;
            ColorStop stop = gradient.stops.get(i);
            EditBox time = rawBox(left, 64, formatFloat(stop.time()), 10);
            bind(time, s -> s.matches(UNSIGNED_FLOAT), s -> parseFloat(s, v -> {
                ColorStop old = gradient.stops.get(index);
                gradient.stops.set(index, new ColorStop(Math.max(0f, Math.min(1f, v)), old.rgb()));
            }));
            EditBox hex = rawBox(left + 68, 64, String.format(Locale.ROOT, "%06X", stop.rgb()), 6);
            bind(hex, s -> s.matches(HEX), s -> {
                if (s.length() == 6) {
                    int rgb = Integer.parseInt(s, 16);
                    ColorStop old = gradient.stops.get(index);
                    gradient.stops.set(index, new ColorStop(old.time(), rgb));
                }
            });
            adder.accept(time);
            adder.accept(hex);
            swatches.add(new Swatch(left + 136, y + 3, 14, 14, () -> {
                if (index < gradient.stops.size()) {
                    return gradient.stops.get(index).rgb();
                }
                return 0;
            }));
            adder.accept(removeButton(left + 156, () -> {
                gradient.removeAt(index);
                rebuild.run();
            }));
            y += ROW + GAP;
        }
        Button add = Button.builder(Component.literal("+ Stop"), b -> {
            gradient.addMidpoint();
            rebuild.run();
        }).bounds(left, y, 64, ROW).build();
        add.active = gradient.stops.size() < MAX_KEYS;
        adder.accept(add);
        y += ROW + GAP;
        bars.add(new GradientBar(left, y + 2, Math.min(232, width), 14, gradient));
        y += 14 + GAP + 4;
    }

    public static void bind(EditBox box, Predicate<String> valid, Consumer<String> onValid) {
        box.setResponder(s -> {
            boolean ok = valid.test(s);
            box.setTextColor(ok ? TEXT_OK : TEXT_BAD);
            if (ok) {
                onValid.accept(s);
            }
        });
    }

    private void stepParticle(EditBox box, int direction) {
        List<String> options = ParticleResolver.options();
        if (options.isEmpty()) {
            return;
        }
        int current = options.indexOf(box.getValue());
        int next = current < 0
                ? (direction > 0 ? 0 : options.size() - 1)
                : Math.floorMod(current + direction, options.size());
        box.setValue(options.get(next));
    }

    private Button removeButton(int x, Runnable action) {
        return Button.builder(Component.literal("x"), b -> action.run())
                .bounds(x, y, 20, ROW).build();
    }

    private EditBox rawBox(int x, int w, String value, int maxLength) {
        EditBox box = new EditBox(font, x, y, w, ROW, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        return box;
    }

    private EditBox cellBox(String label, String value, int maxLength) {
        addCellLabel(label);
        EditBox box = new EditBox(font, cellX() + LABEL_W, y, cellWidth() - LABEL_W, ROW, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value);
        return box;
    }

    private void addCellLabel(String label) {
        labels.add(new Label(label, cellX(), y + 6, LABEL_COLOR));
    }

    private int cellWidth() {
        return (width - GAP) / 2;
    }

    private int cellX() {
        return left + column * (cellWidth() + GAP);
    }

    private void advance() {
        column++;
        if (column > 1) {
            column = 0;
            y += ROW + GAP;
        }
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static String formatFloat(float value) {
        String s = String.format(Locale.ROOT, "%.4f", value);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() || s.equals("-") ? "0" : s;
    }

    private static void parseFloat(String s, Consumer<Float> setter) {
        try {
            setter.accept(Float.parseFloat(s));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void parseInt(String s, Consumer<Integer> setter) {
        try {
            setter.accept(Integer.parseInt(s));
        } catch (NumberFormatException ignored) {
        }
    }
}
