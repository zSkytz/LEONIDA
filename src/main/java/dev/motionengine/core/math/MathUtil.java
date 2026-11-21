package dev.motionengine.core.math;

/** Small, allocation-free math helpers. All angles are radians unless the name says otherwise. */
public final class MathUtil {
    public static final float PI = (float) Math.PI;
    public static final float TAU = PI * 2f;
    public static final float DEG = PI / 180f;
    private static final float LN2 = 0.6931472f;

    private MathUtil() {}

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float inverseLerp(float a, float b, float v) {
        if (a == b) return 0f;
        return clamp01((v - a) / (b - a));
    }

    /** Hermite smoothstep on [0,1]. */
    public static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    /** Quintic smootherstep: zero velocity AND acceleration at both ends (used for foot swing). */
    public static float smootherstep(float t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    public static float wrapRadians(float a) {
        a = a % TAU;
        if (a >= PI) a -= TAU;
        if (a < -PI) a += TAU;
        return a;
    }

    public static float wrapDegrees(float a) {
        a = a % 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    /** Frame-rate independent exponential approach. halfLife in seconds. */
    public static float damp(float current, float target, float halfLife, float dt) {
        if (halfLife <= 1e-5f) return target;
        return target + (current - target) * (float) Math.exp(-LN2 * dt / halfLife);
    }

    /** Moves toward target by at most maxDelta. */
    public static float approach(float current, float target, float maxDelta) {
        float d = target - current;
        if (d > maxDelta) return current + maxDelta;
        if (d < -maxDelta) return current - maxDelta;
        return target;
    }

    /** 1 - (1-t)^2. */
    public static float easeOut(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t);
    }

    public static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    public static float finiteOr(float v, float fallback) {
        return isFinite(v) ? v : fallback;
    }

    public static float length(float x, float z) {
        return (float) Math.sqrt(x * x + z * z);
    }
}
