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
 * 合并站点：{@code Slot.safeInsert}（手动放入槽位）。快照-重算模式见
 * {@link StackingRules} 类 javadoc。只 hook 双参重载——单参版本转发到这里。
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
