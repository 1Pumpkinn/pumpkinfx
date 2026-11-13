package net.saturn.pumpkinfx.export;

import net.fabricmc.loader.api.FabricLoader;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EffectIds;
import net.saturn.pumpkinfx.storage.EffectStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ExportService {
    private static String lastName = "pumpkinfx-pack";

    private ExportService() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("pumpkinfx");
    }

    public static String lastName() {
        return lastName;
    }

    public static PackExporter.Result exportAll(String rawName) throws IOException {
        String name = EffectIds.sanitize(rawName);
        lastName = name;

        Path root = root();
        Path textures = root.resolve("textures");
        Path overlay = root.resolve("pack");
        Files.createDirectories(textures);
        Files.createDirectories(overlay);

        List<String> warnings = new ArrayList<>();
        List<EffectDefinition> effects = new ArrayList<>();
        for (String id : EffectStore.list()) {
            try {
                effects.add(EffectStore.load(id));
            } catch (Exception e) {
                warnings.add("Could not load effect '" + id + "': " + e.getMessage());
            }
        }
        return PackExporter.export(name, effects, textures, overlay, root.resolve("export"), warnings);
    }
}
