package com.eamon.bite.test;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.BiteMod;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
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
     * 加权平均合并重算（Task 10）：stale bread x3 (10%) + fresh bread x1 (100%)
     * 经 {@link SimpleContainer#addItem} 合并后，dest 数量 4、新鲜度应为
     * {@code (0.1*3 + 1.0*1) / 4 = 0.325}（±0.01）。
     *
     * <p>验证链路：
     * <ul>
     *   <li>{@code addItem → moveItemToOccupiedSlotsWithSameType →
     *       ItemStack.isSameItemSameComponents}（Task 9 放宽判定通过，不同 stamp 仍可合并）</li>
     *   <li>{@code moveItemsBetweenStacks(source, destination)} —— Task 10
     *       {@code SimpleContainerMixin} 在 HEAD 快照 destination 的 stamp/count，
     *       TAIL 调用 {@link StackingRules#reconcile} 反解加权平均时间戳。</li>
     *   <li>显式设置 {@code SHELF_LIFE}（task 要求解确定性；reconcile 读取 dest 的 SHELF_LIFE）。</li>
     * </ul>
     *
     * <p>注意：{@link FreshnessClock#update} 必须在断言前同步——reconcile 内部走
     * {@code FreshnessClock.now()}，而 gametest 服务器由 END_SERVER_TICK 异步更新。
     */
    @GameTest(maxTicks = 20)
    public void mergeIsWeightedAverage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long life = 24000L * 6; // bread 6 days

        ItemStack stale = new ItemStack(Items.BREAD, 3);
        stale.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        stale.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - (long) (life * 0.9))); // fraction 0.1

        ItemStack fresh = new ItemStack(Items.BREAD, 1);
        fresh.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        fresh.set(BiteComponents.FRESHNESS, new FreshnessStamp(now)); // fraction 1.0

        // 预检：分数符合预期，避免 setup 误差被误判为 mixin bug
        double fStale = FreshnessMath.fraction(now, stale.get(BiteComponents.FRESHNESS), stale.get(BiteComponents.SHELF_LIFE));
        double fFresh = FreshnessMath.fraction(now, fresh.get(BiteComponents.FRESHNESS), fresh.get(BiteComponents.SHELF_LIFE));
        if (Math.abs(fStale - 0.1) > 0.001 || Math.abs(fFresh - 1.0) > 0.001) {
            helper.fail("test setup invariant violated: fStale=" + fStale + " fFresh=" + fFresh);
            return;
        }

        SimpleContainer container = new SimpleContainer(9);
        container.addItem(stale.copy());
        container.addItem(fresh.copy());

        ItemStack merged = container.getItem(0);
        if (merged.getCount() != 4) {
            helper.fail("expected merged count 4 (stale x3 + fresh x1), got " + merged.getCount());
            return;
        }
        FreshnessStamp stamp = merged.get(BiteComponents.FRESHNESS);
        if (stamp == null) {
            helper.fail("merged stack missing FRESHNESS component");
            return;
        }
        double expected = (0.1 * 3 + 1.0 * 1) / 4.0; // 0.325
        double actual = FreshnessMath.fraction(now, stamp, new ShelfLife(life));
        if (Math.abs(expected - actual) > 0.01) {
            helper.fail("merged freshness " + actual + " != expected " + expected + " (±0.01 tolerance)");
            return;
        }
        helper.succeed();
    }

    /**
     * 漏斗合并重算（Task 10 补充站点）：漏斗从上方箱子吸取不同新鲜度面包时，
     * 合并发生在 {@code HopperBlockEntity.tryMoveInItem}（brief 5 站点表把漏斗
     * 错误映射到 SimpleContainer.moveItemsBetweenStacks，genSources 修正）。
     *
     * <p>验证链路：
     * <ul>
     *   <li>箱子 slot 0 = stale bread x1 (10%)，slot 1 = fresh bread x1 (100%)</li>
     *   <li>漏斗置于箱子下方，每 8 tick 吸取 1 件</li>
     *   <li>第 1 周期：吸 stale → 漏斗 slot 0 = stale x1</li>
     *   <li>第 2 周期：吸 fresh → {@code tryMoveInItem} 合并到漏斗 slot 0（dest grew），
     *       {@code HopperBlockEntityMixin} reconcile → 加权平均</li>
     *   <li>期望 fraction = (0.1*1 + 1.0*1) / 2 = 0.55 ± 0.02</li>
     * </ul>
     *
     * <p>期望值在 assert 时用 {@code FreshnessClock.now()} 同步计算，消除 tick 漂移。
     */
    @GameTest(maxTicks = 80)
    public void hopperMergeIsWeightedAverage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long life = 24000L * 6;

        ItemStack stale = new ItemStack(Items.BREAD, 1);
        stale.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        stale.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - (long) (life * 0.9)));

        ItemStack fresh = new ItemStack(Items.BREAD, 1);
        fresh.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        fresh.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));

        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.HOPPER);

        ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(1, 2, 1), ChestBlockEntity.class);
        if (chest == null) {
            helper.fail("chest block entity not created");
            return;
        }
        chest.setItem(0, stale.copy());
        chest.setItem(1, fresh.copy());

        // 漏斗冷却 8 tick/周期，2 件需 ~20 tick；留余量到 60 tick
        final FreshnessStamp staleStamp = stale.get(BiteComponents.FRESHNESS);
        final FreshnessStamp freshStamp = fresh.get(BiteComponents.FRESHNESS);
        final ShelfLife shelfLife = new ShelfLife(life);
        helper.startSequence()
            .thenExecuteAfter(60, () -> {
                HopperBlockEntity hopper = helper.getBlockEntity(new BlockPos(1, 1, 1), HopperBlockEntity.class);
                if (hopper == null) {
                    helper.fail("hopper block entity not created");
                    return;
                }
                ItemStack merged = hopper.getItem(0);
                if (merged.getCount() != 2) {
                    helper.fail("expected hopper slot 0 count 2 (stale+fresh), got " + merged.getCount());
                    return;
                }
                FreshnessStamp stamp = merged.get(BiteComponents.FRESHNESS);
                if (stamp == null) {
                    helper.fail("hopper merged stack missing FRESHNESS component");
                    return;
                }
                long nowAssert = FreshnessClock.now();
                double fStale = FreshnessMath.fraction(nowAssert, staleStamp, shelfLife);
                double fFresh = FreshnessMath.fraction(nowAssert, freshStamp, shelfLife);
                double expected = (fStale + fFresh) / 2.0;
                double actual = FreshnessMath.fraction(nowAssert, stamp, shelfLife);
                if (Math.abs(expected - actual) > 0.02) {
                    helper.fail("hopper merged freshness " + actual + " != expected " + expected + " (±0.02 tolerance)");
                    return;
                }
            })
            .thenSucceed();
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

    /**
     * 进食营养缩放（Task 11）：STALE 食物（fraction 0.4）应按 nutritionScaleStale
     * （默认 0.75）缩放营养增量。
     *
     * <p>验证链路：
     * <ul>
     *   <li>面包 nutrition=5（genSources: {@code Foods.BREAD = Builder.nutrition(5).saturationModifier(0.6F).build()}）</li>
     *   <li>初始 foodLevel=10；原版 eat 后 → 10+5=15</li>
     *   <li>STALE scale 0.75 → delta 5*0.75=3.75 → round 4 → 10+4=14</li>
     *   <li>fraction 0.4 ∈ (oldThreshold=0.25, staleThreshold=0.5] → STALE → scale 0.75 ✓</li>
     * </ul>
     *
     * <p>26.2 适配：brief 使用 {@code makeTallerMockPlayer}——26.2 GameTestHelper 无此方法，
     * 改用 {@link GameTestHelper#makeMockPlayer(GameType)}（返回 {@link net.minecraft.world.entity.player.Player}）。
     * Player 拥有 FoodData，finishUsingItem 链路
     * {@code ItemStack.finishUsingItem → Item.finishUsingItem → Consumable.onConsume
     * → FoodProperties.onConsume → player.getFoodData().eat(this)} 正常触发。
     *
     * <p>显式设置 SHELF_LIFE + FreshnessClock.update 以保证确定性
     * （mixin RETURN 通过 {@link FreshnessClock#now()} 计算 fraction）。
     */
    @GameTest(maxTicks = 20)
    public void staleFoodGivesReducedNutrition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long life = 24000L * 6; // bread 默认 6 天

        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(10);

        ItemStack bread = new ItemStack(Items.BREAD);
        bread.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        bread.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - (long) (life * 0.6))); // 40% → STALE → 75% 营养

        // 预检：fraction 确为 0.4，避免 setup 误差
        double fraction = FreshnessMath.fraction(now,
            bread.get(BiteComponents.FRESHNESS), bread.get(BiteComponents.SHELF_LIFE));
        if (Math.abs(fraction - 0.4) > 0.001) {
            helper.fail("test setup invariant violated: fraction=" + fraction + " (expected 0.4)");
            return;
        }

        bread.finishUsingItem(level, player);

        int foodLevel = player.getFoodData().getFoodLevel();
        // 面包营养 5 → 5 * 0.75 = 3.75 → round 4 → 10 + 4 = 14
        if (foodLevel != 14) {
            helper.fail("stale bread should give 75% nutrition (foodLevel=14), got " + foodLevel);
            return;
        }
        helper.succeed();
    }

    /**
     * 新鲜食物满营养（Task 11 对照组）：FRESH 食物（fraction 1.0）应保持原版营养。
     *
     * <p>初始 foodLevel=10；面包 nutrition=5；eat 后 → 10+5=15（无缩放）。
     */
    @GameTest(maxTicks = 20)
    public void freshFoodGivesFullNutrition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long life = 24000L * 6;

        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(10);

        ItemStack bread = new ItemStack(Items.BREAD);
        bread.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        bread.set(BiteComponents.FRESHNESS, new FreshnessStamp(now)); // fraction 1.0 → FRESH

        bread.finishUsingItem(level, player);

        int foodLevel = player.getFoodData().getFoodLevel();
        if (foodLevel != 15) {
            helper.fail("fresh bread should give full nutrition (foodLevel=15), got " + foodLevel);
            return;
        }
        helper.succeed();
    }

    /**
     * 满食欲路径（Task 11 review r1 修复）：foodLevel=20 时原版 FoodData.add 把
     * 营养增量钳制为 0（delta=0），但<b>仍添加饱和度</b>——饱和度缩放不得因
     * delta=0 被跳过，否则满食欲吃 STALE 食物获得全额饱和度（bypass）。
     *
     * <p>结构（对照组-治疗组，避免硬编码原版数值）：
     * <ul>
     *   <li>对照：food=20 吃 FRESH 面包（scale=1.0，mixin 不介入）→
     *       测原版饱和度增量 satDeltaFresh
     *       （面包 saturation = 5*0.6*2 = 6.0，genSources Foods.BREAD +
     *       FoodConstants.saturationByModifier）</li>
     *   <li>治疗：food=20 吃 STALE 面包（fraction 0.4 → scale 0.75）→
     *       饱和度增量应为 satDeltaFresh * 0.75</li>
     *   <li>food 应保持 20（原版 clamp；delta=0 时 mixin 不动 foodLevel）</li>
     * </ul>
     *
     * <p>对照断言 {@code controlSatDelta > 0} 防止关系断言空转
     * （若对照增量为 0，0 == 0*0.75 会假通过）。
     */
    @GameTest(maxTicks = 20)
    public void staleFoodAtFoodCapStillScalesSaturation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long life = 24000L * 6;

        // 对照组：food=20 吃 FRESH 面包（scale=1.0，mixin 不介入）
        net.minecraft.world.entity.player.Player control = helper.makeMockPlayer(GameType.SURVIVAL);
        control.getFoodData().setFoodLevel(20);
        float controlSatBefore = control.getFoodData().getSaturationLevel();
        ItemStack freshBread = new ItemStack(Items.BREAD);
        freshBread.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        freshBread.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));
        freshBread.finishUsingItem(level, control);
        float controlSatDelta = control.getFoodData().getSaturationLevel() - controlSatBefore;
        if (controlSatDelta <= 0.0f) {
            helper.fail("control (fresh bread at food cap) should gain saturation, got delta " + controlSatDelta);
            return;
        }

        // 治疗组：food=20 吃 STALE 面包（fraction 0.4 → scale 0.75）
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(20);
        float satBefore = player.getFoodData().getSaturationLevel();
        ItemStack staleBread = new ItemStack(Items.BREAD);
        staleBread.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        staleBread.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - (long) (life * 0.6)));
        staleBread.finishUsingItem(level, player);

        if (player.getFoodData().getFoodLevel() != 20) {
            helper.fail("food level should stay at cap 20, got " + player.getFoodData().getFoodLevel());
            return;
        }
        float satDelta = player.getFoodData().getSaturationLevel() - satBefore;
        float expected = controlSatDelta * 0.75f;
        if (Math.abs(satDelta - expected) > 0.0001f) {
            helper.fail("stale bread saturation delta should be " + expected
                + " (75% of fresh " + controlSatDelta + "), got " + satDelta);
            return;
        }
        helper.succeed();
    }

    /**
     * 变质禁食判定（终审补充）：覆盖 UseItemCallback 拦截分支的唯一未覆盖 gameplay 路径。
     *
     * <p>UseItemCallback 由玩家右键交互触发，gametest 无法直接驱动玩家输入；
     * 故测试回调所调用的<b>决策函数</b> {@link BiteMod#isSpoiledInedible}（终审抽出，
     * 回调与测试共享同一判定）。四个断言覆盖全部分支：
     * <ul>
     *   <li>spoiled food（FRESHNESS 早于 shelf life → fraction 0）→ <b>true</b>（阻止进食）</li>
     *   <li>fresh food（fraction 1.0）→ <b>false</b></li>
     *   <li>unstamped food（无 FRESHNESS）→ <b>false</b>（原版行为）</li>
     *   <li>never-spoil food（SHELF_LIFE spoilTicks ≤ 0）→ <b>false</b>（豁免）</li>
     * </ul>
     */
    @GameTest(maxTicks = 20)
    public void spoiledInedibleDecision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FreshnessClock.update(level.getGameTime());
        long now = FreshnessClock.now();
        long life = 24000L * 6; // bread 6 days

        // (1) spoiled: stamp older than shelf life → fraction 0
        ItemStack spoiled = new ItemStack(Items.BREAD);
        spoiled.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        spoiled.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - life - 1L));
        if (!BiteMod.isSpoiledInedible(spoiled, now)) {
            helper.fail("spoiled food (fraction 0) should be inedible → true");
            return;
        }

        // (2) fresh: fraction 1.0
        ItemStack fresh = new ItemStack(Items.BREAD);
        fresh.set(BiteComponents.SHELF_LIFE, new ShelfLife(life));
        fresh.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));
        if (BiteMod.isSpoiledInedible(fresh, now)) {
            helper.fail("fresh food (fraction 1.0) should not be inedible → false");
            return;
        }

        // (3) unstamped: no FRESHNESS component
        ItemStack unstamped = new ItemStack(Items.BREAD);
        if (BiteMod.isSpoiledInedible(unstamped, now)) {
            helper.fail("unstamped food (no FRESHNESS) should not be inedible → false");
            return;
        }

        // (4) never-spoil: SHELF_LIFE spoilTicks <= 0
        ItemStack neverSpoil = new ItemStack(Items.BREAD);
        neverSpoil.set(BiteComponents.SHELF_LIFE, new ShelfLife(-1L));
        neverSpoil.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - life - 1L));
        if (BiteMod.isSpoiledInedible(neverSpoil, now)) {
            helper.fail("never-spoil food (spoilTicks <= 0) should not be inedible → false");
            return;
        }

        helper.succeed();
    }

    /**
     * BUG 复现 1（诊断）：DefaultItemComponentEvents 注入的默认 bite:shelf_life
     * 在真实物品上是否生效。此前的测试全部显式 set 组件，从未验证默认路径。
     */
    @GameTest
    public void defaultShelfLifeIsInjected(GameTestHelper helper) {
        ItemStack bread = new ItemStack(Items.BREAD);
        ShelfLife life = bread.get(BiteComponents.SHELF_LIFE);
        if (life == null) {
            helper.fail("默认组件未生效：new ItemStack(BREAD) 上没有 bite:shelf_life");
            return;
        }
        if (life.spoilTicks() <= 0) {
            helper.fail("默认 shelf_life 值异常: " + life.spoilTicks());
            return;
        }
        helper.succeed();
    }

    /**
     * BUG 复现 2（诊断）：模拟用户报告的场景 —— 背包里有一块「已打标」食物
     * （懒扫描已处理），拾取一块「从未打标」的同类食物（地上非 loot 来源掉落物，
     * ItemEntity 不是 Container，懒扫描扫不到，永远无 FRESHNESS）。
     * 走真实 Inventory.add 入口。期望（正确行为）合并为 1 堆。
     */
    @GameTest
    public void pickupOfUnstampedFoodMergesWithStamped(GameTestHelper helper) {
        FreshnessClock.update(helper.getLevel().getGameTime());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);

        // 背包里的：已被懒扫描打标（stamp + life）
        ItemStack stamped = new ItemStack(Items.BREAD);
        stamped.set(BiteComponents.SHELF_LIFE, new ShelfLife(24000L * 6));
        stamped.set(BiteComponents.FRESHNESS, new FreshnessStamp(FreshnessClock.now()));
        player.getInventory().add(stamped);

        // 「拾取」的：地上掉落物状态（无 FRESHNESS；SHELF_LIFE 来自默认组件）
        ItemStack unstamped = new ItemStack(Items.BREAD);
        player.getInventory().add(unstamped);

        int breadStacks = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.BREAD)) breadStacks++;
        }
        if (breadStacks != 1) {
            helper.fail("复现：无标掉落物拾取后未与背包已标食物合并，背包里有 "
                + breadStacks + " 堆面包（期望 1 堆）");
            return;
        }
        helper.succeed();
    }
}
