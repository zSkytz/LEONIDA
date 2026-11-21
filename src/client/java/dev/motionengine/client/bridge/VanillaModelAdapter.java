package dev.motionengine.client.bridge;

import dev.motionengine.core.math.Euler;
import dev.motionengine.core.rig.RigidRigSolver;
import dev.motionengine.core.rig.VanillaRigPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

/**
 * Writes a {@link VanillaRigPose} onto a HumanoidModel's parts.
 *
 * <p>Model space: 1 unit = 1 pixel = 1/16 m, +Y down, front = −Z, character right = −X. A part's
 * rotation is applied as Rz·Ry·Rx about its pivot (x, y, z). Rest pivots come from each part's
 * initial pose, so slim (Alex) arms and armor models work unchanged. Overlay layers (hat, jacket,
 * sleeves, pants) are children of these parts and follow automatically.
 */
public final class VanillaModelAdapter {
    private static final float PX = 16f;
    private static final float[] TMP = new float[3];

    private VanillaModelAdapter() {}

    public static void apply(HumanoidModel<?> m, VanillaRigPose p) {
        float w = p.weight;
        if (w <= 0.001f) return;
        float wa = w * p.armsWeight;

        // Torso rotation in model space.
        float tx = p.torsoPitch, ty = p.torsoYaw, tz = -p.torsoRoll;

        // Hip centre (bottom of the body cube), shifted by weight transfer.
        float hipCx = -p.pelvisShiftX * PX, hipCy = 12f, hipCz = -p.pelvisShiftZ * PX;

        // Neck = hip centre − R·(0, 12, 0)
        Euler.rotateZYX(tx, ty, tz, 0f, 12f, 0f, TMP);
        float neckX = hipCx - TMP[0], neckY = hipCy - TMP[1], neckZ = hipCz - TMP[2];

        blend(m.body, neckX, neckY, neckZ, tx, ty, tz, w);
        blend(m.head, neckX, neckY, neckZ,
                tx + p.headPitch, ty + p.headYaw, tz - p.headRoll, w);

        // Arms hang from the shoulders, which ride on the torso.
        for (int i = 0; i < 2; i++) {
            ModelPart arm = i == 0 ? m.rightArm : m.leftArm;
            PartPose rest = arm.getInitialPose();
            Euler.rotateZYX(tx, ty, tz, rest.x(), rest.y(), rest.z(), TMP);
            blend(arm, neckX + TMP[0], neckY + TMP[1], neckZ + TMP[2],
                    tx - p.armPitch[i], ty + p.armYaw[i], tz + p.armRoll[i], wa);
        }

        // Legs pivot at the hips; pelvis yaw swings the hip joints around the centre.
        float cy = (float) Math.cos(p.pelvisYaw), sy = (float) Math.sin(p.pelvisYaw);
        for (int i = 0; i < 2; i++) {
            ModelPart leg = i == 0 ? m.rightLeg : m.leftLeg;
            float side = i == 0 ? 1f : -1f;
            float hx = side * RigidRigSolver.HIP_HALF_WIDTH * cy + p.pelvisShiftX;
            float hz = -side * RigidRigSolver.HIP_HALF_WIDTH * sy + p.pelvisShiftZ;
            blend(leg, -hx * PX, leg.getInitialPose().y(), -hz * PX,
                    -p.legPitch[i], p.legYaw[i], p.legRoll[i], w);
        }
    }

    private static void blend(ModelPart part, float x, float y, float z, float xr, float yr, float zr, float w) {
        if (w >= 0.999f) {
            part.x = x; part.y = y; part.z = z;
            part.xRot = xr; part.yRot = yr; part.zRot = zr;
            return;
        }
        part.x += (x - part.x) * w;
        part.y += (y - part.y) * w;
        part.z += (z - part.z) * w;
        part.xRot += (xr - part.xRot) * w;
        part.yRot += (yr - part.yRot) * w;
        part.zRot += (zr - part.zRot) * w;
    }
}
