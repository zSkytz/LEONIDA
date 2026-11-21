package dev.motionengine.client.bridge;

import dev.motionengine.core.rig.VanillaRigPose;

/**
 * Duck interface implemented on {@code AvatarRenderState}. Carries the solved pose from extraction
 * (where we have the entity) to model setup / rendering (where we only have the render state).
 */
public interface MotionRenderState {
    VanillaRigPose motionengine$pose();

    boolean motionengine$active();

    void motionengine$setActive(boolean active);
}
