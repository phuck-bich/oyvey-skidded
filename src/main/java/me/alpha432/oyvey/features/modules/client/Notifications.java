package me.alpha432.oyvey.features.modules.client;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.TotemPopEvent;
import me.alpha432.oyvey.features.commands.Command;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Notifications extends Module {
    private final Setting<Boolean> totemPops = bool("TotemPops", true);
    private final Setting<Boolean> moduleToggles = bool("ModuleToggles", true);
    private final Setting<Boolean> pingSpike = bool("LatencySpike", true);
    private final Setting<Integer> spikeThreshold = num("LatencyThreshold", 100, 1, 500);

    private final Map<UUID, Integer> pops = new HashMap<>();
    private final Set<UUID> knownPlayers = new HashSet<>();
    private final Set<UUID> knownDeadPlayers = new HashSet<>();
    private long lastSpikeAt;
    private int previousPing = -1;

    public Notifications() {
        super("Notifications", "Totem, death, module and latency notifications inspired by Mint.", Category.CLIENT, true, false, false);
    }

    @Override
    public void onEnable() {
        previousPing = OyVey.serverManager.getPing();
        knownPlayers.clear();
        knownDeadPlayers.clear();
    }

    @Override
    public void onDisable() {
        pops.clear();
        knownPlayers.clear();
        knownDeadPlayers.clear();
    }

    @Override
    public void onToggle() {
        if (!moduleToggles.getValue() || mc.player == null) return;
        if (isEnabled()) {
            Command.sendMessage("Notifications enabled.");
        }
    }

    @Subscribe
    public void onTotemPop(TotemPopEvent event) {
        if (mc.player == null || !totemPops.getValue()) return;

        PlayerEntity player = event.getPlayer();
        int count = pops.merge(player.getUuid(), 1, Integer::sum);
        String name = player == mc.player ? "You" : player.getName().getString();
        String totems = count == 1 ? "totem" : "totems";

        sendNotification(name + " has popped " + count + " " + totems + ".");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (pingSpike.getValue()) {
            int ping = OyVey.serverManager.getPing();
            if (previousPing >= 0 && ping - previousPing >= spikeThreshold.getValue()
                    && System.currentTimeMillis() - lastSpikeAt > 1000L) {
                sendNotification("Latency spike: " + previousPing + "ms -> " + ping + "ms.");
                lastSpikeAt = System.currentTimeMillis();
            }
            previousPing = ping;
        }

        if (totemPops.getValue()) {
            Set<UUID> current = new HashSet<>();
            for (PlayerEntity player : mc.world.getPlayers()) {
                current.add(player.getUuid());
                knownPlayers.add(player.getUuid());

                if (player.isDead() && knownDeadPlayers.add(player.getUuid())) {
                    int count = pops.getOrDefault(player.getUuid(), 0);
                    String name = player == mc.player ? "You" : player.getName().getString();
                    if (count > 0) {
                        sendNotification(name + " died after popping " + count + (count == 1 ? " totem." : " totems."));
                    }
                }

                if (!player.isDead()) {
                    knownDeadPlayers.remove(player.getUuid());
                }
            }

            knownPlayers.retainAll(current);
        }
    }

    private void sendNotification(String message) {
        mc.player.sendMessage(
                Text.literal("[Notifications] " + message),
                false
        );
    }

    public int getPops(PlayerEntity player) {
        return pops.getOrDefault(player.getUuid(), 0);
    }

    @Override
    public String getDisplayInfo() {
        return pops.isEmpty() ? null : Integer.toString(pops.size());
    }
}
