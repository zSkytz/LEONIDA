package dev.motionengine.core.locomotion;

import dev.motionengine.core.math.MathUtil;

/**
 * Plans foot placement with world-locked contacts. This is the component that removes foot sliding:
 *
 * <ul>
 *   <li>The gait phase advances by <b>distance travelled / cycle length</b>, so cadence always
 *       matches real speed (quality criterion "a velocidade das passadas corresponde à velocidade
 *       real").</li>
 *   <li>A planted foot is integrated against the body's own motion and rotation, so it stays fixed
 *       in the world even while speed, gait parameters or direction change.</li>
 *   <li>A swinging foot travels from its lift-off point to a target predicted from the current
 *       velocity, gait and turn rate, landing exactly when the phase says it should.</li>
 *   <li>When the character stops, feet that ended far from a neutral stance take short settle
 *       steps one at a time instead of sliding into place.</li>
 * </ul>
 *
 * Local frame: x = right, z = forward, y = up, origin on the ground under the character.
 */
public final class FootPlanner {
    public static final int RIGHT = 0, LEFT = 1;

    public interface FootstepListener {
        void onFootstep(int foot, float intensity);
    }

    /** Returns ground height (m, relative to the character's ground) at a local position. */
    public interface LocalGround {
        float height(float localX, float localZ);
    }

    public static final class Foot {
        public final int index;
        public final float side; // +1 right, -1 left
        final float phaseOffset;
        public boolean planted = true;
        public boolean airborne;
        public float x, y, z;
        public float swing;
        public float groundY;
        float startX, startZ, startY;
        float targetX, targetZ, targetY;
        boolean settleStep;
        boolean freshTarget;

        /** Constraint weight for the pelvis: 1 planted, fades around lift-off and touchdown. */
        public float contactWeight() {
            if (airborne) return 0f;
            if (planted) return 1f;
            if (settleStep) return 1f - MathUtil.smoothstep(swing / 0.5f) * 0.6f;
            float in = MathUtil.smoothstep((swing - 0.55f) / 0.45f);
            float out = 1f - MathUtil.smoothstep(swing / 0.3f);
            return Math.max(in, out);
        }

        /** Ground height under the foot ignoring swing lift. */
        public float groundEstimate() {
            if (planted) return groundY;
            return MathUtil.lerp(startY, targetY, MathUtil.smootherstep(swing));
        }

        Foot(int index, float side, float phaseOffset) {
            this.index = index;
            this.side = side;
            this.phaseOffset = phaseOffset;
        }
    }

    private static final float MOVING_SPEED = 0.12f;
    private static final float MAX_SWING_TIME = 0.42f;
    private static final float SETTLE_SWING_TIME = 0.30f;
    private static final float SETTLE_DELAY = 0.12f;
    private static final float SETTLE_THRESHOLD = 0.07f;
    private static final float MAX_REACH = 0.62f;

    public final Foot[] feet = {new Foot(RIGHT, 1f, 0f), new Foot(LEFT, -1f, 0.5f)};
    public float phase;
    private float idleTime;
    private FootstepListener listener;
    private LocalGround ground;

    public void setListener(FootstepListener listener) { this.listener = listener; }
    public void setGround(LocalGround ground) { this.ground = ground; }

    public void reset(GaitParams g) {
        phase = 0f;
        idleTime = 0f;
        for (Foot f : feet) {
            f.planted = true;
            f.airborne = false;
            f.swing = 0f;
            f.x = f.side * g.stepWidth;
            f.z = 0f;
            f.y = f.groundY = groundAt(f.x, f.z);
        }
    }

