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
 * 烹饪刷新（饥荒「烹饪把腐坏量减半」）：熔炉/烟熏炉/营火完成烹饪时，
 * 产物继承原料腐坏量的一半。burn 是全部炉类的单点，注入 TAIL——
 * 原版把产物放入 result 槽之后。
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    @Inject(method = "burn", at = @At("TAIL"))
    private static void bite$stampCooked(NonNullList<ItemStack> slots, ItemStack input, ItemStack result, CallbackInfo ci) {
        FreshnessStamper.stampCooked(result, input, FreshnessClock.now());
    }
}
