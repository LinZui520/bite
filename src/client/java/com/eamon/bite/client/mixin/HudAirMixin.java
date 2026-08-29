package com.eamon.bite.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 氧气条让位注入点：{@code Hud} 主渲染里氧气条基线 {@code yLineAir =
 * yLineBase - 10}（平时占 guiHeight-49，与饥渴水滴行冲突；骑生物时再上移）。
 *
 * <p>布局口径（用户 2026-08-30）：饥饿/心 = 第一行（-39）、<b>饥渴水滴
 * 固定第二行（-49）</b>、氧气条第三行（-59）。本 mixin 把氧气基线的
 * 常量偏移从 -10 改为 -20——氧气条无条件比饥饿条高两行（骑生物时
 * 照旧叠加坐骑行偏移，原版 getAirBubbleYLine 逻辑保留）。
 */
@Mixin(Hud.class)
public abstract class HudAirMixin {
    @ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = -10, ordinal = 2))
    private int bite$pushAirLineDown(int original) {
        return original * 2; // -10 → -20：氧气从第二行让到第三行
    }
}
