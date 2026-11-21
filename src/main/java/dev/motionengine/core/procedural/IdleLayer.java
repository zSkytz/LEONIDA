package dev.motionengine.core.procedural;

import dev.motionengine.core.math.MathUtil;
import dev.motionengine.core.math.SmoothNoise;

/**
 * Micro-animations: breathing, slow weight shifts between legs, small head drift and occasional
 * glances. Seeded per character so nobody breathes in sync. Values are additive.
 */
public final class IdleLayer {
    private final SmoothNoise noise;
    private final float breathPeriod;

    public float breath;       // -1..1
    public float torsoPitch;   // rad
    public float shoulderLift; // rad (arm roll outward)
    public float pelvisShiftX; // m (+ = toward right foot)
    public float pelvisRoll;   // rad
    public float headYaw, headPitch; // rad

    public IdleLayer(long seed) {
        this.noise = new SmoothNoise(seed);
        this.breathPeriod = 3.6f + (Math.floorMod(seed, 1000L) / 1000f) * 1.4f;
    }

    /**
     * @param t time (s)
     * @param exertion 0..1, raises breathing rate and depth after running
     * @param frequency user "micro-animation frequency" multiplier
     */
    public void update(float t, float exertion, float frequency) {
        float period = breathPeriod * MathUtil.lerp(1f, 0.45f, exertion);
        breath = (float) Math.sin(t * MathUtil.TAU / period);
        float depth = MathUtil.lerp(1f, 2.2f, exertion);
        torsoPitch = -0.012f * breath * depth;
        shoulderLift = 0.012f * Math.max(0f, breath) * depth;

        float f = Math.max(0.05f, frequency);
        // Weight shift: slow, mostly settled on one leg, occasionally switching.
        float shift = noise.sample(t * 0.09f * f);
        shift = Math.signum(shift) * MathUtil.smoothstep(Math.abs(shift) * 1.6f);
        pelvisShiftX = shift * 0.035f;
        pelvisRoll = -shift * 0.035f;

        headYaw = noise.fbm(t * 0.12f * f + 31.7f) * 0.10f * Math.min(1f, f);
        headPitch = noise.fbm(t * 0.10f * f + 77.1f) * 0.05f * Math.min(1f, f);
    }
}
