package com.eamon.bite.season;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.saveddata.WeatherData;

/**
 * 季节天气控制器：按季节重排原版天气循环的间隔（节奏对齐饥荒 DST
 * 一手数据，2026-08-29 调研：weather.lua 常量 + 200 游戏年模拟）。
 *
 * <p>实现方式：天气状态在 server 级 {@link WeatherData}（overworld 语义），
 * 不 mixin 原版循环——只在「降水刚停」与「换季」两个时机用
 * {@link MinecraftServer#setWeatherParameters} 重写下一段间隔。
 * 间隔到期后原版自动按 RAIN_DURATION（0.5~1 天）开始并控制时长，
 * 恰好覆盖饥荒的单场量级，无需干预时长。
 *
 * <p>节奏（9 天一季 = 饥荒季长的比例缩放）：
 * <ul>
 *   <li><b>冬</b>：间歇性降雪——冬一保底一场雪（对齐饥荒 early-winter
 *       「先铺一层地面积雪」的保底设计），之后每 4~6 天一场。温度已由
 *       {@link SeasonTemperature} 压到 0.15 以下 → 温带雨即雪、水结冰；
 *       地面雪层整个冬天不化（随机刻融化要求季节温度 ≥0.15）——视觉上
 *       整个冬天积雪不退，只有约 1/3 时间天上在下雪，与饥荒一致</li>
 *   <li><b>春</b>：频繁阵雨——每 1~2 天一场（饥荒春为全季降水最密，
 *       比例缩放后 9 天约 4 场、覆盖 ~1/3 时间）</li>
 *   <li><b>秋/夏</b>：不干预（原版低频，对齐饥荒的夏秋偶发）</li>
 * </ul>
 *
 * <p>换季接管：不打断进行中的降水（温度过渡 + rainLevel 渐变让雪自然
 * 变雨；下完后的间隔按新季节节奏重排）。玩家 {@code /weather} 与本控制器
 * 写同一状态、后写者胜——下次降水停止或换季时重新接管。
 */
public final class SeasonWeatherController {
    /** 冬季降雪间隔（tick）：4~6 天（饥荒 15 天 3~4 场的比例缩放）。 */
    static final UniformInt WINTER_SNOW_DELAY = UniformInt.of(96000, 144000);
    /** 春季阵雨间隔（tick）：1~2 天（饥荒春全季最密）。 */
    static final UniformInt SPRING_RAIN_DELAY = UniformInt.of(24000, 48000);
    /** 冬一保底雪时长（tick）：1 天（饥荒 early-winter ground cover）。 */
    private static final int WINTER_OPENING_SNOW = 24000;

    private static boolean wasRaining;
    private static Season lastSeason;

    private SeasonWeatherController() {}

    /** 每 server tick 调用（overworld）。 */
    public static void tick(MinecraftServer server, ServerLevel overworld) {
        if (!overworld.canHaveWeather()) return;
        Season season = SeasonClock.season(overworld);

        if (season != lastSeason) {
            Season previous = lastSeason;
            lastSeason = season;
            wasRaining = overworld.isRaining();
            // 季节温度换了档：清掉本线程全部群系的温度位置缓存（server 线程侧）
            SeasonBiomeCaches.clear(server.registryAccess());
            onSeasonChange(server, overworld, previous, season);
        }

        boolean raining = overworld.isRaining();
        if (wasRaining && !raining) {
            // 降水刚停：原版即将从 RAIN_DELAY 采样下一段间隔，此刻按季节重写
            onPrecipitationStopped(server, season);
        }
        wasRaining = raining;
    }

    private static void onSeasonChange(MinecraftServer server, ServerLevel overworld,
                                       Season previous, Season newSeason) {
        int dayOfSeason = SeasonClock.dayOfSeason(overworld);
        // 离开冬季：不打断正在下的雪——温度过渡会让它在当天自然变成雨
        // （rainLevel 渐变缓冲形态翻转），下完后的间隔按春季节奏走。
        // 只有「雪已停、正处冬季长间隔」时才立即改排春雨间隔。
        if (previous == Season.WINTER && newSeason != Season.WINTER && !overworld.isRaining()) {
            onPrecipitationStopped(server, newSeason);
        }
        // 进入冬季第 1 天：保底一场雪（对齐饥荒 early-winter ground cover；
        // 服务器重启在冬中的情形不补发——尊重存档天气状态）
        if (newSeason == Season.WINTER && dayOfSeason == 1) {
            server.setWeatherParameters(0, WINTER_OPENING_SNOW, true, false);
        }
    }

    /** 降水停止瞬间：按季节安排下一段间隔（clearWeatherTime）。 */
    private static void onPrecipitationStopped(MinecraftServer server, Season season) {
        RandomSource random = server.overworld().getRandom();
        switch (season) {
            case WINTER -> server.setWeatherParameters(WINTER_SNOW_DELAY.sample(random), 0, false, false);
            case SPRING -> server.setWeatherParameters(SPRING_RAIN_DELAY.sample(random), 0, false, false);
            // 秋/夏：放行原版采样（低频）
            default -> { }
        }
    }
}
