package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {
    private final Setting<Mode> mode = mode("Mode", Mode.Vanilla);
    private final Setting<Float> speed = num("Speed", 5.6f, 0.1f, 20.0f);
    private final Setting<Boolean> onlyOnGround = bool("Only On Ground", false);
    private final Setting<Boolean> inLiquids = bool("In Liquids", false);
    private final Setting<Boolean> slowFall = bool("Slow Fall", false);
    private final Setting<Float> fallMultiplier = num("Fall Multiplier", 0.85f, 0.45f, 1.0f);

    private int strafeStage;
    private double distance;

    public Speed() {
        super("Speed", "Increases movement speed with vanilla or strafe acceleration.", Category.MOVEMENT, false, false, false);
        fallMultiplier.setVisibility(value -> slowFall.getValue());
    }

    @Override
    public void onEnable() {
        strafeStage = 0;
        distance = 0;
    }

    @Override
    public void onDisable() {
        strafeStage = 0;
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.player.isGliding() || mc.player.isClimbing() || mc.player.getVehicle() != null) return;
        if (mc.player.isSneaking() || (!inLiquids.getValue() && mc.player.isTouchingWater())) return;
        if (onlyOnGround.getValue() && !mc.player.isOnGround()) return;

        Vec2f movement = mc.player.input.getMovementInput();
        if (movement.lengthSquared() == 0) {
            strafeStage = 0;
            return;
        }

        double base = speed.getValue() / 20.0;
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            base *= 1.0 + 0.2 * (mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1);
        }
        double target = base;
        if (mode.getValue() == Mode.Strafe) {
            double dx = mc.player.getX() - mc.player.lastX;
            double dz = mc.player.getZ() - mc.player.lastZ;
            distance = Math.sqrt(dx * dx + dz * dz);
            if (mc.player.isOnGround()) {
                mc.player.jump();
                target = Math.max(base, Math.max(distance * 1.9, 0.38));
                strafeStage = 1;
            } else if (strafeStage == 1) {
                target = Math.max(base, distance * 1.35);
                strafeStage = 2;
            } else {
                target = Math.max(base, distance - distance / 159.0);
            }
        }

        double yaw = Math.toRadians(mc.player.getYaw());
        double forward = movement.y;
        double sideways = movement.x;
        double x = -Math.sin(yaw) * forward + Math.cos(yaw) * sideways;
        double z = Math.cos(yaw) * forward + Math.sin(yaw) * sideways;
        double length = Math.sqrt(x * x + z * z);
        if (length > 0) {
            x = x / length * target;
            z = z / length * target;
        }

        Vec3d velocity = mc.player.getVelocity();
        double y = velocity.y;
        if (slowFall.getValue() && y < 0 && mc.player.fallDistance < 1.25f) {
            y *= fallMultiplier.getValue();
        }
        mc.player.setVelocity(x, y, z);
    }

    public enum Mode {
        Vanilla,
        Strafe
    }
}
