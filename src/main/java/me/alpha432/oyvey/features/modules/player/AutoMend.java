package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.entity.EquipmentSlot;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

/** Uses experience bottles while worn equipment has repairable durability damage. */
public class AutoMend extends Module {
    private final Setting<Integer> health = register(num("Pause below health", 8, 0, 20));
    private final Timer timer = new Timer();
    public AutoMend() { super("AutoMend", "Uses experience bottles to repair worn equipment", Category.PLAYER, false, false, false); }

    @Override public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || mc.currentScreen != null || mc.player.getHealth() <= health.getValue()) return;
        if (!timer.passedMs(250)) return;
        boolean damaged = false;
        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            net.minecraft.item.ItemStack stack = mc.player.getEquippedStack(equipmentSlot);
            if (stack.isDamageable() && stack.getDamage() > 0) damaged = true;
        }
        if (!damaged) return;
        int slot = -1;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) { slot = i; break; }
        if (slot < 0) return;
        int previous = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(previous);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previous));
        timer.reset();
    }
}
