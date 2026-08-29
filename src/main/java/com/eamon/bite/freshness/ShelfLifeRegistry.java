package com.eamon.bite.freshness;

import com.eamon.bite.config.ServerConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 保质期解析：item_overrides &gt; c:foods 分类 &gt; default 兜底。
 * 返回游戏刻（24000/天）；-1 = 永不腐坏 / 非食物。
 */
public final class ShelfLifeRegistry {
    public static final long DAY = com.eamon.bite.GameTime.TICKS_PER_DAY;

    /** 分类判定顺序（先命中先用）。 */
    private static final List<String> CATEGORIES = List.of(
        "raw_meat", "raw_fish", "cooked_meat", "cooked_fish",
        "bread", "vegetable", "fruit", "berry", "dough",
        "soup", "cookie", "pie", "candy");

    private ShelfLifeRegistry() {}

    /** 解析单物品保质期（游戏刻）；-1 = 永不腐坏或非食物。 */
    public static long resolveShelfLifeTicks(Item item) {
        ServerConfig config = ServerConfig.get();
        String id = describe(item);
        Integer override = config.itemOverrides().get(id);
        if (override != null) return override < 0 ? -1 : override * DAY;
        if (!isFood(item)) return -1;
        Integer days = config.shelfLifeDays().get(categoryOf(item));
        if (days == null) days = config.shelfLifeDays().get("default");
        if (days == null || days < 0) return -1;
        return days * DAY;
    }

    /** 物品是否为食物（拥有 FOOD 或 CONSUMABLE 组件）。 */
    public static boolean isFood(Item item) {
        return item.components().has(DataComponents.FOOD)
            || item.components().has(DataComponents.CONSUMABLE);
    }

    /** 返回物品所属的 c:foods 分类，未命中返回 "default"。 */
    private static String categoryOf(Item item) {
        for (String cat : CATEGORIES) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("c", "foods/" + cat));
            if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(tag)) return cat;
        }
        return "default";
    }

    /** 物品注册表 ID（如 "minecraft:apple"）。 */
    private static String describe(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
