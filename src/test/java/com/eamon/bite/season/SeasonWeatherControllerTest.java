package com.eamon.bite.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 季节天气控制器的间隔参数测试（tick 主流程依赖 MC 运行时，由实测覆盖）。
 * 数值口径来自饥荒 DST 一手数据（weather.lua 常量 + 200 游戏年模拟），
 * 按 9 天一季做比例缩放。
 */
class SeasonWeatherControllerTest {

    @Test
    void winterSnowIsIntermittentNotContinuous() {
        // 饥荒冬季：15 天 3~4 场、间隔 ~8.6 天 → 9 天季按比例 ≈ 5 天间隔。
        // 间隔必须显著大于 0（间歇）且小于季长（季内至少再下一场）
        int min = SeasonWeatherController.WINTER_SNOW_DELAY.minInclusive();
        int max = SeasonWeatherController.WINTER_SNOW_DELAY.maxInclusive();
        assertTrue(min >= 3 * 24000, "冬季间隔下界应 ≥3 天（间歇性，实际 " + min / 24000 + " 天）");
        assertTrue(max < 9 * 24000, "冬季间隔上界应 <9 天（季内再来一场，实际 " + max / 24000 + " 天）");
    }

    @Test
    void springRainsMuchMoreOftenThanWinter() {
        // 饥荒频率序：春 >>> 冬 > 秋 > 夏。春季间隔应显著短于冬季
        int springMax = SeasonWeatherController.SPRING_RAIN_DELAY.maxInclusive();
        int winterMin = SeasonWeatherController.WINTER_SNOW_DELAY.minInclusive();
        assertTrue(springMax < winterMin,
            "春季最疏的阵雨（" + springMax / 24000 + " 天）也应密于冬季最密的雪（" + winterMin / 24000 + " 天）");
    }

    @Test
    void springShowersStayFrequent() {
        // 春季间隔 1~2 天：下界 ≥0.5 天（是真间隔不是连雨），上界 ≤3 天（保持「频繁」体感）
        int min = SeasonWeatherController.SPRING_RAIN_DELAY.minInclusive();
        int max = SeasonWeatherController.SPRING_RAIN_DELAY.maxInclusive();
        assertTrue(min >= 12000, "春季间隔下界应 ≥0.5 天（非连续降雨）");
        assertTrue(max <= 3 * 24000, "春季间隔上界应 ≤3 天（频繁体感）");
    }
}
