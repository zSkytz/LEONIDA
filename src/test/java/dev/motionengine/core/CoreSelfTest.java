package dev.motionengine.core;

import dev.motionengine.core.config.MotionConfig;
import dev.motionengine.core.diagnostics.SlipMeter;
import dev.motionengine.core.graph.LayerWeights;
import dev.motionengine.core.locomotion.FootPlanner;
import dev.motionengine.core.locomotion.GaitBlendSpace;
import dev.motionengine.core.locomotion.GaitParams;
import dev.motionengine.core.locomotion.InputSmoother;
import dev.motionengine.core.locomotion.LocomotionController;
import dev.motionengine.core.locomotion.LocomotionState;
import dev.motionengine.core.locomotion.MotionSample;
import dev.motionengine.core.math.Euler;
import dev.motionengine.core.math.MathUtil;
import dev.motionengine.core.math.Spring;
import dev.motionengine.core.rig.RigidRigSolver;
import dev.motionengine.core.rig.SkeletonPose;
import dev.motionengine.core.rig.TwoBoneIK;
import dev.motionengine.core.rig.VanillaRigPose;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/**
 * Dependency-free test suite for the animation core (runs with plain {@code java}, or via
 * {@code ./gradlew coreTest}). Each test prints its measured metric so results are reviewable.
 */
public final class CoreSelfTest {

    private static int passed, failed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        run("spring converges & is frame-rate independent", CoreSelfTest::springTest);
        run("two-bone IK reaches targets", CoreSelfTest::ikTest);
        run("euler ZYX matches rotation composition", CoreSelfTest::eulerTest);
        run("no foot sliding at constant speed (20/60/144 fps)", CoreSelfTest::slipConstantSpeed);
        run("no foot sliding while accelerating, braking and turning", CoreSelfTest::slipVariable);
        run("cadence matches speed (steps per metre)", CoreSelfTest::cadenceTest);
        run("state graph: idle→start→move→stop→idle", CoreSelfTest::stateFlowTest);
        run("transitions are continuous (no pops)", CoreSelfTest::continuityTest);
        run("jump → airborne → land", CoreSelfTest::jumpTest);
        run("rigid rig keeps planted feet on the ground", CoreSelfTest::rigidContactTest);
        run("foot IK: pelvis drops to reach lower ground", CoreSelfTest::groundIkTest);
        run("settle steps after stopping", CoreSelfTest::settleTest);
        run("input smoother: accel/decel times, no overspeed", CoreSelfTest::inputSmootherTest);
        run("fuzz: random inputs never produce NaN", CoreSelfTest::fuzzTest);
        run("config sanitize clamps hostile values", CoreSelfTest::configTest);

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        for (String f : failures) System.out.println("  FAILED: " + f);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------------------------------
    interface TestBody { void run() throws Exception; }

