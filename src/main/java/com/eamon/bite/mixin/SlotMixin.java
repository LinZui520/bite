package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 合并重算站点 1/5：手动放入槽位（spec §6.2-B）。
 *
 * <p>26.2 签名（genSources 验证）：
 * <ul>
 *   <li>{@code public ItemStack safeInsert(ItemStack stack)} —— 单参重载，内部
 *       直接转发到 {@code safeInsert(stack, stack.getCount())}，故只 hook 2 参版本即可覆盖两条路径。</li>
 *   <li>{@code public ItemStack safeInsert(ItemStack inputStack, int inputAmount)} ——
 *       真正执行 grow 的位置：{@code slotStack.grow(transferableItemCount)} 原地扩容 dest。</li>
 * </ul>
 *
 * <p>快照-重算模式：HEAD 时记录 dest（slotStack）的 stamp/count 与 origin（inputStack）
 * 的 stamp；RETURN 时若 dest 仍持有相同引用且数量增加，调用
 * {@link StackingRules#reconcile}。dest 为空时 stamp=null，跳过。
 * 字段用 {@code @Unique} 前缀 {@code bite$}，每次 RETURN 后 reset 防止跨调用残留。
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Unique private FreshnessStamp bite$destStamp;
    @Unique private int bite$destCount;
    @Unique private FreshnessStamp bite$originStamp;

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private void bite$capture(ItemStack origin, int count, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack dest = ((Slot) (Object) this).getItem();
        bite$destStamp = dest.isEmpty() ? null : dest.get(BiteComponents.FRESHNESS);
        bite$destCount = dest.getCount();
        bite$originStamp = origin.get(BiteComponents.FRESHNESS);
    }

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void bite$reconcile(ItemStack origin, int count, CallbackInfoReturnable<ItemStack> cir) {
        if (bite$destStamp != null && bite$originStamp != null) {
            ItemStack dest = ((Slot) (Object) this).getItem();
            StackingRules.reconcile(dest, bite$destStamp, bite$destCount, bite$originStamp);
        }
        bite$reset();
    }

    @Unique
    private void bite$reset() {
        bite$destStamp = null;
        bite$originStamp = null;
        bite$destCount = 0;
    }
}
