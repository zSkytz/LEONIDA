package dev.motionengine.core.locomotion;

import dev.motionengine.core.config.MotionConfig;
import dev.motionengine.core.graph.LayerWeights;
import dev.motionengine.core.graph.StateMachine;
import dev.motionengine.core.math.MathUtil;
import dev.motionengine.core.math.SmoothNoise;
import dev.motionengine.core.math.Spring;
import dev.motionengine.core.procedural.BodyDynamics;
import dev.motionengine.core.procedural.GroundProbe;
import dev.motionengine.core.procedural.IdleLayer;
import dev.motionengine.core.rig.RigidRigSolver;
import dev.motionengine.core.rig.SkeletonPose;
import dev.motionengine.core.rig.VanillaRigPose;

/**
 * Per-character animation controller: the "animation graph" of the mod.
 *
 * <pre>
 *  MotionSample ─► MotionEstimator ─► StateMachine ─► LayerWeights (cross-faded)
 *                        │                                   │
 *                        ├─► GaitBlendSpace (speed × crouch) ─► FootPlanner (world-locked feet)
 *                        ├─► BodyDynamics (lean, brake, landing springs)
 *                        └─► IdleLayer (breathing, weight shift, head drift)
 *                                                            ▼
 *                                        SkeletonPose (rig independent)
 *                                                            ▼
 *                                  RigidRigSolver ─► VanillaRigPose (6-part model)
 * </pre>
 */
public final class LocomotionController {

    public enum Detail { FULL, REDUCED }

    private static final float AIR_GRACE = 0.08f;

    public final MotionEstimator est = new MotionEstimator();
    public final GaitParams gait = new GaitParams();
    public final FootPlanner planner = new FootPlanner();
    private final GaitBlendSpace blendSpace;
    private final BodyDynamics body = new BodyDynamics();
    private final IdleLayer idle;
    private final SmoothNoise noise;
    private final StateMachine<LocomotionState, LocomotionController> sm;
    private final LayerWeights weights = new LayerWeights();
    private final LayerWeights targets = new LayerWeights();
    private final SkeletonPose pose = new SkeletonPose();
    private final RigidRigSolver rigid = new RigidRigSolver();
    private final VanillaRigPose vanillaPose = new VanillaRigPose();

    private final Spring crouch = new Spring(3.5f, 1f);
    private final Spring stanceShift = new Spring(2.5f, 0.9f);
    private float armsWeight = 1f;
    private float exertion;
    private float time;
    private int leadFootInAir = FootPlanner.RIGHT;
    private boolean initialised;

    private MotionSample sample;
    private MotionConfig cfg = new MotionConfig();
    private GroundProbe probe;
    private float transitionScale = 1f;

    public LocomotionController(long seed) {
        this(seed, GaitBlendSpace.createDefault());
    }

    public LocomotionController(long seed, GaitBlendSpace space) {
        this.blendSpace = space;
        this.idle = new IdleLayer(seed);
        this.noise = new SmoothNoise(seed ^ 0x5DEECE66DL);
        this.sm = new StateMachine<>(LocomotionState.class, LocomotionState.IDLE);
        buildGraph();
        planner.setGround(this::localGround);
    }

    // ------------------------------------------------------------------------------------------
    // Graph definition. Conditions read the estimator; blend times are in seconds.
    // ------------------------------------------------------------------------------------------
    private void buildGraph() {
        sm.addAny("vanilla-only state", LocomotionState.VANILLA, c -> c.sample.vanillaOnlyState(), 0.25f);
        sm.addAny("left ground", LocomotionState.AIRBORNE,
                c -> !c.sample.vanillaOnlyState() && c.isAirborne() && c.sm.current() != LocomotionState.VANILLA, 0.12f);

        sm.add("resume (ground)", LocomotionState.VANILLA, LocomotionState.IDLE,
                c -> !c.sample.vanillaOnlyState() && c.sample.onGround, 0.1f, 0.3f);
        sm.add("resume (air)", LocomotionState.VANILLA, LocomotionState.AIRBORNE,
                c -> !c.sample.vanillaOnlyState() && !c.sample.onGround, 0.1f, 0.3f);

        sm.add("touchdown", LocomotionState.AIRBORNE, LocomotionState.LAND, c -> c.sample.onGround, 0f, 0.06f);
        sm.add("land→move", LocomotionState.LAND, LocomotionState.MOVE, c -> c.est.speed > 0.6f, 0.22f, 0.25f);
        sm.add("land→idle", LocomotionState.LAND, LocomotionState.IDLE, c -> true, 0.4f, 0.35f);

        sm.add("start", LocomotionState.IDLE, LocomotionState.START, c -> c.est.speed > 0.35f, 0f, 0.12f);
        sm.add("start→move", LocomotionState.START, LocomotionState.MOVE,
                c -> c.est.accelForward < 1.5f || c.sm.timeInState() > 0.45f, 0.18f, 0.3f);
        sm.add("start→idle", LocomotionState.START, LocomotionState.IDLE, c -> c.est.speed < 0.15f, 0.05f, 0.3f);

        sm.add("pivot", LocomotionState.MOVE, LocomotionState.PIVOT,
                c -> c.est.speed > 2.0f && Math.abs(c.est.yawRate) > 4.5f, 0.1f, 0.1f);
        sm.add("pivot→move", LocomotionState.PIVOT, LocomotionState.MOVE,
                c -> Math.abs(c.est.yawRate) < 2.5f, 0.25f, 0.25f);
        sm.add("brake", LocomotionState.MOVE, LocomotionState.STOP,
                c -> c.est.accelForward < -5f && c.est.speed < 3.6f, 0.1f, 0.1f);
        sm.add("move→idle", LocomotionState.MOVE, LocomotionState.IDLE, c -> c.est.speed < 0.2f, 0f, 0.35f);

        sm.add("stop→start", LocomotionState.STOP, LocomotionState.START, c -> c.est.accelForward > 3f, 0.1f, 0.15f);
        sm.add("stop→idle", LocomotionState.STOP, LocomotionState.IDLE,
                c -> c.est.speed < 0.15f && c.sm.timeInState() > 0.15f, 0f, 0.4f);
    }

