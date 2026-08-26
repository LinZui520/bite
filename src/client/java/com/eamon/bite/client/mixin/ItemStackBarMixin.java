package com.eamon.bite.client.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ClientConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessMath;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 物品图标下的新鲜度进度条（spec §7.1）：注入 {@link ItemStack} 的
 * isBarVisible / getBarWidth / getBarColor 三个方法 RETURN。
 *
 * <p>耐久条优先：stack 受损时早退让原版值生效；否则持有 freshness 组件且
 * 客户端配置开启时按 fraction 绘制：宽度 {@code round(13 × fraction)}，
 * 颜色五档（绿/黄/橙/红，已腐坏为灰色满条）。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackBarMixin {

    @Inject(method = "isBarVisible", at = @At("RETURN"), cancellable = true)
    private void bite$barVisible(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return; // 耐久条优先
        if (!ClientConfig.get().showBar()) return;
        ItemStack self = (ItemStack) (Object) this;
        cir.setReturnValue(self.has(BiteComponents.FRESHNESS));
    }

    @Inject(method = "getBarWidth", at = @At("RETURN"), cancellable = true)
    private void bite$barWidth(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.isDamaged()) return; // 耐久条优先
        FractionPair p = fractionOf(self);
        if (p == null) return;
        cir.setReturnValue((int) Math.round(13.0 * p.fraction()));
    }

    @Inject(method = "getBarColor", at = @At("RETURN"), cancellable = true)
    private void bite$barColor(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.isDamaged()) return; // 耐久条优先
        FractionPair p = fractionOf(self);
        if (p == null) return;
        cir.setReturnValue(p.color());
    }

    @Unique
    private static FractionPair fractionOf(ItemStack stack) {
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (stamp == null || life == null || life.spoilTicks() <= 0) return null;
        double fraction = FreshnessMath.fraction(FreshnessClock.now(), stamp, life);
        int color;
        if (fraction <= 0.0) color = 0xAAAAAA;       // 已腐坏：灰
        else if (fraction <= 0.25) color = 0xFF5555; // 红
        else if (fraction <= 0.5) color = 0xFFAA00;  // 橙
        else if (fraction <= 0.75) color = 0xFFDD55; // 黄
        else color = 0x55FF55;                       // 绿
        return new FractionPair(fraction, color);
    }

    @Unique
    private record FractionPair(double fraction, int color) {}
}
