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
 * 合并站点：{@code AbstractContainerMenu.moveItemStackTo}（Shift 点击转移）。
 * 站点清单与快照-重算模式见 {@link StackingRules} 类 javadoc。
 *
 * <p>本站点特殊：一次调用可能 grow 多个 dest 槽位，且方法内没有可稳定注入的
 * 单点。放弃 {@code @Local} 捕获局部变量（LVT ordinal 跨版本脆弱），改为
 * 「HEAD 快照全部已打标槽位 → TAIL 比对数量增长后逐个 reconcile」：
 * <ul>
 *   <li>HEAD：记录 origin 的 stamp；对每个非空且有 FRESHNESS 的槽位栈，
 *       以 ItemStack 引用为键存入 {@link IdentityHashMap}（本路径只 setCount
 *       不替换引用，引用稳定）。</li>
 *   <li>TAIL：对快照中数量增长的槽位 reconcile。第二阶段（split 到空槽位）
 *       写入的是 origin 副本，不在快照中，自动跳过。</li>
 * </ul>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Unique private FreshnessStamp bite$originStamp;
    @Unique private final Map<ItemStack, Snapshot> bite$snapshots = new IdentityHashMap<>();

    @Unique
    private record Snapshot(FreshnessStamp stamp, int count) {}

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
            if (snap != null && stack.getCount() > snap.count()) {
                StackingRules.reconcile(stack, snap.stamp(), snap.count(), bite$originStamp);
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
