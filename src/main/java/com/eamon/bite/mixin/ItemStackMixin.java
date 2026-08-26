package com.eamon.bite.mixin;

import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 等价性放宽（spec §6.2-A）：原版 {@code isSameItemSameComponents} 判 false 时，
 * 若 {@link StackingRules#canMergeRelaxed} 允许，兜底改判 true——
 * 单点覆盖全部原版堆叠判定。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "isSameItemSameComponents", at = @At("RETURN"), cancellable = true)
    private static void bite$relaxFreshnessEquality(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (StackingRules.canMergeRelaxed(a, b)) cir.setReturnValue(true);
    }
}
