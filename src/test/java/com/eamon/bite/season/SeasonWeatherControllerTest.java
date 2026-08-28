package com.eamon.bite.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 季节天气控制器的决策函数测试。
 * （tick 主流程依赖 MC 运行时，由 gametest/实测覆盖；此处测纯逻辑边界。）
 */
class SeasonWeatherControllerTest {

    @Test
    void winterSnowForcedOnDays2Through8() {
        assertFalse(SeasonWeatherController.isWinterSnowForced(1),
            "冬一不强制（缓冲日）");
        for (int d = 2; d <= 8; d++) {
            assertTrue(SeasonWeatherController.isWinterSnowForced(d), "冬" + d + " 应强制降雪");
        }
        assertFalse(SeasonWeatherController.isWinterSnowForced(9),
            "冬九不强制（放晴缓冲日）");
    }

    @Test
    void springRainDelayIsShorterThanVanilla() {
        // 原版 RAIN_DELAY = UniformInt(12000, 180000)，春季压缩到 (12000, 36000)
        // 上界大幅低于原版上界 → 期望等待时间显著缩短（约 1/4）
        assertTrue(SeasonWeatherController.SPRING_RAIN_DELAY.maxInclusive() < 180000);
        assertTrue(SeasonWeatherController.SPRING_RAIN_DELAY.minInclusive() >= 12000);
    }
}
