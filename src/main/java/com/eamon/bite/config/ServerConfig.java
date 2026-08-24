package com.eamon.bite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.eamon.bite.BiteMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** 服务端权威配置。v1 重启生效（spec §9）。 */
public record ServerConfig(
    boolean enabled,
    int scanIntervalTicks,
    Map<String, Integer> shelfLifeDays,
    Map<String, Integer> itemOverrides,
    double staleThreshold,
    double oldThreshold,
    double nutritionScaleStale,
    double nutritionScaleOld,
    double hungerEffectChance,
    int hungerEffectDurationTicks,
    boolean spoiledInedible
) {
    public static final ServerConfig DEFAULT = createDefault();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile ServerConfig instance = DEFAULT;

    public static ServerConfig get() { return instance; }

    private static ServerConfig createDefault() {
        Map<String, Integer> shelf = new LinkedHashMap<>();
        shelf.put("raw_meat", 2); shelf.put("raw_fish", 2);
        shelf.put("cooked_meat", 4); shelf.put("cooked_fish", 4);
        shelf.put("bread", 6); shelf.put("vegetable", 6); shelf.put("fruit", 6);
        shelf.put("berry", 6); shelf.put("dough", 6);
        shelf.put("soup", 3); shelf.put("cookie", 3); shelf.put("pie", 3); shelf.put("candy", 3);
        shelf.put("default", 7);
        Map<String, Integer> overrides = new TreeMap<>();
        overrides.put("minecraft:golden_apple", -1);
        overrides.put("minecraft:enchanted_golden_apple", -1);
        overrides.put("minecraft:rotten_flesh", -1);
        overrides.put("minecraft:spider_eye", -1);
        overrides.put("minecraft:poisonous_potato", -1);
        return new ServerConfig(true, 100, shelf, overrides,
            0.5, 0.25, 0.75, 0.5, 0.3, 160, true);
    }

    public static ServerConfig fromJson(String json) {
        Raw raw = GSON.fromJson(json, Raw.class);
        if (raw == null) return DEFAULT;
        ServerConfig d = DEFAULT;
        Map<String, Integer> shelf = raw.shelf_life_days == null ? new LinkedHashMap<>(d.shelfLifeDays) : raw.shelf_life_days;
        if (!shelf.containsKey("default")) shelf.put("default", 7);
        return new ServerConfig(
            orDefault(raw.enabled, d.enabled),
            orDefault(raw.scan_interval_ticks, d.scanIntervalTicks),
            shelf,
            raw.item_overrides == null ? new TreeMap<>(d.itemOverrides) : raw.item_overrides,
            orDefault(raw.stale_threshold, d.staleThreshold),
            orDefault(raw.old_threshold, d.oldThreshold),
            orDefault(raw.nutrition_scale_stale, d.nutritionScaleStale),
            orDefault(raw.nutrition_scale_old, d.nutritionScaleOld),
            orDefault(raw.hunger_effect_chance, d.hungerEffectChance),
            orDefault(raw.hunger_effect_duration_ticks, d.hungerEffectDurationTicks),
            orDefault(raw.spoiled_inedible, d.spoiledInedible));
    }

    private static int orDefault(Integer v, int d) { return v == null ? d : v; }
    private static double orDefault(Double v, double d) { return v == null ? d : v; }
    private static boolean orDefault(Boolean v, boolean d) { return v == null ? d : v; }

    public String toJson() {
        Raw raw = new Raw();
        raw.enabled = enabled; raw.scan_interval_ticks = scanIntervalTicks;
        raw.shelf_life_days = shelfLifeDays; raw.item_overrides = itemOverrides;
        raw.stale_threshold = staleThreshold; raw.old_threshold = oldThreshold;
        raw.nutrition_scale_stale = nutritionScaleStale; raw.nutrition_scale_old = nutritionScaleOld;
        raw.hunger_effect_chance = hungerEffectChance; raw.hunger_effect_duration_ticks = hungerEffectDurationTicks;
        raw.spoiled_inedible = spoiledInedible;
        return GSON.toJson(raw);
    }

    /** 从 config/bite/server.json 加载；文件缺失时写默认值。 */
    public static ServerConfig load(Path configDir) {
        Path file = configDir.resolve("bite").resolve("server.json");
        try {
            if (Files.exists(file)) {
                instance = fromJson(Files.readString(file));
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT.toJson());
                instance = DEFAULT;
            }
        } catch (IOException e) {
            BiteMod.LOGGER.error("Failed to load server config, using defaults", e);
            instance = DEFAULT;
        }
        return instance;
    }

    /** Gson 反序列化骨架（snake_case JSON 键）。 */
    private static final class Raw {
        Boolean enabled;
        Integer scan_interval_ticks;
        Map<String, Integer> shelf_life_days;
        Map<String, Integer> item_overrides;
        Double stale_threshold;
        Double old_threshold;
        Double nutrition_scale_stale;
        Double nutrition_scale_old;
        Double hunger_effect_chance;
        Integer hunger_effect_duration_ticks;
        Boolean spoiled_inedible;
    }
}
