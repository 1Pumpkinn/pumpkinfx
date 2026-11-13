package net.saturn.pumpkinfx.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.saturn.pumpkinfx.model.EffectDefinition;
import net.saturn.pumpkinfx.model.EffectIds;
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
