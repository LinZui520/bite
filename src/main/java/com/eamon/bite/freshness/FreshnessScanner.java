package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 懒扫描：玩家背包 + 已加载区块容器 + 容器实体。未打标的食物首次被观察到时打标。
 *
 * <p>Checkpoint (MC 26.2): {@code ChunkMap.getChunks()} 不存在。公开等价 API：
 * <ul>
 *   <li>{@link net.minecraft.server.level.ServerChunkCache#chunkMap} — {@code public final} 字段</li>
 *   <li>{@link net.minecraft.server.level.ChunkMap#forEachReadyToSendChunk} —
 *       遍历 {@code visibleChunkMap} 中所有已就绪的 {@link LevelChunk}</li>
 * </ul>
 * 不需要 accessor mixin。
 *
 * <p>未开箱的 {@link RandomizableContainerBlockEntity}（loot table != null）跳过，
 * 避免触发 loot 生成。
 */
public final class FreshnessScanner {
    private FreshnessScanner() {}

    /**
     * 扫描整个维度：玩家背包、已加载区块容器、容器实体。
     *
     * @param level 服务端维度
     * @param now   当前游戏刻（{@code level.getGameTime()}）
     */
    public static void scanLevel(ServerLevel level, long now) {
        // (a) 在线玩家背包
        for (var player : level.players()) {
            scanContainer(player.getInventory(), now);
        }
        // (b) 已加载区块的方块实体容器
        level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> {
            for (var be : chunk.getBlockEntities().values()) {
                // 未开箱的 loot 容器跳过（不观察）
                if (be instanceof RandomizableContainerBlockEntity rcbe
                    && rcbe.getLootTable() != null) {
                    continue;
                }
                if (be instanceof Container container) {
                    scanContainer(container, now);
                }
            }
        });
        // (c) 容器实体（如箱子矿车/漏斗矿车等原版容器实体，及其他 mod 的容器实体）
        for (var entity : level.getAllEntities()) {
            if (entity instanceof Container container) {
                scanContainer(container, now);
            }
        }
    }

    private static void scanContainer(Container container, long now) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            FreshnessStamper.stamp(stack, now);
            convertIfSpoiled(container, i, stack, now);
        }
    }

    /**
     * 变质转换（v1.0.1）：fraction ≤ 0 的食物整堆替换为配置的产物
     * （默认 bite:rotten_organic），数量守恒。
     *
     * <p>转换产物自身挂 SHELF_LIFE=NEVER（永不腐坏），天然不会被二次转换。
     * 转换随懒扫描周期批量发生（默认 5 秒一轮），天然限流。
     */
    private static void convertIfSpoiled(Container container, int slot, ItemStack stack, long now) {
        ServerConfig cfg = ServerConfig.get();
        if (!cfg.spoiledConversion()) return;
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        if (stamp == null) return;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return;
        if (FreshnessMath.fraction(now, stamp, life) > 0.0) return;

        Identifier resultId = Identifier.tryParse(cfg.spoiledResult());
        if (resultId == null) return; // 配置了非法 id 字符串 → 保守跳过
        var resultHolder = BuiltInRegistries.ITEM.get(resultId);
        if (resultHolder.isEmpty()) return; // 配置了无效 id → 保守跳过
        Item result = resultHolder.get().value();
        if (result == Items.AIR) return;

        ItemStack converted = new ItemStack(result, stack.getCount());
        container.setItem(slot, converted);
    }
}
