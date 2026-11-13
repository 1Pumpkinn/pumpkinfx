package net.saturn.pumpkinfx.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.saturn.pumpkinfx.PumpkinFxClient;
import net.saturn.pumpkinfx.client.editor.EditorScreen;
import org.lwjgl.glfw.GLFW;

public final class PfxKeys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(PumpkinFxClient.MOD_ID, "main"));

    private static KeyMapping replayKey;
    private static KeyMapping openKey;

    private PfxKeys() {
    }

    public static void register() {
        replayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pumpkinfx.replay",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pumpkinfx.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (replayKey.consumeClick()) {
                if (client.player == null) {
                    continue;
                }
                if (EffectPreview.last() == null) {
                    client.player.sendSystemMessage(Component.literal("PumpkinFX: nothing to replay yet, use /pfx play <id>"));
                } else {
                    EffectPreview.replay();
                }
            }
            while (openKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    EditorScreen.openLast();
                }
            }
        });
    }
}
