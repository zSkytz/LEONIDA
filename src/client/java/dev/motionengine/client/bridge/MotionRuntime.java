package dev.motionengine.client.bridge;

import dev.motionengine.core.diagnostics.SlipMeter;
import dev.motionengine.core.locomotion.LocomotionController;
import dev.motionengine.core.locomotion.MotionSample;

/** Per-entity animation runtime, attached to each Avatar (players, mannequins) via mixin. */
public final class MotionRuntime {
    public final LocomotionController controller;
    public final MotionSample sample = new MotionSample();
    public final SlipMeter slip = new SlipMeter();
    public double lastTime = Double.NaN;

    public MotionRuntime(long seed) {
        this.controller = new LocomotionController(seed);
    }
}
