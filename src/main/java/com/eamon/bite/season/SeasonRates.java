package com.eamon.bite.season;

import net.minecraft.world.entity.player.Player;

/**
 * 季节/维度系数表：各生存系统在四季与维度下的速率修正
 * （用户口径 2026-08-30）。
 *
 * <p>此前 hunger/thirst 两个 Controller 各自持有结构相同的
 * seasonMultiplier switch——收敛于此，语义单一来源：
 * <ul>
 *   <li>饥饿代谢：冬 ×1.25（御寒耗能，仅主世界）</li>
 *   <li>口渴流失：夏 ×2（出汗，仅主世界）；<b>下界 ×2</b>（灼热环境
 *       蒸腾，用户口径 2026-08-30——地狱 1.5 天清零，与盛夏同烈）</li>
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
    /** 下界口渴流失系数（灼热蒸腾，用户口径 2026-08-30：×2——1.5 天清零，与盛夏同烈）。 */
    public static final float NETHER_THIRST = 2.0f;

    /** 饥饿代谢的季节系数（仅玩家在主世界时生效——下界/末地不吃季节）。 */
    public static float hunger(Player player) {
        if (player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return 1.0f;
        return SeasonClock.season(player.level()) == Season.WINTER ? WINTER_HUNGER : 1.0f;
    }

    /** 口渴流失的季节/维度系数（季节仅主世界；下界恒 ×1.5 灼热蒸腾）。 */
    public static float thirst(Player player) {
        var dimension = player.level().dimension();
        if (dimension == net.minecraft.world.level.Level.NETHER) return NETHER_THIRST;
        if (dimension != net.minecraft.world.level.Level.OVERWORLD) return 1.0f;
        return SeasonClock.season(player.level()) == Season.SUMMER ? SUMMER_THIRST : 1.0f;
    }
}
