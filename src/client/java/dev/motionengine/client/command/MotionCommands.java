package dev.motionengine.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.motionengine.client.bridge.MotionHolder;
import dev.motionengine.client.bridge.MotionRuntime;
import dev.motionengine.client.config.ConfigManager;
import dev.motionengine.core.config.MotionConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * /motionengine status | reload | debug | preset &lt;cinematic|balanced|performance|reduced_motion&gt;
 */
public final class MotionCommands {
    private MotionCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("motionengine")
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal(statusLine()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("reload").executes(ctx -> {
                    ConfigManager.load();
                    ctx.getSource().sendFeedback(Component.literal("[Motion Engine] config reloaded: " + ConfigManager.path()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("debug").executes(ctx -> {
                    MotionConfig c = ConfigManager.get().copy();
                    c.debug = !c.debug;
                    ConfigManager.set(c);
                    ctx.getSource().sendFeedback(Component.literal("[Motion Engine] debug " + (c.debug ? "ON" : "OFF")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("preset")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name").toUpperCase(Locale.ROOT);
                        try {
                            MotionConfig.Preset p = MotionConfig.Preset.valueOf(name);
                            ConfigManager.set(ConfigManager.get().copy().applyPreset(p));
                            ctx.getSource().sendFeedback(Component.literal("[Motion Engine] preset " + p));
                            return 1;
                        } catch (IllegalArgumentException e) {
                            ctx.getSource().sendError(Component.literal("Presets: cinematic, balanced, performance, reduced_motion"));
                            return 0;
                        }
                    })))
        ));
    }

    public static String statusLine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "[Motion Engine] no player";
        MotionRuntime rt = ((MotionHolder) mc.player).motionengine$runtime();
        String slip = String.format(" | slip max=%.1fmm contacts=%d", rt.slip.maxSlip * 1000, rt.slip.contacts);
        return "[Motion Engine] " + rt.controller.debugLine() + (ConfigManager.get().debug ? slip : "");
    }
}
