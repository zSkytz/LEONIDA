package dev.motionengine.client.mixin;

import dev.motionengine.client.input.InputController;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Walk modifier + physical inertia: reshapes the movement vector after vanilla reads the keys. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void motionengine$shapeInput(CallbackInfo ci) {
        ClientInput self = (ClientInput) (Object) this;
        Vec2 raw = self.getMoveVector();
        Vec2 shaped = InputController.process(raw);
        if (shaped != raw) ((ClientInputAccessor) self).motionengine$setMoveVector(shaped);
    }
}
