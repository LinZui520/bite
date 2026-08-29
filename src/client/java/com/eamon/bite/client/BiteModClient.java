package com.eamon.bite.client;

import com.eamon.bite.client.hud.FadingHudText;
import com.eamon.bite.client.season.SeasonHud;
import com.eamon.bite.client.thirst.ThirstClientStore;
import com.eamon.bite.client.thirst.ThirstHud;
import com.eamon.bite.config.ClientConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.season.Season;
import com.eamon.bite.season.SeasonBiomeCaches;
import com.eamon.bite.season.SeasonClock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/** 客户端入口：加载客户端配置、每 tick 刷新客户端时钟、注册通用 HUD 文字与季节提示。 */
public class BiteModClient implements ClientModInitializer {
    private Season prevSeason;

    @Override
    public void onInitializeClient() {
        ClientConfig.load(FabricLoader.getInstance().getConfigDir());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                FreshnessClock.update(FreshnessClock.now(client.level));
                Season season = SeasonClock.season(client.level);
                // 换季时清 render 线程的群系温度缓存（server 线程由 SeasonWeatherController 清）
                if (season != prevSeason) {
                    SeasonBiomeCaches.clear(client.level.registryAccess());
                    prevSeason = season;
                }
                SeasonClock.update(client.level);
            }
        });
        FadingHudText.register();
        SeasonHud.register();
        ThirstHud.register();
        ThirstClientStore.register();
    }
}
