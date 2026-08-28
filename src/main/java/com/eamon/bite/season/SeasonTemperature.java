package com.eamon.bite.season;

/**
 * 季节温度：biome 基础温度 + 季节偏移（饥荒式，量级对齐 Serene Seasons）。
 *
 * <p>偏移表：冬 -0.8 / 春 -0.25 / 夏 +0.3 / 秋 0。效果（温带平原基础 0.8）：
 * 冬 0.0 &lt; 0.15 雨转雪、水结冰；春 0.55 雪→雨、雪渐融；夏 1.1；秋 0.8（原版）。
 * 沙漠（has_precipitation=false）不受影响——降水开关与温度无关。
 *
 * <p><b>不钳制</b>结果值：偏移必须可逆（{@code 基础值 = 季节值 - 偏移}），
 * 融化判定靠它反解基础温度来区分「季节雪」与「原生积雪群系」。下游全部是
 * 阈值比较（≥0.15），超出 [-0.5, 2.0] 无副作用；草/叶颜色读的是另一条
 * 路径（直接读字段），世界配色不受季节温度影响。
 */
public final class SeasonTemperature {
    /** 雨雪分界（原版 warmEnoughToRain 阈值）。 */
    public static final float RAIN_SNOW_THRESHOLD = 0.15f;

    private SeasonTemperature() {}

    /** 季节偏移量。 */
    public static float offset(Season season) {
        return switch (season) {
            case WINTER -> -0.8f;
            case SPRING -> -0.25f;
            case SUMMER -> 0.3f;
            case AUTUMN -> 0.0f;
        };
    }

    /** 季节温度 = 基础温度 + 偏移（不钳制，见类 javadoc）。 */
    public static float seasonal(float baseTemperature, Season season) {
        return baseTemperature + offset(season);
    }

    /**
     * 季节性融化判定（保守口径）：当前够暖（季节温度 ≥ 0.15）<b>且</b>该处
     * 不是原生积雪群系（反解出的基础温度 ≥ 0.15）才融。
     *
     * <p>反解依赖「季节温度不钳制」。原生雪原（基础 0.0）夏天季节温度 0.3
     * 虽 ≥ 0.15，但基础 0.0 &lt; 0.15 → 永不融化（保守口径）；温带（基础
     * 0.8）的冬天积雪在春天（0.55）开始融；针叶林（基础 0.25）春天仍
     * 低于 0.15，到夏天才融。
     */
    public static boolean shouldMeltSnow(float seasonalTemperature, Season season) {
        float baseTemperature = seasonalTemperature - offset(season);
        return baseTemperature >= RAIN_SNOW_THRESHOLD && seasonalTemperature >= RAIN_SNOW_THRESHOLD;
    }
}
