package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.render.BetterTooltips;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public class MixinItemStack {
    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void oyvey$appendItemId(CallbackInfoReturnable<List<Text>> cir) {
        if (OyVey.moduleManager == null) return;
        BetterTooltips module = OyVey.moduleManager.getModuleByClass(BetterTooltips.class);
        if (module == null || !module.showsItemId()) return;
        List<Text> lines = new ArrayList<>(cir.getReturnValue());
        ItemStack stack = (ItemStack) (Object) this;
        if (module.showsItemId()) lines.add(Text.literal(Registries.ITEM.getId(stack.getItem()).toString()).styled(style -> style.withColor(0xAAAAAA)));
        if (module.showsShulkerContents()) {
            ContainerComponent contents = stack.get(DataComponentTypes.CONTAINER);
            if (contents != null) {
                int shown = 0;
                for (ItemStack item : contents.iterateNonEmpty()) {
                    if (shown++ >= 8) break;
                    lines.add(Text.literal(item.getName().getString() + " x" + item.getCount()).styled(style -> style.withColor(0xAAAAAA)));
                }
            }
        }
        cir.setReturnValue(lines);
    }
}
