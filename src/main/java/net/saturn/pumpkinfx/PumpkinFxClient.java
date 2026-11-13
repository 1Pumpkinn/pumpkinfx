package net.saturn.pumpkinfx;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PumpkinFxClient implements ClientModInitializer {
    public static final String MOD_ID = "pumpkinfx";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("PumpkinFX loaded");
    }
}
