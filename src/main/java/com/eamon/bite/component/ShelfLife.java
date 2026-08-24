package com.eamon.bite.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 保质期（游戏刻）。负数 = 永不腐坏/非食物。 */
public record ShelfLife(long spoilTicks) {
    public static final ShelfLife NEVER = new ShelfLife(-1);

    public static final Codec<ShelfLife> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("spoil_ticks").forGetter(ShelfLife::spoilTicks)
    ).apply(instance, ShelfLife::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfLife> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, ShelfLife::spoilTicks, ShelfLife::new);
}
