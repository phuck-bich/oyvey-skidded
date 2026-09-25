package me.alpha432.oyvey.features.modules.player;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.modules.Module;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;

/** Prevents the swing animation packet from being sent. */
public class Swing extends Module {
    public Swing() { super("Swing", "Suppresses outgoing hand swing packets", Category.PLAYER, true, false, false); }
    @Subscribe private void onSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof HandSwingC2SPacket) event.cancel();
    }
}
