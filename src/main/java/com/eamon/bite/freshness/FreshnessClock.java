package com.eamon.bite.freshness;

import net.minecraft.world.level.Level;

/**
 * 新鲜度时钟：游戏时间的统一读取口。
 *
 * <p>26.2 有两套独立计数器：{@code level.getGameTime()} 只随真实运行 tick
 * 递增，不受 /time 影响；WorldClock（{@code getOverworldClockTime()}）才是
 * /time add|set 操作、随变速/暂停同步的玩家可感知时间。新鲜度绑定后者——
 * /time add 必须能加速腐坏。
 */
public final class FreshnessClock {
    private static volatile long now;

    private FreshnessClock() {}

    /** 读取 Level 当前的游戏刻（任意维度 → overworld WorldClock）。 */
    public static long now(Level level) {
        return level.getOverworldClockTime();
    }

    /** 最近一次 {@link #update} 刷新的缓存值，供无 Level 上下文的静态钩子读取。 */
    public static long now() { return now; }

    public static void update(long gameTime) { now = gameTime; }
}
