package dev.motionengine.client.mixin;

import dev.motionengine.client.bridge.MotionRenderState;
import dev.motionengine.core.rig.VanillaRigPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements MotionRenderState {
    @Unique
    private final VanillaRigPose motionengine$pose = new VanillaRigPose();
    @Unique
    private boolean motionengine$active;

    @Override
    public VanillaRigPose motionengine$pose() {
        return motionengine$pose;
    }

    @Override
    public boolean motionengine$active() {
        return motionengine$active;
    }

    @Override
    public void motionengine$setActive(boolean active) {
        motionengine$active = active;
    }
}
