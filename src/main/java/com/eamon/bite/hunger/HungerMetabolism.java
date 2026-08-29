package com.eamon.bite.hunger;

import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.season.SeasonRates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

/**
 * 饥饿代谢（饥荒式「时间就是敌人」）：站桩/走路也掉饥饿。
 *
 * <p>口径（2026-08-30 设计）：
 * <ul>
 *   <li><b>饿腹代谢</b>：每 tick 对所有在线玩家加微量疲劳。默认 0.00208
 *       （半档，2 游戏日 20→0）；满档 0.00417 = 1 游戏日掉空。冬季
 *       ×1.25（御寒耗能，用户口径 2026-08-30）。走原版结算链
 *       （4 疲劳 → 1 饱和度/鸡腿）——和平模式天然豁免
 *       （FoodData.tick 的 PEACEFUL 守卫）、饱和度先烧、睡觉跳过的时间
 *       不在此结算（见 {@link #onSleptThroughTicks}）</li>
 *   <li><b>睡觉补结算</b>：睡觉使时钟瞬移到次日 0 刻（被跳过的 tick 不
 *       会自然发生），在此按 <b>50%</b> 代谢率补算——早睡多跳多掉、
 *       23 点睡只掉 ~0.1 格，睡醒饿但不惩罚睡觉</li>
 *   <li><b>动作消耗</b>（mixin 放大原版常量）：走路 0→0.005/米、
 *       疾跑 0.1→0.13/米、攻击 0.1→0.12。挖矿/跳跃不动（已有合理代价）。
 *       设计原则：代谢主导节奏（一天 2~3 顿），动作只做点缀加速 ≤50%</li>
 * </ul>
 *
 * <p>与现有系统的正交性：代谢走 {@code addExhaustion} 通道，将来夏季
 * 中暑/冬季取暖的疲劳可以直接叠在同一通道。
 */
public final class HungerMetabolism {
    private HungerMetabolism() {}

    /** 每 server tick 调用：对每个在线玩家结算饿腹代谢。 */
    public static void tick(Iterable<ServerPlayer> players) {
        float rate = (float) ServerConfig.get().hungerMetabolismPerTick();
        if (rate <= 0.0f) return;
        for (ServerPlayer player : players) {
            // 创造/旁观不消耗（对齐原版：它们不参与饥饿系统）
            if (!com.eamon.bite.ServerPlayers.participatesInSurvival(player)) continue;
            player.causeFoodExhaustion(rate * SeasonRates.hunger(player));
        }
    }


    /**
     * 睡觉补结算：时钟跳变瞬间，按睡眠代谢率（清醒的 50%）对睡过这段
     * 时间的玩家补算疲劳。
     *
     * @param skippedTicks 时钟跳过的 tick 数（睡前时刻 → 次日 0 刻）
     */
    public static void onSleptThroughTicks(Iterable<ServerPlayer> players, long skippedTicks) {
        float rate = (float) ServerConfig.get().hungerMetabolismPerTick();
        if (rate <= 0.0f || skippedTicks <= 0) return;
        float exhaustion = rate * skippedTicks * (float) ServerConfig.get().hungerSleepMetabolismFactor();
        if (exhaustion <= 0.0f) return;
        for (ServerPlayer player : players) {
            if (!com.eamon.bite.ServerPlayers.participatesInSurvival(player)) continue;
            player.causeFoodExhaustion(exhaustion);
        }
    }

    /** 动作疲劳乘数（走路/疾跑/攻击 mixin 调用）。 */
    public static float actionMultiplier() {
        return (float) ServerConfig.get().hungerActionMultiplier();
    }

    // 供测试与调参参考：满档代谢（1 游戏日 20→0）
    // 25 次结算 × 4 疲劳 / 24000 tick = 0.004167
    public static final float FULL_DAY_RATE = 25 * 4 / (float) com.eamon.bite.GameTime.TICKS_PER_DAY;
}
