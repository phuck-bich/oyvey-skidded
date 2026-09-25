package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.Box;
import java.awt.Color;

/** Outlines nearby chests, barrels, and shulker boxes through walls. */
public class StorageESP extends Module {
    private final Setting<Integer> range = register(num("Range", 64, 8, 128));
    public StorageESP() { super("StorageESP", "Highlights nearby storage blocks", Category.RENDER, true, false, false); }

    @Subscribe private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        int maxDistance = range.getValue() * range.getValue();
        for (BlockEntity entity : mc.world.getBlockEntities()) {
            if (mc.player.squaredDistanceTo(entity.getPos().toCenterPos()) > maxDistance) continue;
            var block = mc.world.getBlockState(entity.getPos()).getBlock();
            Color color;
            if (block == Blocks.ENDER_CHEST) color = Color.MAGENTA;
            else if (block == Blocks.BARREL) color = Color.ORANGE;
            else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.SHULKER_BOX
                    || block == Blocks.WHITE_SHULKER_BOX || block == Blocks.ORANGE_SHULKER_BOX || block == Blocks.MAGENTA_SHULKER_BOX
                    || block == Blocks.LIGHT_BLUE_SHULKER_BOX || block == Blocks.YELLOW_SHULKER_BOX || block == Blocks.LIME_SHULKER_BOX
                    || block == Blocks.PINK_SHULKER_BOX || block == Blocks.GRAY_SHULKER_BOX || block == Blocks.LIGHT_GRAY_SHULKER_BOX
                    || block == Blocks.CYAN_SHULKER_BOX || block == Blocks.PURPLE_SHULKER_BOX || block == Blocks.BLUE_SHULKER_BOX
                    || block == Blocks.BROWN_SHULKER_BOX || block == Blocks.GREEN_SHULKER_BOX || block == Blocks.RED_SHULKER_BOX
                    || block == Blocks.BLACK_SHULKER_BOX) color = Color.YELLOW;
            else continue;
            RenderUtil.drawBox(event.getMatrix(), new Box(entity.getPos()), color, 1.5);
        }
    }
}
