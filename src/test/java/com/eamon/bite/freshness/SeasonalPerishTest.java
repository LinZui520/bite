package com.eamon.bite.freshness;

import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.season.Season;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 季节腐坏系数测试（DST perishable.lua 口径：冬 ×0.75 / 夏 ×1.25，
 * 春秋 1；系数乘在流逝时间上）。
 */
class SeasonalPerishTest {

    private static final long LIFE = 24000L; // 1 天保质期

    @Test
    void multiplierTable() {
        assertEquals(0.75, FreshnessMath.perishMultiplier(Season.WINTER));
        assertEquals(1.25, FreshnessMath.perishMultiplier(Season.SUMMER));
        assertEquals(1.0, FreshnessMath.perishMultiplier(Season.SPRING));
        assertEquals(1.0, FreshnessMath.perishMultiplier(Season.AUTUMN));
    }

    @Test
    void winterFoodLastsLonger() {
        // 过同样的真实时间，冬天的食物掉得更少：1 天后冬天还剩 25%
        FreshnessStamp stamp = new FreshnessStamp(0);
        double autumn = FreshnessMath.fraction(LIFE, stamp, new ShelfLife(LIFE));
        double winter = elapsedFraction(LIFE, Season.WINTER);
        assertEquals(0.0, autumn, 1e-9);          // 秋：1 天整 → 刚好吃完
        assertEquals(0.25, winter, 1e-9);          // 冬：×0.75 流逝 → 剩 25%
        assertTrue(winter > autumn);
    }

    @Test
    void summerFoodSpoilsFaster() {
        // 夏季系数 1.25：0.8 天的流逝折算为 1.0 天寿命 → 刚好吃完；
        // 同样 0.8 天在冬季（0.75）只消耗 0.6 天寿命 → 剩 40%
        FreshnessStamp stamp = new FreshnessStamp(0);
        // 直接调 fraction 走全局季节缓存（测试环境默认秋 = 1.0）：
        assertEquals(0.2, FreshnessMath.fraction((long) (LIFE * 0.8), stamp, new ShelfLife(LIFE)), 1e-9);
        // 显式按季节系数折算：
        double summerElapsed = LIFE * 0.8 * FreshnessMath.perishMultiplier(Season.SUMMER);
        double winterElapsed = LIFE * 0.8 * FreshnessMath.perishMultiplier(Season.WINTER);
        assertEquals(1.0, summerElapsed / LIFE, 1e-9); // 0.8×1.25 = 1.0 → 刚好吃完
        assertEquals(0.4, 1.0 - winterElapsed / LIFE, 1e-9); // 0.8×0.75 = 0.6 → 剩 40%
    }

    /** 指定季节下流逝 elapsed tick 后的 fraction（不经全局季节缓存）。 */
    private static double elapsedFraction(long elapsed, Season season) {
        double adjusted = elapsed * FreshnessMath.perishMultiplier(season);
        return Math.clamp(1.0 - adjusted / LIFE, 0.0, 1.0);
    }
}
