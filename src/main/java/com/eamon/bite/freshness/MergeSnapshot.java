package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import net.minecraft.world.item.ItemStack;

/**
 * 快照-重算模式的一次合并现场，由各合并站点 mixin 持有（站点清单见
 * {@link StackingRules} 类 javadoc）：HEAD 时 {@link #capture} 捕获，
 * RETURN/TAIL 时对合并后的 dest 调 {@link #reconcile}。
 *
 * <p>任一方缺 FRESHNESS 戳时 {@link #capture} 返回 null——该次合并不需要
 * 重算（dest 与 origin 至少一方未打标或为空）。
 *
 * <p><b>无戳 origin 例外</b>（熔炉场景）：{@link #captureUnstamped} 允许
 * origin 无戳——按「全新」参与加权平均（recipe 模板每次 assemble 新建，
 * 永远无戳，但产物合并语义上等价于并入全新的一份）。
 */
public final class MergeSnapshot {
    private final FreshnessStamp destStamp;
    private final int destCount;
    private final FreshnessStamp originStamp;

    private MergeSnapshot(FreshnessStamp destStamp, int destCount, FreshnessStamp originStamp) {
        this.destStamp = destStamp;
        this.destCount = destCount;
        this.originStamp = originStamp;
    }

    /** 捕获合并前的 dest（数量将增长的一方）与 origin（并入的一方）。双方须有戳。 */
    public static MergeSnapshot capture(ItemStack dest, ItemStack origin) {
        FreshnessStamp ds = dest.isEmpty() ? null : dest.get(BiteComponents.FRESHNESS);
        FreshnessStamp os = origin.isEmpty() ? null : origin.get(BiteComponents.FRESHNESS);
        if (ds == null || os == null) return null;
        return new MergeSnapshot(ds, dest.getCount(), os);
    }

    /**
     * 熔炉变体：origin（新出炉产物）允许无戳——按全新（stamp=now）参与。
     * dest 无戳时返回 null（首份产物走继承打标，不是合并）。
     */
    public static MergeSnapshot captureUnstamped(ItemStack dest, ItemStack origin, long now) {
        FreshnessStamp ds = dest.isEmpty() ? null : dest.get(BiteComponents.FRESHNESS);
        if (ds == null) return null;
        FreshnessStamp os = origin.isEmpty() || !origin.has(BiteComponents.FRESHNESS)
            ? new FreshnessStamp(now)
            : origin.get(BiteComponents.FRESHNESS);
        return new MergeSnapshot(ds, dest.getCount(), os);
    }

    /** 对合并后的 dest 重算加权平均新鲜度（dest 未增长时为 no-op）。 */
    public void reconcile(ItemStack dest) {
        StackingRules.reconcile(dest, destStamp, destCount, originStamp);
    }
}
