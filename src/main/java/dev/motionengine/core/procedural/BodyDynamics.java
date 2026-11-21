package dev.motionengine.core.procedural;

import dev.motionengine.core.locomotion.MotionEstimator;
import dev.motionengine.core.math.MathUtil;
import dev.motionengine.core.math.Spring;

/**
 * Inertia of the body mass: lean from longitudinal acceleration (start / brake), lean into turns from
 * centripetal acceleration, and landing compression. All values go through slightly under-damped
 * springs, which is what makes the body read as having mass rather than following input instantly.
 */
public final class BodyDynamics {
    private static final float G = 9.81f;

    private final Spring pitch = new Spring(2.2f, 0.72f);
    private final Spring roll = new Spring(2.0f, 0.80f);
    private final Spring compression = new Spring(3.2f, 0.55f);

    /** Lean forward (+) from acceleration, radians. */
    public float leanPitch;
    /** Lean right (+) into turns, radians. */
    public float leanRoll;
    /** Landing compression, metres (&gt;= 0). */
    public float drop;

    public void reset() {
        pitch.reset(0f);
        roll.reset(0f);
        compression.reset(0f);
        leanPitch = leanRoll = drop = 0f;
    }

    public void update(MotionEstimator m, boolean grounded, float strength, float dt) {
        float pitchTarget = 0f, rollTarget = 0f;
        if (grounded && strength > 0f) {
            // A body accelerating at a must lean by atan(a/g) to stay balanced; we show a fraction.
            pitchTarget = (float) Math.atan(m.accelForward / G) * 0.85f;
            float centripetal = m.speed * m.yawRate + m.accelRight * 0.5f;
            rollTarget = (float) Math.atan(centripetal / G) * 0.75f;
            pitchTarget = MathUtil.clamp(pitchTarget * strength, -0.38f, 0.38f);
            rollTarget = MathUtil.clamp(rollTarget * strength, -0.42f, 0.42f);
        }
        leanPitch = pitch.update(pitchTarget, dt);
        leanRoll = roll.update(rollTarget, dt);

        if (m.justLanded) {
            float impact = MathUtil.clamp(m.lastImpactSpeed, 0f, 16f);
            if (impact > 1.5f) compression.impulse(impact * 0.22f * Math.max(0.2f, strength));
        }
        drop = Math.max(0f, compression.update(0f, dt));
    }
}
