package dev.motionengine.client.bridge;

import dev.motionengine.core.procedural.GroundProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Finds the walkable surface under a foot using block collision shapes (so stairs, slabs, snow
 * layers, carpets, etc. are handled per sub-box, not per block). Render-thread only; one instance
 * reused for all characters.
 */
public final class WorldGroundProbe implements GroundProbe {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private Level level;
    // Scratch for the box visitor (avoids capturing locals in a lambda).
    private double qx, qz, bestTop;

    public WorldGroundProbe bind(Level level) {
        this.level = level;
        return this;
    }

    @Override
    public float groundOffset(double x, double y, double z, float up, float down) {
        if (level == null) return Float.NaN;
        double top = y + up, bottom = y - down;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(top); by >= Mth.floor(bottom); by--) {
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            qx = x - bx;
            qz = z - bz;
            bestTop = Double.NaN;
            shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                if (qx >= x1 && qx <= x2 && qz >= z1 && qz <= z2) {
                    if (Double.isNaN(bestTop) || y2 > bestTop) bestTop = y2;
                }
            });
            if (Double.isNaN(bestTop)) continue;
            double surface = by + bestTop;
            if (surface > top + 1e-4) return Float.NaN; // foot would be inside a block: no valid ground
            if (surface >= bottom) return (float) (surface - y);
        }
        return Float.NaN;
    }
}
