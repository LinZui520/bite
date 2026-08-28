package com.eamon.bite.client.season;

import com.eamon.bite.season.Season;
import com.eamon.bite.season.SeasonClock;
import com.eamon.bite.season.SeasonText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

/**
 * 季节提示 HUD：跨自然日时在屏幕中上方以普通字体渲染「秋三」，
 * 渐入（10t）→ 停留（70t）→ 渐出（20t）。
 *
 * <p>纯客户端实现——SeasonClock 是世界时钟纯函数，客户端拿 client.level
 * 直接算，无需任何网络同步。跨日检测每客户端 tick 做一次（与服务端
 * SeasonAnnouncer 语义一致：睡觉跳清晨、熬夜到点都是 totalTicks 跨过
 * 24000 倍数）。
 *
 * <p>渲染：字号 = 原版普通字体（不开阴影，渐隐靠 alpha 分层）；位置 =
 * 屏幕水平居中、垂直 12% 高度处（原版 title 固定在 ~25%，这里更靠上、
 * 字更小）。
 */
@Environment(EnvType.CLIENT)
public final class SeasonHud implements HudElement {
    private static final int FADE_IN = 10, STAY = 70, FADE_OUT = 20;
    private static final int TOTAL_TICKS = FADE_IN + STAY + FADE_OUT;
    /** 屏幕高度的比例定位（0 = 顶）。 */
    private static final float VERTICAL_RATIO = 0.12f;

    private long lastDay = Long.MIN_VALUE;
    private int animationTick = TOTAL_TICKS; // 空闲态（动画结束）
    private Component currentTitle;

    /** 注册入口（客户端 onInitializeClient 调用）。 */
    public static void register() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("bite", "season_announce"),
            new SeasonHud());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker delta) {
        tick();
        if (animationTick >= TOTAL_TICKS) return;

        float alpha = alphaAt(animationTick);
        if (alpha <= 0.0f) return;

        Font font = Minecraft.getInstance().font;
        int width = font.width(currentTitle);
        int x = (gui.guiWidth() - width) / 2;
        int y = Math.round(gui.guiHeight() * VERTICAL_RATIO);

        int a = (int) (alpha * 255.0f) << 24;
        gui.text(font, currentTitle, x, y, 0xFFFFFF | a, false);
    }

    /** 每渲染 tick 前进动画并检测跨日。 */
    private void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            // 掉出世界（断线/切存档）：重置检测基准，重进世界当天的提示不发
            lastDay = Long.MIN_VALUE;
            animationTick = TOTAL_TICKS;
            return;
        }
        long day = SeasonClock.dayOfWorld(client.level);
        if (lastDay == Long.MIN_VALUE) {
            lastDay = day; // 进入世界当天不补发
            return;
        }
        if (day != lastDay) {
            lastDay = day;
            currentTitle = SeasonText.titleText(
                SeasonClock.seasonAtDay(day), SeasonClock.dayOfSeasonAtDay(day));
            animationTick = 0;
        }
        animationTick = Math.min(animationTick + 1, TOTAL_TICKS);
    }

    /** 渐入线性升、停留恒 1、渐出线性降。 */
    private static float alphaAt(int tick) {
        if (tick < FADE_IN) return (tick + 1) / (float) FADE_IN;
        if (tick < FADE_IN + STAY) return 1.0f;
        return (TOTAL_TICKS - tick) / (float) FADE_OUT;
    }
}
