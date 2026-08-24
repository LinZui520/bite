package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessMath;
import com.eamon.bite.freshness.SpoiledFoodHandler;
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
 * 进食惩罚 mixin（spec §8）：在 {@code ItemStack.finishUsingItem} 的 HEAD 捕获
 * 玩家 FoodData 快照，RETURN 处按新鲜度缩放营养增量 + 饱和度增量，
 * 并在 OLD 区间概率施加「饥饿」debuff。
 *
 * <p>26.2 检查点（genSources 验证）：
 * <ul>
 *   <li>目标方法 {@code public ItemStack finishUsingItem(Level, LivingEntity)}
 *       —— 位于 {@link ItemStack}（非 {@code Item}），第 391 行。
 *       内部委托 {@code Item.finishUsingItem(this, level, entity)} →
 *       {@code Consumable.onConsume} → {@code FoodProperties.onConsume}
 *       → {@code player.getFoodData().eat(this)}，故 RETURN 时 FoodData 已更新。</li>
 *   <li>{@link MobEffects#HUNGER} 在 26.2 为 {@code Holder<MobEffect>}，
 *       {@link MobEffectInstance#MobEffectInstance(Holder, int, int)} 构造器存在。</li>
 *   <li>{@link FoodData#getFoodLevel}/{@code setFoodLevel(int)} /
 *       {@link FoodData#getSaturationLevel}/{@code setSaturation(float)} 签名确认。</li>
 *   <li>{@link FreshnessClock#now()} 用于无 Level 上下文时的 game time 读取
 *       （与 {@code StackingRules} 等其它静态钩子一致）。</li>
 * </ul>
 *
 * <p>状态管理：{@code @Unique} 实例字段在 HEAD 写入、RETURN 读取后复位。
 * 同一 ItemStack 实例不会出现并发 finishUsingItem（MC 服务器单线程游戏逻辑），
 * 且 {@code bite$tracked} 在 RETURN 立即置 false，无跨调用残留。
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
        if (!(entity instanceof Player player)) return;
        double fraction = FreshnessMath.fraction(FreshnessClock.now(), bite$stamp, bite$life);
        double scale = SpoiledFoodHandler.nutritionScale(fraction);
        FoodData food = player.getFoodData();
        int delta = food.getFoodLevel() - bite$foodBefore;
        if (delta > 0 && scale < 1.0) {
            food.setFoodLevel(bite$foodBefore + (int) Math.round(delta * scale));
            float satDelta = food.getSaturationLevel() - bite$saturationBefore;
            food.setSaturation(Math.max(0.0f, bite$saturationBefore + satDelta * (float) scale));
        }
        if (SpoiledFoodHandler.shouldApplyHunger(fraction)) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER,
                ServerConfig.get().hungerEffectDurationTicks(), 0));
        }
    }
}
