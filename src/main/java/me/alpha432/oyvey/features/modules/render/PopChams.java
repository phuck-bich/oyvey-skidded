package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.DeathEvent;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Leaves a brief outline at the location of a player death. */
public class PopChams extends Module {
    private final Setting<Integer> duration = register(num("Duration (seconds)", 3, 1, 10));
    private final Map<UUID, Ghost> ghosts = new HashMap<>();
    private final Set<UUID> seen = new HashSet<>();
    public PopChams() { super("PopChams", "Shows a fading outline when a player dies", Category.RENDER, true, false, false); }

    @Subscribe private void onDeath(DeathEvent event) {
        if (event.getEntity() instanceof PlayerEntity player && player != mc.player && seen.add(player.getUuid())) {
            ghosts.put(player.getUuid(), new Ghost(player.getBoundingBox(), System.currentTimeMillis()));
        }
    }

    @Override public void onUpdate() {
        if (mc.world != null) mc.world.getPlayers().stream().filter(player -> player.getHealth() > 0).forEach(player -> seen.remove(player.getUuid()));
    }

    @Subscribe private void onRender(Render3DEvent event) {
        long now = System.currentTimeMillis();
        Iterator<Ghost> iterator = ghosts.values().iterator();
        while (iterator.hasNext()) {
            Ghost ghost = iterator.next();
            long age = now - ghost.created;
            if (age > duration.getValue() * 1000L) iterator.remove();
            else {
                int alpha = Math.max(20, 255 - (int) (age * 235 / (duration.getValue() * 1000L)));
                RenderUtil.drawBoxFilled(event.getMatrix(), ghost.box, new Color(255, 50, 80, alpha));
                RenderUtil.drawBox(event.getMatrix(), ghost.box, new Color(255, 50, 80, alpha), 1.5);
            }
        }
    }

    @Override public void onDisable() { ghosts.clear(); seen.clear(); }
    private record Ghost(Box box, long created) { }
}
