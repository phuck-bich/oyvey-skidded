package me.alpha432.oyvey.mixin;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.movement.Sneak;
import me.alpha432.oyvey.features.modules.movement.Flight;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void keepVanillaSneakInput(CallbackInfo ci) {
        if (OyVey.moduleManager == null) return;
        Sneak sneak = OyVey.moduleManager.getModuleByClass(Sneak.class);
        Input input = (Input) (Object) this;
        PlayerInput current = input.playerInput;
        boolean forceSneak = sneak != null && sneak.usesVanillaInput();
        Flight flight = OyVey.moduleManager.getModuleByClass(Flight.class);
        boolean suppressSneak = flight != null && flight.noSneak();
        if (!forceSneak && (!suppressSneak || !current.sneak())) return;
        input.playerInput = new PlayerInput(
                current.forward(), current.backward(), current.left(), current.right(),
                current.jump(), forceSneak || current.sneak() && !suppressSneak, current.sprint()
        );
    }
}
