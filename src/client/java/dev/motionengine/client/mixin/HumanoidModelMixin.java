package dev.motionengine.client.mixin;

import dev.motionengine.client.bridge.MotionRenderState;
import dev.motionengine.client.bridge.VanillaModelAdapter;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the solved pose after vanilla has set the model up. Targeting HumanoidModel (not just
 * PlayerModel) means armor and cape models — which are HumanoidModels set up from the same
 * AvatarRenderState — follow the body. Other humanoids (zombies...) use other render states and are
 * not affected.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void motionengine$applyPose(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof MotionRenderState mrs && mrs.motionengine$active()) {
            VanillaModelAdapter.apply((HumanoidModel<?>) (Object) this, mrs.motionengine$pose());
        }
    }
}
