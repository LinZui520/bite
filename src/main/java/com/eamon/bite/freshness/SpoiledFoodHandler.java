package com.eamon.bite.freshness;

import com.eamon.bite.config.ServerConfig;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 进食惩罚（spec §8 表）。
 *
 * <p>26.2 检查点：所有阈值均通过 {@link ServerConfig} 读取，无硬编码。
 * {@link #shouldApplyHunger} 使用 {@link ThreadLocalRandom}（与原版
 * {@code MobEffect} 概率判定一致的随机源选择）。
 */
public final class SpoiledFoodHandler {
    private SpoiledFoodHandler() {}

    /**
     * 营养缩放系数。
     *
     * <p>映射表（与 {@link FreshnessMath#grade} 对齐）：
     * <ul>
     *   <li>fraction &gt; staleThreshold → 1.0（FRESH，原版营养）</li>
     *   <li>oldThreshold &lt; fraction ≤ staleThreshold → nutritionScaleStale（默认 0.75）</li>
     *   <li>fraction ≤ oldThreshold → nutritionScaleOld（默认 0.5）</li>
     * </ul>
     *
     * <p>边界约定：STALE 区间下界用严格 &gt;，与 {@link FreshnessMath#grade}
     * 的 {@code fraction <= 0.25 → OLD} 对齐（0.25 恰好落在 OLD）。
     */
    public static double nutritionScale(double fraction) {
        ServerConfig cfg = ServerConfig.get();
        if (fraction > cfg.staleThreshold()) return 1.0;
        if (fraction > cfg.oldThreshold()) return cfg.nutritionScaleStale();
        return cfg.nutritionScaleOld();
    }

    /**
     * 低新鲜度「饥饿」debuff 判定。
     *
     * <p>仅在 fraction ≤ oldThreshold 时以 {@code hungerEffectChance} 概率触发。
     * 返回 true 后由调用方施加 {@link net.minecraft.world.effect.MobEffects#HUNGER}。
     */
    public static boolean shouldApplyHunger(double fraction) {
        return fraction <= ServerConfig.get().oldThreshold()
            && ThreadLocalRandom.current().nextDouble() < ServerConfig.get().hungerEffectChance();
    }
}
