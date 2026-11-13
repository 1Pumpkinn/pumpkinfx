package net.saturn.pumpkinfx.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Curve {
    public List<Keyframe> keys = new ArrayList<>();

    public Curve() {
    }

    public static Curve constant(float value) {
        Curve curve = new Curve();
        curve.keys.add(new Keyframe(0f, value));
        return curve;
    }

    public static Curve linear(float from, float to) {
        Curve curve = new Curve();
        curve.keys.add(new Keyframe(0f, from));
        curve.keys.add(new Keyframe(1f, to));
        return curve;
    }

    public void sort() {
        keys.sort(Comparator.comparingDouble(Keyframe::time));
    }

    public void add(float time, float value) {
        keys.add(new Keyframe(clamp01(time), value));
        sort();
    }

    public void addMidpoint() {
        sort();
        float bestStart = 0f;
        float bestEnd = 1f;
        float bestGap = -1f;
        float prev = 0f;
        for (Keyframe key : keys) {
            float gap = key.time() - prev;
            if (gap > bestGap) {
                bestGap = gap;
                bestStart = prev;
                bestEnd = key.time();
            }
            prev = key.time();
        }
        if (1f - prev > bestGap) {
            bestStart = prev;
            bestEnd = 1f;
        }
        float t = (bestStart + bestEnd) / 2f;
        add(t, evaluate(t));
    }

    public void removeAt(int index) {
        if (keys.size() > 1 && index >= 0 && index < keys.size()) {
            keys.remove(index);
        }
    }

    public void setAt(int index, float time, float value) {
        if (index < 0 || index >= keys.size()) {
            return;
        }
        keys.set(index, new Keyframe(clamp01(time), value));
        sort();
    }

    public float evaluate(float t) {
        if (keys == null || keys.isEmpty()) {
            return 0f;
        }
        t = clamp01(t);
        Keyframe first = keys.get(0);
        if (keys.size() == 1 || t <= first.time()) {
            return first.value();
        }
        Keyframe last = keys.get(keys.size() - 1);
        if (t >= last.time()) {
            return last.value();
        }
        for (int i = 0; i < keys.size() - 1; i++) {
            Keyframe a = keys.get(i);
            Keyframe b = keys.get(i + 1);
            if (t >= a.time() && t <= b.time()) {
                float span = b.time() - a.time();
                if (span <= 0f) {
                    return b.value();
                }
                float f = (t - a.time()) / span;
                return a.value() + (b.value() - a.value()) * f;
            }
        }
        return last.value();
    }

    public Curve copy() {
        Curve curve = new Curve();
        curve.keys = new ArrayList<>(keys);
        return curve;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
