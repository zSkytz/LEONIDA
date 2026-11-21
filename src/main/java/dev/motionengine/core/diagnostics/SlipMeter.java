package dev.motionengine.core.diagnostics;

import dev.motionengine.core.locomotion.FootPlanner;
import dev.motionengine.core.math.MathUtil;

/**
 * Measures foot sliding: how far a planted foot moves in world space during one contact.
 * This turns quality criterion #1 ("o personagem não parecer deslizar") into a number.
 */
public final class SlipMeter {
    private final boolean[] wasPlanted = new boolean[2];
    private final double[] anchorX = new double[2], anchorZ = new double[2];
    public double maxSlip;       // worst single-contact drift, metres
    public double totalSlip;     // sum of drifts
    public int contacts;

    /**
     * @param worldX character world position
     * @param yawDeg character body yaw (Minecraft convention)
     */
    public void sample(FootPlanner planner, double worldX, double worldZ, float yawDeg) {
        float yaw = yawDeg * MathUtil.DEG;
        float fx = -(float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float rx = -fz, rz = fx;
        for (int i = 0; i < 2; i++) {
            FootPlanner.Foot f = planner.feet[i];
            double wx = worldX + rx * f.x + fx * f.z;
            double wz = worldZ + rz * f.x + fz * f.z;
            if (f.planted) {
                if (!wasPlanted[i]) {
                    anchorX[i] = wx;
                    anchorZ[i] = wz;
                    contacts++;
                } else {
                    double d = Math.hypot(wx - anchorX[i], wz - anchorZ[i]);
                    if (d > maxSlip) maxSlip = d;
                }
            } else if (wasPlanted[i]) {
                totalSlip += Math.hypot(wx - anchorX[i], wz - anchorZ[i]);
            }
            wasPlanted[i] = f.planted;
        }
    }
}
