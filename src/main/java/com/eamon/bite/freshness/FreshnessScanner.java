package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/**
 * 懒扫描：按配置周期观察世界里的食物——未打标的首次被观察到时打标，
 * 已完全变质的按配置转换为产物（数量守恒）。
 *
 * <p>未开箱的 loot 容器（loot table 尚未生成）跳过，避免触发 loot 生成。
 */
public final class FreshnessScanner {
    private FreshnessScanner() {}

    /** 扫描整个维度：玩家背包、已加载区块容器、容器实体与地面掉落物。 */
    public static void scanLevel(ServerLevel level, long now) {
        for (var player : level.players()) {
            scanContainer(player.getInventory(), now);
        }
        level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> {
            for (var be : chunk.getBlockEntities().values()) {
                if (be instanceof RandomizableContainerBlockEntity rcbe
                    && rcbe.getLootTable() != null) {
                    continue;
                }
                if (be instanceof Container container) {
                    scanContainer(container, now);
                }
            }
        });
        // 容器实体（箱子矿车等）与地面掉落物不在方块实体之列，单独扫
        for (var entity : level.getAllEntities()) {
            if (entity instanceof Container container) {
                scanContainer(container, now);
            } else if (entity instanceof ItemEntity itemEntity) {
                ItemStack ground = itemEntity.getItem();
                FreshnessStamper.stamp(ground, now);
                ItemStack converted = spoiledResult(ground, now);
                if (converted != null) {
                    itemEntity.setItem(converted);
                }
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

    /** 已完全变质的食物整堆替换为配置产物，数量守恒；产物自身永不腐坏，不会被二次转换。 */
    private static void convertIfSpoiled(Container container, int slot, ItemStack stack, long now) {
        ServerConfig cfg = ServerConfig.get();
        if (!cfg.spoiledConversion()) return;
        ItemStack converted = spoiledResult(stack, now);
        if (converted != null) {
            container.setItem(slot, converted);
        }
    }

    /** 计算变质产物；未变质或不适用返回 null。配置了非法/无效物品 id 时保守跳过。 */
    private static ItemStack spoiledResult(ItemStack stack, long now) {
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        if (stamp == null) return null;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return null;
        if (FreshnessMath.fraction(now, stamp, life) > 0.0) return null;

        Identifier resultId = Identifier.tryParse(ServerConfig.get().spoiledResult());
        if (resultId == null) return null;
        var resultHolder = BuiltInRegistries.ITEM.get(resultId);
        if (resultHolder.isEmpty()) return null;
        Item result = resultHolder.get().value();
        if (result == Items.AIR) return null;

        return new ItemStack(result, stack.getCount());
    }
}
