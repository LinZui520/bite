package com.eamon.bite.freshness;

import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreshnessMathTest {
    private static final ShelfLife LIFE = new ShelfLife(1000L);

    @Test
    void freshItemAtNowHasFullFraction() {
        assertEquals(1.0, FreshnessMath.fraction(5000, new FreshnessStamp(5000), LIFE), 1e-9);
    }

    @Test
    void fullyAgedItemHasZeroFraction() {
        assertEquals(0.0, FreshnessMath.fraction(6000, new FreshnessStamp(5000), LIFE), 1e-9);
    }

    @Test
    void fractionClampsBeyondBounds() {
        assertEquals(1.0, FreshnessMath.fraction(4000, new FreshnessStamp(5000), LIFE), 1e-9); // 未到保质期开始（未来戳）
        assertEquals(0.0, FreshnessMath.fraction(99999, new FreshnessStamp(0), LIFE), 1e-9);
    }

    @Test
    void neverSpoilAlwaysFull() {
        assertEquals(1.0, FreshnessMath.fraction(99999, new FreshnessStamp(0), ShelfLife.NEVER), 1e-9);
    }

    @Test
    void gradeThresholds() {
        assertEquals(FreshnessGrade.FRESH, FreshnessMath.grade(0.51));
        assertEquals(FreshnessGrade.STALE, FreshnessMath.grade(0.5));
        assertEquals(FreshnessGrade.STALE, FreshnessMath.grade(0.26));
        assertEquals(FreshnessGrade.OLD, FreshnessMath.grade(0.25));
        assertEquals(FreshnessGrade.OLD, FreshnessMath.grade(0.01));
        assertEquals(FreshnessGrade.SPOILED, FreshnessMath.grade(0.0));
    }

    @Test
    void mergeWeightedAverageDonStarveStyle() {
        // 1 个 100% + 1 个 50% → 75%
        long now = 5000;
        FreshnessStamp fresh = new FreshnessStamp(now);
        FreshnessStamp half = new FreshnessStamp(now - 500);
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, fresh, 1, half, 1, LIFE);
        assertEquals(0.75, FreshnessMath.fraction(now, merged, LIFE), 1e-6);
    }

    @Test
    void mergeWeightedByCounts() {
        // 3 个 100% + 1 个 0%（临界未腐坏，fraction 极小正数）→ (3+ε)/4
        long now = 5000;
        FreshnessStamp fresh = new FreshnessStamp(now);
        FreshnessStamp almostSpoiled = new FreshnessStamp(now - 999);
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, fresh, 3, almostSpoiled, 1, LIFE);
        assertEquals(0.75, FreshnessMath.fraction(now, merged, LIFE), 1e-3);
    }

    @Test
    void mergeNeverSpoilReturnsFreshOperand() {
        long now = 5000;
        FreshnessStamp a = new FreshnessStamp(1);
        FreshnessStamp b = new FreshnessStamp(2);
        assertEquals(b, FreshnessMath.mergeStamps(now, a, 1, b, 1, ShelfLife.NEVER));
    }

    @Test
    void mergeRoundTripsThroughFraction() {
        long now = 123456;
        FreshnessStamp s1 = new FreshnessStamp(now - 100);
        FreshnessStamp s2 = new FreshnessStamp(now - 900);
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, s1, 7, s2, 5, LIFE);
        double expected = (FreshnessMath.fraction(now, s1, LIFE) * 7 + FreshnessMath.fraction(now, s2, LIFE) * 5) / 12.0;
        assertEquals(expected, FreshnessMath.fraction(now, merged, LIFE), 1e-3);
    }
}
