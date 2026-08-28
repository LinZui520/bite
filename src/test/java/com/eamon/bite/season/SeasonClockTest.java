package com.eamon.bite.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 季节推导纯函数测试：9 天一季、秋起、36 天一轮。
 * 日序号（0 起）与展示（1 起）的换算也在此覆盖。
 */
class SeasonClockTest {

    @Test
    void dayOneIsAutumnOne() {
        assertEquals(Season.AUTUMN, SeasonClock.seasonAtDay(0));
        assertEquals(1, SeasonClock.dayOfSeasonAtDay(0));
    }

    @Test
    void nineDaysPerSeasonInOrder() {
        // 秋一~秋九 = 日序号 0~8，冬一从 9 开始
        for (int d = 0; d < 9; d++) {
            assertEquals(Season.AUTUMN, SeasonClock.seasonAtDay(d));
            assertEquals(d + 1, SeasonClock.dayOfSeasonAtDay(d));
        }
        assertEquals(Season.WINTER, SeasonClock.seasonAtDay(9));
        assertEquals(1, SeasonClock.dayOfSeasonAtDay(9));
    }

    @Test
    void seasonOrderIsAutumnWinterSpringSummer() {
        assertEquals(Season.AUTUMN, SeasonClock.seasonAtDay(0));
        assertEquals(Season.WINTER, SeasonClock.seasonAtDay(9));
        assertEquals(Season.SPRING, SeasonClock.seasonAtDay(18));
        assertEquals(Season.SUMMER, SeasonClock.seasonAtDay(27));
    }

    @Test
    void cycleWrapsAt36Days() {
        assertEquals(Season.AUTUMN, SeasonClock.seasonAtDay(36));
        assertEquals(1, SeasonClock.dayOfSeasonAtDay(36));
        assertEquals(Season.WINTER, SeasonClock.seasonAtDay(45));
        assertEquals(9, SeasonClock.dayOfSeasonAtDay(53));
    }

    @Test
    void negativeDaysUseFloorMod() {
        // /time set 早于 0 的极端场景：floorMod 保证仍是合法季内天数
        assertEquals(Season.SUMMER, SeasonClock.seasonAtDay(-1));
        assertEquals(9, SeasonClock.dayOfSeasonAtDay(-1));
    }
}
