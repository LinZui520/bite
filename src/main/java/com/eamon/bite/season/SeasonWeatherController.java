package com.eamon.bite.season;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.saveddata.WeatherData;

/**
 * 季节天气控制器：按季节调整原版天气循环。
 *
 * <p>实现方式（26.2 事实）：天气循环在 {@code ServerLevel.advanceWeatherCycle}
 * 中驱动，状态存于 <b>server 级</b> {@link WeatherData}（overworld 语义）；
 * 雨停时原版从 {@code RAIN_DELAY = UniformInt(12000, 180000)} 采样下一场雨
 * 的间隔。本控制器不 mixin 原版循环，而是在「雨刚停、采样即将发生前」
 * 用 {@link MinecraftServer#setWeatherParameters} 按季节重写下一段：
 * <ul>
 *   <li><b>春</b>：下一场雨的延迟改为短区间（默认 0.5~1.5 天），雨时长
 *       维持原版采样——下雨概率显著提高</li>
 *   <li><b>冬</b>：本季第 2~8 天强制持续降雪——雨标志置 true、时长覆盖
 *       到季末；冷 biome 雨天自动降雪（{@code Biome.coldEnoughToSnow}），
 *       暖 biome 表现为冬雨</li>
 *   <li><b>秋/夏</b>：不干预（原版行为）</li>
 * </ul>
 *
 * <p>原版雨长 RAIN_DURATION = UniformInt(12000, 24000)（0.5~1 天）。
 * 玩家 {@code /weather} 命令的优先级高于本控制器（写同一个 WeatherData，
 * 后写者胜）——换季/下雨间隔再触发时会重新接管。
 */
public final class SeasonWeatherController {
    /** 春季下一场雨的延迟区间（tick）：0.5 ~ 1.5 游戏天。 */
    static final UniformInt SPRING_RAIN_DELAY = UniformInt.of(12000, 36000);
    /** 强制降雪状态下的雨时长（tick）：一次给满 1 天，到期 tick 里续期。 */
    private static final int WINTER_SNOW_DURATION = 24000;

    private static boolean wasRaining;
    private static Season lastSeason;
    private static int lastDayOfSeason = -1;

    private SeasonWeatherController() {}

    /** 每 server tick 调用（overworld）。 */
    public static void tick(MinecraftServer server, ServerLevel overworld) {
        if (!overworld.canHaveWeather()) return;
        Season season = SeasonClock.season(overworld);
        int day = SeasonClock.dayOfSeason(overworld);

        // 换季瞬间：记录基准（冬季强制雪的持续段由此展开）
        if (season != lastSeason) {
            lastSeason = season;
            wasRaining = overworld.isRaining();
            onSeasonChange(server, overworld, season, day);
        }
        if (day != lastDayOfSeason) {
            lastDayOfSeason = day;
            onDayChange(server, overworld, season, day);
        }

        boolean raining = overworld.isRaining();
        if (wasRaining && !raining) {
            // 雨刚停：原版即将从 RAIN_DELAY 采样下一场雨的间隔，此刻重写
            onRainStopped(server, season);
        }
        wasRaining = raining;

        // 冬季强制段：雨到期就续期（覆盖原版随机停雨）
        if (season == Season.WINTER && isWinterSnowForced(day)
            && overworld.getWeatherData().getRainTime() <= 0) {
            forceSnow(server, WINTER_SNOW_DURATION);
        }
    }

    private static void onSeasonChange(MinecraftServer server, ServerLevel overworld,
                                       Season newSeason, int dayOfSeason) {
        // 进冬即雪：冬季首日（秋九之后的第一天）也纳入强制段
        if (newSeason == Season.WINTER && isWinterSnowForced(dayOfSeason)) {
            forceSnow(server, WINTER_SNOW_DURATION);
        }
        // 离开冬季：立即放晴，结束强制雪（8 天之后的冬九自然停）
        if (lastSeason != null && lastSeason == Season.WINTER
            && newSeason != Season.WINTER && overworld.isRaining()) {
            server.setWeatherParameters(12000, 0, false, false);
        }
    }

    private static void onDayChange(MinecraftServer server, ServerLevel overworld,
                                    Season season, int dayOfSeason) {
        // 冬季进入强制段的当天（第 2 天）启动持续降雪
        if (season == Season.WINTER && dayOfSeason == 2) {
            forceSnow(server, WINTER_SNOW_DURATION);
        }
    }

    /** 雨停瞬间按季节重写下一场雨的间隔。 */
    private static void onRainStopped(MinecraftServer server, Season season) {
        if (season != Season.SPRING) return;
        RandomSource random = server.overworld().getRandom();
        int nextDelay = SPRING_RAIN_DELAY.sample(random);
        // clearWeatherTime = 下一场雨前的晴天时长；rainTime 顺带同步采样值
        server.setWeatherParameters(nextDelay, 0, false, false);
    }

    private static void forceSnow(MinecraftServer server, int durationTicks) {
        // raining=true + thundering=false；冷 biome 雨即雪
        server.setWeatherParameters(0, durationTicks, true, false);
    }

    /** 冬季强制降雪段：本季第 2~8 天。 */
    static boolean isWinterSnowForced(int dayOfSeason) {
        return dayOfSeason >= 2 && dayOfSeason <= 8;
    }
}
