package com.eamon.bite.mixin;

import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessStamper;
import com.eamon.bite.freshness.MergeSnapshot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 合并站点：{@code Inventory.addResource}（背包放入 / 地面拾取）。快照-重算模式见
 * {@link com.eamon.bite.freshness.StackingRules} 类 javadoc。只 hook 双参重载——
 * 单参版本找到槽位后转发到这里，找不到槽位直接返回（无合并发生）。
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Unique private MergeSnapshot bite$snap;

    /** 拾取首见打标：入包先打标再参与堆叠判定——刚掉落的食物可能尚未被懒扫到，不打标则放宽判定看不到它的 stamp。幂等。 */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void bite$stampOnAdd(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        FreshnessStamper.stamp(stack, FreshnessClock.now());
    }

    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"))
    private void bite$capture(int slot, ItemStack origin, CallbackInfoReturnable<Integer> cir) {
        bite$snap = MergeSnapshot.capture(((Inventory) (Object) this).getItem(slot), origin);
    }

    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"))
    private void bite$reconcile(int slot, ItemStack origin, CallbackInfoReturnable<Integer> cir) {
        if (bite$snap != null) {
            bite$snap.reconcile(((Inventory) (Object) this).getItem(slot));
        }
        bite$snap = null;
    }
}
