package net.saturn.pumpkinfx.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EffectSerializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private EffectSerializer() {
    }

    public static String toJson(EffectDefinition effect) {
        return GSON.toJson(effect);
    }

    public static EffectDefinition fromJson(String json) {
        EffectDefinition effect = GSON.fromJson(json, EffectDefinition.class);
        if (effect == null) {
            throw new JsonParseException("Empty effect file");
        }
        effect.normalize();
        return effect;
    }

    public static EffectDefinition load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            EffectDefinition effect = GSON.fromJson(reader, EffectDefinition.class);
            if (effect == null) {
                throw new JsonParseException("Empty effect file: " + path);
            }
            effect.normalize();
            return effect;
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
}
