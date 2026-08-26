package com.eamon.bite;

import com.eamon.bite.command.BiteCommands;
import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessMath;
import com.eamon.bite.freshness.FreshnessScanner;
import com.eamon.bite.freshness.FreshnessStamper;
import com.eamon.bite.freshness.ShelfLifeRegistry;
import com.eamon.bite.item.BiteItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** mod 入口：装配配置、注册、懒扫描与进食拦截。 */
public class BiteMod implements ModInitializer {
    public static final String MOD_ID = "bite";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerConfig.load(FabricLoader.getInstance().getConfigDir());
        BiteComponents.init();
        BiteItems.init();
        registerDefaultShelfLife();
        registerLootDropStamping();
        registerLazyScan();
        registerSpoiledFoodBlock();
        BiteCommands.register();
        // 无 Level 上下文的静态钩子（合并重算、客户端渲染）读 FreshnessClock，
        // 每 tick 用主世界时钟刷新它（客户端侧由 BiteModClient 刷新）。
        ServerTickEvents.END_SERVER_TICK.register(server ->
            FreshnessClock.update(gameTime(server.overworld())));
        LOGGER.info("Because It's Too Easy initialized");
    }

    /**
     * 游戏时钟统一读取口。
     *
     * <p>26.2 有两套独立计数器：{@code level.getGameTime()} 只随真实运行 tick
     * 递增，不受 /time 影响；WorldClock（{@code getOverworldClockTime()}）才是
     * /time add|set 操作、随变速/暂停同步的玩家可感知时间。新鲜度绑定后者——
     * /time add 必须能加速腐坏。
     */
    public static long gameTime(Level level) {
        return level.getOverworldClockTime();
    }

    /** 为所有食物的默认组件注入 bite:shelf_life（天数由 {@link ShelfLifeRegistry} 解析）。 */
    private static void registerDefaultShelfLife() {
        DefaultItemComponentEvents.MODIFY.register(context -> context.modify(
            ShelfLifeRegistry::isFood,
            (components, item) -> {
                long ticks = ShelfLifeRegistry.resolveShelfLifeTicks(item);
                if (ticks > 0) {
                    components.set(BiteComponents.SHELF_LIFE, new ShelfLife(ticks));
                }
            }));
    }

    /** loot 掉落物生成时即打标。 */
    private static void registerLootDropStamping() {
        LootTableEvents.MODIFY_DROPS.register((tableHolder, context, drops) -> {
            long now = gameTime(context.getLevel());
            for (ItemStack drop : drops) {
                FreshnessStamper.stamp(drop, now);
            }
        });
    }

    /** 懒扫描：按配置周期扫描玩家背包、已加载区块容器、容器实体与地面掉落物。 */
    private static void registerLazyScan() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            ServerConfig cfg = ServerConfig.get();
            if (!cfg.enabled()) return;
            if (cfg.scanIntervalTicks() <= 0) return;
            if (level.getGameTime() % cfg.scanIntervalTicks() != 0) return;
            FreshnessScanner.scanLevel(level, gameTime(level));
        });
    }

    /** 已完全变质的食物禁食：动作栏提示 + FAIL 阻止进食（spec §8）。 */
    private static void registerSpoiledFoodBlock() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!ServerConfig.get().spoiledInedible()) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (!isSpoiledInedible(stack, gameTime(level))) return InteractionResult.PASS;
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("bite.msg.spoiled_inedible"));
            }
            return InteractionResult.FAIL;
        });
    }

    /** 变质禁食判定（独立纯函数，供回调与 gametest 共用）。 */
    public static boolean isSpoiledInedible(ItemStack stack, long now) {
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        if (stamp == null) return false;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return false;
        return FreshnessMath.fraction(now, stamp, life) <= 0.0;
    }
}
