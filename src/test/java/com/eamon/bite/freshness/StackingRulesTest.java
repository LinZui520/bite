package com.eamon.bite.freshness;

import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackingRulesTest {
    private static final ShelfLife LIFE = new ShelfLife(1000);

    @Test
    void mergedStampForDeltaMatchesWeightedAverage() {
        long now = 5000;
        FreshnessStamp destBefore = new FreshnessStamp(now - 100);       // 0.9
        FreshnessStamp origin = new FreshnessStamp(now - 900);           // 0.1
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, destBefore, 3, origin, 2, LIFE);
        double expected = (0.9 * 3 + 0.1 * 2) / 5.0;
        assertEquals(expected, FreshnessMath.fraction(now, merged, LIFE), 1e-6);
    }
}
