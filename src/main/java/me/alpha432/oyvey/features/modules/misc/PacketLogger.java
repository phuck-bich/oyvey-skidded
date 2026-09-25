package me.alpha432.oyvey.features.modules.misc;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.commands.Command;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.network.packet.Packet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Counts network packet classes and can optionally print a live trace. */
public class PacketLogger extends Module {
    private final Setting<Boolean> liveLog = register(bool("Live Log", false));
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    public PacketLogger() { super("PacketLogger", "Counts and optionally logs network packets", Category.MISC, true, false, false); }
    @Subscribe private void onReceive(PacketEvent.Receive event) { record("IN", event.getPacket()); }
    @Subscribe private void onSend(PacketEvent.Send event) { record("OUT", event.getPacket()); }

    private void record(String direction, Packet<?> packet) {
        if (packet == null) return;
        String name = packet.getClass().getSimpleName();
        counts.merge(direction + " " + name, 1, Integer::sum);
        if (liveLog.getValue()) Command.sendMessage(direction + " " + name);
    }

    @Override public void onDisable() {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        Command.sendMessage("Captured " + total + " packets across " + counts.size() + " packet types.");
        counts.clear();
    }
}
