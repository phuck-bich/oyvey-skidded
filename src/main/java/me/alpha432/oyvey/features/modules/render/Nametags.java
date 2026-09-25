package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Draws player name, health, and distance above nearby players. */
public class Nametags extends Module {
    private final Setting<Boolean> showHealth = register(bool("Show Health", true));
    private final Setting<Boolean> showDistance = register(bool("Show Distance", true));
    private final Setting<Integer> range = register(num("Range", 64, 8, 128));

    public Nametags() { super("Nametags", "Shows player names with health and distance", Category.RENDER, true, false, false); }

    @Subscribe private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        int maxDistance = range.getValue() * range.getValue();
        VertexConsumerProvider.Immediate buffers = mc.getBufferBuilders().getEntityVertexConsumers();
        TextRenderer renderer = mc.textRenderer;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isRemoved() || mc.player.squaredDistanceTo(player) > maxDistance) continue;
            String label = player.getName().getString();
            if (showHealth.getValue()) label += String.format("  %.0f❤", player.getHealth() + player.getAbsorptionAmount());
            if (showDistance.getValue()) label += String.format("  %.0fm", Math.sqrt(mc.player.squaredDistanceTo(player)));
            Text text = Text.literal(label);
            float x = -renderer.getWidth(text) / 2f;
            event.getMatrix().push();
            event.getMatrix().translate(player.getX() - camera.x, player.getY() + player.getHeight() + 0.35 - camera.y, player.getZ() - camera.z);
            event.getMatrix().scale(-0.025f, -0.025f, 0.025f);
            renderer.draw(text, x, 0, 0xFFFFFFFF, true, event.getMatrix().peek().getPositionMatrix(), buffers,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0x80000000, 15728880);
            event.getMatrix().pop();
        }
        buffers.draw();
    }
}
