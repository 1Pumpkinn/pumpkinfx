package net.saturn.pumpkinfx.storage;

import net.fabricmc.loader.api.FabricLoader;
import net.saturn.pumpkinfx.PumpkinFxClient;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EffectIds;
import net.saturn.pumpkinfx.model.EffectSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class EffectStore {
    private static final String EXTENSION = ".json";

    private EffectStore() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("pumpkinfx").resolve("effects");
    }

    public static Path pathFor(String id) {
        return directory().resolve(EffectIds.sanitize(id) + EXTENSION);
    }

    public static List<String> list() {
        Path dir = directory();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        ids.add(name.substring(0, name.length() - EXTENSION.length()));
                    });
        } catch (IOException e) {
            PumpkinFxClient.LOGGER.error("Could not list effects", e);
        }
        Collections.sort(ids);
        return ids;
    }

    public static EffectDefinition load(String id) throws IOException {
        return EffectSerializer.load(pathFor(id));
    }

    public static void save(EffectDefinition effect) throws IOException {
        effect.normalize();
        EffectSerializer.save(pathFor(effect.id), effect);
    }

    public static boolean delete(String id) throws IOException {
        return Files.deleteIfExists(pathFor(id));
    }

    public static boolean exists(String id) {
        return Files.exists(pathFor(id));
    }
}
