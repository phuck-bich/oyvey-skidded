package me.alpha432.oyvey.features.modules.movement;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class Flight extends Module {
    private final Setting<Mode> mode = mode("Mode", Mode.Abilities);
    private final Setting<Float> speed = num("Speed", 0.05f, 0.01f, 0.2f);
    private final Setting<Boolean> noSneak = bool("No Sneak", false);
    private boolean wasFlying;
    private boolean couldFly;
    private float previousFlySpeed;

    public Flight() {
        super("Flight", "Enables controlled flight.", Category.MOVEMENT, false, false, false);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        wasFlying = mc.player.getAbilities().flying;
        couldFly = mc.player.getAbilities().allowFlying;
        previousFlySpeed = mc.player.getAbilities().getFlySpeed();
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        mc.player.getAbilities().flying = wasFlying;
        mc.player.getAbilities().allowFlying = couldFly;
        mc.player.getAbilities().setFlySpeed(previousFlySpeed);
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.player.isSpectator() || mc.player.getVehicle() != null) return;
        if (mode.getValue() == Mode.Abilities) {
            mc.player.getAbilities().allowFlying = true;
            mc.player.getAbilities().flying = true;
            mc.player.getAbilities().setFlySpeed(speed.getValue());
            return;
        }

        mc.player.getAbilities().flying = false;
        Vec3d horizontal = horizontalVelocity(speed.getValue() * 10.0);
        double vertical = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);
        mc.player.setVelocity(horizontal.x, vertical * speed.getValue() * 10.0, horizontal.z);
    }

    @Subscribe
    private void onAbilities(PacketEvent.Receive event) {
        if (!isOn() || mode.getValue() != Mode.Abilities || nullCheck()
                || !(event.getPacket() instanceof PlayerAbilitiesS2CPacket packet)) return;

        var abilities = mc.player.getAbilities();
        abilities.invulnerable = packet.isInvulnerable();
        abilities.creativeMode = packet.isCreativeMode();
        abilities.allowModifyWorld = packet.isCreativeMode();
        abilities.allowFlying = true;
        abilities.flying = true;
        abilities.setFlySpeed(speed.getValue());
        abilities.setWalkSpeed(packet.getWalkSpeed());
        event.cancel();
    }

    private Vec3d horizontalVelocity(double amount) {
        Vec2f input = mc.player.input.getMovementInput();
        if (input.lengthSquared() == 0) return Vec3d.ZERO;
        double yaw = Math.toRadians(mc.player.getYaw());
        double forward = input.y;
        double sideways = input.x;
        double x = -Math.sin(yaw) * forward + Math.cos(yaw) * sideways;
        double z = Math.cos(yaw) * forward + Math.sin(yaw) * sideways;
        double length = Math.sqrt(x * x + z * z);
        return new Vec3d(x / length * amount, 0, z / length * amount);
    }

    public boolean noSneak() {
        return isOn() && mode.getValue() == Mode.Velocity && noSneak.getValue();
    }

    public enum Mode {
        Abilities,
        Velocity
    }
}
