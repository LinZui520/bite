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

/** mod 物品注册。 */
public final class BiteItems {
    /** 腐烂物：变质食物的转换产物。非食物；永不腐坏，防止被再次转换。 */
    public static final Item ROTTEN_ORGANIC = register("rotten_organic",
        new Item.Properties().component(BiteComponents.SHELF_LIFE, ShelfLife.NEVER)
            .stacksTo(64));

    private BiteItems() {}

    /** 显式触发静态注册（注册发生在字段初始化里，入口调用以避免依赖类加载副作用）。 */
    public static void init() {}

    // Item 构造器读取 descriptionId，要求先 setId(ResourceKey) 再 new Item
    private static Item register(String path, Item.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(BiteMod.MOD_ID, path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, id,
            new Item(properties.setId(key)));
    }
}
