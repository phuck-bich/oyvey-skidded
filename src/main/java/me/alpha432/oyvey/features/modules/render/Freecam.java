package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.util.math.Vec3d;

/** Detaches the view camera from the player without moving the player on the server. */
public class Freecam extends Module {
    private final Setting<Float> speed = register(num("Speed", 0.5f, 0.1f, 2f));
    private double x, y, z;
    private float yaw, pitch;

    public Freecam() { super("Freecam", "Moves the view camera independently of the player", Category.RENDER, false, false, false); }

    @Override public void onEnable() {
        if (mc.player == null) return;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        x = camera.x; y = camera.y; z = camera.z;
        yaw = mc.gameRenderer.getCamera().getYaw();
        pitch = mc.gameRenderer.getCamera().getPitch();
    }

    @Override public void onUpdate() {
        if (mc.player == null) return;
        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double sideways = (mc.options.rightKey.isPressed() ? 1 : 0) - (mc.options.leftKey.isPressed() ? 1 : 0);
        double vertical = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);
        double length = Math.sqrt(forward * forward + sideways * sideways);
        if (length > 0) { forward /= length; sideways /= length; }
        double radians = Math.toRadians(yaw);
        x += (-Math.sin(radians) * forward + Math.cos(radians) * sideways) * speed.getValue();
        z += (Math.cos(radians) * forward + Math.sin(radians) * sideways) * speed.getValue();
        y += vertical * speed.getValue();
    }

    public void setView(double x, double y, double z, float yaw, float pitch) {
        if (!isOn()) return;
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = Math.max(-90, Math.min(90, pitch));
    }

    public void rotate(double deltaX, double deltaY) {
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double scale = Math.pow(sensitivity * 0.6 + 0.2, 3) * 8.0 * 0.15;
        yaw += (float) (deltaX * scale);
        pitch = (float) Math.max(-90, Math.min(90, pitch + deltaY * scale));
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
}
