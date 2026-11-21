package dev.motionengine.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.motionengine.core.config.MotionConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads/saves {@code config/motionengine.json}. Invalid files fall back to defaults (never crash). */
public final class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("motionengine");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile MotionConfig config = new MotionConfig();

    private ConfigManager() {}

    public static MotionConfig get() {
        return config;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("motionengine.json");
    }

    public static void load() {
        Path p = path();
        if (!Files.exists(p)) {
            config = new MotionConfig();
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(p)) {
            MotionConfig loaded = GSON.fromJson(r, MotionConfig.class);
            config = (loaded == null ? new MotionConfig() : loaded).sanitize();
        } catch (Exception e) {
            LOG.warn("Invalid {} — using defaults ({})", p, e.toString());
            config = new MotionConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(config, w);
            }
        } catch (IOException e) {
            LOG.warn("Could not save config: {}", e.toString());
        }
    }

    public static void set(MotionConfig c) {
        config = c.sanitize();
        save();
    }
}
