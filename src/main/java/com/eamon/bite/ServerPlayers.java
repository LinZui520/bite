package com.eamon.bite;

import net.minecraft.server.level.ServerPlayer;

/** 服务端玩家判定的小工具（各 tick 驱动器共用的豁免逻辑）。 */
public final class ServerPlayers {
    private ServerPlayers() {}

    /**
     * 该玩家是否参与生存系统结算（饥饿代谢/口渴流失等）。
     * 创造/旁观不参与（对齐原版：它们不进饥饿系统）。
     */
    public static boolean participatesInSurvival(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator();
    }
}
