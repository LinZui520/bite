package com.eamon.bite.client.thirst;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端饥渴缓存：接收 {@link com.eamon.bite.thirst.ThirstSyncPacket}
 * 存最近值，HUD/疾跑判定读这里（本地玩家实体上的字段是服务端镜像，
 * 客户端侧不可靠）。默认满（未收到包前不误报干渴）。
 */
@Environment(EnvType.CLIENT)
public final class ThirstClientStore {
    private static volatile float thirst = com.eamon.bite.thirst.ThirstData.MAX_THIRST;

    private ThirstClientStore() {}

    public static float get() { return thirst; }

    /** 注册包接收器（onInitializeClient 调用）。 */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
            com.eamon.bite.thirst.ThirstSyncPacket.TYPE,
            (payload, context) -> thirst = payload.thirst());
    }
}
