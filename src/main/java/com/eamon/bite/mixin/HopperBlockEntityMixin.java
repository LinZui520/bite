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
 * 合并重算站点 6（任务说明外的补充站点）：漏斗/矿车漏斗转移（spec §6.2-B）。
 *
 * <p><b>brief 草图误差修正</b>：brief 的 5 站点表把「hopper+container transfer」
 * 映射到 {@code SimpleContainer.moveItemsBetweenStacks}——genSources 验证这是错的。
 * 漏斗的 count 移动发生在 {@code HopperBlockEntity.tryMoveInItem} 内的
 * {@code current.grow(count); itemStack.shrink(count);}，与 SimpleContainer 完全独立。
 * 不加此 mixin，漏斗路径的合并将是 destination-wins（spec §6.3 已知边界），
 * 与 spec §6.2-B 固定语义冲突。本 mixin 闭合该缺口。
 *
 * <p>26.2 签名（genSources + javap 验证）：
 * <ul>
 *   <li>{@code private static ItemStack tryMoveInItem(@Nullable Container from,
 *       Container container, ItemStack itemStack, int slot, @Nullable Direction direction)}
 *       —— 返回剩余 ItemStack。</li>
 *   <li>合并判定 {@code canMergeItems} 走 {@code ItemStack.isSameItemSameComponents}
 *       （Task 9 放宽已覆盖）；count 移动后 reconcile。</li>
 * </ul>
 *
 * <p>快照-重算：HEAD 时 {@code container.getItem(slot)} 取 dest 引用、记录 stamp/count
 * + origin（itemStack）的 stamp；TAIL 时同一 {@code container.getItem(slot)} 引用已 grow，
 * reconcile。{@code tryMoveInItem} 在 {@code addItem} 循环中被逐槽位调用，
 * 每次 HEAD/TAIL 自成一对，静态状态在 TAIL 立即 reset，无跨调用残留。
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
