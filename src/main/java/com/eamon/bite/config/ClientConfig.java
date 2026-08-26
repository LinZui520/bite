package com.eamon.bite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
        String json = ConfigFiles.readOrCreate(configDir, "client.json", DEFAULT.toJson());
        instance = json == null ? DEFAULT : fromJson(json);
        return instance;
    }

    private static final class Raw {
        Boolean show_bar;
    }
}
