package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class Sneak extends Module {
    public enum Mode {
        Packet,
        Vanilla
    }

    private final Setting<Mode> mode = mode("Mode", Mode.Vanilla);

    private boolean packetSneaking;
    private boolean previousSneakKeyState;

    public Sneak() {
        super("Sneak", "Sneaks for you.", Category.MOVEMENT, false, false, false);
    }

    public boolean doPacket() {
        return isEnabled() && mc.player != null
            && !mc.player.getAbilities().flying
            && mode.getValue() == Mode.Packet;
    }

    public boolean doVanilla() {
        return isEnabled() && mc.player != null
            && !mc.player.getAbilities().flying
            && mode.getValue() == Mode.Vanilla;
    }

    @Override
    public void onEnable() {
        packetSneaking = false;

        if (mc.options != null) {
            previousSneakKeyState = mc.options.sneakKey.isPressed();
        }

        applySneakState();
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(previousSneakKeyState);
        }

        stopPacketSneak();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null) return;

        applySneakState();
    }

    private void applySneakState() {
        if (mc.player == null) return;

        if (mc.player.getAbilities().flying) {
            mc.options.sneakKey.setPressed(previousSneakKeyState);
            stopPacketSneak();
            return;
        }

        if (doVanilla()) {
            stopPacketSneak();
            mc.options.sneakKey.setPressed(true);
        } else if (doPacket()) {
            mc.options.sneakKey.setPressed(previousSneakKeyState);
            startPacketSneak();
        }
    }

    private void startPacketSneak() {
        if (packetSneaking || mc.player == null) return;

        mc.player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY)
        );
        packetSneaking = true;
    }

    private void stopPacketSneak() {
        if (!packetSneaking || mc.player == null) return;

        mc.player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY)
        );
        packetSneaking = false;
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue().name();
    }
}
