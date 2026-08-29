package com.eamon.bite.season;

import com.eamon.bite.GameTime;
import net.minecraft.world.level.Level;

/**
 * 季节时钟：从世界时钟推导，零存储零同步（26.2 WorldClock 体系，
 * 与 {@link com.eamon.bite.freshness.FreshnessClock} 同源）。
 *
 * <p>规则：9 天为一季，四季顺序 <b>秋 → 冬 → 春 → 夏</b>——游戏第 1 天是秋一。
 * 36 天一轮回到秋一。
 *
 * <p>天数基准：世界时钟 totalTicks / 24000 的商即「已开始的天数」。
 * 新世界从 tick 0 开始，第 0 个自然日即秋一；之后每跨一个自然日进一位。
 */
public final class SeasonClock {
    public static final int DAYS_PER_SEASON = 9;
    /** 展示顺序（起始季节 = 秋，游戏第 1 天为秋一）。 */
    public static final Season STARTING_SEASON = Season.AUTUMN;
    private static final int DAYS_PER_CYCLE = DAYS_PER_SEASON * Season.values().length;

    /** 当前季节缓存（无 Level 上下文的钩子读取；双端各自每 tick 刷新，模式同 FreshnessClock）。 */
    private static volatile Season current = Season.AUTUMN;
    /** 当前生效的季节温度偏移（换季日当天含线性过渡）。 */
    private static volatile float currentOffset = SeasonTemperature.offset(Season.AUTUMN);

    private SeasonClock() {}

    /** 最近一次 {@link #update} 刷新的当前季节（启动前默认秋 = 偏移 0，新世界首日恰好一致）。 */
    public static Season current() { return current; }

    /** 当前生效的季节温度偏移（含换季过渡）。 */
    public static float currentOffset() { return currentOffset; }

    /** 每 tick 由 server / client 侧调用（overworld）。 */
    public static void update(Level level) {
        current = season(level);
        currentOffset = SeasonTemperature.blendOffset(
            current, dayOfSeason(level), dayProgress(level));
    }

    /** 当日内进度 [0,1)：totalTicks 对 24000 取余。 */
    static float dayProgress(Level level) {
        return (level.getOverworldClockTime() % GameTime.TICKS_PER_DAY) / (float) GameTime.TICKS_PER_DAY;
    }

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
        return Math.floorDiv(level.getOverworldClockTime(), GameTime.TICKS_PER_DAY);
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
