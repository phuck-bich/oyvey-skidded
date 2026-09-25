package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.entity.Entity;

/** Clicks the current entity target at a configurable interval while attack is held. */
public class AutoClicker extends Module {
    private final Timer timer = new Timer();
    private final Setting<Integer> cps = register(num("CPS", 8, 1, 20));

    public AutoClicker() { super("AutoClicker", "Repeats the attack input at a configurable rate", Category.PLAYER, false, false, false); }

    @Override public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || mc.currentScreen != null || !mc.options.attackKey.isPressed()) return;
        long delay = 1000L / cps.getValue();
        if (!timer.passedMs(delay)) return;
        Entity target = mc.targetedEntity;
        if (target != null) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        } else if (mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) {
            mc.interactionManager.attackBlock(hit.getBlockPos(), hit.getSide());
        }
        timer.reset();
    }
}
