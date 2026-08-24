package com.eamon.bite.component;

import com.eamon.bite.freshness.FreshnessMath;
import com.eamon.bite.freshness.FreshnessClock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.function.Consumer;

/** 打标时刻的游戏刻（game time，非 day time）。 */
public record FreshnessStamp(long creationGameTick) implements TooltipProvider {
    public static final Codec<FreshnessStamp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("creation_tick").forGetter(FreshnessStamp::creationGameTick)
    ).apply(instance, FreshnessStamp::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreshnessStamp> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, FreshnessStamp::creationGameTick, FreshnessStamp::new);

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter getter) {
        ShelfLife life = getter.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return;
        long now = FreshnessClock.now();
        double fraction = FreshnessMath.fraction(now, this, life);
        if (fraction <= 0.0) {
            tooltip.accept(Component.translatable("bite.tooltip.spoiled").withStyle(ChatFormatting.GRAY));
            return;
        }
        Component time = formatRemaining(life.spoilTicks() - (now - creationGameTick));
        tooltip.accept(Component.translatable("bite.tooltip.freshness",
            Math.round(fraction * 100), time)
            .withStyle(colorFor(fraction)));
    }

    private static ChatFormatting colorFor(double fraction) {
        if (fraction > 0.75) return ChatFormatting.GREEN;
        if (fraction > 0.5) return ChatFormatting.YELLOW;
        if (fraction > 0.25) return ChatFormatting.GOLD;
        return ChatFormatting.RED;
    }

    private static Component formatRemaining(long ticks) {
        double days = ticks / 24000.0;
        if (days >= 1.0) return Component.translatable("bite.time.days", String.format(java.util.Locale.ROOT, "%.1f", days));
        double hours = days * 24.0;
        return Component.translatable("bite.time.hours", String.format(java.util.Locale.ROOT, "%.1f", hours));
    }
}
