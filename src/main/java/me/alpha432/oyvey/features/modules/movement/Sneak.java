package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

public class Sneak extends Module {
    private final Setting<Mode> mode = mode("Mode", Mode.Vanilla);

    public Sneak() {
        super("Sneak", "Automatically keeps you sneaking.", Category.MOVEMENT, false, false, false);
    }

    public boolean usesVanillaInput() {
        return isActiveForSneaking() && mode.getValue() == Mode.Vanilla;
    }

    public boolean forcesMovementInput() {
        return isActiveForSneaking() && mode.getValue() == Mode.Packet;
    }

    private boolean isActiveForSneaking() {
        return isOn() && !nullCheck() && !mc.player.getAbilities().flying;
    }

    public enum Mode {
        Vanilla,
        Packet
    }
}
