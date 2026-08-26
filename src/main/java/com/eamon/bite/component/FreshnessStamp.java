package com.eamon.bite.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 打标时刻的游戏刻（game time，非 day time）。展示走物品图标进度条，无文字 tooltip。 */
public record FreshnessStamp(long creationGameTick) {
    public static final Codec<FreshnessStamp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("creation_tick").forGetter(FreshnessStamp::creationGameTick)
    ).apply(instance, FreshnessStamp::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreshnessStamp> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, FreshnessStamp::creationGameTick, FreshnessStamp::new);
}
