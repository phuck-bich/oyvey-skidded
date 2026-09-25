package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/** Client-side rendering switches for distracting environmental effects. */
public class NoRender extends Module {
    private final Setting<Boolean> weather = register(bool("Weather", true));
    public NoRender() { super("NoRender", "Hides selected environmental rendering", Category.RENDER, false, false, false); }
    public boolean hidesWeather() { return isOn() && weather.getValue(); }
}
