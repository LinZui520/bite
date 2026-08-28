package com.eamon.bite.season;

import net.minecraft.network.chat.Component;

/** 季节提示文案构造（双端共用：服务端 title 包 / 客户端 HUD）。 */
public final class SeasonText {
    private SeasonText() {}

    /** 「秋三」/ "Autumn 3"——季名与序数各自走 lang，拼装交给翻译方。 */
    public static Component titleText(Season season, int dayOfSeason) {
        return Component.translatable("bite.season.title",
            Component.translatable("bite.season." + season.id()), dayOfSeason);
    }
}
