package com.eamon.bite.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 氧气条让位注入点：{@code Hud.extractAirBubbles} 的 yLineAir 参数。
 *
 * <p>布局口径（用户 2026-08-30）：饥饿/心 = 第一行（-39）、<b>饥渴水滴
 * 固定第二行（-49）</b>、氧气 = 第三行（-59）。原版氧气基线是
 * {@code yLineAir = yLineBase - 10}（第二行，与水滴冲突）——本 mixin
 * 在方法入口把 y 参数再减 10，氧气无条件下移一行。骑生物时原版
 * {@code getAirBubbleYLine} 的坐骑行让位逻辑保留（在入口修改之后
 * 执行，继续叠加）。
 *
 * <p>实现注：不用 ModifyConstant——{@code yLineBase - 10} 被编译成
 * {@code bipush 10; isub}（正 10 + 减法指令），常量 -10 扫描不到
 * （曾因此注入失败 0/1）；参数修改不受编译形态影响。
 */
@Mixin(Hud.class)
public abstract class HudAirMixin {
    @ModifyVariable(method = "extractAirBubbles", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int bite$pushAirLineDown(int yLineAir) {
        return yLineAir - 10; // 氧气从第二行让到第三行
    }
}
