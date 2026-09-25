package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.event.Stage;
import me.alpha432.oyvey.event.impl.UpdateEvent;
import me.alpha432.oyvey.event.impl.UpdateWalkingPlayerEvent;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.movement.Sneak;
import me.alpha432.oyvey.features.modules.misc.InventoryTweaks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void protectValuableDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (InventoryTweaks.shouldBlockDrop(player.getMainHandStack())) cir.setReturnValue(false);
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void forceSneakInMovementPackets(CallbackInfo ci) {
        if (OyVey.moduleManager == null) return;
        Sneak sneak = OyVey.moduleManager.getModuleByClass(Sneak.class);
        if (sneak == null || !sneak.forcesMovementInput()) return;

        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        PlayerInput input = player.input.playerInput;
        player.input.playerInput = new PlayerInput(
                input.forward(), input.backward(), input.left(), input.right(),
                input.jump(), true, input.sprint()
        );
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tickHook(CallbackInfo ci) {
        EVENT_BUS.post(new UpdateEvent());
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V", shift = At.Shift.AFTER))
    private void tickHook2(CallbackInfo ci) {
        EVENT_BUS.post(new UpdateWalkingPlayerEvent(Stage.PRE));
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendMovementPackets()V", shift = At.Shift.AFTER))
    private void tickHook3(CallbackInfo ci) {
        EVENT_BUS.post(new UpdateWalkingPlayerEvent(Stage.POST));
    }
}
