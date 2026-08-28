package com.eamon.bite.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 通用 HUD 渐隐文字提示：在屏幕指定位置以 MC 原版普通字体显示一段文字，
 * 渐入 → 停留 → 渐出。供季节提示等任何「世界中事件 → 屏幕提示」场景复用。
 *
 * <p>用法：静态调用 {@link #show} 即可（内部持有单实例）——
 * <pre>{@code
 * FadingHudText.show(Component.translatable("bite.season.title", ...));
 * }</pre>
 * 新提示会顶替正在展示的提示（重置动画计时）。样式（位置/颜色/时长）
 * 在 {@link #show} 的重载里传，或用默认样式。
 *
 * <p>计时挂 {@link ClientTickEvents#END_CLIENT_TICK}（每秒固定 20 次、
 * 帧率无关）；渲染层只绘制不推进——<b>不得</b>把计数器放进
 * {@code extractRenderState}（每渲染帧调用，会让动画随帧率快进）。
 */
@Environment(EnvType.CLIENT)
public final class FadingHudText implements HudElement {
    /** 默认样式：屏幕水平居中、垂直 12% 高度、白色、渐入 10 / 停留 70 / 渐出 20。 */
    public record Style(float verticalRatio, boolean centered, int color,
                        int fadeIn, int stay, int fadeOut) {
        /** 居中白字、屏幕 12% 高度、0.5s/3.5s/1s。 */
        public static Style defaults() {
            return new Style(0.12f, true, 0xFFFFFF, 10, 70, 20);
        }
    }

    private static final Identifier ID = Identifier.fromNamespaceAndPath("bite", "fading_text");
    private static final FadingHudText INSTANCE = new FadingHudText();

    private Style style = Style.defaults();
    private Component text;
    private int tick;
    private int totalTicks;

    private FadingHudText() {}

    /** 客户端初始化时注册一次（渲染层 + tick 驱动）。 */
    public static void register() {
        HudElementRegistry.addLast(ID, INSTANCE);
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.advance());
    }

    /** 以默认样式（居中、12% 高度、白字）展示一段提示。 */
    public static void show(Component text) {
        show(text, Style.defaults());
    }

    /**
     * 以指定样式展示一段提示。新提示顶替正在展示的提示。
     *
     * @param style 位置（verticalRatio 0=顶 1=底；centered=false 时左对齐）、
     *              颜色（RGB，alpha 由渐隐动画控制）、三段时长（tick）
     */
    public static void show(Component text, Style style) {
        INSTANCE.text = text;
        INSTANCE.style = style;
        INSTANCE.tick = 0;
        INSTANCE.totalTicks = style.fadeIn() + style.stay() + style.fadeOut();
    }

    /** 动画计时（客户端 tick，20/s）。 */
    private void advance() {
        if (tick < totalTicks) tick++;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker delta) {
        if (text == null || tick >= totalTicks) return;

        float alpha = alphaAt(tick);
        if (alpha <= 0.0f) return;

        Font font = Minecraft.getInstance().font;
        int x;
        if (style.centered()) {
            x = (gui.guiWidth() - font.width(text)) / 2;
        } else {
            x = 0;
        }
        int y = Math.round(gui.guiHeight() * style.verticalRatio());
        int argb = style.color() & 0xFFFFFF | (int) (alpha * 255.0f) << 24;

        gui.text(font, text, x, y, argb, false);
    }

    /** 渐入线性升、停留恒 1、渐出线性降。 */
    private float alphaAt(int t) {
        if (t < style.fadeIn()) return (t + 1) / (float) style.fadeIn();
        if (t < style.fadeIn() + style.stay()) return 1.0f;
        return (totalTicks - t) / (float) style.fadeOut();
    }
}
