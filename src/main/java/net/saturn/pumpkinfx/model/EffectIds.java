package net.saturn.pumpkinfx.model;

import java.util.Locale;

public final class EffectIds {
    private EffectIds() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "effect";
        }
        String cleaned = raw.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_\\-]", "");
        return cleaned.isEmpty() ? "effect" : cleaned;
    }
}
