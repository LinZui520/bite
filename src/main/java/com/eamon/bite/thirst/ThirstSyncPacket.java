package com.eamon.bite.thirst;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 饥渴同步包（S2C）：服务端每 tick 末尾向各在线玩家推送其水合值。
 * 客户端 {@code ThirstClientStore} 持有最近值供 HUD 渲染——本地玩家
 * 的实例走包，不直读服务端字段（客户端字段只在本地预测时使用）。
 */
public record ThirstSyncPacket(float thirst) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ThirstSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("bite", "thirst_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThirstSyncPacket> CODEC =
        StreamCodec.composite(ByteBufCodecs.FLOAT, ThirstSyncPacket::thirst, ThirstSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 注册 payload 类型（onInitialize 调用）。 */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }
}
