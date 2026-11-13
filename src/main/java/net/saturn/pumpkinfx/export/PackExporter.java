package net.saturn.pumpkinfx.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EffectIds;
import net.saturn.pumpkinfx.model.EffectSerializer;
import net.saturn.pumpkinfx.model.EffectValidator;
import net.saturn.pumpkinfx.shader.ShaderExport;
import net.saturn.pumpkinfx.shader.VanillaShaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PackExporter {
    public static final int RESOURCE_MIN_FORMAT = 75;
    public static final int RESOURCE_MAX_FORMAT = 88;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String PARTICLE_TEXTURES = "assets/minecraft/textures/particle/";
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9_.\\-]+");

    public record Result(String name, Path zip, Path effectsDir,
                         int effectCount, int textureCount, int overlayCount,
                         boolean shaderExported, List<String> warnings) {
    }

    private PackExporter() {
    }

    public static Result export(String rawName, List<EffectDefinition> effects,
                                Path textureDir, Path overlayDir, Path exportRoot,
                                List<String> warnings) throws IOException {
        String name = EffectIds.sanitize(rawName);
        Path outDir = exportRoot.resolve(name);
        Path effectsDir = outDir.resolve("effects");
        Files.createDirectories(effectsDir);
        clearJson(effectsDir);

        Path zip = outDir.resolve(name + "-resourcepack.zip");
        Set<String> written = new HashSet<>();
        ShaderExport.Entry shader = ShaderExport.plan(effects, VanillaShaders::particleFragment, warnings);
        int textures;
        int overlay;
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeEntry(out, written, "pack.mcmeta", packMeta(name).getBytes(StandardCharsets.UTF_8));
            if (shader != null) {
                writeEntry(out, written, shader.path(), shader.data());
            }
            textures = addTextures(out, written, textureDir, warnings);
            overlay = addOverlay(out, written, overlayDir, warnings);
        }

        List<String> ids = new ArrayList<>();
        for (EffectDefinition effect : effects) {
            effect.normalize();
            if (ids.contains(effect.id)) {
                warnings.add("Duplicate effect id '" + effect.id + "', the later one wins");
            } else {
                ids.add(effect.id);
            }
            for (EffectValidator.Issue issue : EffectValidator.validate(effect, null)) {
                if (issue.severity() != EffectValidator.Severity.INFO) {
                    warnings.add("[" + effect.id + "] " + issue);
                }
            }
            EffectSerializer.save(effectsDir.resolve(effect.id + ".json"), effect);
        }
        writeIndex(effectsDir.resolve("index.json"), name, ids);

        return new Result(name, zip, effectsDir, ids.size(), textures, overlay, shader != null, warnings);
    }

    private static String packMeta(String name) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", name + " (PumpkinFX export)");
        pack.add("min_format", formatPair(RESOURCE_MIN_FORMAT));
        pack.add("max_format", formatPair(RESOURCE_MAX_FORMAT));
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return GSON.toJson(root);
    }

    private static JsonArray formatPair(int major) {
        JsonArray pair = new JsonArray();
        pair.add(major);
        pair.add(0);
        return pair;
    }

    private static void writeIndex(Path path, String name, List<String> ids) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", EffectDefinition.FORMAT_VERSION);
        root.addProperty("pack", name);
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        root.add("effects", array);
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static void clearJson(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
                Files.delete(p);
            }
        }
    }

    private static int addTextures(ZipOutputStream out, Set<String> written, Path dir,
                                   List<String> warnings) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            boolean png = fileName.endsWith(".png");
            boolean meta = fileName.endsWith(".png.mcmeta");
            if (!png && !meta) {
                warnings.add("Skipped '" + fileName + "' in textures (only .png and .png.mcmeta are used)");
                continue;
            }
            if (!FILE_NAME.matcher(fileName).matches()) {
                warnings.add("Skipped '" + fileName + "': use lowercase letters, digits, _ - . only");
                continue;
            }
            if (writeEntry(out, written, PARTICLE_TEXTURES + fileName, Files.readAllBytes(file)) && png) {
                count++;
            }
        }
        return count;
    }

    private static int addOverlay(ZipOutputStream out, Set<String> written, Path dir,
                                  List<String> warnings) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        List<Path> files;
        try (Stream<Path> stream = Files.walk(dir)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            String rel = dir.relativize(file).toString().replace('\\', '/');
            if (rel.equals("pack.mcmeta")) {
                warnings.add("Ignored pack.mcmeta in the pack folder, PumpkinFX writes its own");
                continue;
            }
            if (rel.startsWith("assets/") && !rel.equals(rel.toLowerCase(Locale.ROOT))) {
                warnings.add("'" + rel + "' has uppercase letters, Minecraft will ignore it");
            }
            if (written.contains(rel)) {
                warnings.add("'" + rel + "' already exists in the pack, skipped the pack folder copy");
                continue;
            }
            if (writeEntry(out, written, rel, Files.readAllBytes(file))) {
                count++;
            }
        }
        return count;
    }

    private static boolean writeEntry(ZipOutputStream out, Set<String> written, String path,
                                      byte[] data) throws IOException {
        if (!written.add(path)) {
            return false;
        }
        out.putNextEntry(new ZipEntry(path));
        out.write(data);
        out.closeEntry();
        return true;
    }
}
