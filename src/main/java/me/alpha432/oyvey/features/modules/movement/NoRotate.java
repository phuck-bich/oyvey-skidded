package me.alpha432.oyvey.features.modules.movement;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.mixin.accessor.PlayerPositionLookS2CPacketAccessor;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;

import java.util.EnumSet;

public class NoRotate extends Module {
    public NoRotate() {
        super("NoRotate", "Keeps server corrections from changing your view angle.", Category.MOVEMENT, true, false, false);
    }

    @Subscribe
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck() || !(event.getPacket() instanceof PlayerPositionLookS2CPacket packet)) return;

        EntityPosition change = packet.change().withRotation(mc.player.getYaw(), mc.player.getPitch());
        EnumSet<PositionFlag> relatives = packet.relatives().isEmpty()
                ? EnumSet.noneOf(PositionFlag.class)
                : EnumSet.copyOf(packet.relatives());
        relatives.remove(PositionFlag.Y_ROT);
        relatives.remove(PositionFlag.X_ROT);

        PlayerPositionLookS2CPacketAccessor accessor = (PlayerPositionLookS2CPacketAccessor) (Object) packet;
        accessor.oyvey$setChange(change);
        accessor.oyvey$setRelatives(relatives);
    }
}
