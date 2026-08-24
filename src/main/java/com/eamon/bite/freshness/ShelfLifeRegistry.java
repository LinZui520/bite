package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 保质期解析：item_overrides > c:foods 分类 > 兜底 default。
 * 返回游戏刻（24000/天）；-1 = 永不腐坏 / 非食物。
 *
 * <p>Checkpoint resolutions (MC 26.2, verified via javap + genSources):
 * <ul>
 *   <li>Registry ID: {@code BuiltInRegistries.ITEM.getKey(item)} returns
 *       {@link Identifier} (candidate A — {@code Registry#getKey(T)} confirmed on
 *       {@code net.minecraft.core.Registry}).</li>
 *   <li>Item tag check: {@code BuiltInRegistries.ITEM.wrapAsHolder(item)} returns
 *       {@code Holder<Item>} (non-deprecated; {@code Item.builtInRegistryHolder()}
 *       is {@code @Deprecated} in 26.2). {@code Holder#is(TagKey)} confirmed.</li>
 *   <li>Food check: {@code item.components()} returns
 *       {@code DataComponentMap}; {@code DataComponentMap#has(DataComponentType)} confirmed.</li>
 * </ul>
 */
public final class ShelfLifeRegistry {
    public static final long DAY = 24000L;

    /** 顺序即优先级（先命中先用）。 */
    private static final List<String> CATEGORIES = List.of(
        "raw_meat", "raw_fish", "cooked_meat", "cooked_fish",
        "bread", "vegetable", "fruit", "berry", "dough",
        "soup", "cookie", "pie", "candy");

    private ShelfLifeRegistry() {}

    /**
     * 解析单物品保质期（游戏刻）。
     *
     * <p>优先级：item_overrides（-1 = 永不）> 非食物(-1) > c:foods 分类 >
     * default > 兜底 -1。
     *
     * @return 游戏刻；-1 = 永不腐坏或非食物
     */
    public static long resolveShelfLifeTicks(Item item) {
        String id = describe(item);
        Integer override = ServerConfig.get().itemOverrides().get(id);
        if (override != null) return override < 0 ? -1 : override * DAY;
        if (!isFood(item)) return -1;
        String cat = categoryOf(item);
        Integer days = ServerConfig.get().shelfLifeDays().get(cat);
        if (days == null) days = ServerConfig.get().shelfLifeDays().get("default");
        if (days == null || days < 0) return -1;
        return days * DAY;
    }

    /**
     * 返回物品所属的 c:foods 分类（调试用）。
     * 顺序按 {@link #CATEGORIES}，未命中返回 "default"。
     */
    public static String categoryOf(Item item) {
        for (String cat : CATEGORIES) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("c", "foods/" + cat));
            if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(tag)) return cat;
        }
        return "default";
    }

    /**
     * 判断物品是否为食物（用于默认组件接线）。
     * 26.2 通过 FOOD 或 CONSUMABLE 组件判定。
     */
    public static boolean isFoodItemForDefaults(Item item) {
        return isFood(item);
    }

    private static boolean isFood(Item item) {
        return item.components().has(DataComponents.FOOD)
            || item.components().has(DataComponents.CONSUMABLE);
    }

    /** 返回物品注册表 ID（如 "minecraft:apple"）。 */
    private static String describe(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
