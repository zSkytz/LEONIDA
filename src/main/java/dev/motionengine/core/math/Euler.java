package dev.motionengine.core.math;

/**
 * Rotation helpers matching Minecraft's ModelPart convention: a part's rotation is
 * {@code Quaternionf.rotationZYX(zRot, yRot, xRot)}, i.e. the vector is rotated about X first,
 * then Y, then Z (R = Rz * Ry * Rx).
 */
public final class Euler {
    private Euler() {}

    /** Rotates (x,y,z) by R = Rz(zr) * Ry(yr) * Rx(xr) and writes into out[0..2]. */
    public static void rotateZYX(float xr, float yr, float zr, float x, float y, float z, float[] out) {
        // Rx
        float cx = (float) Math.cos(xr), sx = (float) Math.sin(xr);
        float y1 = y * cx - z * sx;
        float z1 = y * sx + z * cx;
        float x1 = x;
        // Ry
        float cy = (float) Math.cos(yr), sy = (float) Math.sin(yr);
        float x2 = x1 * cy + z1 * sy;
        float z2 = -x1 * sy + z1 * cy;
        float y2 = y1;
        // Rz
        float cz = (float) Math.cos(zr), sz = (float) Math.sin(zr);
        out[0] = x2 * cz - y2 * sz;
        out[1] = x2 * sz + y2 * cz;
        out[2] = z2;
    }
}
