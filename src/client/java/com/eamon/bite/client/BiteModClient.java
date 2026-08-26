package com.eamon.bite.client;

import com.eamon.bite.BiteMod;
import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.config.ClientConfig;
import com.eamon.bite.freshness.FreshnessClock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.fabricmc.loader.api.FabricLoader;

/** 客户端入口：加载客户端配置、每 tick 刷新客户端时钟、注册 freshness 的 tooltip 渲染。 */
public class BiteModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfig.load(FabricLoader.getInstance().getConfigDir());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) FreshnessClock.update(BiteMod.gameTime(client.level));
        });
        ItemComponentTooltipProviderRegistry.addLast(BiteComponents.FRESHNESS);
    }
}
