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

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 通用 HUD 渐隐文字提示：在屏幕指定位置以 MC 原版普通字体显示一段文字，
 * 渐入 → 停留 → 渐出。供季节提示、群系播报等任何「世界中事件 → 屏幕
 * 提示」场景复用。
 *
 * <p><b>单位置播放队列</b>（用户口径 2026-08-30）：所有提示同一位置
 * 依次播放——正在播的播完，后续排队等待（季节跨日遇上群系切换时，
 * 群系先播、季节晚几秒跟上，互不顶替也不重叠）。
 *
 * <p>用法：静态调用 {@link #show} 即可——
 * <pre>{@code
 * FadingHudText.show(Component.translatable("bite.season.title", ...));
 * }</pre>
 * 样式（位置/颜色/时长）在 {@link #show} 的重载里传，或用默认样式。
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

    /** 队列里的一条待播提示。 */
    private record Entry(Component text, Style style) {}

    private static final Identifier ID = Identifier.fromNamespaceAndPath("bite", "fading_text");
    private static final FadingHudText INSTANCE = new FadingHudText();

    /** 待播队列（含正在播的队首）。 */
    private static final Queue<Entry> QUEUE = new ArrayDeque<>();
    /** 正在播的动画计时（对应队首）。 */
    private static int tick;
    private static int totalTicks;
    /** 队列上限：极端刷屏场景（快速跨群系）丢弃最旧的排队项，防无限增长。 */
    private static final int MAX_QUEUE = 8;

    private FadingHudText() {}

    /** 客户端初始化时注册一次（渲染层 + tick 驱动）。 */
    public static void register() {
        HudElementRegistry.addLast(ID, INSTANCE);
        ClientTickEvents.END_CLIENT_TICK.register(client -> advance());
    }

    /** 入队一条提示（默认样式）。当前项播完后自动开始。 */
    public static void show(Component text) {
        show(text, Style.defaults());
    }

    /**
     * 入队一条提示（自定义样式）。队列超上限时丢最旧排队项。
     *
     * @param style 位置（verticalRatio 0=顶 1=底；centered=false 时左对齐）、
     *              颜色（RGB，alpha 由渐隐动画控制）、三段时长（tick）
     */
    public static void show(Component text, Style style) {
        if (QUEUE.size() >= MAX_QUEUE) {
            QUEUE.poll(); // 丢最旧（可能丢掉正在播的——立即切换到下一条）
            startHead();
        }
        QUEUE.add(new Entry(text, style));
        if (QUEUE.size() == 1) {
            startHead(); // 队列从空到有：立即起播（否则 totalTicks=0 会被当播完）
        }
    }

    /** 动画计时（客户端 tick，20/s）：当前项播完即出队、下一项起播。 */
    private static void advance() {
        if (QUEUE.isEmpty()) return;
        tick++;
        if (tick >= totalTicks) {
            QUEUE.poll();
            tick = 0;
            startHead();
        }
    }

    /** 起播新队首（重设计时）。 */
    private static void startHead() {
        Entry head = QUEUE.peek();
        if (head != null) {
            Style s = head.style();
            totalTicks = s.fadeIn() + s.stay() + s.fadeOut();
            tick = 0;
        } else {
            totalTicks = 0;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker delta) {
        Entry head = QUEUE.peek();
        if (head == null || tick >= totalTicks) return;

        float alpha = alphaAt(tick, head.style());
        if (alpha <= 0.0f) return;

        Style style = head.style();
        Component text = head.text();
        Font font = Minecraft.getInstance().font;
        int x = style.centered() ? (gui.guiWidth() - font.width(text)) / 2 : 0;
        int y = Math.round(gui.guiHeight() * style.verticalRatio());
        int argb = style.color() & 0xFFFFFF | (int) (alpha * 255.0f) << 24;

        gui.text(font, text, x, y, argb, false);
    }

    /** 渐入线性升、停留恒 1、渐出线性降。 */
    private static float alphaAt(int t, Style style) {
        if (t < style.fadeIn()) return (t + 1) / (float) style.fadeIn();
        if (t < style.fadeIn() + style.stay()) return 1.0f;
        return (totalTicks - t) / (float) style.fadeOut();
    }
}
