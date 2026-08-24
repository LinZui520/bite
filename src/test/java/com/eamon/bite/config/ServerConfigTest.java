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
        assertEquals(6, cfg.shelfLifeDays().get("bread"));
        assertTrue(cfg.itemOverrides().containsKey("minecraft:golden_apple"));
        assertEquals(0.75, cfg.nutritionScaleStale());
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
        assertEquals(-1, ov.get("minecraft:rotten_flesh"));
        assertEquals(-1, ov.get("minecraft:spider_eye"));
        assertEquals(-1, ov.get("minecraft:poisonous_potato"));
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
}
