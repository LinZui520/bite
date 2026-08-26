package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
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
 * 快照-重算模式见 {@link StackingRules} 类 javadoc。
 */
@Mixin(SimpleContainer.class)
public abstract class SimpleContainerMixin {
    @Unique private FreshnessStamp bite$destStamp;
    @Unique private int bite$destCount;
    @Unique private FreshnessStamp bite$originStamp;

    @Inject(method = "moveItemsBetweenStacks", at = @At("HEAD"))
    private void bite$capture(ItemStack source, ItemStack destination, CallbackInfo ci) {
        bite$destStamp = destination.isEmpty() ? null : destination.get(BiteComponents.FRESHNESS);
        bite$destCount = destination.getCount();
        bite$originStamp = source.isEmpty() ? null : source.get(BiteComponents.FRESHNESS);
    }

    @Inject(method = "moveItemsBetweenStacks", at = @At("TAIL"))
    private void bite$reconcile(ItemStack source, ItemStack destination, CallbackInfo ci) {
        if (bite$destStamp != null && bite$originStamp != null) {
            StackingRules.reconcile(destination, bite$destStamp, bite$destCount, bite$originStamp);
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
