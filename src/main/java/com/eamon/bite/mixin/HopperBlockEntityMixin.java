package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 合并站点：{@code HopperBlockEntity.tryMoveInItem}（漏斗/漏斗矿车转移）。
 * 快照-重算模式见 {@link StackingRules} 类 javadoc。
 *
 * <p>漏斗的 count 移动（{@code current.grow}）发生在自家 tryMoveInItem 里，
 * 与 {@code SimpleContainer.moveItemsBetweenStacks} 是独立路径，需单独挂。
 * 该方法在 addItem 循环中被逐槽位调用，HEAD/TAIL 自成一对。
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Unique private static FreshnessStamp bite$destStamp;
    @Unique private static int bite$destCount;
    @Unique private static FreshnessStamp bite$originStamp;

    @Inject(method = "tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private static void bite$capture(@Nullable Container from, Container container, ItemStack origin, int slot, @Nullable Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack dest = container.getItem(slot);
        bite$destStamp = dest.isEmpty() ? null : dest.get(BiteComponents.FRESHNESS);
        bite$destCount = dest.getCount();
        bite$originStamp = origin.get(BiteComponents.FRESHNESS);
    }

    @Inject(method = "tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private static void bite$reconcile(@Nullable Container from, Container container, ItemStack origin, int slot, @Nullable Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        if (bite$destStamp != null && bite$originStamp != null) {
            ItemStack dest = container.getItem(slot);
            StackingRules.reconcile(dest, bite$destStamp, bite$destCount, bite$originStamp);
        }
        bite$reset();
    }

    @Unique
    private static void bite$reset() {
        bite$destStamp = null;
        bite$originStamp = null;
        bite$destCount = 0;
    }
}
