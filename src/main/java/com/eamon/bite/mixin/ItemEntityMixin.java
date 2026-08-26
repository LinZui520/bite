package com.eamon.bite.mixin;

import com.eamon.bite.freshness.MergeSnapshot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 合并站点：{@code ItemEntity.merge}（掉落物互相合并）。快照-重算模式见
 * {@link com.eamon.bite.freshness.StackingRules} 类 javadoc。
 *
 * <p>注入点是私有 {@code merge(ItemEntity, ItemStack, ItemStack)}：公开的
 * {@code merge(ItemStack, ItemStack, int)} 是纯函数、不改动 dest；真正替换
 * toItem 内容的是这个私有版本（4 参私有重载也转发到它）。目标方法为 static，
 * 快照状态随之 static——游戏逻辑单线程，TAIL 立即置 null 无残留。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique private static MergeSnapshot bite$snap;

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void bite$capture(ItemEntity toItem, ItemStack toStack, ItemStack fromStack, CallbackInfo ci) {
        bite$snap = MergeSnapshot.capture(toStack, fromStack);
    }

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private static void bite$reconcile(ItemEntity toItem, ItemStack toStack, ItemStack fromStack, CallbackInfo ci) {
        if (bite$snap != null) {
            bite$snap.reconcile(toItem.getItem());
        }
        bite$snap = null;
    }
}
