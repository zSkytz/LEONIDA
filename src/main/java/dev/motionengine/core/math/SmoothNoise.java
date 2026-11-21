package dev.motionengine.core.math;

/**
 * Seeded 1D value noise in [-1, 1] with smooth (C1) interpolation.
 * Used for micro-animations so that two players never breathe / sway in sync.
 */
public final class SmoothNoise {
    private final long seed;

    public SmoothNoise(long seed) {
        this.seed = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
    }

    private float hash(long i) {
        long h = i * 0xBF58476D1CE4E5B9L + seed;
        h ^= (h >>> 31);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 29);
        return ((h >>> 40) / (float) (1L << 24)) * 2f - 1f;
    }

    public float sample(float t) {
        double fl = Math.floor(t);
        long i = (long) fl;
        float f = (float) (t - fl);
        float u = f * f * (3f - 2f * f);
        return MathUtil.lerp(hash(i), hash(i + 1), u);
    }

    /** Two octaves, roughly [-1,1]. */
    public float fbm(float t) {
        return sample(t) * 0.7f + sample(t * 2.13f + 17.3f) * 0.3f;
    }
}
