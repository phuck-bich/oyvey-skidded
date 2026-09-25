package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.render.BetterTab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class MixinPlayerListHud {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void oyvey$appendHealth(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (OyVey.moduleManager == null) return;
        BetterTab module = OyVey.moduleManager.getModuleByClass(BetterTab.class);
        var world = MinecraftClient.getInstance().world;
        if (module == null || !module.showsHealth() || world == null) return;
        var player = world.getPlayerByUuid(entry.getProfile().id());
        if (player != null) cir.setReturnValue(cir.getReturnValue().copy().append(Text.literal(" " + String.format("%.0f", player.getHealth() + player.getAbsorptionAmount())).styled(style -> style.withColor(0x55FF55))));
    }
}
