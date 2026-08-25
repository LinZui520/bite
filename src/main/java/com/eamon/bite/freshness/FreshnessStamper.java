package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.world.item.ItemStack;

/**
 * 打标（幂等）：给食物写入出生时间戳。
 *
 * <p>语义：
 * <ul>
 *   <li>空 stack / 已有 FRESHNESS → 不动（幂等）</li>
 *   <li>缺 SHELF_LIFE 时回退到 {@link ShelfLifeRegistry} 解析</li>
 *   <li>非食物 / 永不腐坏（spoilTicks &lt;= 0）→ 不动</li>
 *   <li>否则写入 {@code FRESHNESS = FreshnessStamp(now)}</li>
 * </ul>
 */
public final class FreshnessStamper {
    private FreshnessStamper() {}

    /**
     * 给物品打标。已打标 / 非食物 / 永不腐坏均为 no-op。
     *
     * @param stack 待打标的物品堆
     * @param now   当前游戏刻（level.getGameTime()）
     */
    public static void stamp(ItemStack stack, long now) {
        if (stack.isEmpty() || stack.has(BiteComponents.FRESHNESS)) return;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null) {
            life = new ShelfLife(ShelfLifeRegistry.resolveShelfLifeTicks(stack.getItem()));
        }
        if (life.spoilTicks() <= 0) return;
        stack.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));
    }

    /**
     * 烹饪打标（饥荒「Cooking refreshes spoilage」）：产物的腐坏量为原料的一半。
     *
     * <p>原料 fraction f → 产物 fraction = 1 - (1-f)/2（80% 的新鲜马铃薯
     * 烤成烤马铃薯后 90% 新鲜）。原料无 stamp（如非食物/首次获得）→ 产物全新。
     * 产物自身按其类型保质期解析（烤制前后保质期不同：如生鱼 3 → 熟鱼 6 天）。
     */
    public static void stampCooked(ItemStack result, ItemStack input, long now) {
        stampCrafted(result, java.util.List.of(input), now);
    }

    /**
     * 合成/烹饪打标（饥荒锅料理语义，v1.0.7）：
     * 产物腐坏量 = 全部「已打标食物原料」平均腐坏量的一半。
     *
     * <p>多原料按各自保质期归一化为腐坏比例后取平均（食物类原料才参与；
     * 非食物/无标原料不计入分母），再减半映射到产物自身保质期。
     * 例：3 小麦（2 全新 + 1 半腐）做面包 → 平均腐坏 1/6 → 减半 1/12 →
     * 面包 fraction ≈ 0.917。
     *
     * @param result      产物（未打标且自身可腐坏时才写入）
     * @param ingredients 合成格/炉输入的全部原料（空槽与非食物自动忽略）
     * @param now         当前游戏刻
     */
    public static void stampCrafted(ItemStack result, java.util.List<ItemStack> ingredients, long now) {
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
            double ingredientSpoil = 1.0 - FreshnessMath.fraction(now, stamp, life);
            spoilSum += ingredientSpoil;
            counted++;
        }

        long age = counted == 0 ? 0
            : Math.round(spoilSum / counted / 2.0 * resultLife.spoilTicks());
        result.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - age));
    }
}
