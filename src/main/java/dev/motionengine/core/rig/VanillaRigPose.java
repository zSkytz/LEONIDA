package dev.motionengine.core.rig;

/**
 * Pose for Minecraft's 6-part humanoid (head, body, 2 arms, 2 legs — no knees or elbows), still in
 * the core conventions of {@link SkeletonPose}. The client adapter converts it to ModelPart values.
 */
public final class VanillaRigPose {
    public float weight, armsWeight;

    /** Whole-body translation (metres, local: right, up, forward) and tilt about the ground. */
    public float rootX, rootY, rootZ;
    public float tiltPitch, tiltRoll;

    /** Shift of hips + upper body relative to the feet (metres). Legs re-aim to stay planted. */
    public float pelvisShiftX, pelvisShiftZ;
    public float pelvisYaw;

    public float torsoPitch, torsoYaw, torsoRoll;
    public float headPitch, headYaw, headRoll;

    public final float[] legPitch = new float[2], legRoll = new float[2], legYaw = new float[2];
    public final float[] armPitch = new float[2], armRoll = new float[2], armYaw = new float[2];

    /** Diagnostics: hip height used (m). */
    public float hipHeight;

    public void copyFrom(VanillaRigPose o) {
        weight = o.weight; armsWeight = o.armsWeight;
        rootX = o.rootX; rootY = o.rootY; rootZ = o.rootZ; tiltPitch = o.tiltPitch; tiltRoll = o.tiltRoll;
        pelvisShiftX = o.pelvisShiftX; pelvisShiftZ = o.pelvisShiftZ; pelvisYaw = o.pelvisYaw;
        torsoPitch = o.torsoPitch; torsoYaw = o.torsoYaw; torsoRoll = o.torsoRoll;
        headPitch = o.headPitch; headYaw = o.headYaw; headRoll = o.headRoll; hipHeight = o.hipHeight;
        for (int i = 0; i < 2; i++) {
            legPitch[i] = o.legPitch[i]; legRoll[i] = o.legRoll[i]; legYaw[i] = o.legYaw[i];
            armPitch[i] = o.armPitch[i]; armRoll[i] = o.armRoll[i]; armYaw[i] = o.armYaw[i];
        }
    }
}
