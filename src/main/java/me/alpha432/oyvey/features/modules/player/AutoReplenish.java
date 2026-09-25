package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** Refills depleted hotbar stacks from matching stacks in the main inventory. */
public class AutoReplenish extends Module {
    private final Setting<Integer> threshold = register(num("Refill below", 8, 1, 63));
    private int cooldown;
    public AutoReplenish() { super("AutoReplenish", "Refills low hotbar stacks from your inventory", Category.PLAYER, false, false, false); }

    @Override public void onUpdate() {
        if (nullCheck() || mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        if (cooldown-- > 0) return;
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            ItemStack stack = mc.player.getInventory().getStack(hotbar);
            if (stack.isEmpty() || stack.getCount() > threshold.getValue()) continue;
            for (int slot = 9; slot < 36; slot++) {
                ItemStack candidate = mc.player.getInventory().getStack(slot);
                if (ItemStack.areItemsAndComponentsEqual(stack, candidate) && !candidate.isEmpty()) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 36 + hotbar, 0, SlotActionType.SWAP, mc.player);
                    cooldown = 4;
                    return;
                }
            }
        }
    }
}
