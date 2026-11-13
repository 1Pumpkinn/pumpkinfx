package net.saturn.pumpkinfx.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Upgrades effect files written by older versions. Runs on the raw JSON before it is mapped
 * onto {@link EffectDefinition}, so old files never have to match the current class layout.
 *
 * <p>To change the file format: bump {@link EffectDefinition#FORMAT_VERSION}, add one
 * {@code vNToVN+1} step here and a case for it in {@link #migrate(JsonObject)}.
 */
public final class EffectMigrations {
    private EffectMigrations() {
    }

    public static int versionOf(JsonObject root) {
        JsonElement element = root.get("formatVersion");
        if (element == null || !element.isJsonPrimitive()) {
            return 1;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid formatVersion: " + element);
        }
    }

    public static JsonObject migrate(JsonObject root) {
        int version = versionOf(root);
        if (version > EffectDefinition.FORMAT_VERSION) {
            throw new JsonParseException("This effect uses format " + version
                    + " but this PumpkinFX only understands up to " + EffectDefinition.FORMAT_VERSION
                    + ". Update PumpkinFX to open it.");
        }
        if (version < 1) {
            throw new JsonParseException("Invalid formatVersion: " + version);
        }
        while (version < EffectDefinition.FORMAT_VERSION) {
            switch (version) {
                case 1 -> v1ToV2(root);
                default -> throw new JsonParseException("No migration from format " + version);
            }
            version++;
        }
        root.addProperty("formatVersion", EffectDefinition.FORMAT_VERSION);
        return root;
    }

    /** v2 added effect "seed" and per-keyframe "interp". Both have safe defaults, so only seed is written. */
    private static void v1ToV2(JsonObject root) {
        if (!root.has("seed")) {
            root.addProperty("seed", 0L);
        }
    }
}
