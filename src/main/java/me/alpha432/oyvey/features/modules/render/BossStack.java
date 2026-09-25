package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

public class BossStack extends Module {
    private final Setting<Boolean> hideNames = register(bool("Hide Names", false));
    public BossStack() { super("BossStack", "Combines duplicate boss bars to reduce HUD clutter", Category.RENDER, false, false, false); }
    public boolean hidesNames() { return isOn() && hideNames.getValue(); }
}
