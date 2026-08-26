package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 打标（幂等）：给食物写入出生时间戳。已打标 / 非食物 / 永不腐坏均为 no-op。 */
public final class FreshnessStamper {
    private FreshnessStamper() {}

    /** 给物品打标；缺 SHELF_LIFE 时回退到 {@link ShelfLifeRegistry} 解析。 */
    public static void stamp(ItemStack stack, long now) {
        if (stack.isEmpty() || stack.has(BiteComponents.FRESHNESS)) return;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null) {
            life = new ShelfLife(ShelfLifeRegistry.resolveShelfLifeTicks(stack.getItem()));
        }
        if (life.spoilTicks() <= 0) return;
        stack.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));
    }

    /** 烹饪打标：单原料版本，语义同 {@link #stampCrafted}。 */
    public static void stampCooked(ItemStack result, ItemStack input, long now) {
        stampCrafted(result, List.of(input), now);
    }

    /**
     * 合成/烹饪打标（饥荒锅料理语义）：产物腐坏量 = 全部已打标食物原料
     * 平均腐坏量的一半。原料按各自保质期归一化为腐坏比例后取平均，
     * 非食物/无标原料不计入分母；产物按自身保质期重新归一化。
     * 例：3 小麦（2 全新 + 1 半腐）做面包 → 平均腐坏 1/6 → 减半 1/12 → fraction ≈ 0.917。
     */
    public static void stampCrafted(ItemStack result, List<ItemStack> ingredients, long now) {
        if (result.isEmpty() || result.has(BiteComponents.FRESHNESS)) return;
        ShelfLife resultLife = result.get(BiteComponents.SHELF_LIFE);
        if (resultLife == null) {
            resultLife = new ShelfLife(ShelfLifeRegistry.resolveShelfLifeTicks(result.getItem()));
        }
        if (resultLife.spoilTicks() <= 0) return;

        double spoilSum = 0.0;
        int counted = 0;
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            FreshnessStamp stamp = ingredient.get(BiteComponents.FRESHNESS);
            ShelfLife life = ingredient.get(BiteComponents.SHELF_LIFE);
            if (stamp == null || life == null || life.spoilTicks() <= 0) continue;
            spoilSum += 1.0 - FreshnessMath.fraction(now, stamp, life);
            counted++;
        }

        long age = counted == 0 ? 0
            : Math.round(spoilSum / counted / 2.0 * resultLife.spoilTicks());
        result.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - age));
    }
}
