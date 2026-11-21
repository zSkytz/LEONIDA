package dev.motionengine.core.rig;

/**
 * Rig-independent output of the locomotion controller (the "ideal" skeleton).
 *
 * <p>Conventions (character-local frame, metres / radians):
 * x = right, y = up, z = forward. For limbs, pitch &gt; 0 swings the distal end forward
 * (flexion), roll &gt; 0 moves it toward the character's right, yaw &gt; 0 twists to the right.
 * For pelvis/torso/head, pitch &gt; 0 = lean/look forward-down, roll &gt; 0 = top tilts right,
 * yaw &gt; 0 = turn right.
 *
 * <p>Rig solvers ({@link RigidRigSolver} for the vanilla 6-part model, {@link TwoBoneIK} for a
 * jointed rig) convert this into joint rotations for a concrete model.
 */
public final class SkeletonPose {
    public static final int RIGHT = 0, LEFT = 1;

    /** 0 = pure vanilla animation, 1 = fully ours. */
    public float weight;
    /** Weight of our arm animation (0 while vanilla needs the arms: attack, using items...). */
    public float armsWeight;

    public final float[] footX = new float[2], footY = new float[2], footZ = new float[2];
    public final boolean[] footPlanted = new boolean[2];
    /** 0..1 how much a foot constrains the pelvis (fades in before touchdown, out after lift-off). */
    public final float[] footContact = new float[2];
    /** Ground height under each foot (m), independent of swing lift. */
    public final float[] footGround = new float[2];

    /** Extra vertical drop of the whole body (landing compression), metres, &gt;= 0. */
    public float bodyDrop;
    /** Desired hip height as a fraction of leg length (jointed rigs bend knees to reach it). */
    public float hipHeightRatio = 1f;
    public float pelvisShiftX, pelvisShiftZ;
    public float pelvisYaw, pelvisRoll;

    public float torsoPitch, torsoYaw, torsoRoll;
    /** Head rotation relative to the torso (already compensated so the gaze follows the camera). */
    public float headPitch, headYaw, headRoll;

    public final float[] shoulderPitch = new float[2], shoulderRoll = new float[2], shoulderYaw = new float[2];
    public final float[] elbowBend = new float[2];

    /** Whole-body tilt around the ground contact (inertia lean). */
    public float tiltPitch, tiltRoll;

    public void clear() {
        weight = armsWeight = 0f;
        for (int i = 0; i < 2; i++) {
            footX[i] = footY[i] = footZ[i] = 0f;
            footPlanted[i] = true;
            footContact[i] = 1f;
            footGround[i] = 0f;
            shoulderPitch[i] = shoulderRoll[i] = shoulderYaw[i] = elbowBend[i] = 0f;
        }
        bodyDrop = 0f; hipHeightRatio = 1f;
        pelvisShiftX = pelvisShiftZ = pelvisYaw = pelvisRoll = 0f;
        torsoPitch = torsoYaw = torsoRoll = 0f;
        headPitch = headYaw = headRoll = 0f;
        tiltPitch = tiltRoll = 0f;
    }

    public boolean isFinite() {
        float sum = weight + armsWeight + bodyDrop + hipHeightRatio + pelvisShiftX + pelvisShiftZ + pelvisYaw
                + pelvisRoll + torsoPitch + torsoYaw + torsoRoll + headPitch + headYaw + headRoll + tiltPitch + tiltRoll;
        for (int i = 0; i < 2; i++) {
            sum += footX[i] + footY[i] + footZ[i] + footContact[i] + footGround[i] + shoulderPitch[i] + shoulderRoll[i] + shoulderYaw[i] + elbowBend[i];
        }
        return !Float.isNaN(sum) && !Float.isInfinite(sum);
    }
}
