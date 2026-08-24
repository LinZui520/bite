package com.eamon.bite;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.ShelfLifeRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiteMod implements ModInitializer {
    public static final String MOD_ID = "bite";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerConfig.load(FabricLoader.getInstance().getConfigDir());
        BiteComponents.FRESHNESS.getClass(); // 触发静态注册
        registerDefaultShelfLife();
        ServerTickEvents.END_SERVER_TICK.register(server ->
            FreshnessClock.update(server.overworld().getGameTime()));
        LOGGER.info("Because It's Too Easy initialized");
    }

    /**
     * 为所有食物物品的默认组件注入 bite:shelf_life。
     *
     * <p>Checkpoint 2 resolution (MC 26.2, Fabric API 0.158.0+26.2):
     * {@link DefaultItemComponentEvents#MODIFY} 是 {@code Event<ModifyCallback>}，
     * 回调接收 {@code ModifyContext}。使用重载
     * {@code modify(Predicate<Item>, BiConsumer<DataComponentMap.Builder, Item>)}，
     * BiConsumer 形参顺序为 (builder, item)。
     */
    private static void registerDefaultShelfLife() {
        DefaultItemComponentEvents.MODIFY.register(context -> context.modify(
            ShelfLifeRegistry::isFoodItemForDefaults,
            (components, item) -> {
                long ticks = ShelfLifeRegistry.resolveShelfLifeTicks(item);
                if (ticks > 0) {
                    components.set(BiteComponents.SHELF_LIFE, new ShelfLife(ticks));
                }
            }));
    }
}
