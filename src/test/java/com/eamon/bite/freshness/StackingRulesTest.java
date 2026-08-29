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

    @Test
    void unstampedOriginMergesAsFresh() {
        // 熔炉合并语义：无戳的新产物（= 全新）并入半腐旧产物——加权平均。
        // captureUnstamped 把无戳 origin 折算为 stamp=now（fraction 1.0）
        long now = 5000;
        FreshnessStamp destBefore = new FreshnessStamp(now - 500);       // 0.5：炉里已有的旧产物
        FreshnessStamp freshOrigin = new FreshnessStamp(now);            // 0.1 → captureUnstamped 折算的无戳新产物
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, destBefore, 1, freshOrigin, 1, LIFE);
        assertEquals(0.75, FreshnessMath.fraction(now, merged, LIFE), 1e-6,
            "旧半腐 + 新出炉各 1 份 → 平均 0.75");
    }
}