    private static void run(String name, TestBody body) {
        try {
            body.run();
            passed++;
            System.out.println("[PASS] " + name);
        } catch (Throwable t) {
            failed++;
            failures.add(name + " :: " + t.getMessage());
            System.out.println("[FAIL] " + name + " :: " + t);
        }
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void info(String fmt, Object... args) {
        System.out.println("       " + String.format(fmt, args));
    }

    // ------------------------------------------------------------------------------------------
    /** Simple kinematic character used to drive the controller in tests. */
    static final class Sim {
        final MotionSample s = new MotionSample();
        final LocomotionController c;
        final MotionConfig cfg = new MotionConfig();
        final SlipMeter slip = new SlipMeter();
        double t;
        float yawDeg;

        Sim(long seed) {
            c = new LocomotionController(seed);
            s.onGround = true;
        }

        /** Moves along the current yaw at the given speed for one frame. */
        VanillaRigPose step(float dt, float speed, float yawRateDegPerSec) {
            yawDeg += yawRateDegPerSec * dt;
            float yaw = yawDeg * MathUtil.DEG;
            s.x += -Math.sin(yaw) * speed * dt;
            s.z += Math.cos(yaw) * speed * dt;
            t += dt;
            s.time = t;
            s.bodyYawDeg = yawDeg;
            VanillaRigPose p = c.updateVanilla(s, dt, cfg, LocomotionController.Detail.FULL);
            slip.sample(c.planner, s.x, s.z, yawDeg);
            return p;
        }
    }

    // ------------------------------------------------------------------------------------------
    static void springTest() {
        float[] fps = {20, 60, 240};
        float[] finals = new float[3];
        for (int k = 0; k < 3; k++) {
            Spring sp = new Spring(2f, 0.7f);
            float dt = 1f / fps[k];
            float maxV = 0;
            for (float t = 0; t < 0.4f; t += dt) maxV = Math.max(maxV, sp.update(1f, dt));
            finals[k] = sp.value;
            for (float t = 0; t < 3f; t += dt) sp.update(1f, dt);
            check(Math.abs(sp.value - 1f) < 1e-3f, "did not converge: " + sp.value);
        }
        float spread = Math.max(Math.abs(finals[0] - finals[1]), Math.abs(finals[1] - finals[2]));
        info("value @0.4s for 20/60/240 fps: %.4f %.4f %.4f (spread %.4f)", finals[0], finals[1], finals[2], spread);
        check(spread < 0.03f, "frame-rate dependence too high: " + spread);
    }

    static void ikTest() {
        TwoBoneIK ik = new TwoBoneIK();
        float[] out = new float[2];
        Random r = new Random(1);
        float worst = 0;
        for (int i = 0; i < 2000; i++) {
            float fwd = (r.nextFloat() - 0.5f) * 1.2f;
            float down = 0.15f + r.nextFloat() * 0.6f;
            if (MathUtil.length(fwd, down) > 0.74f) continue;
            boolean knee = r.nextBoolean();
            ik.solve(0.375f, 0.375f, fwd, down, knee);
            TwoBoneIK.forward(0.375f, 0.375f, ik.rootAngle, ik.bend, knee, out);
            float err = MathUtil.length(out[0] - fwd, out[1] - down);
            worst = Math.max(worst, err);
            check(ik.bend >= 0f, "negative bend");
        }
        info("max end-effector error: %.6f m", worst);
        check(worst < 1e-4f, "IK error " + worst);
    }

    static void eulerTest() {
        float[] o = new float[3];
        // Pure X rotation of a downward vector (model space y-down) by +90°: (0,1,0) → (0,0,1)
        Euler.rotateZYX(MathUtil.PI / 2, 0, 0, 0, 1, 0, o);
        check(Math.abs(o[2] - 1f) < 1e-5f && Math.abs(o[1]) < 1e-5f, "X rotation wrong");
        // Z after Y after X: compare with manual composition for random angles
        Random r = new Random(7);
        for (int i = 0; i < 100; i++) {
            float ax = r.nextFloat() * 2 - 1, ay = r.nextFloat() * 2 - 1, az = r.nextFloat() * 2 - 1;
            float[] a = new float[3], b = new float[3], c = new float[3];
            Euler.rotateZYX(ax, 0, 0, 0.3f, 0.5f, -0.2f, a);
            Euler.rotateZYX(0, ay, 0, a[0], a[1], a[2], b);
            Euler.rotateZYX(0, 0, az, b[0], b[1], b[2], c);
            Euler.rotateZYX(ax, ay, az, 0.3f, 0.5f, -0.2f, o);
            for (int k = 0; k < 3; k++) check(Math.abs(o[k] - c[k]) < 1e-5f, "composition mismatch");
        }
    }

    static void slipConstantSpeed() {
        float[] speeds = {1.5f, 4.317f, 5.612f};
        float[] fpsList = {20, 60, 144};
        for (float v : speeds) {
            for (float fps : fpsList) {
                Sim sim = new Sim(42);
                float dt = 1f / fps;
                for (int i = 0; i < fps * 8; i++) sim.step(dt, v, 0);
                info("v=%.2f fps=%3.0f contacts=%3d maxSlip=%.5f m", v, fps, sim.slip.contacts, sim.slip.maxSlip);
                check(sim.slip.contacts > 6, "too few contacts");
                check(sim.slip.maxSlip < 0.002, "foot slid " + sim.slip.maxSlip + " m at v=" + v + " fps=" + fps);
            }
        }
    }

    static void slipVariable() {
        Sim sim = new Sim(3);
        float dt = 1f / 60f;
        float speed = 0f;
        double t = 0;
        for (int i = 0; i < 60 * 20; i++) {
            t += dt;
            float target = (t % 6) < 3 ? 5.6f : ((t % 6) < 4.5 ? 1.5f : 0f);
            speed = MathUtil.approach(speed, target, dt * 9f);
            float yawRate = (t % 4) < 2 ? 90f : -60f;
            sim.step(dt, speed, speed > 0.5f ? yawRate : 0f);
        }
        info("contacts=%d maxSlip=%.5f m", sim.slip.contacts, sim.slip.maxSlip);
        check(sim.slip.maxSlip < 0.005, "slip under acceleration/turning: " + sim.slip.maxSlip);
    }

    static void cadenceTest() {
        GaitBlendSpace space = GaitBlendSpace.createDefault();
        GaitParams g = new GaitParams();
        for (float v : new float[]{1.5f, 4.317f, 5.612f}) {
            space.sample(v, 0f, g);
            Sim sim = new Sim(9);
            int[] steps = {0};
            sim.c.planner.setListener((foot, intensity) -> steps[0]++);
            float dt = 1f / 60f;
            for (int i = 0; i < 60; i++) sim.step(dt, v, 0); // warm-up
            steps[0] = 0;
            double z0 = sim.s.z;
            for (int i = 0; i < 60 * 10; i++) sim.step(dt, v, 0);
            double dist = sim.s.z - z0;
            double expected = dist * 2.0 / g.cycleLength;
            info("v=%.2f dist=%.1fm steps=%d expected≈%.1f cadence=%.2f steps/s", v, dist, steps[0], expected, steps[0] / 10.0);
            check(Math.abs(steps[0] - expected) <= 2.5, "cadence mismatch at v=" + v);
        }
    }

    static void stateFlowTest() {
        Sim sim = new Sim(5);
        float dt = 1f / 60f;
        List<LocomotionState> seen = new ArrayList<>();
        float speed = 0;
        for (int i = 0; i < 60 * 6; i++) {
            double t = i * dt;
            float target = t < 0.5 ? 0 : (t < 3 ? 4.317f : 0);
            float rate = target > speed ? 12f : 18f;
            speed = MathUtil.approach(speed, target, dt * rate);
            sim.step(dt, speed, 0);
            LocomotionState st = sim.c.state();
            if (seen.isEmpty() || seen.get(seen.size() - 1) != st) seen.add(st);
        }
        info("sequence: %s", seen);
        check(seen.contains(LocomotionState.START), "no START");
        check(seen.contains(LocomotionState.MOVE), "no MOVE");
        check(seen.contains(LocomotionState.STOP), "no STOP (braking)");
        check(seen.get(seen.size() - 1) == LocomotionState.IDLE, "did not return to IDLE");
    }

    static void continuityTest() {
        Sim sim = new Sim(11);
        float dt = 1f / 144f;
        float speed = 0;
        VanillaRigPose prev = new VanillaRigPose();
        float[] prevW = new float[4];
        int frame = 0;
        float maxVel = 0, maxAcc = 0, maxTorsoAcc = 0, maxRootAcc = 0;
        for (int i = 0; i < 144 * 12; i++) {
            double t = i * dt;
            // Hard input changes: sprint, abrupt stop, sharp turns, crouch toggles.
            float target = (t % 4) < 2 ? 5.6f : 0f;
            speed = MathUtil.approach(speed, target, dt * 20f);
            sim.s.crouching = (t % 5) > 4;
            VanillaRigPose p = sim.step(dt, speed, (t % 3) < 1.5 ? 200f : -200f);
            if (frame > 0) {
                float[] w = {
                    (p.legPitch[0] - prev.legPitch[0]) / dt, (p.legPitch[1] - prev.legPitch[1]) / dt,
                    (p.torsoPitch - prev.torsoPitch) / dt, (p.rootY - prev.rootY) / dt};
                for (int k = 0; k < 2; k++) maxVel = Math.max(maxVel, Math.abs(w[k]));
                if (frame > 1) {
                    for (int k = 0; k < 2; k++) maxAcc = Math.max(maxAcc, Math.abs(w[k] - prevW[k]) / dt);
                    maxTorsoAcc = Math.max(maxTorsoAcc, Math.abs(w[2] - prevW[2]) / dt);
                    maxRootAcc = Math.max(maxRootAcc, Math.abs(w[3] - prevW[3]) / dt);
                }
                prevW = w;
            }
            prev.copyFrom(p);
            frame++;
        }
        info("legs: peak %.1f rad/s, peak accel %.0f rad/s²; torso accel %.0f rad/s²; root accel %.1f m/s²",
                maxVel, maxAcc, maxTorsoAcc, maxRootAcc);
        // A pop = a one-frame jump. At 144 fps a 0.07 rad jump alone is ~1450 rad/s² of acceleration;
        // sprint touchdown (foot retraction meeting the ground) measures ~900 rad/s².
        check(maxVel < 25f, "leg too fast: " + maxVel);
        check(maxAcc < 1450f, "leg pop (accel " + maxAcc + ")");
        check(maxTorsoAcc < 250f, "torso pop (accel " + maxTorsoAcc + ")");
        // Root: a 7 mm one-frame jump (≈1/9 of a skin pixel) would read ~150 m/s².
        check(maxRootAcc < 150f, "root pop (accel " + maxRootAcc + ")");
    }

    static void jumpTest() {
        Sim sim = new Sim(13);
        float dt = 1f / 60f;
        for (int i = 0; i < 60; i++) sim.step(dt, 4.3f, 0);
        // Ballistic jump: vy0 = 8.4 m/s ≈ vanilla jump (0.42 blocks/tick)
        float vy = 8.4f;
        boolean sawAir = false;
        while (true) {
            vy -= 32f * dt; // Minecraft-like gravity (0.08 b/t² ≈ 32 m/s²)
            sim.s.y += vy * dt;
            if (sim.s.y <= 0) { sim.s.y = 0; sim.s.onGround = true; } else sim.s.onGround = false;
            sim.step(dt, 4.3f, 0);
            if (sim.c.state() == LocomotionState.AIRBORNE) sawAir = true;
            if (sim.s.onGround && sawAir) break;
        }
        sim.step(dt, 4.3f, 0);
        LocomotionState after = sim.c.state();
        info("after touchdown: %s, impact %.1f m/s", after, sim.c.est.lastImpactSpeed);
        check(sawAir, "never AIRBORNE");
        check(after == LocomotionState.LAND, "expected LAND, got " + after);
        for (int i = 0; i < 60; i++) sim.step(dt, 4.3f, 0);
        check(sim.c.state() == LocomotionState.MOVE, "expected MOVE after landing, got " + sim.c.state());
    }

    static void rigidContactTest() {
        Sim sim = new Sim(17);
        sim.cfg.rigidBobScale = 1f; // full geometric bob → planted feet exactly on the ground
        float dt = 1f / 120f;
        float worstFloat = 0, sumErr = 0;
        int n = 0;
        for (int i = 0; i < 120 * 6; i++) {
            VanillaRigPose p = sim.step(dt, 1.5f, 0);
            if (i < 120) continue;
            SkeletonPose sp = sim.c.pose();
            for (int k = 0; k < 2; k++) {
                if (!sp.footPlanted[k]) continue;
                // End of a rigid leg = hip height - L*cos(pitch)*cos(roll)
                float footY = p.hipHeight - RigidRigSolver.LEG * (float) Math.cos(p.legPitch[k]) * (float) Math.cos(p.legRoll[k]);
                float err = footY - sp.footY[k];
                worstFloat = Math.max(worstFloat, err);
                sumErr += Math.abs(err);
                n++;
            }
        }
        float mean = sumErr / n;
        info("rigid legs, full bob: mean |error| %.4f m, worst float %.4f m", mean, worstFloat);
        // Penetration happens briefly at support exchange (hip spring); floating must not.
        // Knee-less legs cannot be exact during double support; the bound is ~1/4 of a skin pixel.
        check(worstFloat < 0.015f, "feet float: " + worstFloat);
        check(mean < 0.03f, "mean contact error: " + mean);
    }

    static void groundIkTest() {
        Sim sim = new Sim(19);
        // Ground 0.3 m lower everywhere to the right of the character (e.g. a slab edge).
        sim.c.setGroundProbe((x, y, z, up, down) -> x < -0.05 ? -0.3f : 0f); // right side is -X when facing +Z
        float dt = 1f / 60f;
        VanillaRigPose p = null;
        for (int i = 0; i < 120; i++) p = sim.step(dt, 0, 0);
        info("standing on edge: rootY=%.3f m, right foot y=%.3f, left foot y=%.3f",
                p.rootY, sim.c.pose().footY[0], sim.c.pose().footY[1]);
        check(sim.c.pose().footY[0] < -0.25f, "right foot did not find lower ground");
        check(p.rootY < -0.15f, "pelvis did not drop");
        check(p.rootY >= -sim.cfg.maxPelvisDrop - 0.01f, "pelvis drop exceeded limit");
    }

    static void settleTest() {
        Sim sim = new Sim(23);
        float dt = 1f / 60f;
        float speed = 4.3f;
        for (int i = 0; i < 90; i++) sim.step(dt, speed, 0);
        int[] steps = {0};
        sim.c.planner.setListener((f, k) -> steps[0]++);
        for (int i = 0; i < 90; i++) {
            speed = MathUtil.approach(speed, 0, dt * 25f);
            sim.step(dt, speed, 0);
        }
        for (int i = 0; i < 120; i++) sim.step(dt, 0, 0);
        FootPlanner.Foot r = sim.c.planner.feet[0], l = sim.c.planner.feet[1];
        info("after stop: right(%.2f,%.2f) left(%.2f,%.2f) steps during stop=%d", r.x, r.z, l.x, l.z, steps[0]);
        check(Math.abs(r.z) < 0.08f && Math.abs(l.z) < 0.08f, "feet did not settle under the body");
        check(r.planted && l.planted, "feet not planted at rest");
        check(sim.slip.maxSlip < 0.005, "slid while stopping: " + sim.slip.maxSlip);
    }

    static void inputSmootherTest() {
        InputSmoother sm = new InputSmoother();
        float dt = 1f / 20f; // client ticks
        float t95 = -1;
        for (int i = 0; i < 40; i++) {
            sm.update(0, 1, 1f, 0.3f, 0.16f, dt);
            if (t95 < 0 && sm.outZ >= 0.95f) t95 = (i + 1) * dt;
            check(MathUtil.length(sm.outX, sm.outZ) <= 1.0001f, "overspeed");
        }
        float stop = -1;
        for (int i = 0; i < 40; i++) {
            sm.update(0, 0, 1f, 0.3f, 0.16f, dt);
            if (stop < 0 && sm.outZ <= 0.01f) stop = (i + 1) * dt;
        }
        info("reach 95%%: %.2fs, stop: %.2fs", t95, stop);
        check(t95 > 0.2f && t95 < 0.45f, "accel time off");
        check(stop > 0.05f && stop < 0.3f, "decel time off");
        sm.reset();
        sm.update(0.6f, 0.8f, 0f, 0.3f, 0.16f, dt);
        check(sm.outX == 0.6f && sm.outZ == 0.8f, "passthrough broken");
    }

    static void fuzzTest() {
        Random r = new Random(99);
        for (int run = 0; run < 20; run++) {
            Sim sim = new Sim(r.nextLong());
            sim.c.setGroundProbe((x, y, z, up, down) -> r.nextFloat() < 0.1f ? Float.NaN : (r.nextFloat() - 0.5f));
            for (int i = 0; i < 2000; i++) {
                float dt = switch (r.nextInt(6)) {
                    case 0 -> 0f;
                    case 1 -> 2f;
                    case 2 -> 1e-5f;
                    default -> r.nextFloat() * 0.1f;
                };
                if (r.nextInt(200) == 0) { sim.s.x += 100; } // teleport
                sim.s.onGround = r.nextInt(10) != 0;
                sim.s.crouching = r.nextInt(7) == 0;
                sim.s.swimming = r.nextInt(50) == 0;
                sim.s.armsBusy = r.nextBoolean();
                sim.s.headYawDeg = (r.nextFloat() - 0.5f) * 400f;
                sim.s.headPitchDeg = (r.nextFloat() - 0.5f) * 180f;
                VanillaRigPose p = sim.step(dt, r.nextFloat() * 12f, (r.nextFloat() - 0.5f) * 2000f);
                float sum = p.rootY + p.tiltPitch + p.tiltRoll + p.torsoPitch + p.headYaw + p.headPitch
                        + p.legPitch[0] + p.legPitch[1] + p.armPitch[0] + p.armPitch[1] + p.legRoll[0] + p.armRoll[1];
                check(MathUtil.isFinite(sum), "non-finite pose at run " + run + " frame " + i);
                check(p.weight >= 0 && p.weight <= 1, "weight out of range");
            }
        }
        info("20 runs × 2000 frames of hostile input: all finite");
    }

    static void configTest() {
        MotionConfig c = new MotionConfig();
        c.inertiaStrength = Float.NaN;
        c.lodMaxDistance = -5;
        c.maxPelvisDrop = 99;
        c.preset = null;
        c.sanitize();
        check(c.inertiaStrength == 0f, "NaN not clamped");
        check(c.lodMaxDistance >= c.lodFullDistance, "lod order");
        check(c.maxPelvisDrop <= 0.5f, "drop clamp");
        check(c.preset == MotionConfig.Preset.BALANCED, "preset default");
        for (MotionConfig.Preset p : MotionConfig.Preset.values()) new MotionConfig().applyPreset(p).sanitize();
        check(LayerWeights.COUNT == 8, "layer count");
    }

    @SuppressWarnings("unused")
    private static DoubleUnaryOperator unused;
}
