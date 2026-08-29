package com.eamon.bite.thirst;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * 补水表：饮食的 hydration 值（正数回填水合）。
 * 用户口径 2026-08-30：不做对水右键直饮——补水只靠饮食。
 *
 * <p>内部用字符串 id（纯 JVM 单测可测，不触碰 MC 注册表 bootstrap），
 * 查询时经 {@link BuiltInRegistries#ITEM} 解析（运行时注册表已就绪）。
 */
public final class HydrationRegistry {
    /** 补水值表（0 = 不补水，未列出的食物一律不补水）。 */
    private static final Map<String, Integer> HYDRATION = Map.ofEntries(
        // 饮品主力
        Map.entry("minecraft:potion", 6),
        Map.entry("minecraft:milk_bucket", 8),
        Map.entry("minecraft:honey_bottle", 10),
        // 含水食物
        Map.entry("minecraft:melon_slice", 3),
        Map.entry("minecraft:sweet_berries", 1),
        Map.entry("minecraft:glow_berries", 1),
        Map.entry("minecraft:apple", 1),
        Map.entry("minecraft:carrot", 1),
        Map.entry("minecraft:beetroot", 1),
        // 汤
        Map.entry("minecraft:mushroom_stew", 5),
        Map.entry("minecraft:beetroot_soup", 5),
        Map.entry("minecraft:rabbit_stew", 5),
        Map.entry("minecraft:suspicious_stew", 5)
    );

    private HydrationRegistry() {}

    /** 该饮食的补水值（0 = 无）。 */
    public static int hydrationOf(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return hydrationOfId(id.toString());
    }

    /** 按 "namespace:path" 查补水值（纯函数，单测入口）。 */
    public static int hydrationOfId(String itemId) {
        return HYDRATION.getOrDefault(itemId, 0);
    }
}
