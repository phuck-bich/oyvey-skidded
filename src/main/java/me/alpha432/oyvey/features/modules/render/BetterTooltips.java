package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

public class BetterTooltips extends Module {
    private final Setting<Boolean> itemId = register(bool("Show Item ID", true));
    private final Setting<Boolean> shulkerContents = register(bool("Show Shulker Contents", true));
    public BetterTooltips() { super("BetterTooltips", "Adds useful technical details to item tooltips", Category.RENDER, false, false, false); }
    public boolean showsItemId() { return isOn() && itemId.getValue(); }
    public boolean showsShulkerContents() { return isOn() && shulkerContents.getValue(); }
}
