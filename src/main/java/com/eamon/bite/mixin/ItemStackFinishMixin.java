package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessMath;
import com.eamon.bite.freshness.SpoiledFoodHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进食惩罚（spec §8）：在 {@code ItemStack.finishUsingItem} 的 HEAD 捕获玩家
 * FoodData 快照，RETURN 处按新鲜度缩放营养与饱和度增量，并在 OLD 区间概率
 * 施加「饥饿」debuff。
 *
 * <p>缩放语义：
 * <ul>
 *   <li>营养：delta=0（满食欲被原版 clamp 的 no-op）时跳过</li>
 *   <li>饱和度：<b>独立于营养缩放</b>——满食欲时原版 {@code FoodData.eat}
 *       仍添加饱和度，不缩放会漏惩罚（曾为真实 bug）</li>
 *   <li>饱和度上限对齐原版语义：clamp 到缩放后的 foodLevel</li>
 * </ul>
 *
 * <p>{@code @Unique} 状态 HEAD 写、RETURN 读后立即复位；游戏逻辑单线程，
 * 同一栈不会并发进食。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackFinishMixin {
    @Unique private int bite$foodBefore;
    @Unique private float bite$saturationBefore;
    @Unique private FreshnessStamp bite$stamp;
    @Unique private ShelfLife bite$life;
    @Unique private boolean bite$tracked;

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void bite$capture(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        bite$tracked = false;
        ItemStack self = (ItemStack) (Object) this;
        bite$stamp = self.get(BiteComponents.FRESHNESS);
        bite$life = self.get(BiteComponents.SHELF_LIFE);
        if (bite$stamp == null || bite$life == null || bite$life.spoilTicks() <= 0) return;
        if (!(entity instanceof Player player)) return;
        FoodData food = player.getFoodData();
        bite$foodBefore = food.getFoodLevel();
        bite$saturationBefore = food.getSaturationLevel();
        bite$tracked = true;
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void bite$applyPenalty(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        if (!bite$tracked) return;
        bite$tracked = false;
        // entity 在 HEAD 已通过 Player 检查（bite$tracked 只在其后置位），此处必为 Player
        Player player = (Player) entity;
        double fraction = FreshnessMath.fraction(FreshnessClock.now(), bite$stamp, bite$life);
        double scale = SpoiledFoodHandler.nutritionScale(fraction);
        if (scale < 1.0) {
            FoodData food = player.getFoodData();
            // 营养缩放：delta=0（满食欲被原版 clamp）时为 no-op，跳过
            int delta = food.getFoodLevel() - bite$foodBefore;
            if (delta > 0) {
                food.setFoodLevel(bite$foodBefore + (int) Math.round(delta * scale));
            }
            // 饱和度缩放：独立于营养——满食欲（delta=0）时原版仍加饱和度，必须照样缩放
            float satDelta = food.getSaturationLevel() - bite$saturationBefore;
            if (satDelta != 0.0f) {
                float scaled = bite$saturationBefore + satDelta * (float) scale;
                food.setSaturation(Mth.clamp(scaled, 0.0F, food.getFoodLevel()));
            }
        }
        if (SpoiledFoodHandler.shouldApplyHunger(fraction)) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER,
                ServerConfig.get().hungerEffectDurationTicks(), 0));
        }
    }
}
