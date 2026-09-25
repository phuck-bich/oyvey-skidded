package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.render.BossStack;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Temporarily collapses equal boss names while vanilla lays out the HUD bars. */
@Mixin(BossBarHud.class)
public abstract class MixinBossBarHud {
    @Shadow @Final private Map<UUID, ClientBossBar> bossBars;
    @Unique private Map<UUID, ClientBossBar> oyvey$originalBars;
    @Unique private Map<ClientBossBar, Text> oyvey$originalNames;

    @Inject(method = "render", at = @At("HEAD"))
    private void oyvey$collapseBossBars(net.minecraft.client.gui.DrawContext context, CallbackInfo ci) {
        BossStack module = getModule();
        if (module == null || !module.isOn() || bossBars.size() < 2) return;
        oyvey$originalBars = new HashMap<>(bossBars);
        oyvey$originalNames = new HashMap<>();
        Map<String, ClientBossBar> unique = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (ClientBossBar bar : bossBars.values()) counts.merge(bar.getName().getString(), 1, Integer::sum);
        bossBars.entrySet().removeIf(entry -> {
            ClientBossBar bar = entry.getValue();
            String name = bar.getName().getString();
            ClientBossBar first = unique.putIfAbsent(name, bar);
            if (first == null) {
                if (module.hidesNames()) {
                    oyvey$originalNames.put(bar, bar.getName());
                    bar.setName(Text.empty());
                } else if (counts.get(name) > 1) {
                    oyvey$originalNames.put(bar, bar.getName());
                    bar.setName(bar.getName().copy().append(Text.literal(" x" + counts.get(name))));
                }
                return false;
            }
            return true;
        });
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void oyvey$restoreBossBars(net.minecraft.client.gui.DrawContext context, CallbackInfo ci) {
        if (oyvey$originalBars == null) return;
        bossBars.clear();
        bossBars.putAll(oyvey$originalBars);
        oyvey$originalNames.forEach(ClientBossBar::setName);
        oyvey$originalBars = null;
        oyvey$originalNames = null;
    }

    @Unique private static BossStack getModule() {
        return OyVey.moduleManager == null ? null : OyVey.moduleManager.getModuleByClass(BossStack.class);
    }
}
