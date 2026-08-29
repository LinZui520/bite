package com.eamon.bite.hunger;

import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.mixin.FoodDataExhaustionAccess;
import net.minecraft.world.entity.player.Player;

/**
 * 疲劳差值缩放：动作疲劳 mixin 的公共模式——HEAD 记录疲劳、RETURN
 * 取差值退回再按 {@code hunger_action_multiplier} 收取（退回再收，
 * 净额不受 exhaustion 上限钳制影响）。
 *
 * <p>两个调用方（移动 {@code checkMovementStatistics}、攻击
 * {@code attack}）此前各自复制了同一份 HEAD/RETURN 差值样板。
 */
public final class ExhaustionScaling {
    private ExhaustionScaling() {}

    /** HEAD 时调用：记录当前疲劳值。 */
    public static float capture(Player player) {
        return ((FoodDataExhaustionAccess) player.getFoodData()).bite$exhaustionLevel();
    }

    /**
     * RETURN 时调用：把期间新增的疲劳按动作乘数重收。
     *
     * @return 原版口径的疲劳增量（&gt;0 时），供调用方叠加额外计费（如走路按米）
     */
    public static float scaleDelta(Player player, float before) {
        float delta = capture(player) - before;
        if (delta <= 0.0f) return 0.0f;
        float multiplier = (float) ServerConfig.get().hungerActionMultiplier();
        if (multiplier <= 1.0f) return delta;
        player.getFoodData().addExhaustion(-delta);
        player.getFoodData().addExhaustion(delta * multiplier);
        return delta;
    }
}
