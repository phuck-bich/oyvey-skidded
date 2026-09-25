package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import net.minecraft.util.hit.BlockHitResult;

/** Continues mining the targeted block as soon as vanilla allows another break update. */
public class InstantRebreak extends Module {
    public InstantRebreak() { super("InstantRebreak", "Immediately resumes mining a block after it breaks", Category.PLAYER, false, false, false); }
    @Override public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || !mc.options.attackKey.isPressed() || !(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (!mc.world.getBlockState(hit.getBlockPos()).isAir()) {
            mc.interactionManager.attackBlock(hit.getBlockPos(), hit.getSide());
            mc.interactionManager.updateBlockBreakingProgress(hit.getBlockPos(), hit.getSide());
        }
    }
}
