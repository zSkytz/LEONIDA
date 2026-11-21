package dev.motionengine.core.rig;

import dev.motionengine.core.math.MathUtil;

/**
 * Analytic two-bone IK in a plane (law of cosines). Used for legs (hip→knee→ankle) and arms
 * (shoulder→elbow→wrist) on a jointed rig, and to emulate a "virtual knee" on the vanilla rig.
 *
 * <p>Plane coordinates: (forward, down). Results: {@link #rootAngle} = pitch of the upper bone from
 * straight down (+ = forward), {@link #bend} = flexion of the middle joint (always &gt;= 0; the
 * caller decides its direction: knees bend backward, elbows forward).
 */
public final class TwoBoneIK {
    public float rootAngle;
    public float bend;
    public boolean reached;

    /**
     * @param l1 upper bone length
     * @param l2 lower bone length
     * @param forward target offset forward from the root joint
     * @param down target offset downward from the root joint (positive = below)
     * @param bendBackward true for knees (middle joint goes forward, lower bone points back)
     */
    public TwoBoneIK solve(float l1, float l2, float forward, float down, boolean bendBackward) {
        float d = MathUtil.length(forward, down);
        float maxReach = (l1 + l2) * 0.9999f;
        float minReach = Math.abs(l1 - l2) + 1e-4f;
        reached = d <= maxReach && d >= minReach;
        d = MathUtil.clamp(d, minReach, maxReach);

        float targetAngle = (float) Math.atan2(forward, down);
        // Angle at root between target line and upper bone.
        float cosA = (l1 * l1 + d * d - l2 * l2) / (2f * l1 * d);
        float a = (float) Math.acos(MathUtil.clamp(cosA, -1f, 1f));
        // Interior angle at the middle joint.
        float cosB = (l1 * l1 + l2 * l2 - d * d) / (2f * l1 * l2);
        float interior = (float) Math.acos(MathUtil.clamp(cosB, -1f, 1f));
        bend = MathUtil.PI - interior;
        // Knee in front of the hip→ankle line: upper bone rotates forward.
        rootAngle = bendBackward ? targetAngle + a : targetAngle - a;
        return this;
    }

    /** Forward kinematics helper: end position (forward, down) for given angles. */
    public static void forward(float l1, float l2, float rootAngle, float bend, boolean bendBackward, float[] out) {
        float lowerAngle = bendBackward ? rootAngle - bend : rootAngle + bend;
        out[0] = l1 * (float) Math.sin(rootAngle) + l2 * (float) Math.sin(lowerAngle);
        out[1] = l1 * (float) Math.cos(rootAngle) + l2 * (float) Math.cos(lowerAngle);
    }
}
