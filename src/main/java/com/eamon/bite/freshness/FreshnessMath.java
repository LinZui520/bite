package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.season.Season;
import com.eamon.bite.season.SeasonClock;
import net.minecraft.world.item.ItemStack;

/** 新鲜度纯函数。双端共用；时间一律 game time。 */
public final class FreshnessMath {
    private FreshnessMath() {}

    /**
     * 新鲜度 [0,1]。永不腐坏（spoilTicks &lt;= 0）恒为 1。
     *
     * <p>季节腐坏系数（饥荒 perishable.lua 口径：冬 ×0.75 / 夏 ×1.25，
     * 春秋无修正）乘在<b>流逝时间</b>上而非寿命上——换季时已积累的
     * 食物年龄不变，只有未来流逝速度变化，换季自然平滑。
     */
    public static double fraction(long now, FreshnessStamp stamp, ShelfLife life) {
        if (life.spoilTicks() <= 0) return 1.0;
        double elapsed = (now - stamp.creationGameTick()) * perishMultiplier(SeasonClock.current());
        double f = 1.0 - elapsed / life.spoilTicks();
        return Math.clamp(f, 0.0, 1.0);
    }

    /**
     * 季节/维度腐坏系数：冬 ×0.75（慢）/ 夏 ×1.25（快）/ 春秋 ×1
     * （配置可改，1 = 关）；<b>下界恒 ×1.25</b>（灼热环境——与盛夏
     * 同烈，用户口径 2026-08-30）。
     *
     * <p>维度判定经 {@link SeasonScope}：下界/末地的物品不吃季节系数
     * （季节仅主世界）；下界叠加自己的环境系数。无标记的调用点
     * （纯 JVM 等）保守 1.0。
     */
    public static double perishMultiplier(Season season) {
        com.eamon.bite.season.SeasonScope.Scope scope = com.eamon.bite.season.SeasonScope.current();
        if (scope == com.eamon.bite.season.SeasonScope.Scope.NETHER) {
            return com.eamon.bite.season.SeasonRates.NETHER_PERISH;
        }
        if (scope != com.eamon.bite.season.SeasonScope.Scope.OVERWORLD) return 1.0;
        ServerConfig cfg = ServerConfig.get();
        return switch (season) {
            case WINTER -> cfg.perishWinterMultiplier();
            case SUMMER -> cfg.perishSummerMultiplier();
            default -> 1.0;
        };
    }

    /**
     * 饥荒式合并：新鲜度按数量加权平均，反解出新时间戳（守恒）。
     *
     * <p>反解 age 时要<b>除回当前季节系数</b>——fraction 侧乘系数计税，
     * stamp 侧必须免税还原，否则合并的年龄被双重计税（夏季合并一次
     * 就凭空多老 25%）。换季瞬间合并的栈会按新季节系数还原，轻微
     * 漂移可接受（与「历史时间按当前季节折算」同一近似口径）。
     */
    public static FreshnessStamp mergeStamps(long now, FreshnessStamp s1, long count1, FreshnessStamp s2, long count2, ShelfLife life) {
        if (life.spoilTicks() <= 0) return s2;
        double f1 = fraction(now, s1, life);
        double f2 = fraction(now, s2, life);
        double merged = (f1 * count1 + f2 * count2) / (count1 + count2);
        double k = perishMultiplier(SeasonClock.current());
        long age = Math.round((1.0 - merged) * life.spoilTicks() / k);
        return new FreshnessStamp(now - age);
    }

    /** 栈级判定：是否已完全变质（fraction ≤ 0）。无戳 / 永不腐坏 → false。 */
    public static boolean isSpoiled(ItemStack stack, long now) {
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        if (stamp == null) return false;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return false;
        return fraction(now, stamp, life) <= 0.0;
    }

    /** 档位划分（渲染/调试用硬阈值；营养缩放走配置阈值，见 SpoiledFoodHandler）。 */
    public static FreshnessGrade grade(double fraction) {
        if (fraction <= 0.0) return FreshnessGrade.SPOILED;
        if (fraction <= 0.25) return FreshnessGrade.OLD;
        if (fraction <= 0.5) return FreshnessGrade.STALE;
        return FreshnessGrade.FRESH;
    }
}
