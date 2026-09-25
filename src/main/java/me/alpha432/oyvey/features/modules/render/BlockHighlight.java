package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

import java.awt.*;

public class BlockHighlight extends Module {
    private final Setting<Integer> red = register(num("Red", 255, 0, 255));
    private final Setting<Integer> green = register(num("Green", 70, 0, 255));
    private final Setting<Integer> blue = register(num("Blue", 70, 0, 255));
    private final Setting<Integer> fillAlpha = register(num("Fill Alpha", 35, 0, 180));
    private final Setting<Float> width = register(num("Line Width", 1f, 0.5f, 4f));
    private final Setting<Boolean> filled = register(bool("Filled", true));

    public BlockHighlight() {
        super("BlockHighlight", "Draws box at the block that you are looking at", Category.RENDER, true, false, false);
    }

    @Subscribe public void onRender3D(Render3DEvent event) {
        if (mc.crosshairTarget instanceof BlockHitResult result) {
            VoxelShape shape = mc.world.getBlockState(result.getBlockPos()).getOutlineShape(mc.world, result.getBlockPos());
            if (shape.isEmpty()) return;
            Box box = shape.getBoundingBox();
            box = box.offset(result.getBlockPos());
            Color color = new Color(red.getValue(), green.getValue(), blue.getValue());
            if (filled.getValue() && fillAlpha.getValue() > 0) {
                RenderUtil.drawBoxFilled(event.getMatrix(), box, new Color(red.getValue(), green.getValue(), blue.getValue(), fillAlpha.getValue()));
            }
            RenderUtil.drawBox(event.getMatrix(), box, color, width.getValue());
        }
    }
}
