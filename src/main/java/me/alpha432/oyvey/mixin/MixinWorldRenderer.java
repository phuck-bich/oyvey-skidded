package me.alpha432.oyvey.mixin;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.render.NoRender;
import net.minecraft.client.render.*;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;
import static me.alpha432.oyvey.util.traits.Util.mc;

@Mixin( WorldRenderer.class )
public class MixinWorldRenderer {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void oyvey$hideWeather(FrameGraphBuilder builder, com.mojang.blaze3d.buffers.GpuBufferSlice fog, CallbackInfo ci) {
        if (OyVey.moduleManager == null) return;
        NoRender module = OyVey.moduleManager.getModuleByClass(NoRender.class);
        if (module != null && module.hidesWeather()) ci.cancel();
    }

    @Inject(method = "renderMain", at = @At("RETURN"))
    private void renderMain(FrameGraphBuilder frameGraphBuilder, Frustum frustum, Matrix4f positionMatrix,
                            com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, boolean renderBlockOutline,
                            WorldRenderState renderState, RenderTickCounter tickCounter,
                            net.minecraft.util.profiler.Profiler profiler, CallbackInfo ci) {
        MatrixStack stack = new MatrixStack();
        stack.push();
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(mc.gameRenderer.getCamera().getYaw() + 180f));

        profiler.push("oyvey-render-3d");

        Render3DEvent event = new Render3DEvent(stack, tickCounter.getTickProgress(true));
        EVENT_BUS.post(event);
        stack.pop();
        profiler.pop();
    }
}
