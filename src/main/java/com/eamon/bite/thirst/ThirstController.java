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
 * <p><b>惩罚与原版饿死完全同构</b>（FoodData.tick 的 starve 分支逐行对齐）：
 * <ul>
 *   <li>≤6 不能疾跑（客户端 isSprintingPossible 预测拦截 + 服务端
 *       setSprinting(false) 兜底）</li>
 *   <li>=0 每 80 tick 1 点伤害，判定式与原版相同：
 *       {@code health > 10 || HARD || (health > 1 && NORMAL)}——
 *       简单扣到 10 血、普通扣到 1 血、困难可致死</li>
 *   <li>伤害源用 {@code drown()}（26.2 无 dehydrate；drown 的死亡消息
 *       「被淹死了」最接近渴死语义——原版 starve 也只是 generic 类
 *       伤害源 + 专属消息，复用现成源避免注册新 DamageType）</li>
 *   <li>伤害节拍用独立计时器 thirstTimer（对齐原版 tickTimer 语义：
 *       从归零那刻起数 80 tick，而非 tickCount 取模——死亡重生后
 *       tickCount 清零的错拍问题同样规避）</li>
 * </ul>
 * 和平/创造/旁观完全豁免（不流失、不伤害；和平缓慢回满）。
 *
 * <p>同步：每 tick 末尾向各玩家推送 {@link ThirstSyncPacket}。
 */
public final class ThirstController {

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

        // 干渴伤害（=0 时）——判定式与原版 starve 分支完全一致
        if (ThirstData.get(player) <= 0.0f) {
            ThirstData.setThirstTimer(player, ThirstData.thirstTimer(player) + 1);
            if (ThirstData.thirstTimer(player) >= ThirstData.DAMAGE_INTERVAL_TICKS) {
                Difficulty difficulty = player.level().getDifficulty();
                if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD
                    || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
                    player.hurtServer(player.level(), player.damageSources().drown(), 1.0F);
                }
                ThirstData.setThirstTimer(player, 0);
            }
        } else {
            ThirstData.setThirstTimer(player, 0);
        }
    }

    /** 季节流失系数：夏 ×1.25（出汗），其余 1（用户口径 2026-08-30）。 */
    public static float seasonMultiplier(Player player) {
        return switch (SeasonClock.season(player.level())) {
            case SUMMER -> 1.25f;
            default -> 1.0f;
        };
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
