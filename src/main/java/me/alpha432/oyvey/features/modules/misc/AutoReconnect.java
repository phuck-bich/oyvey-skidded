package me.alpha432.oyvey.features.modules.misc;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Reconnects to the last multiplayer server after a disconnect. */
public class AutoReconnect extends Module {
    private final Setting<Integer> delay = register(num("Delay (seconds)", 5, 1, 60));
    private final Setting<Integer> maxRetries = register(num("Maximum Retries", 5, 1, 20));
    private int retries;

    public AutoReconnect() { super("AutoReconnect", "Reconnects to the last server after disconnect", Category.MISC, false, false, false); }

    public void onDisconnected() {
        if (!isOn() || mc.getCurrentServerEntry() == null || retries >= maxRetries.getValue()) return;
        ServerInfo server = mc.getCurrentServerEntry();
        retries++;
        CompletableFuture.delayedExecutor(delay.getValue(), TimeUnit.SECONDS).execute(() -> mc.execute(() -> {
            if (!isOn() || !(mc.currentScreen instanceof DisconnectedScreen)) return;
            ConnectScreen.connect(mc.currentScreen, mc, ServerAddress.parse(server.address), server, false, null);
        }));
    }

    @Override public void onUpdate() {
        if (mc.player != null && mc.world != null) retries = 0;
    }

    @Override public void onDisable() { retries = 0; }
}
