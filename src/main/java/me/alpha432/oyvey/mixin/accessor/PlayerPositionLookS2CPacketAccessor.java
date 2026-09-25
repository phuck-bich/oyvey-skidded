package me.alpha432.oyvey.mixin.accessor;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(PlayerPositionLookS2CPacket.class)
public interface PlayerPositionLookS2CPacketAccessor {
    @Mutable
    @Accessor("change")
    void oyvey$setChange(EntityPosition change);

    @Mutable
    @Accessor("relatives")
    void oyvey$setRelatives(Set<PositionFlag> relatives);
}
