package com.eamon.bite.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 季节温度纯函数测试：偏移表、不钳制可逆性、保守化雪判定。
 * 群系参照温度：平原 0.8 / 针叶林 0.25 / 雪原 0.0 / 沙漠 2.0。
 */
class SeasonTemperatureTest {

    @Test
    void offsetTable() {
        assertEquals(0.0f, SeasonTemperature.offset(Season.AUTUMN));
        assertEquals(-0.8f, SeasonTemperature.offset(Season.WINTER));
        assertEquals(-0.25f, SeasonTemperature.offset(Season.SPRING));
        assertEquals(0.3f, SeasonTemperature.offset(Season.SUMMER));
    }

    @Test
    void seasonalIsUnclampedAndReversible() {
        // 沙漠 2.0 夏天 = 2.3（超出 SS 常用 [-0.5, 2.0] 钳制区间，但下游全是阈值比较）
        assertEquals(2.3f, SeasonTemperature.seasonal(2.0f, Season.SUMMER));
        // 反解恒等（融化判定依赖这一点）
        for (Season s : Season.values()) {
            for (float base : new float[]{-0.5f, 0.0f, 0.25f, 0.8f, 2.0f}) {
                assertEquals(base,
                    SeasonTemperature.seasonal(base, s) - SeasonTemperature.offset(s), 1e-6f);
            }
        }
    }

    @Test
    void plainsSeasonalSnowMeltsFromSpring() {
        // 平原 0.8：冬 -0.8 → 0.0 下雪；春 0.55 / 夏 1.1 / 秋 0.8 均融化
        assertFalse(SeasonTemperature.shouldMeltSnow(0.0f, Season.WINTER));
        assertTrue(SeasonTemperature.shouldMeltSnow(0.55f, Season.SPRING));
        assertTrue(SeasonTemperature.shouldMeltSnow(1.1f, Season.SUMMER));
        assertTrue(SeasonTemperature.shouldMeltSnow(0.8f, Season.AUTUMN));
    }

    @Test
    void taigaSnowLingersUntilSummer() {
        // 针叶林 0.25：冬 -0.55、春 0.0 仍冻结，夏 0.55 才融
        assertFalse(SeasonTemperature.shouldMeltSnow(-0.55f, Season.WINTER));
        assertFalse(SeasonTemperature.shouldMeltSnow(0.0f, Season.SPRING));
        assertTrue(SeasonTemperature.shouldMeltSnow(0.55f, Season.SUMMER));
    }

    @Test
    void snowyBiomesNeverMelt() {
        // 雪原 0.0：夏 0.3 虽 ≥ 0.15，但反解基础 0.0 < 0.15 → 原生积雪受保护
        for (Season s : Season.values()) {
            float seasonal = SeasonTemperature.seasonal(0.0f, s);
            assertFalse(SeasonTemperature.shouldMeltSnow(seasonal, s),
                s + " 不应融化原生雪原（季节值 " + seasonal + "）");
        }
    }

    @Test
    void winterDropsTemperateBiomesBelowRainSnowLine() {
        // 温带（≥0.8）冬天全部破 0.15 雨雪线；0.25 的针叶林也破
        assertTrue(SeasonTemperature.seasonal(0.8f, Season.WINTER) < SeasonTemperature.RAIN_SNOW_THRESHOLD);
        assertTrue(SeasonTemperature.seasonal(0.25f, Season.WINTER) < SeasonTemperature.RAIN_SNOW_THRESHOLD);
        // 但沙漠 2.0 即使冬天 1.2 也仍在雨线之上（且其降水开关本来就关）
        assertTrue(SeasonTemperature.seasonal(2.0f, Season.WINTER) > SeasonTemperature.RAIN_SNOW_THRESHOLD);
    }
}
