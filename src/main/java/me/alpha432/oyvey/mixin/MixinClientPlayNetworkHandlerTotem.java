package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.event.impl.TotemPopEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandlerTotem {
    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (packet.getStatus() != 35) return;

        Entity entity = packet.getEntity(mc.world);
        if (entity instanceof PlayerEntity player) {
            EVENT_BUS.post(new TotemPopEvent(player));
        }
    }

    private static final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
}
