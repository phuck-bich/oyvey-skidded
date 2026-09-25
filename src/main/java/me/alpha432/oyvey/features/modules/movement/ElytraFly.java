package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class ElytraFly extends Module {
    private final Setting<Float> horizontalSpeed = num("Horizontal Speed", 1.6f, 0.1f, 10.0f);
    private final Setting<Float> verticalSpeed = num("Vertical Speed", 1.0f, 0.1f, 5.0f);
    private final Setting<Float> fallMultiplier = num("Fall Multiplier", 0.5f, 0.0f, 1.0f);
    private final Setting<Boolean> autoTakeoff = bool("Auto Takeoff", true);
    private boolean jumpWasPressed;

    public ElytraFly() {
        super("ElytraFly", "Provides configurable movement while gliding with an elytra.", Category.MOVEMENT, false, false, false);
    }

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        boolean jumpPressed = mc.options.jumpKey.isPressed();
        boolean wearingElytra = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        if (autoTakeoff.getValue() && wearingElytra && jumpPressed && !jumpWasPressed
                && !mc.player.isOnGround() && !mc.player.isGliding()) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
        jumpWasPressed = jumpPressed;

        if (!mc.player.isGliding()) return;
        Vec2f input = mc.player.input.getMovementInput();
        Vec3d velocity = horizontalVelocity(input, horizontalSpeed.getValue() / 10.0);
        double vertical = 0;
        if (jumpPressed) vertical = verticalSpeed.getValue() / 10.0;
        else if (mc.options.sneakKey.isPressed()) vertical = -verticalSpeed.getValue() / 10.0;
        else if (mc.player.getVelocity().y < 0) vertical = mc.player.getVelocity().y * fallMultiplier.getValue();
        mc.player.setVelocity(velocity.x, vertical, velocity.z);
    }

    private Vec3d horizontalVelocity(Vec2f input, double speed) {
        if (input.lengthSquared() == 0) return Vec3d.ZERO;
        double yaw = Math.toRadians(mc.player.getYaw());
        double x = -Math.sin(yaw) * input.y + Math.cos(yaw) * input.x;
        double z = Math.cos(yaw) * input.y + Math.sin(yaw) * input.x;
        double length = Math.sqrt(x * x + z * z);
        return new Vec3d(x / length * speed, 0, z / length * speed);
    }
}
