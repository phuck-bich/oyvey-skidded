package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.awt.Color;

/** Highlights nearby one-block holes with safe/unsafe colors. */
public class HoleESP extends Module {
    private final Setting<Integer> range = register(num("Range", 6, 2, 12));

    public HoleESP() { super("HoleESP", "Highlights nearby bedrock and obsidian holes", Category.RENDER, true, false, false); }

    @Subscribe private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue();
        for (int x = -r; x <= r; x++) for (int y = -2; y <= 2; y++) for (int z = -r; z <= r; z++) {
            BlockPos pos = origin.add(x, y, z);
            if (!mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.up()).isAir()) continue;
            boolean safe = true;
            boolean valid = true;
            for (Direction direction : Direction.Type.HORIZONTAL) {
                var block = mc.world.getBlockState(pos.offset(direction)).getBlock();
                if (block == Blocks.OBSIDIAN) safe = false;
                else if (block != Blocks.BEDROCK) valid = false;
            }
            if (valid) RenderUtil.drawBox(event.getMatrix(), new net.minecraft.util.math.Box(pos), safe ? Color.GREEN : Color.ORANGE, 1.5);
        }
    }
}
