package com.eamon.bite.season;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 季节/维度作用域：季节影响只作用于主世界；下界有自己的环境系数
 * （口渴 ×2 / 腐坏 ×1.25——灼热蒸腾，用户口径 2026-08-30）；末地
 * 全基准。
 *
 * <p>难点：Biome 实例是 server 级共享的（WORLDGEN 注册表跨维度共用），
 * {@code Biome.getBaseTemperature} 无 Level 参数拿不到维度。解法：
 * server tick 的维度分派处设置 {@link #setCurrentDimension}，钩子读取
 * 判定——游戏逻辑单线程，ThreadLocal 即调用点语义。
 *
 * <p>纯 JVM 单测不能触碰 {@code Level.OVERWORLD}（MC bootstrap 炸）——
 * 测试走 {@link #setScopeForTest} 的枚举直设通道。
 */
public final class SeasonScope {
    /** 作用域三态：主世界（季节全开）/ 下界（环境系数）/ 其他（末地等，全基准）。 */
    public enum Scope { OVERWORLD, NETHER, OTHER }

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private SeasonScope() {}

    /** server/client tick 的维度分派处调用。 */
    public static void setCurrentDimension(ResourceKey<Level> dimension) {
        CURRENT.set(of(dimension));
    }

    private static Scope of(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) return Scope.OVERWORLD;
        if (dimension == Level.NETHER) return Scope.NETHER;
        return Scope.OTHER;
    }

    /** 当前线程的作用域（无标记 = OTHER——非 tick 路径不吃任何环境系数）。 */
    public static Scope current() {
        Scope scope = CURRENT.get();
        return scope != null ? scope : Scope.OTHER;
    }

    /** 当前线程是否在主世界上下文。 */
    public static boolean isOverworld() {
        return current() == Scope.OVERWORLD;
    }

    /** tick 结束后清（防跨线程/跨维度残留）。 */
    public static void clear() {
        CURRENT.remove();
    }

    /** 纯 JVM 测试入口：直设作用域（绕过 MC bootstrap）。 */
    public static void setScopeForTest(Scope scope) {
        CURRENT.set(scope);
    }
}
