package com.eamon.bite.thirst;

import net.minecraft.world.entity.player.Player;

/**
 * 饥渴值：0~20（10 滴水，与饥饿同视觉语言），服务端权威 + S2C 包同步。
 *
 * <p>存储：每个 Player 实例一个 {@code @Unique} float 字段（PlayerDataMixin
 * 注入，NBT 持久化键 {@code bite:thirst}）。同步：服务端每 tick 末尾对
 * 在线玩家发 {@link ThirstSyncPacket}（客户端只读展示；疾跑门槛在服务端
 * tick 校验——见 ThirstController）。
 *
 * <p>为什么不用 DataTracker：26.2 的 Player 数据槽位已满（defineId 越界，
 * gametest 实测 Index 21/21），只能走自定义通道。
 *
 * <p>数值口径（2026-08-30 设计）：
 * <ul>
 *   <li><b>基础流失</b>：20 / (3 × 24000) tick——不动恰好 3 游戏日清零</li>
 *   <li><b>季节修正</b>：夏 ×1.25（出汗）——用户口径 2026-08-30</li>
 *   <li><b>疾跑</b>：+0.02/秒</li>
 *   <li><b>睡觉</b>：跳过时间按 50% 补流失</li>
 *   <li><b>惩罚</b>：≤6 服务端强制停疾跑；=0 每 80 tick 1 点干渴伤害</li>
 * </ul>
 */
public final class ThirstData {
    public static final int MAX_THIRST = 20;
    /** 不动 3 游戏日清零的基础流失（每 tick）。 */
    public static final float BASE_DRAIN_PER_TICK = MAX_THIRST / (3f * 24000f);
    /** 疾跑额外流失（每 tick）。 */
    public static final float SPRINT_DRAIN_PER_TICK = 0.02f / 20f;
    /** 睡觉期间流失系数（清醒的 50%）。 */
    public static final float SLEEP_DRAIN_FACTOR = 0.5f;
    /** 干渴伤害节拍（tick，对齐原版饿死 80 tick）。 */
    public static final int DAMAGE_INTERVAL_TICKS = 80;
    /** 疾跑门槛（对齐原版饥饿 SPRINT_LEVEL=6）。 */
    public static final int SPRINT_THRESHOLD = 6;

    private ThirstData() {}

    /** 取该玩家的水合值（存储在 PlayerDataMixin 的 @Unique 字段）。 */
    public static float get(Player player) {
        return asHolder(player).bite$getThirst();
    }

    public static void set(Player player, float value) {
        asHolder(player).bite$setThirst(Math.clamp(value, 0.0f, MAX_THIRST));
    }

    public static void add(Player player, float delta) {
        set(player, get(player) + delta);
    }

    /** 存储接口（PlayerDataMixin 实现于 Player 上）。 */
    public interface Holder {
        float bite$getThirst();
        void bite$setThirst(float value);
        /** 干渴伤害计时器（对齐原版 FoodData.tickTimer 的语义：归零起数 80 tick）。 */
        int bite$thirstTimer();
        void bite$setThirstTimer(int value);
    }

    private static Holder asHolder(Player player) {
        return (Holder) player;
    }

    /** 干渴伤害计时器（供 ThirstController 读写）。 */
    public static int thirstTimer(Player player) {
        return asHolder(player).bite$thirstTimer();
    }

    public static void setThirstTimer(Player player, int value) {
        asHolder(player).bite$setThirstTimer(value);
    }
}
