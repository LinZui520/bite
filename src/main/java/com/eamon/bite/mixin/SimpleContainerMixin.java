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
 * 合并重算站点 4/5：容器间转移（漏斗 / SimpleContainer.addItem 等）（spec §6.2-B）。
 *
 * <p>26.2 签名（genSources 验证，与 brief 草图有修正）：
 * <ul>
 *   <li>{@code private void moveItemsBetweenStacks(ItemStack source, ItemStack destination)} ——
 *       <b>实例方法</b>（非 static），brief 草图写 static 是错的。本 mixin 用实例字段。</li>
 *   <li>方法体执行 {@code destination.grow(diff); source.shrink(diff);} ——
 *       dest 引用不变、原地扩容，capture/reconcile on destination 生效。</li>
 *   <li>调用方：{@code moveItemToOccupiedSlotsWithSameType}（addItem 路径）、
 *       漏斗（HopperBlockEntity）通过 {@code ContainerHelper} 或直接调用同款逻辑。
 *       gametest 通过 addItem 验证；漏斗走同一方法，等价覆盖。</li>
 * </ul>
 *
 * <p>快照-重算：HEAD 时记录 destination 的 stamp/count + source 的 stamp；
 * TAIL 时 destination 已 grow，reconcile。
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
