package dev.motionengine.core.procedural;

/**
 * Queries the world for walkable ground. Implemented by the Minecraft bridge with block collision
 * shapes; implemented with height functions in tests.
 */
public interface GroundProbe {
    /**
     * Height of the highest walkable surface at (x, z) between {@code y - down} and {@code y + up},
     * returned relative to {@code y}. Returns {@link Float#NaN} if none is found.
     */
    float groundOffset(double x, double y, double z, float up, float down);
}
