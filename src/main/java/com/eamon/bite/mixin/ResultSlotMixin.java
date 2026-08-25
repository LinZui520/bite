package com.eamon.bite.mixin;

import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessStamper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成继承新鲜度（v1.0.7，饥荒锅料理语义）：
 * 玩家从合成结果槽取走产物时，产物继承合成格内全部食物原料的
 * 平均腐坏量的一半（FreshnessStamper.stampCrafted）。
 *
 * <p>26.2 签名（javap 验证）：{@code ResultSlot} 持有
 * {@code private final CraftingContainer craftSlots}；{@code onTake(Player, ItemStack)}
 * 在 vanilla 消耗原料（shrink）之前调用——TAIL 时原料仍在格中，可读。
 *
 * <p>覆盖 2×2 手持合成与 3×3 工作台（同一 ResultSlot）。
 * 已知边界：自动合成器（Crafter）不走 ResultSlot，其产物由懒扫描
 * 首见打标（全新）——不继承。
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Shadow @Final private CraftingContainer craftSlots;

    @Inject(method = "onTake", at = @At("TAIL"))
    private void bite$stampCrafted(Player player, ItemStack result, CallbackInfo ci) {
        if (result.isEmpty()) return;
        List<ItemStack> ingredients = new ArrayList<>();
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ingredients.add(craftSlots.getItem(i));
        }
        FreshnessStamper.stampCrafted(result, ingredients, FreshnessClock.now());
    }
}
