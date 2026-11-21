package dev.motionengine.client.bridge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.motionengine.client.config.ConfigManager;
import dev.motionengine.core.config.MotionConfig;
import dev.motionengine.core.locomotion.LocomotionController;
import dev.motionengine.core.locomotion.MotionSample;
import dev.motionengine.core.rig.VanillaRigPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Glue between Minecraft's render pipeline and the engine-agnostic core.
 *
 * <ol>
 *   <li>{@link #extract} — called at the end of {@code AvatarRenderer.extractRenderState}: builds a
 *       {@link MotionSample} from the entity + render state, runs the controller, stores the
 *       solved pose in the render state.</li>
 *   <li>{@link #applyRootTransform} — end of {@code AvatarRenderer.setupRotations}: whole-body
 *       translation (pelvis height) and inertia tilt around the feet.</li>
 *   <li>{@link VanillaModelAdapter#apply} — end of {@code HumanoidModel.setupAnim}: joint angles
 *       (also reaches armor models, which are HumanoidModels set up from the same state).</li>
 * </ol>
 */
public final class MotionBridge {
    private static final WorldGroundProbe PROBE = new WorldGroundProbe();

    private MotionBridge() {}

    public static void extract(Avatar entity, AvatarRenderState state, float partialTick) {
        MotionRenderState mrs = (MotionRenderState) state;
        mrs.motionengine$setActive(false);
        MotionConfig cfg = ConfigManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.enabled || mc.player == null) return;

        // Inventory / GUI previews re-extract the player with fake rotations at partialTick 1.0.
        // Leave those vanilla so they never disturb the in-world simulation.
        if (mc.screen != null && partialTick == 1.0f) return;

        boolean isPlayer = entity instanceof Player;
        boolean isLocal = entity == mc.player;
        if (isPlayer && !isLocal && !cfg.animateRemotePlayers) return;
        if (!isPlayer && !cfg.animateMannequins) return;

        double distSq = isLocal ? 0.0 : entity.distanceToSqr(mc.player);
        if (distSq > (double) cfg.lodMaxDistance * cfg.lodMaxDistance) return;
        LocomotionController.Detail detail = distSq <= (double) cfg.lodFullDistance * cfg.lodFullDistance
                ? LocomotionController.Detail.FULL : LocomotionController.Detail.REDUCED;

        MotionRuntime rt = ((MotionHolder) entity).motionengine$runtime();
        double time = (entity.tickCount + partialTick) / 20.0;
        float dt;
        if (Double.isNaN(rt.lastTime)) {
            dt = 0f;
            rt.lastTime = time;
        } else if (time <= rt.lastTime) {
            dt = 0f; // same frame extracted twice (e.g. shadow pass): reuse last pose
        } else {
            dt = (float) Math.min(time - rt.lastTime, 1.0);
            rt.lastTime = time;
        }

        MotionSample s = rt.sample;
        s.time = time;
        s.x = state.x;
        s.y = state.y;
        s.z = state.z;
        s.bodyYawDeg = state.bodyRot;
        s.headYawDeg = state.yRot;
        s.headPitchDeg = state.xRot;
        s.onGround = entity.onGround();
        s.sprinting = entity.isSprinting();
        s.crouching = state.isCrouching;
        s.swimming = state.isVisuallySwimming || state.swimAmount > 0f;
        s.fallFlying = state.isFallFlying;
        s.passenger = state.isPassenger;
        s.sleeping = state.pose == Pose.SLEEPING;
        s.autoSpin = state.isAutoSpinAttack;
        s.inLiquid = entity.isInWater() || entity.isInLava();
        boolean creativeFlying = entity instanceof Player p && p.getAbilities().flying;
        s.climbing = (entity.onClimbable() && !s.onGround) || creativeFlying;
        s.armsBusy = state.attackTime > 0f || state.isUsingItem
                || !armPoseIsOurs(state.rightArmPose) || !armPoseIsOurs(state.leftArmPose);
        // AvatarRenderer lowers crouching avatars by 2/16 of their scale.
        s.renderOffsetY = s.crouching ? -0.125f : 0f;

        rt.controller.setGroundProbe(detail == LocomotionController.Detail.FULL ? PROBE.bind(entity.level()) : null);
        VanillaRigPose pose = rt.controller.updateVanilla(s, dt, cfg, detail);
        if (isLocal && cfg.debug) rt.slip.sample(rt.controller.planner, s.x, s.z, s.bodyYawDeg);

        mrs.motionengine$pose().copyFrom(pose);
        mrs.motionengine$setActive(pose.weight > 0.001f);
    }

    private static boolean armPoseIsOurs(HumanoidModel.ArmPose pose) {
        return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM;
    }

    /**
     * Called at the end of setupRotations. Local frame there: origin at the feet, +Y up,
     * +X = character right, −Z = character forward (after the body-yaw rotation).
     */
    public static void applyRootTransform(AvatarRenderState state, PoseStack poseStack) {
        MotionRenderState mrs = (MotionRenderState) state;
        if (!mrs.motionengine$active()) return;
        VanillaRigPose p = mrs.motionengine$pose();
        float w = p.weight;
        poseStack.translate(p.rootX * w, p.rootY * w, -p.rootZ * w);
        if (p.tiltPitch != 0f) poseStack.mulPose(Axis.XP.rotation(-p.tiltPitch * w));
        if (p.tiltRoll != 0f) poseStack.mulPose(Axis.ZP.rotation(-p.tiltRoll * w));
    }
}
