package com.eamon.bite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** 服务端权威配置（config/bite/server.json），重启生效。 */
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
    boolean spoiledInedible,
    boolean spoiledConversion,
    String spoiledResult,
    double hungerMetabolismPerTick,
    double hungerSleepMetabolismFactor,
    double hungerActionMultiplier,
    double perishWinterMultiplier,
    double perishSummerMultiplier
) {
    public static final ServerConfig DEFAULT = createDefault();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile ServerConfig instance = DEFAULT;

    public static ServerConfig get() { return instance; }

    /** 默认保质期天数直搬饥荒（DST）梯度：分类默认 + 逐物品 overrides。 */
    private static ServerConfig createDefault() {
        Map<String, Integer> shelf = new LinkedHashMap<>();
        shelf.put("raw_meat", 6); shelf.put("raw_fish", 3);
        shelf.put("cooked_meat", 10); shelf.put("cooked_fish", 6);
        shelf.put("vegetable", 10); shelf.put("fruit", 6);
        shelf.put("berry", 6); shelf.put("dough", 10);
        shelf.put("bread", 15); shelf.put("cookie", 15); shelf.put("pie", 15); shelf.put("candy", 15);
        shelf.put("soup", 6);
        shelf.put("default", 15); // 兜底：料理档（DST 大多菜肴 10-20 天）

        Map<String, Integer> overrides = new TreeMap<>();
        // 永不腐坏：金苹果系、药水、牛奶、金胡萝卜等魔法/特殊食物
        overrides.put("minecraft:golden_apple", -1);
        overrides.put("minecraft:enchanted_golden_apple", -1);
        overrides.put("minecraft:potion", -1);
        overrides.put("minecraft:milk_bucket", -1);
        overrides.put("minecraft:golden_carrot", -1);
        overrides.put("minecraft:spider_eye", -1);
        overrides.put("minecraft:poisonous_potato", -1);
        // 蜂蜜对齐现实：永不腐坏（蜜脾同）
        overrides.put("minecraft:honey_bottle", -1);
        overrides.put("minecraft:honeycomb", -1);
        // 腐肉本身已是腐坏物：放宽到 40 天
        overrides.put("minecraft:rotten_flesh", 40);
        // 蛋糕按料理档 15 天
        overrides.put("minecraft:cake", 15);
        // 浆果 6 天（DST：烤浆果反而坏得更快，取生浆果档）
        overrides.put("minecraft:sweet_berries", 6);
        overrides.put("minecraft:glow_berries", 6);
        // 烤制后坏得更快（DST：马铃薯 10 → 烤 6）
        overrides.put("minecraft:baked_potato", 6);
        // 熟鱼比生鱼耐放（DST：鱼 3 → 熟鱼 6）
        overrides.put("minecraft:cooked_cod", 6);
        overrides.put("minecraft:cooked_salmon", 6);
        // 种子类 40 天（DST 种子）
        overrides.put("minecraft:wheat_seeds", 40);
        overrides.put("minecraft:pumpkin_seeds", 40);
        overrides.put("minecraft:melon_seeds", 40);
        overrides.put("minecraft:beetroot_seeds", 40);
        overrides.put("minecraft:torchflower_seeds", 40);
        overrides.put("minecraft:pitcher_seeds", 40);
        return new ServerConfig(true, 20, shelf, overrides,
            0.5, 0.2, 0.75, 0.5, 0.3, 160, true,
            true, "bite:rotten_organic",
            // 饿腹代谢半档：2 游戏日 20→0（满档 FULL_DAY_RATE = 1 日）；
            // 睡觉代谢 40%；动作疲劳 ×1.3（疾跑 0.1→0.13/米）；
            // 季节腐坏（DST perishable.lua 口径）：冬 ×0.75 / 夏 ×1.25
            0.00208, 0.4, 1.3, 0.75, 1.25);
    }

    public static ServerConfig fromJson(String json) {
        Raw raw = GSON.fromJson(json, Raw.class);
        if (raw == null) return DEFAULT;
        ServerConfig d = DEFAULT;
        Map<String, Integer> shelf = raw.shelf_life_days == null ? new LinkedHashMap<>(d.shelfLifeDays) : raw.shelf_life_days;
        if (!shelf.containsKey("default")) shelf.put("default", 7);
        int scanInterval = Math.max(1, orDefault(raw.scan_interval_ticks, d.scanIntervalTicks));
        double stale = orDefault(raw.stale_threshold, d.staleThreshold);
        double old = orDefault(raw.old_threshold, d.oldThreshold);
        // 非法配置防御：stale 必须严格大于 old，否则档位判定失真
        if (stale > 0.0 && old > 0.0 && stale <= old) {
            stale = Math.max(old + 0.01, stale);
        }
        return new ServerConfig(
            orDefault(raw.enabled, d.enabled),
            scanInterval,
            shelf,
            raw.item_overrides == null ? new TreeMap<>(d.itemOverrides) : raw.item_overrides,
            stale,
            old,
            orDefault(raw.nutrition_scale_stale, d.nutritionScaleStale),
            orDefault(raw.nutrition_scale_old, d.nutritionScaleOld),
            orDefault(raw.hunger_effect_chance, d.hungerEffectChance),
            orDefault(raw.hunger_effect_duration_ticks, d.hungerEffectDurationTicks),
            orDefault(raw.spoiled_inedible, d.spoiledInedible),
            orDefault(raw.spoiled_conversion, d.spoiledConversion),
            orDefault(raw.spoiled_result, d.spoiledResult),
            orDefault(raw.hunger_metabolism_per_tick, d.hungerMetabolismPerTick),
            orDefault(raw.hunger_sleep_metabolism_factor, d.hungerSleepMetabolismFactor),
            orDefault(raw.hunger_action_multiplier, d.hungerActionMultiplier),
            orDefault(raw.perish_winter_multiplier, d.perishWinterMultiplier),
            orDefault(raw.perish_summer_multiplier, d.perishSummerMultiplier));
    }

    private static int orDefault(Integer v, int d) { return v == null ? d : v; }
    private static double orDefault(Double v, double d) { return v == null ? d : v; }
    private static boolean orDefault(Boolean v, boolean d) { return v == null ? d : v; }
    private static String orDefault(String v, String d) { return v == null ? d : v; }

    public String toJson() {
        Raw raw = new Raw();
        raw.enabled = enabled; raw.scan_interval_ticks = scanIntervalTicks;
        raw.shelf_life_days = shelfLifeDays; raw.item_overrides = itemOverrides;
        raw.stale_threshold = staleThreshold; raw.old_threshold = oldThreshold;
        raw.nutrition_scale_stale = nutritionScaleStale; raw.nutrition_scale_old = nutritionScaleOld;
        raw.hunger_effect_chance = hungerEffectChance; raw.hunger_effect_duration_ticks = hungerEffectDurationTicks;
        raw.spoiled_inedible = spoiledInedible;
        raw.spoiled_conversion = spoiledConversion; raw.spoiled_result = spoiledResult;
        raw.hunger_metabolism_per_tick = hungerMetabolismPerTick;
        raw.hunger_sleep_metabolism_factor = hungerSleepMetabolismFactor;
        raw.hunger_action_multiplier = hungerActionMultiplier;
        raw.perish_winter_multiplier = perishWinterMultiplier;
        raw.perish_summer_multiplier = perishSummerMultiplier;
        return GSON.toJson(raw);
    }

    /** 从 config/bite/server.json 加载；文件缺失时写默认值。 */
    public static ServerConfig load(Path configDir) {
        String json = ConfigFiles.readOrCreate(configDir, "server.json", DEFAULT.toJson());
        instance = json == null ? DEFAULT : fromJson(json);
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
        // spoiled_conversion 暂时口径：true = 扫描器移除变质食物（原 = 转换为
        // spoiled_result；转换暂缓后该键暂未读取，保留以备恢复）
        Boolean spoiled_conversion;
        String spoiled_result;
        // 饥饿代谢：每 tick 疲劳（0 = 关闭，站桩不掉）；睡觉期间代谢系数
        // （0.4 = 睡觉消耗为清醒的 40%）；动作疲劳乘数（1 = 原版，>1 更快饿）
        Double hunger_metabolism_per_tick;
        Double hunger_sleep_metabolism_factor;
        Double hunger_action_multiplier;
        // 季节腐坏系数（DST 口径：冬 0.75 / 夏 1.25；1 = 无季节影响）
        Double perish_winter_multiplier;
        Double perish_summer_multiplier;
    }
}
