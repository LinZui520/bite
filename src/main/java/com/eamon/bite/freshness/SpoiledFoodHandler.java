package com.eamon.bite.freshness;

import com.eamon.bite.config.ServerConfig;
import java.util.concurrent.ThreadLocalRandom;

/** 进食惩罚：营养按新鲜度档位缩放，低新鲜度概率附加「饥饿」debuff。阈值全部来自 {@link ServerConfig}。 */
public final class SpoiledFoodHandler {
    private SpoiledFoodHandler() {}

    /**
     * 营养缩放系数：fraction &gt; stale → 1.0；old &lt; fraction ≤ stale → stale 档；
     * ≤ old → old 档。STALE 下界用严格 &gt;，与 {@link FreshnessMath#grade} 的档位边界对齐。
     */
    public static double nutritionScale(double fraction) {
        ServerConfig cfg = ServerConfig.get();
        if (fraction > cfg.staleThreshold()) return 1.0;
        if (fraction > cfg.oldThreshold()) return cfg.nutritionScaleStale();
        return cfg.nutritionScaleOld();
    }

    /** fraction ≤ oldThreshold 时按配置概率触发（调用方施加 HUNGER 效果）。 */
    public static boolean shouldApplyHunger(double fraction) {
        return fraction <= ServerConfig.get().oldThreshold()
            && ThreadLocalRandom.current().nextDouble() < ServerConfig.get().hungerEffectChance();
    }
}
