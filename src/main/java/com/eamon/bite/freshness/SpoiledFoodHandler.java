package com.eamon.bite.freshness;

import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 进食惩罚：营养按新鲜度档位缩放，低新鲜度概率附加「饥饿」debuff。
 * 阈值全部来自 {@link ServerConfig}。
 */
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

    /**
     * 对吃完后的玩家按新鲜度施加惩罚（{@code finishUsingItem} RETURN 时由
     * {@link EatSnapshot} 调用；foodBefore/saturationBefore 为 HEAD 时的快照）。
     *
     * <p>缩放语义：
     * <ul>
     *   <li>营养：delta=0（满食欲被原版 clamp 的 no-op）时跳过</li>
     *   <li>饱和度：<b>独立于营养缩放</b>——满食欲时原版 {@code FoodData.eat}
     *       仍添加饱和度，不缩放会漏惩罚（曾为真实 bug）</li>
     *   <li>饱和度上限对齐原版语义：clamp 到缩放后的 foodLevel</li>
     * </ul>
     */
    static void applyEatPenalty(Player player, FreshnessStamp stamp, ShelfLife life,
                                int foodBefore, float saturationBefore) {
        double fraction = FreshnessMath.fraction(FreshnessClock.now(), stamp, life);
        double scale = nutritionScale(fraction);
        if (scale < 1.0) {
            FoodData food = player.getFoodData();
            // 营养缩放：delta=0（满食欲被原版 clamp）时为 no-op，跳过
            int delta = food.getFoodLevel() - foodBefore;
            if (delta > 0) {
                food.setFoodLevel(foodBefore + (int) Math.round(delta * scale));
            }
            // 饱和度缩放：独立于营养——满食欲（delta=0）时原版仍加饱和度，必须照样缩放
            float satDelta = food.getSaturationLevel() - saturationBefore;
            if (satDelta != 0.0f) {
                float scaled = saturationBefore + satDelta * (float) scale;
                food.setSaturation(Mth.clamp(scaled, 0.0F, food.getFoodLevel()));
            }
        }
        if (shouldApplyHunger(fraction)) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER,
                ServerConfig.get().hungerEffectDurationTicks(), 0));
        }
    }
}
