package com.eamon.bite.season;

import net.minecraft.world.entity.player.Player;

/**
 * 季节系数表：各生存系统在四季下的速率修正（用户口径 2026-08-30）。
 *
 * <p>此前 hunger/thirst 两个 Controller 各自持有结构相同的
 * seasonMultiplier switch——收敛于此，语义单一来源：
 * <ul>
 *   <li>饥饿代谢：冬 ×1.25（御寒耗能）</li>
 *   <li>口渴流失：夏 ×2（出汗）</li>
 *   <li>食物腐坏：见 {@link com.eamon.bite.freshness.FreshnessMath#perishMultiplier}
 *       （冬 0.75 / 夏 1.25，配置驱动——属配置域不在此列）</li>
 * </ul>
 */
public final class SeasonRates {
    private SeasonRates() {}

    /** 冬季饥饿代谢系数（御寒耗能）。 */
    public static final float WINTER_HUNGER = 1.25f;
    /** 夏季口渴流失系数（出汗，用户口径 2026-08-30：×2——夏天 1.5 天清零）。 */
    public static final float SUMMER_THIRST = 2.0f;

    /** 饥饿代谢的季节系数。 */
    public static float hunger(Player player) {
        return SeasonClock.season(player.level()) == Season.WINTER ? WINTER_HUNGER : 1.0f;
    }

    /** 口渴流失的季节系数。 */
    public static float thirst(Player player) {
        return SeasonClock.season(player.level()) == Season.SUMMER ? SUMMER_THIRST : 1.0f;
    }
}
