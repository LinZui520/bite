package com.eamon.bite.mixin;

import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 等价性放宽（spec §6.2-A）：在 {@code ItemStack.isSameItemSameComponents} 返回
 * {@code false} 时，若 {@link StackingRules#canMergeRelaxed} 判定可放宽合并，
 * 则改返回 {@code true}。单点覆盖全部原版堆叠判定调用点。
 *
 * <p>26.2 检查点：
 * <ul>
 *   <li>目标方法签名 {@code static boolean isSameItemSameComponents(ItemStack, ItemStack)}
 *       —— 经 {@code javap} 验证存在（唯一同名方法）。</li>
 *   <li>{@code @Inject} 静态方法要求 handler 为 static，故此处为
 *       {@code private static void}。</li>
 *   <li>early-return：原版返回 true 时直接返回，避免无谓的 canMergeRelaxed 调用。</li>
 * </ul>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "isSameItemSameComponents", at = @At("RETURN"), cancellable = true)
    private static void bite$relaxFreshnessEquality(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (StackingRules.canMergeRelaxed(a, b)) cir.setReturnValue(true);
    }
}
