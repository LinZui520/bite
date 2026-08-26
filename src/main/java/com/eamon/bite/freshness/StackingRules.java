package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.world.item.ItemStack;

/** 饥荒式堆叠规则（spec §6）。被 mixin 调用；无 Level 上下文，时间取 FreshnessClock。 */
public final class StackingRules {
    private StackingRules() {}

    /** 等价性放宽判定：同 item、双方已打标、保质期有限、双方未腐坏（fraction>0）。 */
    public static boolean canMergeRelaxed(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || !a.is(b.getItem())) return false;
        FreshnessStamp sa = a.get(BiteComponents.FRESHNESS);
        FreshnessStamp sb = b.get(BiteComponents.FRESHNESS);
        if (sa == null || sb == null) return false;
        ShelfLife la = a.get(BiteComponents.SHELF_LIFE);
        ShelfLife lb = b.get(BiteComponents.SHELF_LIFE);
        if (la == null || la.spoilTicks() <= 0) return false;
        if (lb == null || lb.spoilTicks() <= 0) return false;
        long now = FreshnessClock.now();
        return FreshnessMath.fraction(now, sa, la) > 0.0 && FreshnessMath.fraction(now, sb, lb) > 0.0;
    }

    /** 合并重算：dest 数量已增加 delta = dest.getCount() - destCountBefore 时调用。 */
    public static void reconcile(ItemStack dest, FreshnessStamp destStampBefore, int destCountBefore, FreshnessStamp originStamp) {
        int delta = dest.getCount() - destCountBefore;
        // null 检查省略：全部 6 个调用方（Slot/Inventory/ItemEntity/SimpleContainer/
        // Hopper/AbstractContainerMenu mixin）在调用前已保证两个 stamp 非 null
        if (delta <= 0) return;
        ShelfLife life = dest.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return;
        long now = FreshnessClock.now();
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, destStampBefore, destCountBefore, originStamp, delta, life);
        dest.set(BiteComponents.FRESHNESS, merged);
    }

}