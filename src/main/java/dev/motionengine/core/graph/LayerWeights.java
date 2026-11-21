package dev.motionengine.core.graph;

import dev.motionengine.core.math.MathUtil;

/**
 * Weights of the procedural animation layers. Each state declares a target set; the controller
 * moves the live weights toward it at a rate given by the transition's blend time. Weights are
 * always continuous, which is what guarantees pop-free transitions.
 */
public final class LayerWeights {
    public static final int IDLE = 0, LOCOMOTION = 1, START = 2, BRAKE = 3, PIVOT = 4, AIR = 5, LAND = 6, ACTIVE = 7;
    public static final int COUNT = 8;
    private static final String[] NAMES = {"idle", "loco", "start", "brake", "pivot", "air", "land", "active"};

    public final float[] w = new float[COUNT];

    public LayerWeights set(int layer, float value) {
        w[layer] = value;
        return this;
    }

    public float get(int layer) {
        return w[layer];
    }

    public void copyFrom(LayerWeights other) {
        System.arraycopy(other.w, 0, w, 0, COUNT);
    }

    /** Linear move toward target so that a full 0→1 change takes {@code blendTime} seconds. */
    public void moveToward(LayerWeights target, float blendTime, float dt) {
        float step = blendTime <= 1e-4f ? 1f : dt / blendTime;
        for (int i = 0; i < COUNT; i++) w[i] = MathUtil.approach(w[i], target.w[i], step);
    }

    /** Smoothstepped value, used when a layer contributes a pose (removes velocity discontinuity). */
    public float eased(int layer) {
        return MathUtil.smoothstep(w[layer]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            if (w[i] > 0.005f) sb.append(NAMES[i]).append('=').append(String.format("%.2f", w[i])).append(' ');
        }
        return sb.toString().trim();
    }
}
