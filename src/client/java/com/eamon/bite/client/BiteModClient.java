package com.eamon.bite.client;

import com.eamon.bite.client.season.SeasonHud;
import com.eamon.bite.config.ClientConfig;
import com.eamon.bite.freshness.FreshnessClock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/** 客户端入口：加载客户端配置、每 tick 刷新客户端时钟、注册季节提示 HUD。 */
public class BiteModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfig.load(FabricLoader.getInstance().getConfigDir());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) FreshnessClock.update(FreshnessClock.now(client.level));
        });
        SeasonHud.register();
    }
}
