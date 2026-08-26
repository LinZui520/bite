package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.world.item.ItemStack;

/** 新鲜度纯函数。双端共用；时间一律 game time。 */
public final class FreshnessMath {
    private FreshnessMath() {}

    /** 新鲜度 [0,1]。永不腐坏（spoilTicks &lt;= 0）恒为 1。 */
    public static double fraction(long now, FreshnessStamp stamp, ShelfLife life) {
        if (life.spoilTicks() <= 0) return 1.0;
        double f = 1.0 - (double) (now - stamp.creationGameTick()) / life.spoilTicks();
        return Math.clamp(f, 0.0, 1.0);
    }

    /** 饥荒式合并：新鲜度按数量加权平均，反解出新时间戳（守恒）。 */
    public static FreshnessStamp mergeStamps(long now, FreshnessStamp s1, long count1, FreshnessStamp s2, long count2, ShelfLife life) {
        if (life.spoilTicks() <= 0) return s2;
        double f1 = fraction(now, s1, life);
        double f2 = fraction(now, s2, life);
        double merged = (f1 * count1 + f2 * count2) / (count1 + count2);
        long age = Math.round((1.0 - merged) * life.spoilTicks());
        return new FreshnessStamp(now - age);
    }

    /** 栈级判定：是否已完全变质（fraction ≤ 0）。无戳 / 永不腐坏 → false。 */
    public static boolean isSpoiled(ItemStack stack, long now) {
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        if (stamp == null) return false;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return false;
        return fraction(now, stamp, life) <= 0.0;
    }

    /** 档位划分（渲染/调试用硬阈值；营养缩放走配置阈值，见 SpoiledFoodHandler）。 */
    public static FreshnessGrade grade(double fraction) {
        if (fraction <= 0.0) return FreshnessGrade.SPOILED;
        if (fraction <= 0.25) return FreshnessGrade.OLD;
        if (fraction <= 0.5) return FreshnessGrade.STALE;
        return FreshnessGrade.FRESH;
    }
}
