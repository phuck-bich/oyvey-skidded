package me.alpha432.oyvey.features.modules.movement;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.Vec2f;

public class Sprint extends Module {
    private final Setting<Mode> mode = mode("Mode", Mode.Strict);
    private final Setting<Boolean> keepSprint = bool("Keep Sprint", false);
    private final Setting<Boolean> unsprintOnHit = bool("Unsprint On Hit", false);
    private final Setting<Boolean> unsprintInWater = bool("Unsprint In Water", true);
    private final Setting<Boolean> sprintWhileStationary = bool("Sprint While Stationary", false);

    public Sprint() {
        super("Sprint", "Automatically sprints while moving.", Category.MOVEMENT, true, false, false);
        unsprintInWater.setVisibility(value -> mode.getValue() == Mode.Rage);
        sprintWhileStationary.setVisibility(value -> mode.getValue() == Mode.Rage);
    }

    @Override
    public void onUpdate() {
        if (nullCheck()) return;
        if (mode.getValue() == Mode.Rage && unsprintInWater.getValue() && mc.player.isTouchingWater()) return;

        mc.player.setSprinting(shouldSprint());
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        if (!unsprintOnHit.getValue() || nullCheck()) return;
        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet)
                || packet.type.getType() != PlayerInteractEntityC2SPacket.InteractType.ATTACK
                || !mc.player.isSprinting()) return;

        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        mc.player.setSprinting(false);
    }

    @Subscribe
    private void onPacketSent(PacketEvent.Sent event) {
        if (!unsprintOnHit.getValue() || !keepSprint.getValue() || nullCheck()) return;
        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet)
                || packet.type.getType() != PlayerInteractEntityC2SPacket.InteractType.ATTACK
                || !shouldSprint()) return;

        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        mc.player.setSprinting(true);
    }

    public boolean shouldSprint() {
        if (nullCheck() || mc.currentScreen != null) return false;

        Vec2f movement = mc.player.input.getMovementInput();
        float amount = mode.getValue() == Mode.Rage
                ? Math.abs(movement.x) + Math.abs(movement.y)
                : movement.y;

        if (amount <= (mc.player.isSubmergedInWater() ? 1.0E-5F : 0.8F)) {
            if (mode.getValue() == Mode.Strict || !sprintWhileStationary.getValue()) return false;
        }

        if (mode.getValue() == Mode.Rage) return true;

        return !mc.player.isTouchingWater()
                && !mc.player.isSubmergedInWater()
                && !mc.player.isUsingItem()
                && mc.player.getHungerManager().getFoodLevel() > 6
                && !mc.player.horizontalCollision;
    }

    public boolean isRageMode() {
        return isOn() && mode.getValue() == Mode.Rage;
    }

    public boolean shouldUnsprintInWater() {
        return isOn() && mode.getValue() == Mode.Rage && unsprintInWater.getValue();
    }

    public boolean shouldStopSprintingAfterAttack() {
        return !isOn() || !keepSprint.getValue();
    }

    public enum Mode {
        Strict,
        Rage
    }
}