    private boolean isAirborne() {
        return !sample.onGround && (est.airTime > AIR_GRACE || est.velY > 1.5f);
    }

    private void targetWeightsFor(LocomotionState s) {
        java.util.Arrays.fill(targets.w, 0f);
        if (s == LocomotionState.VANILLA) return;
        targets.set(LayerWeights.ACTIVE, 1f);
        switch (s) {
            case IDLE -> targets.set(LayerWeights.IDLE, 1f).set(LayerWeights.LOCOMOTION, 1f);
            case START -> targets.set(LayerWeights.START, 1f).set(LayerWeights.LOCOMOTION, 1f);
            case MOVE -> targets.set(LayerWeights.LOCOMOTION, 1f);
            case STOP -> targets.set(LayerWeights.BRAKE, 1f).set(LayerWeights.LOCOMOTION, 1f);
            case PIVOT -> targets.set(LayerWeights.PIVOT, 1f).set(LayerWeights.LOCOMOTION, 1f);
            case AIRBORNE -> targets.set(LayerWeights.AIR, 1f);
            case LAND -> targets.set(LayerWeights.LAND, 1f).set(LayerWeights.LOCOMOTION, 1f);
            default -> { }
        }
    }

    public void setGroundProbe(GroundProbe probe) {
        this.probe = probe;
    }

