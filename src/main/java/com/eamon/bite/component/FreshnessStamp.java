package com.eamon.bite.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** 打标时刻的游戏刻（game time，非 day time）。 */
public record FreshnessStamp(long creationGameTick) {
    public static final Codec<FreshnessStamp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("creation_tick").forGetter(FreshnessStamp::creationGameTick)
    ).apply(instance, FreshnessStamp::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreshnessStamp> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, FreshnessStamp::creationGameTick, FreshnessStamp::new);
}
