package com.eamon.bite.season;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 季节作用域：季节影响只作用于主世界（用户口径 2026-08-30——下界/
 * 末地不引入季节）。
 *
 * <p>难点：Biome 实例是 server 级共享的（WORLDGEN 注册表跨维度共用，
 * 下界群系与主世界群系在同一注册表），{@code Biome.getBaseTemperature}
 * 无 Level 参数拿不到维度。解法：server tick 的维度分派处设置
 * {@link #setCurrentDimension}，BiomeMixin 读取判断——游戏逻辑单线程，
 * ThreadLocal 即调用点语义。
 *
 * <p>纯 JVM 单测不能触碰 {@code Level.OVERWORLD}（MC bootstrap 炸）——
 * 测试走 {@link #setOverworldFlag} 的布尔通道。
 */
public final class SeasonScope {
    private static final ThreadLocal<Object> CURRENT = new ThreadLocal<>();
    /** 主世界哨兵（不引用 MC 类，测试/生产共用）。 */
    private static final Object OVERWORLD = new Object();

    private SeasonScope() {}

    /** server/client tick 的维度分派处调用。 */
    public static void setCurrentDimension(ResourceKey<Level> dimension) {
        CURRENT.set(dimension == Level.OVERWORLD ? OVERWORLD : dimension);
    }

    /** 当前线程是否在主世界上下文（未设置时保守 false——非 tick 路径不吃季节）。 */
    public static boolean isOverworld() {
        return CURRENT.get() == OVERWORLD;
    }

    /** tick 结束后清（防跨线程/跨维度残留）。 */
    public static void clear() {
        CURRENT.remove();
    }

    /** 纯 JVM 测试入口：直接断言主世界标记。 */
    public static void setOverworldFlag(boolean overworld) {
        CURRENT.set(overworld ? OVERWORLD : null);
    }
}
