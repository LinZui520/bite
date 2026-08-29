package com.eamon.bite.thirst;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 饥渴数值测试：3 天清零口径、季节/疾跑/睡觉修正、补水表。
 * （tick 主流程依赖 MC 运行时，由实测覆盖。）
 */
class ThirstDataTest {

    @Test
    void baseDrainEmptiesInThreeGameDays() {
        assertEquals(3.0, ThirstData.MAX_THIRST / ThirstData.BASE_DRAIN_PER_TICK / 24000, 1e-3,
            "基础流失应恰好 3 游戏日清零");
    }

    @Test
    void sprintDrainsNoticably() {
        // 疾跑 0.02/秒：持续疾跑一天额外消耗
        double extraPerDay = ThirstData.SPRINT_DRAIN_PER_TICK * 24000;
        assertTrue(extraPerDay > 10, "疾跑一天应额外消耗 >10 点（占一半额度），实际 " + extraPerDay);
        assertTrue(extraPerDay < 30, "疾跑额外消耗应 <30（不越界），实际 " + extraPerDay);
    }

    @Test
    void sleepDehydratesAtHalfRate() {
        assertEquals(0.5, ThirstData.SLEEP_DRAIN_FACTOR, 1e-6);
    }

    @Test
    void hydrationTableCoversDrinksAndWateryFoods() {
        assertEquals(6, HydrationRegistry.hydrationOfId("minecraft:potion"));
        assertEquals(8, HydrationRegistry.hydrationOfId("minecraft:milk_bucket"));
        assertEquals(10, HydrationRegistry.hydrationOfId("minecraft:honey_bottle"));
        assertEquals(3, HydrationRegistry.hydrationOfId("minecraft:melon_slice"));
        assertEquals(1, HydrationRegistry.hydrationOfId("minecraft:apple"));
        // 面包不补水
        assertEquals(0, HydrationRegistry.hydrationOfId("minecraft:bread"));
    }

    @Test
    void sprintThresholdMatchesHungerGate() {
        assertEquals(6, ThirstData.SPRINT_THRESHOLD, "门槛应与原版饥饿疾跑线一致");
    }
}
