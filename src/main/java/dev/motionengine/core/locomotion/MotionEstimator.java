package dev.motionengine.core.locomotion;

import dev.motionengine.core.math.MathUtil;

/**
 * Derives kinematic quantities from successive {@link MotionSample}s: velocity (world and
 * body-local), acceleration, turn rate, air time and landing impact.
 *
 * <p>Animation is derived from replicated motion, so remote players animate correctly without any
 * extra network traffic: positions are already synchronised by vanilla.
 */
public final class MotionEstimator {
    private static final float TELEPORT_DISTANCE = 4f;

    private boolean initialised;
    private double lastX, lastY, lastZ;
    private float lastYawDeg;

    // Filtered outputs
    public float velX, velY, velZ;           // world, m/s
    public float localForward, localRight;   // body-local horizontal velocity, m/s
    public float speed;                      // horizontal speed, m/s
    public float accelForward, accelRight;   // body-local horizontal acceleration, m/s^2
    public float yawRate;                    // rad/s, + = turning right
    public float yawDelta;                   // rad this frame
    public float airTime, groundTime;
    public float lastImpactSpeed;            // m/s downward at last touchdown
    public boolean justLanded, justLeftGround, teleported;
    public float travelYawDeg;               // direction of horizontal travel (MC yaw convention)
    /** Exact (unfiltered) horizontal displacement this frame, in the current body frame, metres. */
    public float frameRight, frameForward;

    private float rawPrevForward, rawPrevRight;
    private boolean wasOnGround = true;
    private float minVelYInAir;

    public void reset() {
        initialised = false;
    }

    public void update(MotionSample s, float dt) {
        justLanded = false;
        justLeftGround = false;
        teleported = false;
        if (!initialised) {
            initialised = true;
            lastX = s.x; lastY = s.y; lastZ = s.z; lastYawDeg = s.bodyYawDeg;
            velX = velY = velZ = 0f;
            wasOnGround = s.onGround;
            return;
        }
        if (!(dt > 0f)) { yawDelta = 0f; frameRight = frameForward = 0f; return; }

        float dx = (float) (s.x - lastX), dy = (float) (s.y - lastY), dz = (float) (s.z - lastZ);
        if (dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE * TELEPORT_DISTANCE || dt > 1f) {
            teleported = true;
            lastX = s.x; lastY = s.y; lastZ = s.z; lastYawDeg = s.bodyYawDeg;
            velX = velY = velZ = 0f; speed = 0f; localForward = localRight = 0f;
            accelForward = accelRight = 0f; yawRate = 0f; yawDelta = 0f;
            frameRight = frameForward = 0f;
            return;
        }
        lastX = s.x; lastY = s.y; lastZ = s.z;

        // Vanilla moves remote entities in per-tick linear segments; a short filter removes the
        // resulting saw-tooth in velocity without adding noticeable latency (~50 ms half-life).
        velX = MathUtil.damp(velX, dx / dt, 0.045f, dt);
        velY = MathUtil.damp(velY, dy / dt, 0.03f, dt);
        velZ = MathUtil.damp(velZ, dz / dt, 0.045f, dt);
        speed = MathUtil.length(velX, velZ);

        float yawRad = s.bodyYawDeg * MathUtil.DEG;
        float fx = -(float) Math.sin(yawRad), fz = (float) Math.cos(yawRad);
        float rx = -fz, rz = fx; // right = (-cos, 0, -sin)
        frameForward = dx * fx + dz * fz;
        frameRight = dx * rx + dz * rz;
        float newForward = velX * fx + velZ * fz;
        float newRight = velX * rx + velZ * rz;

        float rawAccF = (newForward - rawPrevForward) / dt;
        float rawAccR = (newRight - rawPrevRight) / dt;
        rawPrevForward = newForward;
        rawPrevRight = newRight;
        accelForward = MathUtil.damp(accelForward, MathUtil.clamp(rawAccF, -40f, 40f), 0.08f, dt);
        accelRight = MathUtil.damp(accelRight, MathUtil.clamp(rawAccR, -40f, 40f), 0.08f, dt);
        localForward = newForward;
        localRight = newRight;

        yawDelta = MathUtil.wrapDegrees(s.bodyYawDeg - lastYawDeg) * MathUtil.DEG;
        lastYawDeg = s.bodyYawDeg;
        yawRate = MathUtil.damp(yawRate, yawDelta / dt, 0.06f, dt);

        if (speed > 0.05f) {
            travelYawDeg = (float) Math.toDegrees(Math.atan2(-velX, velZ));
        }

        if (s.onGround) {
            if (!wasOnGround) {
                justLanded = true;
                lastImpactSpeed = Math.max(0f, -minVelYInAir);
            }
            groundTime += dt;
            airTime = 0f;
        } else {
            if (wasOnGround) {
                justLeftGround = true;
                minVelYInAir = 0f;
            }
            airTime += dt;
            groundTime = 0f;
            minVelYInAir = Math.min(minVelYInAir, velY);
        }
        wasOnGround = s.onGround;
    }
}
