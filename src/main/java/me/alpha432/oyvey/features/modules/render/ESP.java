package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.entity.player.PlayerEntity;
import java.awt.Color;

/** Outlines other players in the world. */
public class ESP extends Module {
    private final Setting<Integer> range = register(num("Range", 96, 8, 256));

    public ESP() { super("ESP", "Draws outlines around nearby players", Category.RENDER, true, false, false); }

    @Subscribe private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isRemoved() || mc.player.squaredDistanceTo(player) > range.getValue() * range.getValue()) continue;
            RenderUtil.drawBox(event.getMatrix(), player.getBoundingBox(), Color.CYAN, 1.5);
        }
    }
}
