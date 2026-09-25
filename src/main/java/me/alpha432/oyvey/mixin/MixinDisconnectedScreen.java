package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.misc.AutoReconnect;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public class MixinDisconnectedScreen {
    @Inject(method = "init", at = @At("TAIL"))
    private void oyvey$scheduleReconnect(CallbackInfo ci) {
        if (OyVey.moduleManager == null) return;
        AutoReconnect module = OyVey.moduleManager.getModuleByClass(AutoReconnect.class);
        if (module != null) module.onDisconnected();
    }
}
