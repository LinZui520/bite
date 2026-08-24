package com.eamon.bite.test;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.config.ServerConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 新鲜度系统 GameTest 基建（后续任务的测试追加到本类）。
 *
 * <p>Checkpoint resolutions (MC 26.2, Fabric API 0.158.0+26.2):
 * <ul>
 *   <li>{@code FabricGameTest} 接口在 26.2 已不存在 — entrypoint {@code fabric-gametest}
 *       接受任意 Object，由 {@code TestAnnotationLocator} 扫描
 *       {@link GameTest} 注解方法（public、非 static、单参
 *       {@link GameTestHelper}、返回 void）。</li>
 *   <li>{@link GameTestHelper#absolutePos} 返回 {@link BlockPos}（非 Vec3），
 *       故用 {@link Vec3#atCenterOf} 转 {@code ORIGIN}。</li>
 *   <li>断言用 {@link GameTestHelper#fail(String)}（junit 不在 gametest 编译路径）。</li>
 *   <li>{@link LootTable#getRandomItems(LootParams, java.util.function.Consumer)}
 *       内部链路 {@code getRandomItemsRaw(LootParams)→getRandomItemsRaw(LootContext)}，
 *       后者被 Fabric {@code LootTableMixin} {@code @WrapMethod} 拦截，触发
 *       {@code MODIFY_DROPS}，故打标会在返回给 consumer 前生效。</li>
 * </ul>
 */
public class FreshnessGameTests {

    @GameTest
    public void lootDropsGetStamped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(
            ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath("bite", "gametest/food")));

        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, origin)
            .create(LootContextParamSets.CHEST);

        List<ItemStack> drops = new ArrayList<>();
        table.getRandomItems(params, drops::add);

        if (drops.isEmpty()) {
            helper.fail("loot table should drop bread");
            return;
        }

        long now = level.getGameTime();
        for (ItemStack drop : drops) {
            FreshnessStamp stamp = drop.get(BiteComponents.FRESHNESS);
            if (stamp == null) {
                helper.fail("loot drop should be stamped, got null");
                return;
            }
            if (stamp.creationGameTick() != now) {
                helper.fail("stamp tick " + stamp.creationGameTick() + " != game time " + now);
                return;
            }
        }

        helper.succeed();
    }

    /**
     * 懒扫描打标：放一个箱子塞入无 stamp 的面包；扫描周期后应被打标。
     *
     * <p>验证 FreshnessScanner + END_LEVEL_TICK 接线：
     * <ul>
     *   <li>新放置的箱子无 loot table（getLootTable == null），不会被跳过</li>
     *   <li>等待 scanIntervalTicks + 40 刻后，扫描至少跑过一次</li>
     *   <li>面包应被 FreshnessStamper 打上 FRESHNESS 组件</li>
     * </ul>
     */
    @GameTest(maxTicks = 200)
    public void scannerStampsUnstampedFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(1, 1, 1), ChestBlockEntity.class);
        if (chest == null) {
            helper.fail("chest block entity not created");
            return;
        }
        ItemStack bread = new ItemStack(Items.BREAD);
        if (bread.has(BiteComponents.FRESHNESS)) {
            helper.fail("bread should not have FRESHNESS before scan");
            return;
        }
        chest.setItem(0, bread);

        int interval = ServerConfig.get().scanIntervalTicks();
        helper.startSequence()
            .thenExecuteAfter(interval + 40, () -> {
                ItemStack stacked = chest.getItem(0);
                if (!stacked.has(BiteComponents.FRESHNESS)) {
                    helper.fail("scanner should have stamped chest food");
                }
            })
            .thenSucceed();
    }
}
