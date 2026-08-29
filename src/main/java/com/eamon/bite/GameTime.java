package com.eamon.bite;

/**
 * 全 mod 共用的游戏时间/坐标常量。
 *
 * <p>这些数值散落各处曾是魔法数字（24000 出现在 8 个文件）；语义
 * 集中于此，改「一天的 tick 数」（如装了改日长 mod 的兼容层）只动一处。
 */
public final class GameTime {
    /** 一游戏日的 tick 数（原版）。 */
    public static final long TICKS_PER_DAY = 24000L;

    private GameTime() {}

    /** 天数 → tick。 */
    public static long daysToTicks(long days) {
        return days * TICKS_PER_DAY;
    }

    /** tick → 天（浮点，显示用）。 */
    public static double ticksToDays(long ticks) {
        return (double) ticks / TICKS_PER_DAY;
    }
}
