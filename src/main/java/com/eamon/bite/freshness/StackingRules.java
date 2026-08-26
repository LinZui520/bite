package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.world.item.ItemStack;

/**
 * 饥荒式堆叠规则：不同新鲜度的同类食物允许合并，合并后新鲜度按数量加权平均。
 * 被 mixin 调用；无 Level 上下文，时间取 {@link FreshnessClock#now()}。
 *
 * <p>原版在 {@code ItemStack.isSameItemSameComponents} 里做组件相等性比较，
 * FRESHNESS 戳不同的两堆食物会被判为不可堆叠。放开它需要两处配合：
 * <ol>
 *   <li>{@link #canMergeRelaxed}——等价性放宽判定。ItemStackMixin 挂在
 *       {@code isSameItemSameComponents} 的 RETURN，原版判 false 时兜底改 true。</li>
 *   <li>{@link #reconcile}——合并重算（快照-重算模式：HEAD 捕获 dest 的
 *       stamp/count 与 origin 的 stamp；RETURN/TAIL 时若 dest 数量增长，
 *       反解出加权平均时间戳写回）。</li>
 * </ol>
 *
 * <p>合并站点一览——每处都是原版「原地 grow dest」的独立代码路径，各有 mixin，
 * 均按上述快照-重算模式实现：
 * <ul>
 *   <li>SlotMixin → {@code Slot.safeInsert}：手动放入槽位</li>
 *   <li>InventoryMixin → {@code Inventory.addResource}：背包放入 / 地面拾取</li>
 *   <li>ItemEntityMixin → {@code ItemEntity.merge}：掉落物互相合并</li>
 *   <li>SimpleContainerMixin → {@code moveItemsBetweenStacks}：容器间转移</li>
 *   <li>AbstractContainerMenuMixin → {@code moveItemStackTo}：Shift 点击转移</li>
 *   <li>HopperBlockEntityMixin → {@code tryMoveInItem}：漏斗转移</li>
 * </ul>
 */
public final class StackingRules {
    private StackingRules() {}

    /** 等价性放宽判定：同 item、双方已打标、保质期有限、双方未完全变质。 */
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

    /**
     * 合并重算：dest 数量已增长 delta = dest.getCount() - destCountBefore 时调用。
     * 契约：两个 stamp 参数非 null（各站点 capture 时判空后才记录）。
     */
    public static void reconcile(ItemStack dest, FreshnessStamp destStampBefore, int destCountBefore, FreshnessStamp originStamp) {
        int delta = dest.getCount() - destCountBefore;
        if (delta <= 0) return;
        ShelfLife life = dest.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return;
        long now = FreshnessClock.now();
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, destStampBefore, destCountBefore, originStamp, delta, life);
        dest.set(BiteComponents.FRESHNESS, merged);
    }
}
