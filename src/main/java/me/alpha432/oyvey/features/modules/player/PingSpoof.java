package me.alpha432.oyvey.features.modules.player;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Delays keep-alive replies to make the reported connection latency match the configured value. */
public class PingSpoof extends Module {
    private final Setting<Integer> latency = register(num("Latency (ms)", 150, 0, 1000));
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "OyVey-PingSpoof");
        thread.setDaemon(true);
        return thread;
    });

    public PingSpoof() { super("PingSpoof", "Delays server keep-alive replies", Category.PLAYER, true, false, false); }

    @Subscribe private void onPacket(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof KeepAliveS2CPacket packet) || latency.getValue() <= 0) return;
        event.cancel();
        long id = packet.getId();
        pending.add(id);
        scheduler.schedule(() -> reply(id), latency.getValue(), TimeUnit.MILLISECONDS);
    }

    private void reply(long id) {
        pending.remove(id);
        mc.execute(() -> {
            if (mc.player != null && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new KeepAliveC2SPacket(id));
            }
        });
    }

    @Override public void onDisable() {
        for (long id : pending.toArray(Long[]::new)) reply(id);
    }

    @Override public String getDisplayInfo() { return latency.getValue() + " ms"; }
}
