package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.render.Chams;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void oyvey$setChamsOutline(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (OyVey.moduleManager == null) return;
        Chams chams = OyVey.moduleManager.getModuleByClass(Chams.class);
        if (chams != null && chams.outlines(entity)) state.outlineColor = chams.color();
    }
}
