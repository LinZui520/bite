package com.eamon.bite.mixin;

import com.eamon.bite.freshness.MergeSnapshot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 合并站点：{@code SimpleContainer.moveItemsBetweenStacks}（{@code addItem} 等
 * 容器间转移路径）。方法体 {@code destination.grow(diff)} 原地扩容 dest，
 * 快照-重算模式见 {@link com.eamon.bite.freshness.StackingRules} 类 javadoc。
 */
@Mixin(SimpleContainer.class)
public abstract class SimpleContainerMixin {
    @Unique private MergeSnapshot bite$snap;

    @Inject(method = "moveItemsBetweenStacks", at = @At("HEAD"))
    private void bite$capture(ItemStack source, ItemStack destination, CallbackInfo ci) {
        bite$snap = MergeSnapshot.capture(destination, source);
    }

    @Inject(method = "moveItemsBetweenStacks", at = @At("TAIL"))
    private void bite$reconcile(ItemStack source, ItemStack destination, CallbackInfo ci) {
        if (bite$snap != null) {
            bite$snap.reconcile(destination);
        }
        bite$snap = null;
    }
}
