package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.entity.ItemEntity;
import java.awt.Color;

/** Highlights dropped items so they are easier to find. */
public class ItemHighlight extends Module {
    private final Setting<Integer> range = register(num("Range", 64, 8, 128));
    public ItemHighlight() { super("ItemHighlight", "Outlines dropped items nearby", Category.RENDER, true, false, false); }

    @Subscribe private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        int maxDistance = range.getValue() * range.getValue();
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item) || item.isRemoved() || mc.player.squaredDistanceTo(item) > maxDistance) continue;
            RenderUtil.drawBox(event.getMatrix(), item.getBoundingBox(), Color.WHITE, 1.25);
        }
    }
}
