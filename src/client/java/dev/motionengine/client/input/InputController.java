package dev.motionengine.client.input;

import dev.motionengine.client.config.ConfigManager;
import dev.motionengine.core.config.MotionConfig;
import dev.motionengine.core.locomotion.InputSmoother;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;

/**
 * Local-player input shaping (client side only): walk modifier and physical inertia.
 * Called once per client tick from KeyboardInputMixin.
 */
public final class InputController {
    private static final float TICK = 0.05f;
    private static final InputSmoother SMOOTHER = new InputSmoother();
    private static boolean walkToggled;
    private static boolean walkHeld;

    private InputController() {}

    public static void onWalkKey(boolean down, boolean pressedThisTick) {
        walkHeld = down;
        if (pressedThisTick) walkToggled = !walkToggled;
    }

    public static boolean walking() {
        return ConfigManager.get().walkKeyToggles ? walkToggled : walkHeld;
    }

    public static Vec2 process(Vec2 raw) {
        MotionConfig cfg = ConfigManager.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!cfg.enabled || player == null) return raw;

        float x = raw.x, z = raw.y;
        boolean sprintKey = mc.options.keySprint.isDown();
        if (walking() && !sprintKey) {
            x *= cfg.walkSpeedFactor;
            z *= cfg.walkSpeedFactor;
        }

        boolean special = player.getAbilities().flying || player.isFallFlying() || player.isSwimming()
                || player.isInWater() || player.isPassenger() || player.onClimbable();
        if (special || cfg.physicalInertia <= 0f) {
            SMOOTHER.update(x, z, 0f, cfg.accelerationTime, cfg.decelerationTime, TICK);
        } else {
            SMOOTHER.update(x, z, cfg.physicalInertia, cfg.accelerationTime, cfg.decelerationTime, TICK);
        }
        if (SMOOTHER.outX == raw.x && SMOOTHER.outZ == raw.y) return raw;
        return new Vec2(SMOOTHER.outX, SMOOTHER.outZ);
    }
}
