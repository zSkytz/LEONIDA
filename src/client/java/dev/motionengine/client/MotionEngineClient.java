package dev.motionengine.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.motionengine.client.command.MotionCommands;
import dev.motionengine.client.config.ConfigManager;
import dev.motionengine.client.input.InputController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class MotionEngineClient implements ClientModInitializer {
    public static final String MOD_ID = "motionengine";

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static KeyMapping walkKey;
    private int debugTicks;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        walkKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.motionengine.walk", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));
        MotionCommands.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean pressed = false;
            while (walkKey.consumeClick()) pressed = true;
            InputController.onWalkKey(walkKey.isDown(), pressed);

            if (ConfigManager.get().debug && client.player != null && ++debugTicks >= 5) {
                debugTicks = 0;
                client.player.displayClientMessage(Component.literal(MotionCommands.statusLine()), true);
            }
        });
    }
}
