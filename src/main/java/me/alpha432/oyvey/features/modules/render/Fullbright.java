package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;

/** Sets the vanilla gamma option to its maximum while enabled. */
public class Fullbright extends Module {
    private double oldGamma;
    public Fullbright() { super("Fullbright", "Keeps the vanilla brightness setting at maximum", Category.RENDER, false, false, false); }
    @Override public void onEnable() {
        oldGamma = mc.options.getGamma().getValue();
        mc.options.getGamma().setValue(1.0);
    }
    @Override public void onUpdate() { if (mc.options.getGamma().getValue() < 1.0) mc.options.getGamma().setValue(1.0); }
    @Override public void onDisable() { mc.options.getGamma().setValue(oldGamma); }
}
