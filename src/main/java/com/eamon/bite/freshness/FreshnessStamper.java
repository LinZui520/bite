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
}
