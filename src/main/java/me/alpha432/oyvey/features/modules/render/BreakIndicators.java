package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Shows blocks currently being mined by other players. */
public class BreakIndicators extends Module {
    private final Setting<Integer> lifetime = register(num("Lifetime (seconds)", 8, 1, 30));
    private final Map<BlockPos, Long> breaking = new HashMap<>();

    public BreakIndicators() { super("BreakIndicators", "Highlights blocks other players are mining", Category.RENDER, true, false, false); }

    @Subscribe private void onPacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof BlockBreakingProgressS2CPacket packet) {
            BlockPos pos = packet.getPos().toImmutable();
            if (packet.getProgress() < 0) breaking.remove(pos);
            else breaking.put(pos, System.currentTimeMillis());
        }
    }

    @Subscribe private void onRender(Render3DEvent event) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, Long>> iterator = breaking.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (now - entry.getValue() > lifetime.getValue() * 1000L) iterator.remove();
            else RenderUtil.drawBox(event.getMatrix(), new net.minecraft.util.math.Box(entry.getKey()), Color.ORANGE, 1.5);
        }
    }

    @Override public void onDisable() { breaking.clear(); }
}
