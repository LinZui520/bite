package com.eamon.bite.freshness;

/** 双端每 tick 更新的 game time 缓存。供无 Level 上下文的静态钩子（等价性放宽、物品条渲染）读取。 */
public final class FreshnessClock {
    private static volatile long now;

    private FreshnessClock() {}

    public static void update(long gameTime) { now = gameTime; }
    public static long now() { return now; }
}
