package net.saturn.pumpkinfx.client;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class PendingActions {
    private static final List<Runnable> QUEUE = new ArrayList<>();

    private PendingActions() {
    }

    public static void defer(Runnable action) {
        QUEUE.add(action);
    }

    public static void tick(Minecraft mc) {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Runnable> batch = new ArrayList<>(QUEUE);
        QUEUE.clear();
        batch.forEach(Runnable::run);
    }
}
