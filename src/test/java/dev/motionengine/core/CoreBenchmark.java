package dev.motionengine.core;

import dev.motionengine.core.config.MotionConfig;
import dev.motionengine.core.locomotion.LocomotionController;
import dev.motionengine.core.locomotion.MotionSample;
import dev.motionengine.core.math.MathUtil;

/**
 * Measures the CPU cost of the animation core per character per frame (no rendering).
 * Run: {@code ./gradlew coreBenchmark}. Reports mean and p99 for FULL and REDUCED detail.
 */
public final class CoreBenchmark {

    public static void main(String[] args) {
        int characters = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int frames = args.length > 1 ? Integer.parseInt(args[1]) : 3000;
        for (LocomotionController.Detail d : LocomotionController.Detail.values()) {
            bench(characters, frames, d, true);  // warm-up (JIT)
            bench(characters, frames, d, false);
        }
    }

    private static void bench(int n, int frames, LocomotionController.Detail detail, boolean warmup) {
        MotionConfig cfg = new MotionConfig();
        LocomotionController[] cs = new LocomotionController[n];
        MotionSample[] ss = new MotionSample[n];
        float[] yaw = new float[n];
        for (int i = 0; i < n; i++) {
            cs[i] = new LocomotionController(i);
            cs[i].setGroundProbe((x, y, z, up, down) -> (float) (Math.sin(x * 0.7) * 0.1));
            ss[i] = new MotionSample();
            ss[i].onGround = true;
        }
        float dt = 1f / 60f;
        long[] frameNs = new long[frames];
        double t = 0;
        for (int f = 0; f < frames; f++) {
            t += dt;
            long start = System.nanoTime();
            for (int i = 0; i < n; i++) {
                MotionSample s = ss[i];
                float speed = (float) (2.8 + 2.8 * Math.sin(t * 0.5 + i));
                yaw[i] += (float) (Math.sin(t + i) * 120 * dt);
                float y = yaw[i] * MathUtil.DEG;
                s.x += -Math.sin(y) * speed * dt;
                s.z += Math.cos(y) * speed * dt;
                s.bodyYawDeg = yaw[i];
                s.time = t;
                cs[i].updateVanilla(s, dt, cfg, detail);
            }
            frameNs[f] = System.nanoTime() - start;
        }
        if (warmup) return;
        java.util.Arrays.sort(frameNs);
        double mean = 0;
        for (long v : frameNs) mean += v;
        mean /= frames;
        double perCharUs = mean / n / 1000.0;
        double p99Us = frameNs[(int) (frames * 0.99)] / (double) n / 1000.0;
        System.out.printf("%-8s %4d characters: %.2f µs/character/frame (p99 %.2f µs), %.3f ms/frame total%n",
                detail, n, perCharUs, p99Us, mean / 1e6);
    }
}
