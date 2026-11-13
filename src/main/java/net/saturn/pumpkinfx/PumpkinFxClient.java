package net.saturn.pumpkinfx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.saturn.pumpkinfx.client.EffectPreview;
import net.saturn.pumpkinfx.client.PfxCommands;
import net.saturn.pumpkinfx.client.PendingActions;
import net.saturn.pumpkinfx.client.PfxKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PumpkinFxClient implements ClientModInitializer {
    public static final String MOD_ID = "pumpkinfx";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        PfxKeys.register();
        PfxCommands.register();
        ClientTickEvents.END_CLIENT_TICK.register(EffectPreview::tick);
        ClientTickEvents.END_CLIENT_TICK.register(PendingActions::tick);
        LOGGER.info("PumpkinFX loaded");
    }
}
