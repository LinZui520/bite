package com.eamon.bite.item;

import com.eamon.bite.BiteMod;
import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * mod 物品注册（v1.0.1+）。
 *
 * <p>26.2 注册表 API（genSources 验证）：Item 构造器内即读取
 * {@code Properties.effectiveDescriptionId()}（要求 itemId 已知），
 * 故注册模式为「Properties.setId(ResourceKey) → new Item(properties) →
 * Registry.register」。
 */
public final class BiteItems {
    /** 腐烂物：变质食物的转换产物。非食物；永不腐坏（防止自我转换套娃）。 */
    public static final Item ROTTEN_ORGANIC = register("rotten_organic",
        new Item.Properties().component(BiteComponents.SHELF_LIFE, ShelfLife.NEVER)
            .stacksTo(64));

    private BiteItems() {}

    private static Item register(String path, Item.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(BiteMod.MOD_ID, path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, id,
            new Item(properties.setId(key)));
    }
}
