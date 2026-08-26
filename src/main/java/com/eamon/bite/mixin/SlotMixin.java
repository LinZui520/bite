package com.eamon.bite.mixin;

import com.eamon.bite.freshness.MergeSnapshot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 合并站点：{@code Slot.safeInsert}（手动放入槽位）。快照-重算模式见
 * {@link com.eamon.bite.freshness.StackingRules} 类 javadoc。只 hook 双参重载——
 * 单参版本转发到这里。
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Unique private MergeSnapshot bite$snap;

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private void bite$capture(ItemStack origin, int count, CallbackInfoReturnable<ItemStack> cir) {
        bite$snap = MergeSnapshot.capture(((Slot) (Object) this).getItem(), origin);
    }

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void bite$reconcile(ItemStack origin, int count, CallbackInfoReturnable<ItemStack> cir) {
        if (bite$snap != null) {
            bite$snap.reconcile(((Slot) (Object) this).getItem());
        }
        bite$snap = null;
    }
}
