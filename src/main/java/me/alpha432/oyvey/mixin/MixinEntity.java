package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.movement.EntityControl;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Inject(method = "getControllingPassenger", at = @At("RETURN"), cancellable = true)
    private void oyvey$allowRiderControl(CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() != null || OyVey.moduleManager == null) return;
        Entity entity = (Entity) (Object) this;
        EntityControl control = OyVey.moduleManager.getModuleByClass(EntityControl.class);
        if (control != null && control.shouldControl(entity)) cir.setReturnValue(mcPlayer());
    }

    private static Entity mcPlayer() {
        return net.minecraft.client.MinecraftClient.getInstance().player;
    }
}
