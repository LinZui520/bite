package com.eamon.bite.season;

import net.minecraft.world.level.Level;

/**
 * 季节时钟：从世界时钟推导，零存储零同步（26.2 WorldClock 体系，
 * 与 {@link com.eamon.bite.freshness.FreshnessClock} 同源）。
 *
 * <p>规则（用户口径 2026-08-29）：
 * <ul>
 *   <li>9 天为一季，四季顺序 <b>秋 → 冬 → 春 → 夏</b>——游戏第 1 天是秋一</li>
 *   <li>展示为「秋一～秋九」「冬一～冬九」…36 天一轮回到秋一</li>
 * </ul>
 *
 * <p>天数基准：世界时钟 totalTicks / 24000 的商即「已开始的天数」。
 * 新世界从 tick 0 开始，第 0 个自然日即秋一；之后每跨一个自然日进一位。
 * 「早上起床或熬夜到点」的提示触发由 SeasonAnnouncer 检测自然日进位实现，
 * 本类只负责静态推导，供后续季节化世界属性直接查询。
 */
public final class SeasonClock {
    public static final int DAYS_PER_SEASON = 9;
    /** 展示顺序（起始季节 = 秋，游戏第 1 天为秋一）。 */
    public static final Season STARTING_SEASON = Season.AUTUMN;
    private static final int DAYS_PER_CYCLE = DAYS_PER_SEASON * Season.values().length;

    private SeasonClock() {}

    /** 从世界时钟 totalTicks 推导当前季节（任意维度 → overworld WorldClock）。 */
    public static Season season(Level level) {
        return seasonAtDay(dayOfWorld(level));
    }

    /** 当前是本季第几天（1~9）。 */
    public static int dayOfSeason(Level level) {
        return dayOfSeasonAtDay(dayOfWorld(level));
    }

    /** 世界自然日序号（0 起）：totalTicks / 24000。 */
    public static long dayOfWorld(Level level) {
        return Math.floorDiv(level.getOverworldClockTime(), 24000L);
    }

    /** 日序号 → 季节（0 起：秋一当天 → AUTUMN）。 */
    public static Season seasonAtDay(long dayOfWorld) {
        Season[] seasons = Season.values();
        int idx = (int) Math.floorMod(dayOfWorld, DAYS_PER_CYCLE) / DAYS_PER_SEASON;
        // values() 声明顺序即展示顺序：AUTUMN, WINTER, SPRING, SUMMER
        return seasons[idx];
    }

    /** 日序号 → 本季第几天（1~9）。 */
    public static int dayOfSeasonAtDay(long dayOfWorld) {
        return (int) Math.floorMod(dayOfWorld, DAYS_PER_SEASON) + 1;
    }
}
