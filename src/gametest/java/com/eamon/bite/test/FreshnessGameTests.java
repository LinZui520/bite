package com.eamon.bite.test;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessMath;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
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

    /**
     * 等价性放宽 mixin（Task 9）：两个不同 FRESHNESS 戳的同种未腐坏食物，
     * 经 {@code ItemStack.isSameItemSameComponents} 放宽后应能通过
     * {@link SimpleContainer#addItem} 合并到同一槽位。
     *
     * <p>链路：{@code addItem → moveItemToOccupiedSlotsWithSameType →
     * ItemStack.isSameItemSameComponents(a, b)}（已 {@code javap} 确认 26.2 字节码）。
     * mixin 在 RETURN 处把 false 改写为 true，使合并通过。
     */
    @GameTest(maxTicks = 20)
    public void relaxedEqualityMergesDifferentStamps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long shelfLifeTicks = 6L * 24000L; // bread 默认 6 天
        long staleAge = (long) (0.8 * shelfLifeTicks); // 80% 已过 → fraction 0.2

        ItemStack fresh = new ItemStack(Items.BREAD);
        fresh.set(BiteComponents.SHELF_LIFE, new ShelfLife(shelfLifeTicks));
        fresh.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));

        ItemStack stale = new ItemStack(Items.BREAD);
        stale.set(BiteComponents.SHELF_LIFE, new ShelfLife(shelfLifeTicks));
        stale.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - staleAge));

        SimpleContainer container = new SimpleContainer(2);
        container.addItem(fresh.copy());
        container.addItem(stale.copy());

        int count0 = container.getItem(0).getCount();
        int count1 = container.getItem(1).getCount();
        if (count0 != 2 || count1 != 0) {
            helper.fail("expected fresh+stale to merge (slot0=2, slot1=0), got slot0=" + count0 + " slot1=" + count1);
            return;
        }
        helper.succeed();
    }

    /**
     * 反例 1：不同 item（面包 vs 牛肉）不应合并——canMergeRelaxed 第一步
     * {@code a.is(b.getItem())} 即为 false。
     */
    @GameTest(maxTicks = 20)
    public void relaxedEqualityDoesNotMergeDifferentItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long shelfLifeTicks = 6L * 24000L;

        ItemStack bread = new ItemStack(Items.BREAD);
        bread.set(BiteComponents.SHELF_LIFE, new ShelfLife(shelfLifeTicks));
        bread.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));

        ItemStack beef = new ItemStack(Items.BEEF);
        beef.set(BiteComponents.SHELF_LIFE, new ShelfLife(shelfLifeTicks));
        beef.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));

        SimpleContainer container = new SimpleContainer(2);
        container.addItem(bread.copy());
        container.addItem(beef.copy());

        int count0 = container.getItem(0).getCount();
        int count1 = container.getItem(1).getCount();
        if (count0 != 1 || count1 != 1) {
            helper.fail("expected bread+beef NOT to merge (slot0=1, slot1=1), got slot0=" + count0 + " slot1=" + count1);
            return;
        }
        helper.succeed();
    }

    /**
     * 反例 2：已腐坏（fraction = 0）的栈不应合并——canMergeRelaxed 要求双方
     * {@code fraction > 0}。SPOILED 食物即使同 item 也不放宽。
     */
    @GameTest(maxTicks = 20)
    public void relaxedEqualityDoesNotMergeSpoiled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long shelfLifeTicks = 6L * 24000L;
        long spoiledAge = shelfLifeTicks + 1L; // 完全过期 → fraction 0

        ItemStack fresh = new ItemStack(Items.BREAD);
        fresh.set(BiteComponents.SHELF_LIFE, new ShelfLife(shelfLifeTicks));
        fresh.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));

        ItemStack spoiled = new ItemStack(Items.BREAD);
        spoiled.set(BiteComponents.SHELF_LIFE, new ShelfLife(shelfLifeTicks));
        spoiled.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - spoiledAge));

        double fFresh = FreshnessMath.fraction(now, fresh.get(BiteComponents.FRESHNESS), fresh.get(BiteComponents.SHELF_LIFE));
        double fSpoiled = FreshnessMath.fraction(now, spoiled.get(BiteComponents.FRESHNESS), spoiled.get(BiteComponents.SHELF_LIFE));
        if (fFresh <= 0.0 || fSpoiled > 0.0) {
            helper.fail("test setup invariant violated: fFresh=" + fFresh + " fSpoiled=" + fSpoiled + " (need fFresh>0, fSpoiled<=0)");
            return;
        }

        SimpleContainer container = new SimpleContainer(2);
        container.addItem(fresh.copy());
        container.addItem(spoiled.copy());

        int count0 = container.getItem(0).getCount();
        int count1 = container.getItem(1).getCount();
        if (count0 != 1 || count1 != 1) {
            helper.fail("expected fresh+spoiled NOT to merge (slot0=1, slot1=1), got slot0=" + count0 + " slot1=" + count1);
            return;
        }
        helper.succeed();
    }
}
