package com.eamon.bite.mixin;

import com.eamon.bite.freshness.MergeSnapshot;
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
 * 快照-重算模式见 {@link com.eamon.bite.freshness.StackingRules} 类 javadoc。
 *
 * <p>漏斗的 count 移动（{@code current.grow}）发生在自家 tryMoveInItem 里，
 * 与 {@code SimpleContainer.moveItemsBetweenStacks} 是独立路径，需单独挂。
 * 该方法在 addItem 循环中被逐槽位调用，HEAD/RETURN 自成一对；目标方法为
 * static，快照状态随之 static，RETURN 立即置 null 无残留。
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Unique private static MergeSnapshot bite$snap;

    @Inject(method = "tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private static void bite$capture(@Nullable Container from, Container container, ItemStack origin, int slot, @Nullable Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        bite$snap = MergeSnapshot.capture(container.getItem(slot), origin);
    }

    @Inject(method = "tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private static void bite$reconcile(@Nullable Container from, Container container, ItemStack origin, int slot, @Nullable Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        if (bite$snap != null) {
            bite$snap.reconcile(container.getItem(slot));
        }
        bite$snap = null;
    }
}
