package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

/** Throws experience bottles while the configured use input is held. */
public class EXPThrower extends Module {
    private final Setting<Boolean> autoSwitch = register(bool("Auto switch", true));
    public EXPThrower() { super("EXPThrower", "Throws experience bottles while use is held", Category.PLAYER, false, false, false); }
    @Override public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || mc.currentScreen != null || !mc.options.useKey.isPressed()) return;
        int old = mc.player.getInventory().getSelectedSlot();
        int exp = -1;
        if (mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) exp = old;
        else if (autoSwitch.getValue()) for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) { exp = i; break; }
        if (exp < 0) return;
        if (exp != old) { mc.player.getInventory().setSelectedSlot(exp); mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(exp)); }
        mc.itemUseCooldown = 0;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (exp != old) { mc.player.getInventory().setSelectedSlot(old); mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(old)); }
    }
}
