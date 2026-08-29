package com.eamon.bite.mixin;

import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessStamper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 篝火烹饪继承（饥荒「烹饪把腐坏量减半」）：{@code CampfireBlockEntity.
 * cookTick} 烤熟时产物经 {@code Containers.dropItemStack} 直接弹出
 * （不进槽位、不走熔炉的 burn 路径）——此前产物无戳，被地面掉落物的
 * 懒扫描当全新打标（篝火 = 免费保鲜漏洞）。
 *
 * <p>用 WrapOperation 包住 dropItemStack：弹出前对产物按「篝火槽内
 * 的原料」做继承打标。原料定位：drop 发生在槽位清空<b>之前</b>，
 * 取实体 4 槽中第一个非空栈——cookTick 循环体单槽自洽（达标即
 * drop + 清空），正在处理的槽此刻必然非空。
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {

    @WrapOperation(
        method = "cookTick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V")
    )
    private static void bite$stampCampfireResult(net.minecraft.world.level.Level level, double x, double y, double z,
                                                 ItemStack result, Operation<Void> original,
                                                 ServerLevel serverLevel, BlockPos pos, BlockState state,
                                                 CampfireBlockEntity entity,
                                                 RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipeCache) {
        // cookTick 是 static——entity 从尾部追加参数拿（MixinExtras 约定：
        // 目标方法的形参可追加在 Operation 之后）
        ItemStack ingredient = ItemStack.EMPTY;
        for (int slot = 0; slot < 4; slot++) {
            ItemStack inSlot = entity.getItems().get(slot);
            if (!inSlot.isEmpty()) {
                ingredient = inSlot;
                break;
            }
        }
        if (!ingredient.isEmpty()) {
            FreshnessStamper.stampCooked(result, ingredient, FreshnessClock.now());
        }
        original.call(level, x, y, z, result);
    }
}
