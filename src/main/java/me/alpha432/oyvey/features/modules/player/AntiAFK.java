package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.util.PlayerInput;

/** Periodically moves or jumps while idle to avoid server AFK timeouts. */
public class AntiAFK extends Module {
    private final Timer timer = new Timer();
    private final me.alpha432.oyvey.features.settings.Setting<Integer> interval =
            register(num("Interval (seconds)", 30, 5, 300));
    private boolean moving;

    public AntiAFK() { super("AntiAFK", "Periodically moves to avoid idle timeouts", Category.PLAYER, false, false, false); }

    @Override public void onUpdate() {
        if (nullCheck() || mc.currentScreen != null || !timer.passedMs(interval.getValue() * 1000L)) return;
        PlayerInput input = mc.player.input.playerInput;
        moving = !moving;
        mc.player.input.playerInput = new PlayerInput(moving, false, false, false, !moving, input.sneak(), false);
        timer.reset();
    }

    @Override public void onDisable() {
        moving = false;
    }
}
