package com.eamon.bite.season;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 季节提示：跨自然日时（睡觉跳到清晨或熬夜到点，都会使 totalTicks
 * 跨过 24000 的倍数）向玩家发送 title——大字号原版字体，屏幕上方约
 * 1/4 处，自带渐入渐出。
 *
 * <p>发送时机：每 server tick 比较上一 tick 的日序号；进位则向该维度
 * 所有在线玩家发「季 + 天」（如「秋三」）。用 title 三包序列：
 * animation（淡入 10 / 停留 70 / 淡出 20 tick）+ title 文本。
 * 服务器重启后 lastDay 从当前日重新起算——当天的提示不重发，次日恢复。
 */
public final class SeasonAnnouncer {
    /** 淡入 / 停留 / 淡出（tick）。 */
    private static final int FADE_IN = 10, STAY = 70, FADE_OUT = 20;

    private static long lastDay = Long.MIN_VALUE;

    private SeasonAnnouncer() {}

    /** 每个服务器 tick 调用（overworld）。 */
    public static void tick(ServerLevel overworld) {
        long day = SeasonClock.dayOfWorld(overworld);
        if (lastDay == Long.MIN_VALUE) {
            lastDay = day; // 启动当天不补发
            return;
        }
        if (day == lastDay) return;
        lastDay = day;
        for (var player : overworld.players()) {
            sendTitle(player, SeasonClock.seasonAtDay(day), SeasonClock.dayOfSeasonAtDay(day));
        }
    }

    private static void sendTitle(ServerPlayer player, Season season, int dayOfSeason) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN, STAY, FADE_OUT));
        player.connection.send(new ClientboundSetTitleTextPacket(titleText(season, dayOfSeason)));
        // 空副标题包：清掉上一条可能残留的 subtitle，保证只显示主标题
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
    }

    /** 「秋三」/ "Autumn 3"——季名与序数各自走 lang，拼装交给翻译方。 */
    static Component titleText(Season season, int dayOfSeason) {
        return Component.translatable("bite.season.title",
            Component.translatable("bite.season." + season.id()), dayOfSeason);
    }
}
