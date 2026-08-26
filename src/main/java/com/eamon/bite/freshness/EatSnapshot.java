package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

/**
 * 进食现场快照（快照-重算模式）：{@code ItemStack.finishUsingItem} 的 HEAD
 * 时 {@link #capture} 捕获，RETURN 时对已吃完的玩家调 {@link #applyPenalty}。
 *
 * <p>捕获条件：栈已打标、可腐坏、进食者是玩家。任一不满足返回 null
 * （该次进食无需惩罚，mixin 直接跳过）。
 */
public final class EatSnapshot {
    private final FreshnessStamp stamp;
    private final ShelfLife life;
    private final int foodBefore;
    private final float saturationBefore;

    private EatSnapshot(FreshnessStamp stamp, ShelfLife life, int foodBefore, float saturationBefore) {
        this.stamp = stamp;
        this.life = life;
        this.foodBefore = foodBefore;
        this.saturationBefore = saturationBefore;
    }

    /** 捕获开始进食时的栈组件与玩家 FoodData。 */
    public static EatSnapshot capture(ItemStack stack, LivingEntity entity) {
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (stamp == null || life == null || life.spoilTicks() <= 0) return null;
        if (!(entity instanceof Player player)) return null;
        FoodData food = player.getFoodData();
        return new EatSnapshot(stamp, life, food.getFoodLevel(), food.getSaturationLevel());
    }

    /** 对吃完后的玩家施加惩罚（营养/饱和度缩放 + 概率饥饿 debuff）。 */
    public void applyPenalty(Player player) {
        SpoiledFoodHandler.applyEatPenalty(player, stamp, life, foodBefore, saturationBefore);
    }
}
