package dev.motionengine.core.config;

/**
 * All user-tunable parameters. Plain public fields so it serialises 1:1 to JSON (Gson in the client
 * layer). Values are multipliers around 1.0 unless documented otherwise.
 */
public final class MotionConfig {

    public enum Preset { CINEMATIC, BALANCED, PERFORMANCE, REDUCED_MOTION }

    public boolean enabled = true;
    public Preset preset = Preset.BALANCED;

    // --- Body / inertia -----------------------------------------------------------------------
    /** Visual inertia: scales lean from acceleration/turns and brake reactions. 0 disables. */
    public float inertiaStrength = 1.0f;
    /** Scales bob, sway, arm swing and twist. */
    public float bodyMotionIntensity = 1.0f;
    /** Multiplies transition speed (2 = transitions twice as fast). */
    public float transitionSpeed = 1.0f;

    // --- Procedural -----------------------------------------------------------------------------
    public boolean proceduralEnabled = true;
    public boolean footIkEnabled = true;
    /** Vanilla (knee-less) rig: fraction of the geometric compass-gait bob that is shown. */
    public float rigidBobScale = 0.35f;
    /** Max pelvis drop in metres used to reach lower ground with the feet (vanilla rig). */
    public float maxPelvisDrop = 0.22f;
    /** 0..2: how often idle micro-animations (weight shifts, glances) happen. */
    public float microAnimationFrequency = 1.0f;

    // --- Physical inertia (local player input smoothing, client side only) ---------------------
    /** 0 = vanilla input. 1 = full configured accel/decel times. */
    public float physicalInertia = 0.5f;
    /** Seconds to reach full input when starting to move (at physicalInertia = 1). */
    public float accelerationTime = 0.30f;
    /** Seconds to stop (at physicalInertia = 1). Kept short to preserve control precision. */
    public float decelerationTime = 0.16f;

    // --- Walk modifier ------------------------------------------------------------------------------
    /** Movement input multiplier while the walk key is active (vanilla walk 4.3 m/s → ~1.5 m/s). */
    public float walkSpeedFactor = 0.35f;
    public boolean walkKeyToggles = false;

    // --- LOD / multiplayer ---------------------------------------------------------------------------
    public boolean animateRemotePlayers = true;
    public boolean animateMannequins = true;
    /** Full detail (ground probes, micro-animation) within this distance (m). */
    public float lodFullDistance = 24f;
    /** Reduced detail within this distance; beyond → vanilla animation. */
    public float lodMaxDistance = 64f;

    // --- Diagnostics ---------------------------------------------------------------------------------
    public boolean debug = false;

    public MotionConfig copy() {
        MotionConfig c = new MotionConfig();
        c.enabled = enabled; c.preset = preset;
        c.inertiaStrength = inertiaStrength; c.bodyMotionIntensity = bodyMotionIntensity;
        c.transitionSpeed = transitionSpeed; c.proceduralEnabled = proceduralEnabled;
        c.footIkEnabled = footIkEnabled; c.rigidBobScale = rigidBobScale; c.maxPelvisDrop = maxPelvisDrop;
        c.microAnimationFrequency = microAnimationFrequency; c.physicalInertia = physicalInertia;
        c.accelerationTime = accelerationTime; c.decelerationTime = decelerationTime;
        c.walkSpeedFactor = walkSpeedFactor; c.walkKeyToggles = walkKeyToggles;
        c.animateRemotePlayers = animateRemotePlayers; c.animateMannequins = animateMannequins;
        c.lodFullDistance = lodFullDistance; c.lodMaxDistance = lodMaxDistance; c.debug = debug;
        return c;
    }

    /** Applies a preset over the current values. */
    public MotionConfig applyPreset(Preset p) {
        preset = p;
        switch (p) {
            case CINEMATIC -> {
                inertiaStrength = 1.25f; bodyMotionIntensity = 1.15f; transitionSpeed = 0.9f;
                proceduralEnabled = true; footIkEnabled = true; microAnimationFrequency = 1.3f;
                physicalInertia = 0.8f; lodFullDistance = 32f; lodMaxDistance = 96f;
            }
            case BALANCED -> {
                inertiaStrength = 1.0f; bodyMotionIntensity = 1.0f; transitionSpeed = 1.0f;
                proceduralEnabled = true; footIkEnabled = true; microAnimationFrequency = 1.0f;
                physicalInertia = 0.5f; lodFullDistance = 24f; lodMaxDistance = 64f;
            }
            case PERFORMANCE -> {
                inertiaStrength = 0.9f; bodyMotionIntensity = 0.9f; transitionSpeed = 1.1f;
                proceduralEnabled = true; footIkEnabled = false; microAnimationFrequency = 0.5f;
                physicalInertia = 0.4f; lodFullDistance = 12f; lodMaxDistance = 32f;
            }
            case REDUCED_MOTION -> {
                inertiaStrength = 0.4f; bodyMotionIntensity = 0.5f; transitionSpeed = 1.2f;
                proceduralEnabled = true; footIkEnabled = true; microAnimationFrequency = 0.3f;
                physicalInertia = 0.0f;
            }
        }
        return this;
    }

    /** Clamps every value into a safe range (protects against hand-edited JSON). */
    public MotionConfig sanitize() {
        inertiaStrength = clamp(inertiaStrength, 0f, 3f);
        bodyMotionIntensity = clamp(bodyMotionIntensity, 0f, 3f);
        transitionSpeed = clamp(transitionSpeed, 0.25f, 4f);
        rigidBobScale = clamp(rigidBobScale, 0f, 1f);
        maxPelvisDrop = clamp(maxPelvisDrop, 0f, 0.5f);
        microAnimationFrequency = clamp(microAnimationFrequency, 0f, 2f);
        physicalInertia = clamp(physicalInertia, 0f, 1f);
        accelerationTime = clamp(accelerationTime, 0.01f, 1.5f);
        decelerationTime = clamp(decelerationTime, 0.01f, 1.0f);
        walkSpeedFactor = clamp(walkSpeedFactor, 0.1f, 1f);
        lodFullDistance = clamp(lodFullDistance, 4f, 256f);
        lodMaxDistance = clamp(lodMaxDistance, lodFullDistance, 512f);
        if (preset == null) preset = Preset.BALANCED;
        return this;
    }

    private static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
