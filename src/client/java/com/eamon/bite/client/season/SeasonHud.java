package com.eamon.bite.client.season;

import com.eamon.bite.client.hud.FadingHudText;
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
 *
 * <p>首次播报规则：游戏进程内第一次观测到世界（= 新世界第一天，或
 * 启动后直接进存档）会播报当天；此后断线/切存档重进不再重播当天，
 * 只响应新的跨日。
 */
@Environment(EnvType.CLIENT)
public final class SeasonHud {
    private long lastDay = Long.MIN_VALUE;
    /** 游戏进程内尚未观测过世界（首次观测 → 播报；重进 → 只更新基准）。 */
    private boolean firstObservation = true;

    /** 注册跨日检测（onInitializeClient 调用一次；渲染由 FadingHudText 承担）。 */
    public static void register() {
        SeasonHud hud = new SeasonHud();
        ClientTickEvents.END_CLIENT_TICK.register(client -> hud.tick(client));
    }

    private void tick(Minecraft client) {
        if (client.level == null) {
            // 掉出世界（断线/切存档）：重置日基准，重进当天不重播
            lastDay = Long.MIN_VALUE;
            return;
        }
        long day = SeasonClock.dayOfWorld(client.level);
        com.eamon.bite.BiteMod.LOGGER.info("[bite-season] day={} lastDay={} firstObs={}",
            day, lastDay, firstObservation);
        if (lastDay == Long.MIN_VALUE) {
            boolean announce = firstObservation;
            firstObservation = false;
            lastDay = day;
            if (announce) {
                announceDay(day);
            }
            return;
        }
        if (day != lastDay) {
            lastDay = day;
            announceDay(day);
        }
    }

    private static void announceDay(long day) {
        FadingHudText.show(SeasonText.titleText(
            SeasonClock.seasonAtDay(day), SeasonClock.dayOfSeasonAtDay(day)));
    }
}
