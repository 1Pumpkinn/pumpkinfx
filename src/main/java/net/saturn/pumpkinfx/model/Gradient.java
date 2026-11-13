package net.saturn.pumpkinfx.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Gradient {
    public List<ColorStop> stops = new ArrayList<>();

    public Gradient() {
    }

    public static Gradient solid(int rgb) {
        Gradient gradient = new Gradient();
        gradient.stops.add(new ColorStop(0f, rgb & 0xFFFFFF));
        return gradient;
    }

    public static Gradient between(int from, int to) {
        Gradient gradient = new Gradient();
        gradient.stops.add(new ColorStop(0f, from & 0xFFFFFF));
        gradient.stops.add(new ColorStop(1f, to & 0xFFFFFF));
        return gradient;
    }

    public void add(float time, int rgb) {
        stops.add(new ColorStop(clamp01(time), rgb & 0xFFFFFF));
        stops.sort(Comparator.comparingDouble(ColorStop::time));
    }

    public void removeAt(int index) {
        if (stops.size() > 1 && index >= 0 && index < stops.size()) {
            stops.remove(index);
        }
    }

    public void setAt(int index, float time, int rgb) {
        if (index < 0 || index >= stops.size()) {
            return;
        }
        stops.set(index, new ColorStop(clamp01(time), rgb & 0xFFFFFF));
        stops.sort(Comparator.comparingDouble(ColorStop::time));
    }

    public int evaluate(float t) {
        if (stops == null || stops.isEmpty()) {
            return 0xFFFFFF;
        }
        t = clamp01(t);
        ColorStop first = stops.get(0);
        if (stops.size() == 1 || t <= first.time()) {
            return first.rgb();
        }
        ColorStop last = stops.get(stops.size() - 1);
        if (t >= last.time()) {
            return last.rgb();
        }
        for (int i = 0; i < stops.size() - 1; i++) {
            ColorStop a = stops.get(i);
            ColorStop b = stops.get(i + 1);
            if (t >= a.time() && t <= b.time()) {
                float span = b.time() - a.time();
                if (span <= 0f) {
                    return b.rgb();
                }
                return lerpRgb(a.rgb(), b.rgb(), (t - a.time()) / span);
            }
        }
        return last.rgb();
    }

    public Gradient copy() {
        Gradient gradient = new Gradient();
        gradient.stops = new ArrayList<>(stops);
        return gradient;
    }

    private static int lerpRgb(int a, int b, float f) {
        int r = lerpChannel((a >> 16) & 0xFF, (b >> 16) & 0xFF, f);
        int g = lerpChannel((a >> 8) & 0xFF, (b >> 8) & 0xFF, f);
        int bl = lerpChannel(a & 0xFF, b & 0xFF, f);
        return (r << 16) | (g << 8) | bl;
    }

    private static int lerpChannel(int a, int b, float f) {
        return Math.round(a + (b - a) * f);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
