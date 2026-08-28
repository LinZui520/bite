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
        assertFalse(SeasonTemperature.shouldMeltSnow(0.0f, SeasonTemperature.offset(Season.WINTER)));
        assertTrue(SeasonTemperature.shouldMeltSnow(0.55f, SeasonTemperature.offset(Season.SPRING)));
        assertTrue(SeasonTemperature.shouldMeltSnow(1.1f, SeasonTemperature.offset(Season.SUMMER)));
        assertTrue(SeasonTemperature.shouldMeltSnow(0.8f, SeasonTemperature.offset(Season.AUTUMN)));
    }

    @Test
    void taigaSnowLingersUntilSummer() {
        // 针叶林 0.25：冬 -0.55、春 0.0 仍冻结，夏 0.55 才融
        assertFalse(SeasonTemperature.shouldMeltSnow(-0.55f, SeasonTemperature.offset(Season.WINTER)));
        assertFalse(SeasonTemperature.shouldMeltSnow(0.0f, SeasonTemperature.offset(Season.SPRING)));
        assertTrue(SeasonTemperature.shouldMeltSnow(0.55f, SeasonTemperature.offset(Season.SUMMER)));
    }

    @Test
    void snowyBiomesNeverMelt() {
        // 雪原 0.0：夏 0.3 虽 ≥ 0.15，但反解基础 0.0 < 0.15 → 原生积雪受保护
        for (Season s : Season.values()) {
            float seasonal = SeasonTemperature.seasonal(0.0f, s);
            assertFalse(SeasonTemperature.shouldMeltSnow(seasonal, SeasonTemperature.offset(s)),
                s + " 不应融化原生雪原（季节值 " + seasonal + "）");
        }
    }

    @Test
    void seasonTransitionBlendsOffsetOverFirstDay() {
        // 冬→春：换季日（dayOfSeason=1）从 -0.8 线性过渡到 -0.25；次日为纯春值
        assertEquals(-0.8f, SeasonTemperature.blendOffset(Season.SPRING, 1, 0.0f), 1e-6f);
        assertEquals(-0.525f, SeasonTemperature.blendOffset(Season.SPRING, 1, 0.5f), 1e-6f);
        assertEquals(-0.25f, SeasonTemperature.blendOffset(Season.SPRING, 1, 1.0f), 1e-6f);
        assertEquals(-0.25f, SeasonTemperature.blendOffset(Season.SPRING, 2, 0.0f), 1e-6f);
        // 非换季日恒为目标值
        assertEquals(0.3f, SeasonTemperature.blendOffset(Season.SUMMER, 5, 0.3f), 1e-6f);
    }

    @Test
    void snowToRainFlipsMidTransitionNotInstantly() {
        // 温带 0.8：冬→春过渡中，雨雪线（0.15）在偏移 = -(0.8-0.15) = -0.65 处穿越
        // （即换季日 ~23% 进度处），而不是换季瞬间
        float base = 0.8f;
        float atStart = base + SeasonTemperature.blendOffset(Season.SPRING, 1, 0.0f);
        float atQuarter = base + SeasonTemperature.blendOffset(Season.SPRING, 1, 0.25f);
        float atHalf = base + SeasonTemperature.blendOffset(Season.SPRING, 1, 0.5f);
        assertTrue(atStart < SeasonTemperature.RAIN_SNOW_THRESHOLD, "过渡开始仍是雪");
        assertTrue(atQuarter < SeasonTemperature.RAIN_SNOW_THRESHOLD, "25% 进度仍是雪");
        assertTrue(atHalf > SeasonTemperature.RAIN_SNOW_THRESHOLD, "50% 进度已转雨");
    }

    @Test
    void previousSeasonWraps() {
        assertEquals(Season.SUMMER, SeasonTemperature.previousSeason(Season.AUTUMN));
        assertEquals(Season.AUTUMN, SeasonTemperature.previousSeason(Season.WINTER));
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
