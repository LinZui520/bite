package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 合并重算站点 5/5：Shift 点击转移（spec §6.2-B）。
 *
 * <p>26.2 签名（genSources 验证）：
 * <ul>
 *   <li>{@code protected boolean moveItemStackTo(ItemStack, int, int, boolean)} ——
 *       实例方法。循环内对每个匹配槽位调用
 *       {@code target.setCount(totalStack)}（原地改写 dest 引用的 count），
 *       可一次调用影响多个 dest 槽位。</li>
 *   <li>两阶段：先合并到已存在的同 item 槽位（grow target），再放入空槽位（split origin）。
 *       只有第一阶段产生合并，需要 reconcile。</li>
 * </ul>
 *
 * <p>捕获策略（brief ⚠️ checkpoint 的允许替代方案）：放弃 {@code @WrapOperation + @Local(ordinal=1)}
 * （LVT ordinal 在跨版本下脆弱），改用「HEAD 快照所有槽位 → TAIL 比对增长槽位」：
 * <ul>
 *   <li>HEAD：记录 origin 的 stamp；遍历 {@code this.slots}，对每个非空且有 FRESHNESS 的 ItemStack
 *       引用，存入 {@link IdentityHashMap}（键 = 引用，值 = (stamp, count) 快照）。</li>
 *   <li>TAIL：再次遍历 slots，对每个 ItemStack 引用若在快照中且 count 增长，调用 reconcile。
 *       空槽位（Phase 2 setByPlayer 写入的 split）不在快照中，自动跳过——这些是 origin 的副本，非合并。</li>
 * </ul>
 *
 * <p>性能：单次 shift-click 遍历 slots 两次（典型 ≤100 槽位），远低于热路径开销，可接受。
 * 引用稳定性：moveItemStackTo 仅 {@code setCount} 不替换引用，故 HEAD/TAIL 间 ItemStack 引用稳定。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Unique private FreshnessStamp bite$originStamp;
    @Unique private final Map<ItemStack, Snapshot> bite$snapshots = new IdentityHashMap<>();

    @Unique
    private static final class Snapshot {
        final FreshnessStamp stamp;
        final int count;
        Snapshot(FreshnessStamp stamp, int count) {
            this.stamp = stamp;
            this.count = count;
        }
    }

    @Inject(method = "moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z", at = @At("HEAD"))
    private void bite$capture(ItemStack origin, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        bite$snapshots.clear();
        bite$originStamp = origin.get(BiteComponents.FRESHNESS);
        if (bite$originStamp == null) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        for (Slot slot : self.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.has(BiteComponents.FRESHNESS)) {
                bite$snapshots.put(stack, new Snapshot(stack.get(BiteComponents.FRESHNESS), stack.getCount()));
            }
        }
    }

    @Inject(method = "moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z", at = @At("TAIL"))
    private void bite$reconcile(ItemStack origin, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        if (bite$originStamp == null) {
            bite$reset();
            return;
        }
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        for (Slot slot : self.slots) {
            ItemStack stack = slot.getItem();
            Snapshot snap = bite$snapshots.get(stack);
            if (snap != null && stack.getCount() > snap.count) {
                StackingRules.reconcile(stack, snap.stamp, snap.count, bite$originStamp);
            }
        }
        bite$reset();
    }

    @Unique
    private void bite$reset() {
        bite$originStamp = null;
        bite$snapshots.clear();
    }
}
