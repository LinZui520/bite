package com.eamon.bite.client.season;

import com.eamon.bite.client.hud.FadingHudText;
import com.eamon.bite.season.Season;
import com.eamon.bite.season.SeasonClock;
import com.eamon.bite.season.SeasonText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * 季节提示：跨自然日（睡觉跳清晨、熬夜到点都是 totalTicks 跨过 24000
 * 倍数）时，经 {@link FadingHudText} 在屏幕中上方显示「秋三」——
 * 普通字体、渐入渐出，样式见 {@link FadingHudText.Style#defaults}。
 *
 * <p>纯客户端实现——SeasonClock 是世界时钟纯函数，客户端拿 client.level
 * 直接算，无需任何网络同步。
 */
@Environment(EnvType.CLIENT)
public final class SeasonHud {
    private long lastDay = Long.MIN_VALUE;

    /** 注册跨日检测（onInitializeClient 调用一次；渲染由 FadingHudText 承担）。 */
    public static void register() {
        SeasonHud hud = new SeasonHud();
        ClientTickEvents.END_CLIENT_TICK.register(client -> hud.tick(client));
    }

    private void tick(Minecraft client) {
        if (client.level == null) {
            // 掉出世界（断线/切存档）：重置检测基准，重进世界当天的提示不发
            lastDay = Long.MIN_VALUE;
            return;
        }
        long day = SeasonClock.dayOfWorld(client.level);
        if (lastDay == Long.MIN_VALUE) {
            lastDay = day; // 进入世界当天不补发
            return;
        }
        if (day != lastDay) {
            lastDay = day;
            FadingHudText.show(SeasonText.titleText(
                SeasonClock.seasonAtDay(day), SeasonClock.dayOfSeasonAtDay(day)));
        }
    }
}