    private float localGround(float lx, float lz) {
        if (probe == null || sample == null || !cfg.footIkEnabled) return 0f;
        float yaw = sample.bodyYawDeg * MathUtil.DEG;
        float fx = -(float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float rx = -fz, rz = fx;
        double wx = sample.x + rx * lx + fx * lz;
        double wz = sample.z + rz * lx + fz * lz;
        float g = probe.groundOffset(wx, sample.y, wz, 0.3f, 0.6f);
        return Float.isNaN(g) ? 0f : MathUtil.clamp(g, -0.6f, 0.3f);
    }

    /** Main entry point: advance by dt seconds and produce the ideal skeleton pose. */
    public SkeletonPose update(MotionSample s, float dt, MotionConfig config, Detail detail) {
        this.sample = s;
        this.cfg = config;
        GroundProbe savedProbe = probe;
        if (detail != Detail.FULL) probe = null;
        try {
            step(s, dt, detail);
        } finally {
            probe = savedProbe;
        }
        return pose;
    }

    /** Convenience: update and solve for Minecraft's 6-part model. */
    public VanillaRigPose updateVanilla(MotionSample s, float dt, MotionConfig config, Detail detail) {
        update(s, dt, config, detail);
        rigid.solve(pose, config, Math.max(0f, Math.min(dt, 0.25f)), vanillaPose);
        return vanillaPose;
    }

    private void step(MotionSample s, float dt, Detail detail) {
        if (!initialised) {
            est.update(s, 0f);
            blendSpace.sample(0f, s.crouching ? 1f : 0f, gait);
            crouch.reset(s.crouching ? 1f : 0f);
            planner.reset(gait);
            LocomotionState init = s.vanillaOnlyState() ? LocomotionState.VANILLA
                    : (s.onGround ? LocomotionState.IDLE : LocomotionState.AIRBORNE);
            sm.force(init, 0f, "init");
            targetWeightsFor(init);
            weights.copyFrom(targets);
            initialised = true;
        }
        if (!(dt > 0f)) return;
        dt = Math.min(dt, 0.25f);
        time += dt;

        est.update(s, dt);
        if (est.teleported) {
            planner.reset(gait);
            body.reset();
            rigid.reset();
        }

        // Graph
        sm.update(this, dt);
        targetWeightsFor(sm.current());
        transitionScale = 1f / Math.max(0.25f, cfg.transitionSpeed);
        weights.moveToward(targets, sm.lastBlendTime() * transitionScale, dt);

        // Parameters
        float crouchW = crouch.update(s.crouching ? 1f : 0f, dt);
        blendSpace.sample(est.speed, crouchW, gait);
        boolean airborne = sm.current() == LocomotionState.AIRBORNE;
        planner.update(dt, est, gait, airborne);
        body.update(est, s.onGround, cfg.inertiaStrength, dt);
        exertion = MathUtil.approach(exertion, est.speed > 3.5f ? 1f : 0f, dt / (est.speed > 3.5f ? 10f : 20f));
        if (detail == Detail.FULL && cfg.proceduralEnabled) {
            idle.update(time, exertion, cfg.microAnimationFrequency);
        } else {
            idle.update(time, exertion, 0f);
        }
        armsWeight = MathUtil.approach(armsWeight, s.armsBusy ? 0f : 1f, dt / 0.12f);

        if (est.justLeftGround) {
            leadFootInAir = planner.feet[0].z >= planner.feet[1].z ? FootPlanner.RIGHT : FootPlanner.LEFT;
        }
        if (airborne) driveAirFeet(dt);

        compose(s, dt);
    }

    private void driveAirFeet(float dt) {
        float tuck = MathUtil.smoothstep(est.airTime / 0.22f);
        float fall = MathUtil.smoothstep((est.airTime - 0.45f) / 0.5f) * (est.velY < -4f ? 1f : 0f);
        for (FootPlanner.Foot f : planner.feet) {
            boolean lead = f.index == leadFootInAir;
            float tz = (lead ? 0.22f : -0.14f) * tuck;
            float ty = (lead ? 0.17f : 0.09f) * tuck;
            tz = MathUtil.lerp(tz, lead ? 0.07f : -0.05f, fall);
            ty = MathUtil.lerp(ty, 0.04f, fall);
            float tx = f.side * MathUtil.lerp(0.12f, 0.15f, fall);
            planner.setAirPosition(f.index,
                    MathUtil.damp(f.x, tx, 0.06f, dt),
                    MathUtil.damp(f.y, ty, 0.06f, dt),
                    MathUtil.damp(f.z, tz, 0.06f, dt));
        }
    }

    private void compose(MotionSample s, float dt) {
        float intensity = cfg.bodyMotionIntensity;
        float wIdle = weights.eased(LayerWeights.IDLE);
        float wLoco = weights.eased(LayerWeights.LOCOMOTION);
        float wStart = weights.eased(LayerWeights.START);
        float wBrake = weights.eased(LayerWeights.BRAKE);
        float wPivot = weights.eased(LayerWeights.PIVOT);
        float wAir = weights.eased(LayerWeights.AIR);
        float wLand = weights.eased(LayerWeights.LAND);
        float micro = cfg.proceduralEnabled ? 1f : 0f;

        pose.weight = weights.eased(LayerWeights.ACTIVE);
        pose.armsWeight = MathUtil.smoothstep(armsWeight);

        // Feet (renderer may lower the model, e.g. crouch: keep feet on the real ground).
        for (int i = 0; i < 2; i++) {
            FootPlanner.Foot f = planner.feet[i];
            pose.footX[i] = f.x;
            pose.footY[i] = f.y - s.renderOffsetY;
            pose.footZ[i] = f.z;
            pose.footPlanted[i] = f.planted;
            pose.footContact[i] = f.contactWeight();
            pose.footGround[i] = f.groundEstimate() - s.renderOffsetY;
        }
        FootPlanner.Foot r = planner.feet[FootPlanner.RIGHT], l = planner.feet[FootPlanner.LEFT];
        float halfStance = Math.max(0.08f, gait.stanceLength() * 0.5f);

        // Pelvis
        pose.pelvisYaw = -gait.pelvisYaw * intensity * (r.z - l.z) * wLoco;
        float shiftTarget = 0f;
        if (r.planted != l.planted) {
            float walkiness = MathUtil.clamp01((gait.dutyFactor - 0.45f) * 6f);
            shiftTarget = (r.planted ? 1f : -1f) * 0.022f * walkiness;
        }
        float shift = stanceShift.update(shiftTarget, dt);
        pose.pelvisShiftX = (shift * wLoco + idle.pelvisShiftX * wIdle * micro) * intensity;
        pose.pelvisShiftZ = 0f;
        pose.pelvisRoll = idle.pelvisRoll * wIdle * micro;
        pose.hipHeightRatio = gait.hipHeightRatio;
        pose.bodyDrop = body.drop * (wLand + wLoco * 0.5f) + 0.03f * wBrake;

        // Torso
        float lean = body.leanPitch;
        pose.tiltPitch = lean * 0.6f;
        pose.tiltRoll = body.leanRoll * 0.7f;
        pose.torsoPitch = gait.torsoLean * (wLoco + wAir * 0.5f)
                + lean * 0.4f
                + 0.10f * wStart
                - 0.10f * wBrake
                + body.drop * 2.2f
                + idle.torsoPitch * micro * (0.4f + 0.6f * wIdle);
        pose.torsoRoll = body.leanRoll * 0.3f + idle.pelvisRoll * -0.5f * wIdle * micro;
        pose.torsoYaw = -gait.counterTwist * pose.pelvisYaw;

        // Arms
        for (int i = 0; i < 2; i++) {
            float side = i == SkeletonPose.RIGHT ? 1f : -1f;
            FootPlanner.Foot opposite = planner.feet[1 - i];
            float swing = MathUtil.clamp(opposite.z / halfStance, -1.25f, 1.25f);
            float pitch = gait.armSwing * intensity * swing * wLoco
                    + 0.04f
                    + 0.12f * wStart * swing
                    + 0.30f * wBrake;
            float elbow = gait.elbowBend + 0.3f * wBrake + 0.25f * wStart;
            float roll = side * (gait.armOutward + idle.shoulderLift * micro + body.drop * 1.5f);

            if (wAir > 0f) {
                boolean leadArm = (1 - i) == leadFootInAir; // arm opposite to the lead leg goes forward
                float tuck = MathUtil.smoothstep(est.airTime / 0.22f);
                float fall = MathUtil.smoothstep((est.airTime - 0.45f) / 0.5f) * (est.velY < -4f ? 1f : 0f);
                float airPitch = (leadArm ? 0.55f : -0.25f) * tuck;
                float flail = noise.sample(time * 3.1f + i * 13f) * 0.35f;
                airPitch = MathUtil.lerp(airPitch, -0.2f + flail, fall);
                float airRoll = side * MathUtil.lerp(0.18f, 0.95f + flail * 0.3f, fall);
                float airElbow = MathUtil.lerp(0.9f, 0.35f, fall);
                pitch = MathUtil.lerp(pitch, airPitch, wAir);
                roll = MathUtil.lerp(roll, airRoll, wAir);
                elbow = MathUtil.lerp(elbow, airElbow, wAir);
            }
            pose.shoulderPitch[i] = pitch;
            pose.shoulderRoll[i] = roll;
            pose.shoulderYaw[i] = 0f;
            pose.elbowBend[i] = elbow;
        }

        // Head: camera look distributed along the spine, then compensated so gaze is preserved.
        float lookYaw = s.headYawDeg * MathUtil.DEG;
        float lookPitch = s.headPitchDeg * MathUtil.DEG;
        float spineShare = MathUtil.clamp(lookYaw * 0.22f, -0.32f, 0.32f);
        pose.torsoYaw += spineShare;
        float pivotLead = Math.signum(est.yawRate) * 0.22f * wPivot;
        pose.headYaw = MathUtil.clamp(lookYaw + pivotLead + idle.headYaw * wIdle * micro - pose.torsoYaw, -1.3f, 1.3f);
        pose.headPitch = MathUtil.clamp(lookPitch + idle.headPitch * wIdle * micro
                - (pose.torsoPitch + pose.tiltPitch) * 0.85f, -1.2f, 1.3f);
        pose.headRoll = -pose.tiltRoll * 0.5f;

        if (!pose.isFinite()) {
            // Never let a numerical problem reach the renderer.
            pose.clear();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------------------------------
    public LocomotionState state() { return sm.current(); }
    public LayerWeights weights() { return weights; }
    public SkeletonPose pose() { return pose; }
    public VanillaRigPose vanillaPose() { return vanillaPose; }
    public String lastTransition() { return sm.lastTransition(); }

    public String debugLine() {
        return String.format("%s | v=%.2fm/s fwd=%.2f acc=%.1f yawRate=%.2f | gait cycle=%.2fm duty=%.2f cad=%.2fHz phase=%.2f | %s",
                sm.current(), est.speed, est.localForward, est.accelForward, est.yawRate,
                gait.cycleLength, gait.dutyFactor, est.speed / Math.max(0.3f, gait.cycleLength), planner.phase, weights);
    }
}
