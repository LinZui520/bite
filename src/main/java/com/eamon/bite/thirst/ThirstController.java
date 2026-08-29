package com.eamon.bite.thirst;

import com.eamon.bite.season.Season;
import com.eamon.bite.season.SeasonClock;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * 饥渴 tick 驱动：流失（基础 × 季节 + 疾跑）与惩罚（禁疾跑 / 干渴伤害）。
 *
 * <p>惩罚结构对齐原版饥饿：≤6 服务端强制 setSprinting(false)（客户端
 * isSprintingPossible mixin 做本地预测拦截）；=0 每 80 tick 1 点
 * generic damage——简单/普通难度扣到 {10, 1} 血为止，困难可致死。
 * 和平/创造/旁观完全豁免（不流失、不伤害）。
 *
 * <p>同步：每 tick 末尾向各玩家推送 {@link ThirstSyncPacket}——
 * FLOAT 包 ~7 字节/玩家/tick，20 人服 ~2.8 KB/s，可忽略。
 */
public final class ThirstController {
    private static final float[] DAMAGE_CAP = {10.0f, 1.0f}; // easy, normal

    private ThirstController() {}

    /** 每 server tick 调用。 */
    public static void tick(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (player.isCreative() || player.isSpectator()) continue;
            tickPlayer(player);
            ServerPlayNetworking.send(player, new ThirstSyncPacket(ThirstData.get(player)));
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            // 和平：缓慢回满（对齐原版和平回饥饿的精神）
            if (ThirstData.get(player) < ThirstData.MAX_THIRST) {
                ThirstData.add(player, 0.01f);
            }
            return;
        }

        float drain = ThirstData.BASE_DRAIN_PER_TICK * seasonMultiplier(player);
        if (player.isSprinting()) {
            drain += ThirstData.SPRINT_DRAIN_PER_TICK;
            // 干渴 ≤6：服务端权威强制停疾跑（客户端预测拦截之外的兜底）
            if (ThirstData.get(player) <= ThirstData.SPRINT_THRESHOLD) {
                player.setSprinting(false);
            }
        }
        ThirstData.add(player, -drain);

        // 干渴伤害（=0 时）
        if (ThirstData.get(player) <= 0.0f
            && player.tickCount % ThirstData.DAMAGE_INTERVAL_TICKS == 0) {
            applyThirstDamage(player);
        }
    }

    /** 季节流失系数：夏 1.5 / 冬 0.75 / 春秋 1。 */
    public static float seasonMultiplier(Player player) {
        return switch (SeasonClock.season(player.level())) {
            case SUMMER -> 1.5f;
            case WINTER -> 0.75f;
            default -> 1.0f;
        };
    }

    private static void applyThirstDamage(ServerPlayer player) {
        Difficulty difficulty = player.level().getDifficulty();
        float health = player.getHealth();
        float cap = switch (difficulty) {
            case EASY -> DAMAGE_CAP[0];
            case NORMAL -> DAMAGE_CAP[1];
            default -> Float.MAX_VALUE; // HARD 可致死
        };
        if (health > cap || health > 1.0f && difficulty == Difficulty.HARD) {
            player.hurtServer(player.level(),
                player.damageSources().generic(), 1.0f);
        }
    }

    /** 睡觉补结算（时钟跳变时由 SleepMixin 调用，skippedTicks 为跳过量）。 */
    public static void onSleptThroughTicks(Iterable<ServerPlayer> players, long skippedTicks) {
        float drain = ThirstData.BASE_DRAIN_PER_TICK * skippedTicks * ThirstData.SLEEP_DRAIN_FACTOR;
        if (drain <= 0.0f) return;
        for (ServerPlayer player : players) {
            if (player.isCreative() || player.isSpectator()) continue;
            ThirstData.add(player, -drain);
        }
    }

    /** 饮食补水入口（ItemStackFinishMixin 进食路径调用）。 */
    public static void onConsume(Player player, Item item) {
        int hydration = HydrationRegistry.hydrationOf(item);
        if (hydration > 0) {
            ThirstData.add(player, hydration);
        }
    }
}
