package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Marks where remote players were when they left the server. */
public class LogoutSpots extends Module {
    private final Map<UUID, Spot> spots = new HashMap<>();
    public LogoutSpots() { super("LogoutSpots", "Marks the last known positions of players who leave", Category.RENDER, true, false, false); }

    @Subscribe private void onPacket(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof PlayerRemoveS2CPacket packet) || mc.world == null) return;
        for (UUID id : packet.profileIds()) {
            var entity = mc.world.getPlayers().stream().filter(player -> player.getUuid().equals(id)).findFirst().orElse(null);
            if (entity != null && entity != mc.player) spots.put(id, new Spot(new Vec3d(entity.getX(), entity.getY(), entity.getZ()), System.currentTimeMillis()));
        }
    }

    @Subscribe private void onRender(Render3DEvent event) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Spot>> iterator = spots.entrySet().iterator();
        while (iterator.hasNext()) {
            Spot spot = iterator.next().getValue();
            if (now - spot.time > 300_000) iterator.remove();
            else RenderUtil.drawBox(event.getMatrix(), new Box(spot.position.x - 0.3, spot.position.y, spot.position.z - 0.3,
                    spot.position.x + 0.3, spot.position.y + 1.8, spot.position.z + 0.3), Color.GRAY, 1.5);
        }
    }

    @Override public void onDisable() { spots.clear(); }
    private record Spot(Vec3d position, long time) { }
}
