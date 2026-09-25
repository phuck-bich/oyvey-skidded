package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;

public class Jesus extends Module {
    private final Setting<Boolean> water = bool("Water", true);
    private final Setting<Boolean> lava = bool("Lava", false);

    public Jesus() {
        super("Jesus", "Keeps you moving across liquid surfaces.", Category.MOVEMENT, false, false, false);
    }

    public boolean shouldSolidify(BlockState state, BlockPos pos) {
        if (!isOn() || nullCheck() || mc.player.isSneaking() || mc.player.isGliding()) return false;
        if (pos.getY() > Math.floor(mc.player.getY()) - 1) return false;

        var fluid = state.getFluidState().getFluid();
        if (fluid == Fluids.WATER) return water.getValue() && !mc.player.isTouchingWater();
        if (fluid == Fluids.LAVA) return lava.getValue() && !mc.player.isTouchingWater();
        return false;
    }
}
