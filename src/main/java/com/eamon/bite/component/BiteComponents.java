package com.eamon.bite.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import static com.eamon.bite.BiteMod.MOD_ID;

/** 数据组件类型注册。静态初始化在首次类引用时触发 Registry.register。 */
public final class BiteComponents {
    public static final DataComponentType<FreshnessStamp> FRESHNESS = register("freshness", FreshnessStamp.CODEC, FreshnessStamp.STREAM_CODEC);
    public static final DataComponentType<ShelfLife> SHELF_LIFE = register("shelf_life", ShelfLife.CODEC, ShelfLife.STREAM_CODEC);

    private BiteComponents() {}

    private static <T> DataComponentType<T> register(String path, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(MOD_ID, path),
            DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build());
    }
}
