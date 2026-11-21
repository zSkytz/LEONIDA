package dev.motionengine.core.locomotion;

import dev.motionengine.core.math.MathUtil;

/**
 * 2D blend space: axis 1 = horizontal speed (m/s), axis 2 = crouch weight (0..1).
 * Each row is a 1D blend tree over speed anchors. Sampling is piecewise-linear along speed and
 * linear between rows.
 *
 * <p>Reference speeds: Minecraft's base walk is 4.317 m/s (a human jog/run), sprint 5.612 m/s,
 * sneak ~1.3 m/s. The walk modifier key scales input to reach the true walking anchors.
 */
public final class GaitBlendSpace {
    private final GaitParams[] standing;
    private final GaitParams[] crouched;
    private final GaitParams tmpA = new GaitParams();
    private final GaitParams tmpB = new GaitParams();

    public GaitBlendSpace(GaitParams[] standing, GaitParams[] crouched) {
        this.standing = standing;
        this.crouched = crouched;
    }

    public static GaitBlendSpace createDefault() {
        //                         speed cycle duty  width lift  arm   elbow lean  pYaw  twist armOut hip
        GaitParams[] standing = {
            new GaitParams(0.0f, 1.00f, 0.66f, 0.125f, 0.06f, 0.00f, 0.12f, 0.02f, 0.00f, 0.00f, 0.06f, 1.00f),
            new GaitParams(0.9f, 1.05f, 0.64f, 0.120f, 0.07f, 0.22f, 0.16f, 0.03f, 0.10f, 0.50f, 0.06f, 0.98f),
            new GaitParams(1.5f, 1.35f, 0.62f, 0.115f, 0.09f, 0.34f, 0.22f, 0.05f, 0.12f, 0.55f, 0.07f, 0.97f),
            new GaitParams(2.3f, 1.70f, 0.57f, 0.105f, 0.11f, 0.46f, 0.45f, 0.08f, 0.12f, 0.55f, 0.08f, 0.96f),
            new GaitParams(3.2f, 2.05f, 0.45f, 0.095f, 0.16f, 0.52f, 1.10f, 0.11f, 0.10f, 0.60f, 0.09f, 0.94f),
            new GaitParams(4.3f, 2.45f, 0.38f, 0.080f, 0.20f, 0.60f, 1.35f, 0.15f, 0.09f, 0.65f, 0.10f, 0.93f),
            new GaitParams(5.6f, 2.95f, 0.33f, 0.070f, 0.26f, 0.78f, 1.50f, 0.22f, 0.08f, 0.70f, 0.10f, 0.92f),
            new GaitParams(7.5f, 3.60f, 0.30f, 0.065f, 0.28f, 0.85f, 1.55f, 0.25f, 0.08f, 0.70f, 0.10f, 0.92f),
        };
        GaitParams[] crouched = {
            new GaitParams(0.0f, 0.90f, 0.70f, 0.150f, 0.05f, 0.05f, 0.55f, 0.38f, 0.00f, 0.00f, 0.10f, 0.78f),
            new GaitParams(1.3f, 1.00f, 0.66f, 0.150f, 0.07f, 0.22f, 0.60f, 0.40f, 0.10f, 0.45f, 0.10f, 0.76f),
            new GaitParams(2.6f, 1.40f, 0.58f, 0.140f, 0.09f, 0.35f, 0.75f, 0.42f, 0.10f, 0.50f, 0.10f, 0.74f),
            new GaitParams(4.3f, 2.10f, 0.45f, 0.120f, 0.12f, 0.50f, 1.10f, 0.45f, 0.09f, 0.55f, 0.10f, 0.74f),
        };
        return new GaitBlendSpace(standing, crouched);
    }

    public void sample(float speed, float crouchWeight, GaitParams out) {
        crouchWeight = MathUtil.clamp01(crouchWeight);
        if (crouchWeight <= 0f) { sampleRow(standing, speed, out); return; }
        if (crouchWeight >= 1f) { sampleRow(crouched, speed, out); return; }
        sampleRow(standing, speed, tmpA);
        sampleRow(crouched, speed, tmpB);
        out.lerp(tmpA, tmpB, crouchWeight);
        out.speed = speed;
    }

    private static void sampleRow(GaitParams[] row, float speed, GaitParams out) {
        if (speed <= row[0].speed) { out.set(row[0]); out.speed = speed; return; }
        for (int i = 1; i < row.length; i++) {
            if (speed <= row[i].speed) {
                float t = MathUtil.inverseLerp(row[i - 1].speed, row[i].speed, speed);
                out.lerp(row[i - 1], row[i], t);
                out.speed = speed;
                return;
            }
        }
        // Beyond last anchor: extend cycle length proportionally so stride still matches speed.
        GaitParams last = row[row.length - 1];
        out.set(last);
        out.cycleLength = last.cycleLength * Math.min(1.6f, speed / last.speed);
        out.speed = speed;
    }
}
