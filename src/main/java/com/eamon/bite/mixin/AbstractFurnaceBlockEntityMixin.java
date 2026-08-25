package com.eamon.bite.mixin;

import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessStamper;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 烹饪刷新（饥荒「Cooking refreshes spoilage」，spec v1.0.2）：
 * 熔炉/烟熏炉/营火完成烹饪时，产物继承原料腐坏量的一半。
 *
 * <p>26.2 签名（javap 验证）：
 * {@code private static void burn(NonNullList<ItemStack> slots,
 * ItemStack input, ItemStack result)} —— 单点覆盖全部炉类烹饪。
 * 注入 TAIL：vanilla 把产物放入 result 槽后，对产物按原料打标。
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    @Inject(method = "burn", at = @At("TAIL"))
    private static void bite$stampCooked(NonNullList<ItemStack> slots, ItemStack input, ItemStack result, CallbackInfo ci) {
        FreshnessStamper.stampCooked(result, input, FreshnessClock.now());
    }
}
