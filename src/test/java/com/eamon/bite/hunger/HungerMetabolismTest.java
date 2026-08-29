package com.eamon.bite.hunger;

import com.eamon.bite.config.ServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 饥饿代谢数值测试：速率档位、睡觉补结算、一天掉空的口径。
 * （tick 主流程依赖 MC 运行时，由实测覆盖；此处验证纯数值关系。）
 */
class HungerMetabolismTest {

    /** 25 次结算 × 4 疲劳 = 从满（20+饱和5）到 0 的总疲劳。 */
    private static final float FULL_DRAIN_EXHAUSTION = 25 * 4;

    @Test
    void fullDayRateDrainsInOneGameDay() {
        assertEquals(FULL_DRAIN_EXHAUSTION,
            HungerMetabolism.FULL_DAY_RATE * 24000, 0.01f,
            "满档代谢 × 24000 tick 应恰好吃完全部结算量");
    }

    @Test
    void defaultIsHalfRate() {
        // 默认半档：2 游戏日掉空（挂机一晚不至于饿到 1 血）
        double days = FULL_DRAIN_EXHAUSTION / ServerConfig.DEFAULT.hungerMetabolismPerTick() / 24000;
        assertEquals(2.0, days, 0.05, "默认代谢应为 2 游戏日掉空，实际 " + days);
    }

    @Test
    void sleepCostsLessThanStayingAwake() {
        // 睡觉代谢 50%（与口渴的 SLEEP_DRAIN_FACTOR 统一）：
        // 睡过同一时长消耗恰为清醒的一半
        assertEquals(0.5, ServerConfig.DEFAULT.hungerSleepMetabolismFactor(), 1e-9);
        assertEquals(0.5, com.eamon.bite.thirst.ThirstData.SLEEP_DRAIN_FACTOR, 1e-9,
            "两个系统的睡眠系数应统一");
    }

    @Test
    void sleepExhaustionIsReasonable() {
        // 23:00 睡（跳 ~1000 tick）与 19:00 睡（跳 ~7000 tick）的补算量
        double rate = ServerConfig.DEFAULT.hungerMetabolismPerTick();
        double factor = ServerConfig.DEFAULT.hungerSleepMetabolismFactor();
        double lateNight = rate * 1000 * factor;   // ≈ 0.83 疲劳 ≈ 0.1 格
        double earlyNight = rate * 7000 * factor;  // ≈ 5.8 疲劳 ≈ 0.7 格
        assertTrue(lateNight < 1.2f, "晚睡补算应 < 1/3 结算（0.1 格级），实际 " + lateNight);
        assertTrue(earlyNight < 4 * 2, "早睡补算应 < 2 次结算（1 格级），实际 " + earlyNight);
    }

    @Test
    void actionMultiplierDefaultIsGentle() {
        // 默认 1.3：疾跑 0.1 → 0.13/米（+30%），走路 0.005/米独立计费
        assertEquals(1.3, ServerConfig.DEFAULT.hungerActionMultiplier(), 1e-6);
    }
}