    private float groundAt(float x, float z) {
        return ground == null ? 0f : ground.height(x, z);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static boolean crossed(float prev, float now, float mark) {
        if (now >= prev) return prev < mark && now >= mark;
        return prev < mark || now >= mark; // wrapped
    }

    /**
     * @param airborne true while the character is off the ground.
     */
    public void update(float dt, MotionEstimator m, GaitParams g, boolean airborne) {
        if (!(dt > 0f)) return;
        float speed = m.speed;
        boolean moving = speed > MOVING_SPEED && !airborne;

        // 1. World-lock planted feet: local_new = Rot(yawDelta) * local_old - displacement(new frame).
        //    Uses the exact frame displacement (not filtered velocity), so a planted foot is fixed in
        //    the world to floating-point precision, for local and remote players alike.
        float a = m.yawDelta, ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        for (Foot f : feet) {
            if (!f.planted) continue;
            float px = f.x * ca - f.z * sa;
            float pz = f.x * sa + f.z * ca;
            f.x = px - m.frameRight;
            f.z = pz - m.frameForward;
        }

        // 2. Airborne: feet leave the ground; the air layer drives their pose.
        if (airborne) {
            for (Foot f : feet) {
                f.planted = false;
                f.airborne = true;
                f.settleStep = false;
            }
            idleTime = 0f;
            return;
        }
        boolean landing = feet[0].airborne || feet[1].airborne;
        if (landing) land(g);

        // 3. Advance phase by distance travelled.
        float prevPhase = phase;
        float dPhase = moving ? speed * dt / Math.max(0.3f, g.cycleLength) : 0f;
        phase = frac(phase + dPhase);
        float duty = MathUtil.clamp(g.dutyFactor, 0.2f, 0.85f);

        idleTime = moving ? 0f : idleTime + dt;

        float invSpeed = speed > 1e-3f ? 1f / speed : 0f;
        float dirX = m.localRight * invSpeed, dirZ = m.localForward * invSpeed;
        float halfStance = g.stanceLength() * 0.5f;

        for (Foot f : feet) {
            float fiPrev = frac(prevPhase + f.phaseOffset);
            float fi = frac(phase + f.phaseOffset);
            Foot other = feet[1 - f.index];

            if (f.planted) {
                boolean lift = false;
                if (moving) {
                    if (crossed(fiPrev, fi, duty)) lift = true;
                    float ex = f.x - f.side * g.stepWidth, ez = f.z;
                    if (!lift && other.planted && ex * ex + ez * ez > MAX_REACH * MAX_REACH) lift = true;
                }
                if (lift) beginSwing(f, false);
            } else {
                float dp;
                if (f.settleStep) {
                    dp = dt / SETTLE_SWING_TIME;
                } else {
                    float byPhase = moving ? dPhase / Math.max(0.15f, 1f - duty) : 0f;
                    dp = Math.max(byPhase, dt / MAX_SWING_TIME);
                }
                f.swing = Math.min(1f, f.swing + dp);

                // Predict landing point (smoothed so that changes of plan never teleport the foot).
                float newTX, newTZ;
                if (moving && !f.settleStep) {
                    float remaining = (1f - f.swing) * (1f - duty) * g.cycleLength * invSpeed;
                    float turn = -m.yawRate * remaining * 0.5f;
                    float bx = f.side * g.stepWidth + dirX * halfStance;
                    float bz = dirZ * halfStance;
                    float ct = (float) Math.cos(turn), st = (float) Math.sin(turn);
                    newTX = bx * ct - bz * st;
                    newTZ = bx * st + bz * ct;
                } else {
                    newTX = f.side * g.stepWidth;
                    newTZ = 0f;
                }
                if (f.freshTarget) {
                    f.targetX = newTX;
                    f.targetZ = newTZ;
                    f.freshTarget = false;
                } else {
                    // Converges faster as the foot nears touchdown so it still lands on target.
                    float hl = MathUtil.lerp(0.06f, 0.015f, f.swing);
                    f.targetX = MathUtil.damp(f.targetX, newTX, hl, dt);
                    f.targetZ = MathUtil.damp(f.targetZ, newTZ, hl, dt);
                }
                f.targetY = groundAt(f.targetX, f.targetZ);

                // Cubic Hermite with ground-speed matching: the foot leaves and meets the ground
                // moving backward at the body's speed (foot retraction), so there is no velocity
                // discontinuity at lift-off or touchdown.
                float s = f.swing;
                float swingTime = (moving && !f.settleStep)
                        ? MathUtil.clamp((1f - duty) * g.cycleLength * invSpeed, 0.12f, MAX_SWING_TIME)
                        : 0f;
                float mx = -m.localRight * swingTime, mz = -m.localForward * swingTime;
                float s2 = s * s, s3 = s2 * s;
                float h00 = 2f * s3 - 3f * s2 + 1f, h10 = s3 - 2f * s2 + s;
                float h01 = -2f * s3 + 3f * s2, h11 = s3 - s2;
                f.x = h00 * f.startX + h10 * mx + h01 * f.targetX + h11 * mx;
                f.z = h00 * f.startZ + h10 * mz + h01 * f.targetZ + h11 * mz;
                float sv = MathUtil.smootherstep(f.swing);
                float lift = f.settleStep ? 0.05f : g.footLift;
                // Bump 16·s²(1−s)²: peak 1 at mid-swing, zero vertical velocity at both contacts.
                float bump = 16f * s2 * (1f - s) * (1f - s);
                f.y = MathUtil.lerp(f.startY, f.targetY, sv) + lift * bump;

                if (f.swing >= 1f) plant(f, speed);
            }
        }

        // 4. Settle steps when idle: one foot at a time, worst first.
        if (!moving && idleTime > SETTLE_DELAY && feet[0].planted && feet[1].planted) {
            Foot worst = null;
            float worstErr = SETTLE_THRESHOLD;
            for (Foot f : feet) {
                float ex = f.x - f.side * g.stepWidth, ez = f.z;
                float err = MathUtil.length(ex, ez);
                if (err > worstErr) { worstErr = err; worst = f; }
            }
            if (worst != null) beginSwing(worst, true);
        }

        // 5. Planted feet follow the terrain under them.
        for (Foot f : feet) {
            if (f.planted) {
                f.groundY = groundAt(f.x, f.z);
                f.y = f.groundY;
            }
        }
    }

    private void beginSwing(Foot f, boolean settle) {
        f.planted = false;
        f.settleStep = settle;
        f.swing = 0f;
        f.startX = f.x;
        f.startZ = f.z;
        f.startY = f.y;
        f.freshTarget = true;
    }

    private void plant(Foot f, float speed) {
        f.planted = true;
        f.settleStep = false;
        f.swing = 0f;
        f.x = f.targetX;
        f.z = f.targetZ;
        f.groundY = f.targetY;
        f.y = f.groundY;
        if (listener != null) listener.onFootstep(f.index, MathUtil.clamp01(0.25f + speed / 7f));
    }

    /** Touchdown after a jump/fall: plant both feet where they are and resync the phase. */
    private void land(GaitParams g) {
        Foot lead = feet[0].z >= feet[1].z ? feet[0] : feet[1];
        for (Foot f : feet) {
            f.airborne = false;
            f.planted = true;
            f.swing = 0f;
            f.groundY = groundAt(f.x, f.z);
            f.y = f.groundY;
        }
        // Leading foot has just made contact.
        phase = frac(0.01f - lead.phaseOffset);
        if (listener != null) listener.onFootstep(lead.index, 1f);
    }

    /** Sets a foot's local position directly (used by the air layer before landing). */
    public void setAirPosition(int foot, float x, float y, float z) {
        Foot f = feet[foot];
        f.x = x;
        f.y = y;
        f.z = z;
    }
}
