package com.eamon.bite.mixin;

import com.eamon.bite.freshness.EatSnapshot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进食惩罚注入点：{@code ItemStack.finishUsingItem} 的 HEAD 捕获、RETURN 施罚
 * （快照-重算模式，语义见 {@link com.eamon.bite.freshness.SpoiledFoodHandler}）。
 *
 * <p>{@code @Unique} 状态 HEAD 写、RETURN 读后立即复位；游戏逻辑单线程，
 * 同一栈不会并发进食。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackFinishMixin {
    @Unique private EatSnapshot bite$snap;

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void bite$capture(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        bite$snap = EatSnapshot.capture((ItemStack) (Object) this, entity);
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void bite$applyPenalty(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        if (bite$snap == null) return;
        // capture 非 null 已保证 entity 是 Player（EatSnapshot.capture 的捕获条件）
        bite$snap.applyPenalty((Player) entity);
        bite$snap = null;
        // 饮食补水（饥渴系统）：ITEM 返回后从 this 取实际物品
        ItemStack self = (ItemStack) (Object) this;
        com.eamon.bite.thirst.ThirstController.onConsume((Player) entity, self.getItem());
    }
}
