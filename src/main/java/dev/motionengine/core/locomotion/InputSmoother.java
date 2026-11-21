package dev.motionengine.core.locomotion;

import dev.motionengine.core.math.MathUtil;

/**
 * Physical inertia for the local player: shapes the 2D movement input (strafe, forward) so that
 * starting takes a little time and stopping is a short deceleration instead of an instant cut.
 *
 * <p>Only magnitude is smoothed with separate accel/decel times; direction changes pass through
 * immediately (with a short blend) so aiming/strafing precision is preserved. The output magnitude
 * never exceeds 1 (the vanilla maximum), so the server never sees faster-than-vanilla movement.
 */
public final class InputSmoother {
    private float magnitude;
    private float dirX, dirZ = 1f;
    public float outX, outZ;

    public void reset() {
        magnitude = 0f;
        outX = outZ = 0f;
    }

    /**
     * @param inX strafe input (-1..1)
     * @param inZ forward input (-1..1)
     * @param amount 0 = passthrough, 1 = full smoothing
     */
    public void update(float inX, float inZ, float amount, float accelTime, float decelTime, float dt) {
        float inMag = MathUtil.length(inX, inZ);
        if (amount <= 0f || !(dt > 0f)) {
            outX = inX; outZ = inZ;
            magnitude = inMag;
            if (inMag > 1e-4f) { dirX = inX / inMag; dirZ = inZ / inMag; }
            return;
        }
        if (inMag > 1e-4f) {
            float nx = inX / inMag, nz = inZ / inMag;
            float dot = nx * dirX + nz * dirZ;
            if (dot < -0.2f) {
                // Reversal: shed speed quickly before turning around (a real body plants and pushes).
                magnitude = MathUtil.approach(magnitude, 0f, dt / Math.max(0.01f, decelTime * amount));
                if (magnitude < 0.05f) { dirX = nx; dirZ = nz; }
            } else {
                float k = 1f - (float) Math.exp(-dt / 0.05f);
                dirX = MathUtil.lerp(dirX, nx, k);
                dirZ = MathUtil.lerp(dirZ, nz, k);
                float dl = MathUtil.length(dirX, dirZ);
                if (dl > 1e-4f) { dirX /= dl; dirZ /= dl; }
            }
        }
        boolean accelerating = inMag > magnitude;
        float time = (accelerating ? accelTime : decelTime) * amount;
        float target = inMag;
        if (inMag > 1e-4f && (dirX * inX + dirZ * inZ) / inMag < -0.2f) target = 0f;
        magnitude = MathUtil.approach(magnitude, target, dt / Math.max(0.01f, time));
        if (accelerating) magnitude = Math.min(magnitude, inMag);
        magnitude = Math.min(magnitude, 1f);
        if (inMag <= 1e-4f && magnitude < 0.02f) magnitude = 0f;
        outX = dirX * magnitude;
        outZ = dirZ * magnitude;
    }
}
