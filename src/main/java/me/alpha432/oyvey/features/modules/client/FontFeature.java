package me.alpha432.oyvey.features.modules.client;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

import java.awt.Font;

public class FontFeature extends Module {
    public enum Style { Plain, Bold, Italic, BoldItalic }

    public final Setting<String> name = str("Name", "Tahoma");
    public final Setting<Integer> size = num("Size", 18, 8, 48);
    public final Setting<Style> style = mode("Style", Style.Plain);
    public final Setting<Integer> xOffset = num("X-Offset", 0, -10, 10);
    public final Setting<Integer> yOffset = num("Y-Offset", 0, -10, 10);
    public final Setting<Double> shadowOffset = num("Shadow-Offset", 0.5d, -2.0d, 2.0d);
    public final Setting<Boolean> global = bool("Global", false);

    private Font awtFont;

    public FontFeature() {
        super("Font", "Custom HUD font configuration inspired by Mint.", Category.CLIENT, false, false, false);
        updateFont();
    }

    @Override
    public void onEnable() {
        updateFont();
    }

    @Override
    public void onDisable() {
        updateFont();
    }

    public void updateFont() {
        int awtStyle = switch (style.getValue()) {
            case Bold -> Font.BOLD;
            case Italic -> Font.ITALIC;
            case BoldItalic -> Font.BOLD | Font.ITALIC;
            case Plain -> Font.PLAIN;
        };
        awtFont = new Font(name.getValue(), awtStyle, size.getValue());
    }

    public Font getAwtFont() {
        return awtFont;
    }

    public int getXOffset() {
        return xOffset.getValue();
    }

    public int getYOffset() {
        return yOffset.getValue();
    }

    public double getShadowOffset() {
        return shadowOffset.getValue();
    }

    public boolean isGlobal() {
        return isEnabled() && global.getValue();
    }

    @Override
    public String getDisplayInfo() {
        return size.getValue() + "px";
    }
}
