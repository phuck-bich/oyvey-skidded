package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class EntityControl extends Module {
    private final Setting<Boolean> lockYaw = bool("Lock Yaw", true);
    private final Setting<Boolean> speed = bool("Speed", false);
    private final Setting<Float> horizontalSpeed = num("Horizontal Speed", 0.5f, 0.05f, 2.5f);
    private final Setting<Boolean> flight = bool("Flight", false);
    private final Setting<Float> verticalSpeed = num("Vertical Speed", 0.3f, 0.05f, 1.0f);

    public EntityControl() {
        super("EntityControl", "Improves control while riding an entity.", Category.MOVEMENT, false, false, false);
        horizontalSpeed.setVisibility(value -> speed.getValue());
        verticalSpeed.setVisibility(value -> flight.getValue());
    }

    public boolean shouldControl(Entity entity) {
        return isOn() && !nullCheck() && mc.player.getVehicle() == entity;
    }

    @Override
    public void onUpdate() {
        if (nullCheck()) return;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null || vehicle.getControllingPassenger() != mc.player) return;

        if (lockYaw.getValue()) vehicle.setYaw(mc.player.getYaw());
        Vec3d velocity = vehicle.getVelocity();
        double x = velocity.x;
        double z = velocity.z;
        if (speed.getValue()) {
            Vec2f movement = mc.player.input.getMovementInput();
            double yaw = Math.toRadians(mc.player.getYaw());
            double forward = movement.y;
            double sideways = movement.x;
            x = -Math.sin(yaw) * forward + Math.cos(yaw) * sideways;
            z = Math.cos(yaw) * forward + Math.sin(yaw) * sideways;
            double length = Math.sqrt(x * x + z * z);
            if (length > 0) {
                x = x / length * horizontalSpeed.getValue();
                z = z / length * horizontalSpeed.getValue();
            }
        }

        double y = velocity.y;
        if (flight.getValue()) {
            y = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);
            y *= verticalSpeed.getValue();
        }
        vehicle.setVelocity(x, y, z);
    }
}
