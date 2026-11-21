package dev.motionengine.core.locomotion;

/**
 * Engine-agnostic snapshot of one character for one rendered frame. Filled by the Minecraft bridge
 * (or by tests). Reused every frame — no allocation.
 *
 * <p>World conventions follow Minecraft: Y up, yaw in degrees where 0 faces +Z and yaw grows when
 * turning right (clockwise seen from above). Forward = (-sin yaw, 0, cos yaw).
 */
public final class MotionSample {
    /** Continuous time in seconds (entity ticks + partial tick, / 20). */
    public double time;
    public double x, y, z;
    public float bodyYawDeg;
    /** Head yaw relative to body, degrees (what vanilla passes to the model). */
    public float headYawDeg;
    public float headPitchDeg;

    public boolean onGround = true;
    public boolean sprinting;
    public boolean crouching;
    /** States we do not animate yet: hand control back to vanilla (smoothly). */
    public boolean swimming, fallFlying, passenger, sleeping, inLiquid, climbing, autoSpin;
    /** True while vanilla wants the arms (attack swing, using an item, special arm poses). */
    public boolean armsBusy;
    /** Model ground offset applied by the renderer (vanilla lowers crouching players). */
    public float renderOffsetY;

    public boolean vanillaOnlyState() {
        return swimming || fallFlying || passenger || sleeping || autoSpin || climbing;
    }
}
