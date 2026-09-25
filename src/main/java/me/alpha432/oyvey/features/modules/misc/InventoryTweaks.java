package me.alpha432.oyvey.features.modules.misc;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Prevents accidental drops of valuable or equipped gear. */
public class InventoryTweaks extends Module {
    private final Setting<Boolean> protectDurableGear = register(bool("Protect Durable Gear", true));
    private final Setting<Boolean> protectValuables = register(bool("Protect Valuables", true));

    public InventoryTweaks() { super("InventoryTweaks", "Protects valuable items from accidental drops", Category.MISC, false, false, false); }

    public static boolean shouldBlockDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty() || OyVey.moduleManager == null) return false;
        InventoryTweaks module = OyVey.moduleManager.getModuleByClass(InventoryTweaks.class);
        if (module == null || !module.isOn()) return false;
        if (module.protectDurableGear.getValue() && (stack.isDamageable() || stack.hasEnchantments())) return true;
        if (!module.protectValuables.getValue()) return false;
        return stack.isOf(Items.TOTEM_OF_UNDYING) || stack.isOf(Items.ELYTRA)
                || stack.isOf(Items.NETHERITE_INGOT) || stack.isOf(Items.DIAMOND)
                || stack.isOf(Items.ANCIENT_DEBRIS) || stack.isOf(Items.NETHERITE_SCRAP);
    }
}
