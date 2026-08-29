package com.eamon.bite.mixin;

import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessStamper;
import com.eamon.bite.freshness.MergeSnapshot;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 烹饪刷新注入点（饥荒「烹饪把腐坏量减半」）：熔炉/烟熏炉/营火完成
 * 烹饪时，产物继承原料腐坏量的一半。burn 是全部炉类的单点。
 *
 * <p><b>产物合并 + 打标时序</b>（26.2 burn 源码）：
 * <ol>
 *   <li>result 槽空：{@code items.set(2, result.copy())}——新产物落槽</li>
 *   <li>result 槽非空：{@code resultItemStack.grow(result.getCount())}
 *       ——新旧产物合并（<b>合并站点</b>：HEAD 快照 + TAIL 对 grow 后
 *       的产物做加权平均——新鲜度语义与背包/漏斗一致）</li>
 *   <li>TAIL：对 {@code items.get(2)}（实际产物，含合并结果）按原料
 *       打继承标。<b>不能</b> stamp 第三参 {@code result}——那是 recipe
 *       的模板栈（每次 assemble 新建、无 FRESHNESS），stamp 它等于
 *       没打（曾导致产物被懒扫描当全新 → 新鲜度回满）</li>
 * </ol>
 *
 * <p>配套：{@code canBurn} 的 {@code isSameItemSameComponents(result,
 * burnResult)} 比较「有戳产物 vs 无戳模板」会被放宽判定接住
 * （canMergeRelaxed 已支持单边有戳）——第二个食物才能正常入炉。
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    @Unique private static MergeSnapshot bite$snap;

    @Inject(method = "burn", at = @At("HEAD"))
    private static void bite$captureMerge(NonNullList<ItemStack> slots, ItemStack input, ItemStack result, CallbackInfo ci) {
        bite$snap = MergeSnapshot.captureUnstamped(slots.get(2), result, FreshnessClock.now());
    }

    @Inject(method = "burn", at = @At("TAIL"))
    private static void bite$stampCooked(NonNullList<ItemStack> slots, ItemStack input, ItemStack result, CallbackInfo ci) {
        ItemStack produced = slots.get(2);
        if (bite$snap != null) {
            // 合并路径：旧产物 + 新产物加权平均（无戳的模板按全新参与）
            bite$snap.reconcile(produced);
        } else {
            // 首个产物：按原料继承一半腐坏量
            FreshnessStamper.stampCooked(produced, input, FreshnessClock.now());
        }
        bite$snap = null;
    }
}
