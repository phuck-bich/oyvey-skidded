package me.alpha432.oyvey.features.modules.misc;

import me.alpha432.oyvey.features.modules.Module;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn extends Module {
    private boolean sentRespawn;
    public AutoRespawn() { super("AutoRespawn", "Automatically respawns after death", Category.MISC, false, false, false); }

    @Override public void onUpdate() {
        if (mc.currentScreen instanceof DeathScreen && mc.player != null) {
            if (!sentRespawn) mc.player.requestRespawn();
            sentRespawn = true;
        } else sentRespawn = false;
    }
}
