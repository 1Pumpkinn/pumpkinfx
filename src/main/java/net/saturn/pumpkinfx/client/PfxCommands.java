package net.saturn.pumpkinfx.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.saturn.pumpkinfx.client.editor.EditorScreen;
import net.saturn.pumpkinfx.export.ExportService;
import net.saturn.pumpkinfx.export.PackExporter;
import net.saturn.pumpkinfx.graph.GraphPresets;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EffectIds;
import net.saturn.pumpkinfx.model.EffectValidator;
import net.saturn.pumpkinfx.storage.EffectStore;

import java.io.IOException;
import java.util.List;

public final class PfxCommands {
    private PfxCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("pfx")
                        .then(ClientCommands.literal("list").executes(PfxCommands::list))
                        .then(ClientCommands.literal("stop").executes(PfxCommands::stop))
                        .then(ClientCommands.literal("edit")
                                .executes(PfxCommands::editLast)
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            EffectStore.list().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(PfxCommands::edit)))
                        .then(ClientCommands.literal("export")
                                .executes(ctx -> export(ctx, ExportService.lastName()))
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> export(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(ClientCommands.literal("validate")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            EffectStore.list().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(PfxCommands::validate)))
                        .then(ClientCommands.literal("demo")
                                .executes(PfxCommands::demo)
                                .then(ClientCommands.literal("shader").executes(PfxCommands::demoShader)))
                        .then(ClientCommands.literal("new")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(PfxCommands::create)))
                        .then(ClientCommands.literal("play")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            EffectStore.list().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(PfxCommands::play)))));
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        List<String> ids = EffectStore.list();
        if (ids.isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal("No effects yet. Create one with /pfx new <id>"));
        } else {
            ctx.getSource().sendFeedback(Component.literal("Effects: " + String.join(", ", ids)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int editLast(CommandContext<FabricClientCommandSource> ctx) {
        PendingActions.defer(EditorScreen::openLast);
        return Command.SINGLE_SUCCESS;
    }

    private static int edit(CommandContext<FabricClientCommandSource> ctx) {
        String id = EffectIds.sanitize(StringArgumentType.getString(ctx, "id"));
        if (!EffectStore.exists(id)) {
            ctx.getSource().sendError(Component.literal("No effect named '" + id + "'"));
            return 0;
        }
        EffectDefinition loaded;
        try {
            loaded = EffectStore.load(id);
        } catch (Exception e) {
            ctx.getSource().sendError(Component.literal("Could not load '" + id + "': " + e.getMessage()));
            return 0;
        }
        PendingActions.defer(() -> EditorScreen.open(loaded));
        return Command.SINGLE_SUCCESS;
    }

    private static int export(CommandContext<FabricClientCommandSource> ctx, String name) {
        try {
            PackExporter.Result result = ExportService.exportAll(name);
            ctx.getSource().sendFeedback(Component.literal("Exported " + result.effectCount() + " effects, "
                    + result.textureCount() + " textures, " + result.overlayCount() + " pack files"
                    + (result.shaderExported() ? ", patched particle shader" : "")));
            ctx.getSource().sendFeedback(Component.literal("Resource pack: " + result.zip().toAbsolutePath()));
            ctx.getSource().sendFeedback(Component.literal("Plugin effects: " + result.effectsDir().toAbsolutePath()));
            for (String warning : result.warnings()) {
                ctx.getSource().sendFeedback(Component.literal("Warning: " + warning));
            }
        } catch (IOException e) {
            ctx.getSource().sendError(Component.literal("Export failed: " + e.getMessage()));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int validate(CommandContext<FabricClientCommandSource> ctx) {
        String id = EffectIds.sanitize(StringArgumentType.getString(ctx, "id"));
        if (!EffectStore.exists(id)) {
            ctx.getSource().sendError(Component.literal("No effect named '" + id + "'"));
            return 0;
        }
        EffectDefinition effect;
        try {
            effect = EffectStore.load(id);
        } catch (Exception e) {
            ctx.getSource().sendError(Component.literal("Could not load '" + id + "': " + e.getMessage()));
            return 0;
        }
        List<EffectValidator.Issue> issues = EffectValidator.validate(effect, ParticleResolver::isKnown);
        if (issues.isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal("'" + id + "' looks good, no problems found"));
        }
        for (EffectValidator.Issue issue : issues) {
            ctx.getSource().sendFeedback(Component.literal(issue.severity() + " " + issue));
        }
        return EffectValidator.hasErrors(issues) ? 0 : Command.SINGLE_SUCCESS;
    }

    private static int demo(CommandContext<FabricClientCommandSource> ctx) {
        EffectDefinition effect = GraphPresets.helix("demo_helix");
        try {
            EffectStore.save(effect);
        } catch (IOException e) {
            ctx.getSource().sendError(Component.literal("Could not save: " + e.getMessage()));
            return 0;
        }
        EffectPreview.play(effect);
        ctx.getSource().sendFeedback(Component.literal("Saved and playing 'demo_helix', a graph-driven helix. Stop it with /pfx stop"));
        return Command.SINGLE_SUCCESS;
    }

    private static int demoShader(CommandContext<FabricClientCommandSource> ctx) {
        EffectDefinition effect = GraphPresets.shaderTint("demo_shader");
        try {
            EffectStore.save(effect);
        } catch (IOException e) {
            ctx.getSource().sendError(Component.literal("Could not save: " + e.getMessage()));
            return 0;
        }
        EffectPreview.play(effect);
        ctx.getSource().sendFeedback(Component.literal("Saved and playing 'demo_shader'. Its shader only shows after /pfx export and loading the pack"));
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(CommandContext<FabricClientCommandSource> ctx) {
        EffectPreview.stop();
        ctx.getSource().sendFeedback(Component.literal("Stopped all previews"));
        return Command.SINGLE_SUCCESS;
    }

    private static int create(CommandContext<FabricClientCommandSource> ctx) {
        String id = EffectIds.sanitize(StringArgumentType.getString(ctx, "id"));
        if (EffectStore.exists(id)) {
            ctx.getSource().sendError(Component.literal("Effect '" + id + "' already exists"));
            return 0;
        }
        try {
            EffectStore.save(EffectDefinition.createDefault(id));
        } catch (IOException e) {
            ctx.getSource().sendError(Component.literal("Could not save: " + e.getMessage()));
            return 0;
        }
        ctx.getSource().sendFeedback(Component.literal("Created '" + id + "'. Play it with /pfx play " + id));
        return Command.SINGLE_SUCCESS;
    }

    private static int play(CommandContext<FabricClientCommandSource> ctx) {
        String id = EffectIds.sanitize(StringArgumentType.getString(ctx, "id"));
        if (!EffectStore.exists(id)) {
            ctx.getSource().sendError(Component.literal("No effect named '" + id + "'"));
            return 0;
        }
        try {
            EffectPreview.play(EffectStore.load(id));
        } catch (Exception e) {
            ctx.getSource().sendError(Component.literal("Could not load '" + id + "': " + e.getMessage()));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }
}
