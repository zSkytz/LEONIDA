package dev.motionengine.core.locomotion;

import dev.motionengine.core.math.MathUtil;

/**
 * Parameters of a gait at one reference speed. Gaits are not baked clips: the procedural gait
 * generator turns these numbers into foot trajectories, so blending two gaits is just blending
 * numbers — the result is always a valid, foot-locked gait (no sliding, no phase mismatch).
 */
public final class GaitParams {
    /** Reference speed (m/s) of this anchor in the blend space. */
    public float speed;
    /** Distance travelled per full gait cycle (two steps), metres. Cadence = speed / cycleLength. */
    public float cycleLength = 1.3f;
    /** Fraction of the cycle a foot is in contact. &gt;0.5 walk (double support), &lt;0.5 run (flight). */
    public float dutyFactor = 0.62f;
    /** Lateral distance of each foot from the centre line, metres. */
    public float stepWidth = 0.12f;
    /** Peak foot lift during swing, metres. */
    public float footLift = 0.09f;
    /** Shoulder pitch amplitude, radians. */
    public float armSwing = 0.35f;
    /** Elbow flexion, radians. */
    public float elbowBend = 0.22f;
    /** Static forward torso lean, radians. */
    public float torsoLean = 0.05f;
    /** Pelvis yaw toward the leading leg, radians per metre of foot separation. */
    public float pelvisYaw = 0.12f;
    /** Fraction of pelvis yaw the shoulders counter-rotate. */
    public float counterTwist = 0.55f;
    /** Arm abduction (outward), radians. */
    public float armOutward = 0.07f;
    /** Desired hip height ratio for a jointed rig (1 = straight legs). */
    public float hipHeightRatio = 0.97f;

    public GaitParams() {}

    public GaitParams(float speed, float cycleLength, float dutyFactor, float stepWidth, float footLift,
                      float armSwing, float elbowBend, float torsoLean, float pelvisYaw, float counterTwist,
                      float armOutward, float hipHeightRatio) {
        this.speed = speed; this.cycleLength = cycleLength; this.dutyFactor = dutyFactor;
        this.stepWidth = stepWidth; this.footLift = footLift; this.armSwing = armSwing;
        this.elbowBend = elbowBend; this.torsoLean = torsoLean; this.pelvisYaw = pelvisYaw;
        this.counterTwist = counterTwist; this.armOutward = armOutward; this.hipHeightRatio = hipHeightRatio;
    }

    public void set(GaitParams o) {
        speed = o.speed; cycleLength = o.cycleLength; dutyFactor = o.dutyFactor; stepWidth = o.stepWidth;
        footLift = o.footLift; armSwing = o.armSwing; elbowBend = o.elbowBend; torsoLean = o.torsoLean;
        pelvisYaw = o.pelvisYaw; counterTwist = o.counterTwist; armOutward = o.armOutward;
        hipHeightRatio = o.hipHeightRatio;
    }

    public void lerp(GaitParams a, GaitParams b, float t) {
        speed = MathUtil.lerp(a.speed, b.speed, t);
        cycleLength = MathUtil.lerp(a.cycleLength, b.cycleLength, t);
        dutyFactor = MathUtil.lerp(a.dutyFactor, b.dutyFactor, t);
        stepWidth = MathUtil.lerp(a.stepWidth, b.stepWidth, t);
        footLift = MathUtil.lerp(a.footLift, b.footLift, t);
        armSwing = MathUtil.lerp(a.armSwing, b.armSwing, t);
        elbowBend = MathUtil.lerp(a.elbowBend, b.elbowBend, t);
        torsoLean = MathUtil.lerp(a.torsoLean, b.torsoLean, t);
        pelvisYaw = MathUtil.lerp(a.pelvisYaw, b.pelvisYaw, t);
        counterTwist = MathUtil.lerp(a.counterTwist, b.counterTwist, t);
        armOutward = MathUtil.lerp(a.armOutward, b.armOutward, t);
        hipHeightRatio = MathUtil.lerp(a.hipHeightRatio, b.hipHeightRatio, t);
    }

    /** Distance a foot sweeps backward (relative to the hip) while planted. */
    public float stanceLength() {
        return cycleLength * dutyFactor;
    }
}
