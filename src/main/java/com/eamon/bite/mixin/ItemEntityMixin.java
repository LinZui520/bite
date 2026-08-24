package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 合并重算站点 3/5：掉落物互相合并（spec §6.2-B）。
 *
 * <p>26.2 签名（genSources 验证，与 brief 草图有重要修正）：
 * <ul>
 *   <li>{@code public static ItemStack merge(ItemStack, ItemStack, int)} —— 纯工具方法，
 *       <b>不</b> mutate dest；返回一个新的 {@code copyWithCount} 后的栈。
 *       brief 草图 hook 此方法并 reconcile 入参 {@code destination} 是错的——dest 引用根本没增长。</li>
 *   <li>{@code private static void merge(ItemEntity, ItemStack, ItemStack)} —— 真正的合并点：
 *       调用上述工具后执行 {@code toItem.setItem(newToStack)}，<b>这里</b> dest 实际被替换。
 *       本 mixin 钩这个方法。</li>
 *   <li>{@code private static void merge(ItemEntity, ItemStack, ItemEntity, ItemStack)} —— 4 参重载，
 *       内部转发到 3 参版本；hook 3 参即覆盖两条路径。</li>
 * </ul>
 *
 * <p>快照-重算：HEAD 时记录 toStack（pre-merge dest）的 stamp/count + fromStack 的 stamp；
 * TAIL 时 {@code toItem.getItem()} 是合并后的新栈（count 已增长），对它执行 reconcile。
 * 目标方法 private static，handler 必须 static，状态用静态 {@link MergeState} 持有
 * （MC 游戏逻辑单线程，无需 ThreadLocal；reconcile 后立即 clear 防残留）。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique private static FreshnessStamp bite$destStamp;
    @Unique private static int bite$destCount;
    @Unique private static FreshnessStamp bite$originStamp;

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void bite$capture(ItemEntity toItem, ItemStack toStack, ItemStack fromStack, CallbackInfo ci) {
        bite$destStamp = toStack.isEmpty() ? null : toStack.get(BiteComponents.FRESHNESS);
        bite$destCount = toStack.getCount();
        bite$originStamp = fromStack.isEmpty() ? null : fromStack.get(BiteComponents.FRESHNESS);
    }

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private static void bite$reconcile(ItemEntity toItem, ItemStack toStack, ItemStack fromStack, CallbackInfo ci) {
        if (bite$destStamp != null && bite$originStamp != null) {
            ItemStack merged = toItem.getItem();
            StackingRules.reconcile(merged, bite$destStamp, bite$destCount, bite$originStamp);
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
