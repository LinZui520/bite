package com.eamon.bite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.eamon.bite.BiteMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record ClientConfig(boolean showBar, boolean showTooltip, String tooltipStyle) {
    public static final ClientConfig DEFAULT = new ClientConfig(true, true, "percent_and_time");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ClientConfig instance = DEFAULT;

    public static ClientConfig get() { return instance; }

    public static ClientConfig fromJson(String json) {
        Raw raw = GSON.fromJson(json, Raw.class);
        if (raw == null) return DEFAULT;
        String style = raw.tooltip_style == null ? DEFAULT.tooltipStyle : raw.tooltip_style;
        if (!style.equals("percent") && !style.equals("time") && !style.equals("percent_and_time")) style = DEFAULT.tooltipStyle;
        return new ClientConfig(
            raw.show_bar == null ? DEFAULT.showBar : raw.show_bar,
            raw.show_tooltip == null ? DEFAULT.showTooltip : raw.show_tooltip,
            style);
    }

    public String toJson() {
        Raw raw = new Raw();
        raw.show_bar = showBar; raw.show_tooltip = showTooltip; raw.tooltip_style = tooltipStyle;
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
        Boolean show_tooltip;
        String tooltip_style;
    }
}
