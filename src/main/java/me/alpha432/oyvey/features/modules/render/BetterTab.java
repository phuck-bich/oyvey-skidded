package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

public class BetterTab extends Module {
    private final Setting<Boolean> showHealth = register(bool("Show Health", true));
    public BetterTab() { super("BetterTab", "Adds health values to player names in the tab list", Category.RENDER, false, false, false); }
    public boolean showsHealth() { return isOn() && showHealth.getValue(); }
}
