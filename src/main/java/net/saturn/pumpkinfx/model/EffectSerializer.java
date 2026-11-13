package net.saturn.pumpkinfx.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EffectSerializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private EffectSerializer() {
    }

    public static String toJson(EffectDefinition effect) {
        return GSON.toJson(effect);
    }

    /** Single-line JSON, used for undo snapshots where readability does not matter. */
    public static String toCompactJson(EffectDefinition effect) {
        return COMPACT.toJson(effect);
    }

    public static EffectDefinition fromJson(String json) {
        return fromElement(JsonParser.parseString(json), "effect");
    }

    public static EffectDefinition load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return fromElement(JsonParser.parseReader(reader), path.toString());
        }
    }

    public static void save(Path path, EffectDefinition effect) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(effect, writer);
        }
    }

    private static EffectDefinition fromElement(JsonElement element, String source) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("Empty or invalid effect file: " + source);
        }
        JsonObject migrated = EffectMigrations.migrate(element.getAsJsonObject());
        EffectDefinition effect = GSON.fromJson(migrated, EffectDefinition.class);
        if (effect == null) {
            throw new JsonParseException("Empty effect file: " + source);
        }
        effect.normalize();
        return effect;
    }
}
