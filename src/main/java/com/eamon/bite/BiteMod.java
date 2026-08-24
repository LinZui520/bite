package com.eamon.bite;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.freshness.FreshnessClock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiteMod implements ModInitializer {
    public static final String MOD_ID = "bite";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BiteComponents.FRESHNESS.getClass(); // 触发静态注册
        ServerTickEvents.END_SERVER_TICK.register(server ->
            FreshnessClock.update(server.overworld().getGameTime()));
        LOGGER.info("Because It's Too Easy initialized");
    }
}
