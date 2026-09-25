package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/** Avoids sprint exhaustion when food is already low. */
public class AntiHunger extends Module {
    private final Setting<Integer> foodThreshold = register(num("Food Threshold", 6, 1, 20));

    public AntiHunger() { super("AntiHunger", "Stops sprinting when food is low to limit exhaustion", Category.PLAYER, false, false, false); }

    @Override public void onUpdate() {
        if (mc.player != null && mc.player.getHungerManager().getFoodLevel() <= foodThreshold.getValue()) {
            mc.player.setSprinting(false);
        }
    }
}
