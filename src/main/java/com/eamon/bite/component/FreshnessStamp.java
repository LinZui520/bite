package com.eamon.bite.component;

/** 打标时刻的游戏刻（game time，非 day time）。Task 3 会补充 CODEC/STREAM_CODEC/TooltipProvider。 */
public record FreshnessStamp(long creationGameTick) {
}
