package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessStamper;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 合并重算站点 2/5：玩家背包放入 + 地面拾取（spec §6.2-B）。
 *
 * <p>26.2 签名（genSources 验证）：
 * <ul>
 *   <li>{@code public boolean add(ItemStack)} —— 拾取/收取的公开入口。
 *       HEAD 时对入参打标（首见观察）：地上掉落物（ItemEntity 非 Container，
 *       懒扫描扫不到）拾取瞬间才首次进入观察范围。打标发生在槽位搜索
 *       （getSlotWithRemainingSpace → hasRemainingSpaceForItem →
 *       isSameItemSameComponents）之前，故放宽判定可见双方 stamp。</li>
 *   <li>{@code private int addResource(ItemStack)} —— 单参重载，内部找到可用槽位后
 *       转发到 {@code addResource(slot, itemStack)}；找不到槽位时直接返回
 *       {@code itemStack.getCount()}（无合并发生）。只 hook 2 参版本即可覆盖全部合并路径。</li>
 *   <li>{@code private int addResource(int slot, ItemStack)} —— 真正执行
 *       {@code itemStackInSlot.grow(toAdd)} 原地扩容 dest。</li>
 * </ul>
 *
 * <p>快照-重算：HEAD 时记录 dest（slot 内栈）的 stamp/count 与 origin 的 stamp；
 * RETURN 时若 dest 引用未变且数量增加，reconcile。
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Unique private FreshnessStamp bite$destStamp;
    @Unique private int bite$destCount;
    @Unique private FreshnessStamp bite$originStamp;

    /** 拾取首见打标：任何经 Inventory.add 入包的食物先打标再参与堆叠判定（幂等）。 */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void bite$stampOnAdd(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        FreshnessStamper.stamp(stack, FreshnessClock.now());
    }

    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"))
    private void bite$capture(int slot, ItemStack origin, CallbackInfoReturnable<Integer> cir) {
        ItemStack dest = ((Inventory) (Object) this).getItem(slot);
        bite$destStamp = dest.isEmpty() ? null : dest.get(BiteComponents.FRESHNESS);
        bite$destCount = dest.getCount();
        bite$originStamp = origin.get(BiteComponents.FRESHNESS);
    }

    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"))
    private void bite$reconcile(int slot, ItemStack origin, CallbackInfoReturnable<Integer> cir) {
        if (bite$destStamp != null && bite$originStamp != null) {
            ItemStack dest = ((Inventory) (Object) this).getItem(slot);
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
