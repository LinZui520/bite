package com.eamon.bite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.eamon.bite.BiteMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 客户端显示配置（config/bite/client.json）。 */
public record ClientConfig(boolean showBar) {
    public static final ClientConfig DEFAULT = new ClientConfig(true);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile ClientConfig instance = DEFAULT;

    public static ClientConfig get() { return instance; }

    public static ClientConfig fromJson(String json) {
        Raw raw = GSON.fromJson(json, Raw.class);
        if (raw == null) return DEFAULT;
        return new ClientConfig(
            raw.show_bar == null ? DEFAULT.showBar : raw.show_bar);
    }

    public String toJson() {
        Raw raw = new Raw();
        raw.show_bar = showBar;
        return GSON.toJson(raw);
    }

    public static ClientConfig load(Path configDir) {
        Path file = configDir.resolve("bite").resolve("client.json");
        try {
            if (Files.exists(file)) {
                instance = fromJson(Files.readString(file));
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT.toJson());
                instance = DEFAULT;
            }
        } catch (IOException e) {
            BiteMod.LOGGER.error("Failed to load client config, using defaults", e);
            instance = DEFAULT;
        }
        return instance;
    }

    private static final class Raw {
        Boolean show_bar;
    }
}
