package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.movement.Jesus;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.block.ShapeContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidBlock.class)
public abstract class MixinFluidBlock {
    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void oyvey$solidifyFluid(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (OyVey.moduleManager == null) return;
        Jesus jesus = OyVey.moduleManager.getModuleByClass(Jesus.class);
        if (jesus != null && jesus.shouldSolidify(state, pos)) cir.setReturnValue(VoxelShapes.fullCube());
    }
}
