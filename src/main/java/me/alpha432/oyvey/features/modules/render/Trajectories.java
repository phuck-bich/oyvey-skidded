package me.alpha432.oyvey.features.modules.render;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.awt.Color;

/** Draws a short estimated path in front of airborne projectiles. */
public class Trajectories extends Module {
    private final Setting<Integer> steps = register(num("Path Steps", 24, 4, 60));
    public Trajectories() { super("Trajectories", "Displays estimated projectile paths", Category.RENDER, true, false, false); }

    @Subscribe private void onRender(Render3DEvent event) {
        if (mc.world == null) return;
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof ProjectileEntity projectile)) continue;
            Vec3d pos = new Vec3d(projectile.getX(), projectile.getY(), projectile.getZ());
            Vec3d velocity = projectile.getVelocity();
            for (int i = 0; i < steps.getValue(); i++) {
                pos = pos.add(velocity);
                RenderUtil.drawBox(event.getMatrix(), new Box(pos.x - 0.035, pos.y - 0.035, pos.z - 0.035,
                        pos.x + 0.035, pos.y + 0.035, pos.z + 0.035), Color.CYAN, 1.0);
                velocity = velocity.multiply(0.99).add(0, -0.03, 0);
                if (!mc.world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(pos)).isAir()) break;
            }
        }
    }
}
