package com.eamon.bite.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @Test
    void parsesFullJson() {
        String json = """
            {
              "enabled": true,
              "scan_interval_ticks": 50,
              "shelf_life_days": {"raw_meat": 2, "default": 7},
              "item_overrides": {"minecraft:golden_apple": -1, "somemod:sushi": 3},
              "stale_threshold": 0.5,
              "old_threshold": 0.25,
              "nutrition_scale_stale": 0.75,
              "nutrition_scale_old": 0.5,
              "hunger_effect_chance": 0.3,
              "hunger_effect_duration_ticks": 160,
              "spoiled_inedible": true
            }
            """;
        ServerConfig cfg = ServerConfig.fromJson(json);
        assertTrue(cfg.enabled());
        assertEquals(50, cfg.scanIntervalTicks());
        assertEquals(2, cfg.shelfLifeDays().get("raw_meat"));
        assertEquals(-1, cfg.itemOverrides().get("minecraft:golden_apple"));
        assertEquals(3, cfg.itemOverrides().get("somemod:sushi"));
        assertTrue(cfg.spoiledInedible());
    }

    @Test
    void missingFieldsFallBackToDefaults() {
        ServerConfig cfg = ServerConfig.fromJson("{}");
        assertTrue(cfg.enabled());
        assertEquals(100, cfg.scanIntervalTicks());
        // 饥荒化默认表（DST 参照）：面包 15（料理档）、生肉 6、生鱼 3
        assertEquals(15, cfg.shelfLifeDays().get("bread"));
        assertEquals(6, cfg.shelfLifeDays().get("raw_meat"));
        assertEquals(3, cfg.shelfLifeDays().get("raw_fish"));
        assertTrue(cfg.itemOverrides().containsKey("minecraft:golden_apple"));
        assertEquals(0.75, cfg.nutritionScaleStale());
        // 饥荒档位阈值：陈旧档 0-20%
        assertEquals(0.2, cfg.oldThreshold());
    }

    @Test
    void dontStarveOverridesTable() {
        Map<String, Integer> ov = ServerConfig.DEFAULT.itemOverrides();
        // 浆果 6 天（饥荒浆果；MC 无烤浆果物品故无「烤更快」项）
        assertEquals(6, ov.get("minecraft:sweet_berries"));
        assertEquals(6, ov.get("minecraft:glow_berries"));
        // 烤马铃薯坏得比生马铃薯快（饥荒：10 → 6，熟加工≠更耐放的反直觉档）
        assertEquals(6, ov.get("minecraft:baked_potato"));
        // 熟鱼 6 天（饥荒：鱼 3 → 熟鱼 6，鱼类熟反而更耐放）
        assertEquals(6, ov.get("minecraft:cooked_cod"));
        assertEquals(6, ov.get("minecraft:cooked_salmon"));
        // 种子类 40 天（比照饥荒种子）
        assertEquals(40, ov.get("minecraft:wheat_seeds"));
        assertEquals(40, ov.get("minecraft:pumpkin_seeds"));
        // 蜂蜜 40（饥荒里蜂蜜也会坏）
        assertEquals(40, ov.get("minecraft:honey_bottle"));
        assertEquals(40, ov.get("minecraft:honeycomb"));
    }

    @Test
    void roundTrips() {
        ServerConfig cfg = ServerConfig.fromJson(ServerConfig.DEFAULT.toJson());
        assertEquals(cfg, ServerConfig.fromJson(cfg.toJson()));
    }

    @Test
    void defaultOverridesIncludeVanillaExemptions() {
        Map<String, Integer> ov = ServerConfig.DEFAULT.itemOverrides();
        assertEquals(-1, ov.get("minecraft:golden_apple"));
        assertEquals(-1, ov.get("minecraft:enchanted_golden_apple"));
        // 药水/牛奶/金胡萝卜不需要新鲜度（用户口径 v1.0.4）
        assertEquals(-1, ov.get("minecraft:potion"));
        assertEquals(-1, ov.get("minecraft:milk_bucket"));
        assertEquals(-1, ov.get("minecraft:golden_carrot"));
        assertEquals(-1, ov.get("minecraft:spider_eye"));
        assertEquals(-1, ov.get("minecraft:poisonous_potato"));
    }

    @Test
    void cakeAndRottenFleshHaveFreshness() {
        Map<String, Integer> ov = ServerConfig.DEFAULT.itemOverrides();
        // 蛋糕需要新鲜度（料理档 15 天）
        assertEquals(15, ov.get("minecraft:cake"));
        // 腐肉需要新鲜度，40 天（原为豁免；用户口径 v1.0.4）
        assertEquals(40, ov.get("minecraft:rotten_flesh"));
    }

    @Test
    void scanIntervalTicksZeroClampsToOne() {
        String json = """
            {
              "enabled": true,
              "scan_interval_ticks": 0
            }
            """;
        ServerConfig cfg = ServerConfig.fromJson(json);
        assertEquals(1, cfg.scanIntervalTicks(),
            "scan_interval_ticks=0 must clamp to 1 to avoid ArithmeticException in modulo gate");
    }

    @Test
    void spoiledConversionDefaults() {
        assertTrue(ServerConfig.DEFAULT.spoiledConversion(),
            "变质转换默认开启");
        assertEquals("bite:rotten_organic", ServerConfig.DEFAULT.spoiledResult(),
            "默认转换产物是 bite:rotten_organic");
    }

    @Test
    void spoiledConversionParses() {
        String json = """
            {
              "spoiled_conversion": false,
              "spoiled_result": "minecraft:rotten_flesh"
            }
            """;
        ServerConfig cfg = ServerConfig.fromJson(json);
        assertFalse(cfg.spoiledConversion());
        assertEquals("minecraft:rotten_flesh", cfg.spoiledResult());
        // 缺省回退
        ServerConfig empty = ServerConfig.fromJson("{}");
        assertTrue(empty.spoiledConversion());
        assertEquals("bite:rotten_organic", empty.spoiledResult());
    }
}
