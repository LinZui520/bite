package com.eamon.bite.component;

/** 保质期（游戏刻）。负数 = 永不腐坏/非食物。 */
public record ShelfLife(long spoilTicks) {
    public static final ShelfLife NEVER = new ShelfLife(-1);
}
