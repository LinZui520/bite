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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
        registerLootDropStamping();
        registerLazyScan();
        registerSpoiledFoodBlock();
        BiteCommands.register();
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

    /**
     * 给 loot 掉落物打标。
     *
     * <p>Checkpoint (MC 26.2, Fabric API 0.158.0+26.2):
     * {@link LootTableEvents.ModifyDrops#modifyLootTableDrops} 签名为
     * {@code (Holder<LootTable>, LootContext, List<ItemStack>)}，
     * 非 brief 中的 {@code (ResourceKey, context, drops)}。
     * 通过 {@link net.minecraft.world.level.storage.loot.LootContext#getLevel()}
     * 取 ServerLevel 后再取 game time。
     */
    private static void registerLootDropStamping() {
        LootTableEvents.MODIFY_DROPS.register((tableHolder, context, drops) -> {
            long now = context.getLevel().getGameTime();
            for (ItemStack drop : drops) {
                FreshnessStamper.stamp(drop, now);
            }
        });
    }

    /**
     * 懒扫描：每 {@code scanIntervalTicks} 刻扫描一次在线玩家背包、已加载区块容器、容器实体。
     *
     * <p>使用 {@link ServerTickEvents#END_LEVEL_TICK} 逐维度触发，门控条件：
     * {@code level.getGameTime() % scanIntervalTicks == 0 && enabled}。
     */
    private static void registerLazyScan() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            ServerConfig cfg = ServerConfig.get();
            if (!cfg.enabled()) return;
            if (cfg.scanIntervalTicks() <= 0) return;
            if (level.getGameTime() % cfg.scanIntervalTicks() != 0) return;
            FreshnessScanner.scanLevel(level, level.getGameTime());
        });
    }

    /**
     * 拦截变质食物的进食开始（spec §8 拦截）。
     *
     * <p>通过 {@link UseItemCallback#EVENT}（右键开始时触发）实现：
     * <ul>
     *   <li>配置 {@code spoiledInedible=false} → 一律 PASS（不拦截）</li>
     *   <li>无 FRESHNESS 戳 / SHELF_LIFE 永不腐坏 → PASS（原版行为）</li>
     *   <li>fraction &gt; 0（未完全变质）→ PASS</li>
     *   <li>fraction ≤ 0（已变质）→ 服务端发送动作栏消息，返回 FAIL 阻止进食</li>
     * </ul>
     *
     * <p>26.2 检查点：
     * <ul>
     *   <li>{@link UseItemCallback#interact} 签名 {@code (Player, Level, InteractionHand) -> InteractionResult}，
     *       通过 javap 验证。</li>
     *   <li>{@link InteractionResult#PASS} 让原版继续；{@link InteractionResult#FAIL} 阻止并触发挥手动画。</li>
     *   <li>{@code Player.displayClientMessage} 在 26.2 已不存在——改用
     *       {@link net.minecraft.server.level.ServerPlayer#sendOverlayMessage}（动作栏消息）。
     *       服务端 player 必为 ServerPlayer，故 instanceof 守卫后调用。</li>
     *   <li>lang key {@code bite.msg.spoiled_inedible} 由 Task 12 添加；缺失时显示原始 key，不影响功能。</li>
     * </ul>
     */
    private static void registerSpoiledFoodBlock() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!ServerConfig.get().spoiledInedible()) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (!isSpoiledInedible(stack, level.getGameTime())) return InteractionResult.PASS;
            if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("bite.msg.spoiled_inedible"));
            }
            return InteractionResult.FAIL;
        });
    }

    /**
     * 变质禁食判定（包可见，供 gametest 直接调用）。
     *
     * <p>抽出为独立纯函数，使 UseItemCallback 回调与 gametest 共享同一决策逻辑：
     * <ul>
     *   <li>无 FRESHNESS 戳 → false（原版行为，未打标食物不受禁食约束）</li>
     *   <li>无 SHELF_LIFE 或 spoilTicks ≤ 0 → false（永不腐坏豁免）</li>
     *   <li>fraction &gt; 0（未完全变质）→ false</li>
     *   <li>fraction ≤ 0（已完全变质）→ true（应阻止进食）</li>
     * </ul>
     *
     * <p>实现记录（spec §8）：本判定仅作用于玩家进食路径（UseItemCallback / 玩家右键使用）。
     * 生物进食（僵尸等）的 mob parity 延后至 v1.1。
     *
     * @param stack 待判定物品栈
     * @param now   当前游戏刻（服务端权威时间）
     * @return 若该栈已完全变质且应被禁食则返回 true
     */
    public static boolean isSpoiledInedible(ItemStack stack, long now) {
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        if (stamp == null) return false;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return false;
        return FreshnessMath.fraction(now, stamp, life) <= 0.0;
    }
}
