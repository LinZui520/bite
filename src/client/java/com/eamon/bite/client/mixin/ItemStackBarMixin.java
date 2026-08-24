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
 * 物品图标下的新鲜度进度条（spec §7.1）。
 *
 * <p>26.2 渲染路径（genSources/javap 验证）：{@link ItemStack#isBarVisible()}、
 * {@link ItemStack#getBarWidth()}、{@link ItemStack#getBarColor()} 三个实例方法仍保留在
 * {@link ItemStack} 上（内部委派给 {@code Item.isBarVisible/getBarWidth/getBarColor(ItemStack)}）。
 * 渲染由 {@code GuiGraphicsExtractor.itemBar(ItemStack, int, int)} 调用这三个方法绘制 13px 宽条。
 * 故采用方案 A：直接 {@code @Inject} 三个方法 {@code @At("RETURN")}。
 *
 * <p>语义：
 * <ul>
 *   <li>耐久条优先：{@code isBarVisible} 原版返回 true（即 {@link ItemStack#isDamaged()}）
 *       时早退，保留原版耐久条；{@code getBarWidth}/{@code getBarColor} 在 {@code isDamaged()}
 *       时早退，让原版值生效。</li>
 *   <li>否则当 stack 持有 {@code bite:freshness} 组件且客户端配置 {@code show_bar=true} 时，
 *       以新鲜度分数绘制：宽度 {@code round(13*fraction)}，颜色按 0.75/0.5/0.25/0.0 五档。</li>
 *   <li>已腐坏（fraction &le; 0）显示灰色满条，与 spec 一致。</li>
 * </ul>
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
