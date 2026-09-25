package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/** Locks the local view to the yaw and pitch captured when enabled. */
public class Rotation extends Module {
    private final Setting<Boolean> lockYaw = register(bool("Lock yaw", true));
    private final Setting<Boolean> lockPitch = register(bool("Lock pitch", true));
    private float yaw, pitch;
    public Rotation() { super("Rotation", "Keeps your view at the angles captured on activation", Category.PLAYER, false, false, false); }
    @Override public void onEnable() { if (mc.player != null) { yaw = mc.player.getYaw(); pitch = mc.player.getPitch(); } }
    @Override public void onUpdate() {
        if (nullCheck()) return;
        if (lockYaw.getValue()) { mc.player.setYaw(yaw); mc.player.headYaw = yaw; }
        if (lockPitch.getValue()) mc.player.setPitch(pitch);
    }
}
