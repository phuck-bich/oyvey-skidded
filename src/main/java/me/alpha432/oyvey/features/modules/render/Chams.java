package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/** Applies a configurable, wall-visible outline to player models. */
public class Chams extends Module {
    private final Setting<Integer> red = register(num("Red", 255, 0, 255));
    private final Setting<Integer> green = register(num("Green", 70, 0, 255));
    private final Setting<Integer> blue = register(num("Blue", 70, 0, 255));
    private final Setting<Boolean> self = register(bool("Include Self", false));

    public Chams() { super("Chams", "Adds colored player outlines visible through blocks", Category.RENDER, false, false, false); }
    public boolean outlines(net.minecraft.entity.Entity entity) {
        return isOn() && entity instanceof net.minecraft.entity.player.PlayerEntity && (self.getValue() || entity != mc.player);
    }
    public int color() { return 0xFF000000 | red.getValue() << 16 | green.getValue() << 8 | blue.getValue(); }
}
