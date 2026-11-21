package dev.motionengine.core.math;

/**
 * Damped harmonic spring (mass = 1). Used everywhere we want "weight": lean, landing compression,
 * pelvis height, head follow. Damping ratio 1 = critically damped (no overshoot), &lt;1 = overshoot.
 *
 * Integration is semi-implicit Euler with fixed sub-steps so that results are stable and nearly
 * identical at 20, 60 or 240 fps.
 */
public final class Spring {
    private static final float MAX_STEP = 1f / 240f;

    public float value;
    public float velocity;
    private float frequency;
    private float dampingRatio;

    public Spring(float frequencyHz, float dampingRatio) {
        this.frequency = frequencyHz;
        this.dampingRatio = dampingRatio;
    }

    public Spring set(float frequencyHz, float dampingRatio) {
        this.frequency = frequencyHz;
        this.dampingRatio = dampingRatio;
        return this;
    }

    public float update(float target, float dt) {
        if (!(dt > 0f)) return value;
        if (dt > 0.25f) dt = 0.25f;
        int steps = (int) Math.ceil(dt / MAX_STEP);
        float h = dt / steps;
        float omega = MathUtil.TAU * frequency;
        float k = omega * omega;
        float c = 2f * dampingRatio * omega;
        for (int i = 0; i < steps; i++) {
            float accel = k * (target - value) - c * velocity;
            velocity += accel * h;
            value += velocity * h;
        }
        if (!MathUtil.isFinite(value) || !MathUtil.isFinite(velocity)) {
            value = target;
            velocity = 0f;
        }
        return value;
    }

    public void reset(float v) {
        value = v;
        velocity = 0f;
    }

    /** Adds instantaneous velocity (e.g. landing impact). */
    public void impulse(float dv) {
        velocity += dv;
    }
}
