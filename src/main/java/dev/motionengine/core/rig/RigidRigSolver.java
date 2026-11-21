package dev.motionengine.core.rig;

import dev.motionengine.core.config.MotionConfig;
import dev.motionengine.core.math.MathUtil;
import dev.motionengine.core.math.Spring;

/**
 * Maps the ideal {@link SkeletonPose} onto Minecraft's knee-less 6-part model.
 *
 * <ul>
 *   <li>Each rigid leg aims at its foot target. Because a rigid leg cannot shorten, the hip height
 *       is limited by the planted feet (compass-gait geometry: h = sqrt(L² − r²)). The resulting
 *       natural vertical bob is scaled by {@code rigidBobScale}.</li>
 *   <li>Lower ground under a foot (stairs, slabs, slopes) lowers the pelvis, clamped by
 *       {@code maxPelvisDrop}, so the lower foot actually touches the ground.</li>
 *   <li>Arms with an elbow bend are collapsed to the shoulder→hand direction ("virtual elbow").</li>
 * </ul>
 * One instance per character (holds smoothing state).
 */
public final class RigidRigSolver {
    /** Minecraft leg/arm length: 12 px = 0.75 m. Hip half-width: 1.9 px. */
    public static final float LEG = 0.75f, ARM = 0.75f, HIP_HALF_WIDTH = 1.9f / 16f;

    private final Spring hipSpring = new Spring(12f, 1f);
    private boolean primed;
    private final TwoBoneIK ik = new TwoBoneIK();
    private final float[] tmp = new float[2];

    public void reset() {
        primed = false;
    }

    public void solve(SkeletonPose in, MotionConfig cfg, float dt, VanillaRigPose out) {
        out.weight = in.weight;
        out.armsWeight = in.armsWeight;
        out.tiltPitch = in.tiltPitch;
        out.tiltRoll = in.tiltRoll;
        out.pelvisShiftX = in.pelvisShiftX;
        out.pelvisShiftZ = in.pelvisShiftZ;
        out.pelvisYaw = in.pelvisYaw;
        out.torsoPitch = in.torsoPitch;
        out.torsoYaw = in.torsoYaw;
        out.torsoRoll = in.torsoRoll;
        out.headPitch = in.headPitch;
        out.headYaw = in.headYaw;
        out.headRoll = in.headRoll;

        float cy = (float) Math.cos(in.pelvisYaw), sy = (float) Math.sin(in.pelvisYaw);

        // Hip height from feet in contact. Each foot's limit sqrt(L² - r²) is faded in/out with its
        // contact weight, so the hip height is continuous across lift-off and touchdown.
        float minCompass = LEG, minGround = 0f;
        boolean anyPlanted = false;
        for (int i = 0; i < 2; i++) {
            float w = in.footContact[i];
            if (w <= 0f) continue;
            float hx = hipX(i, cy) + in.pelvisShiftX, hz = hipZ(i, sy) + in.pelvisShiftZ;
            float dx = in.footX[i] - hx, dz = in.footZ[i] - hz;
            float r2 = Math.min(dx * dx + dz * dz, LEG * LEG * 0.81f);
            float limit = MathUtil.lerp(LEG, (float) Math.sqrt(LEG * LEG - r2), w);
            minCompass = anyPlanted ? smoothMin(minCompass, limit, 0.015f) : Math.min(LEG, limit);
            float g = MathUtil.lerp(0f, in.footGround[i], w);
            minGround = anyPlanted ? Math.min(minGround, g) : g;
            anyPlanted = true;
        }
        float groundDrop = cfg.footIkEnabled ? MathUtil.clamp(Math.min(0f, minGround), -cfg.maxPelvisDrop, 0f) : 0f;
        float bob = anyPlanted ? (LEG - minCompass) * cfg.rigidBobScale * cfg.bodyMotionIntensity : 0f;
        float target = LEG + groundDrop - bob - Math.min(in.bodyDrop, 0.08f);
        if (!primed) { hipSpring.reset(target); primed = true; }
        float h = hipSpring.update(target, dt);
        out.hipHeight = h;
        out.rootX = 0f;
        out.rootZ = 0f;
        out.rootY = h - LEG;

        // Legs aim at their foot targets.
        for (int i = 0; i < 2; i++) {
            float hx = hipX(i, cy) + in.pelvisShiftX, hz = hipZ(i, sy) + in.pelvisShiftZ;
            float vx = in.footX[i] - hx;
            float vy = in.footY[i] - h;
            float vz = in.footZ[i] - hz;
            float down = Math.max(0.05f, -vy);
            out.legPitch[i] = (float) Math.atan2(vz, down);
            out.legRoll[i] = (float) Math.atan2(vx, MathUtil.length(down, vz));
            out.legYaw[i] = in.pelvisYaw * 0.5f;
        }

        // Arms: collapse shoulder + elbow into the shoulder→hand direction.
        for (int i = 0; i < 2; i++) {
            TwoBoneIK.forward(ARM * 0.5f, ARM * 0.5f, in.shoulderPitch[i], in.elbowBend[i], false, tmp);
            out.armPitch[i] = (float) Math.atan2(tmp[0], tmp[1]);
            out.armRoll[i] = in.shoulderRoll[i];
            out.armYaw[i] = in.shoulderYaw[i];
        }
    }

    /** Smooth minimum (log-sum-exp); removes slope kinks when the supporting foot changes. */
    static float smoothMin(float a, float b, float k) {
        float m = Math.min(a, b);
        return m - k * (float) Math.log(Math.exp(-(a - m) / k) + Math.exp(-(b - m) / k));
    }

    private static float hipX(int i, float cosYaw) {
        float side = i == SkeletonPose.RIGHT ? 1f : -1f;
        return side * HIP_HALF_WIDTH * cosYaw;
    }

    private static float hipZ(int i, float sinYaw) {
        float side = i == SkeletonPose.RIGHT ? 1f : -1f;
        return -side * HIP_HALF_WIDTH * sinYaw;
    }

    /** Exposed for tests: the IK helper used by jointed rigs. */
    public TwoBoneIK ik() {
        return ik;
    }
}
