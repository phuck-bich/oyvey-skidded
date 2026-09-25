package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import java.awt.Color;

/** Shows a short velocity-based estimate of where players are moving. */
public class Predictions extends Module {
    private final Setting<Float> seconds = register(num("Prediction Time", 0.5f, 0.1f, 2f));
    public Predictions() { super("Predictions", "Projects nearby player movement briefly", Category.RENDER, true, false, false); }

    @Subscribe private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isRemoved() || mc.player.squaredDistanceTo(player) > 64 * 64) continue;
            var velocity = player.getVelocity().multiply(seconds.getValue());
            Box predicted = player.getBoundingBox().offset(velocity);
            RenderUtil.drawBox(event.getMatrix(), predicted, Color.ORANGE, 1.25);
        }
    }
}
