package com.eamon.bite.mixin;

import com.eamon.bite.season.SeasonClock;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 季节温度注入点：{@code Biome.getBaseTemperature} 的返回值加季节偏移
 * （经 {@link SeasonClock#currentOffset()}，换季日当天含线性过渡）。
 *
 * <p>该方法是全部运行时温度消费的汇合点（雨雪分界 warmEnoughToRain /
 * coldEnoughToSnow / getPrecipitationAt、结冰、雪层堆积、F3 显示都经
 * getTemperature → getHeightAdjustedTemperature → 这里），海拔冷却与
 * FROZEN 修饰符逻辑自动保留。Biome 是 common 类 → 双端一个 mixin 生效、
 * 零网络同步（季节由双端各自读 overworld WorldClock 推导，天然一致）。
 *
 * <p>世界生成期消费方（SnowAndFreezeFeature 等）也走这里——冬季探索的
 * 新区块自带积雪（属「季节雪」，春夏会融），秋季新区块与原版一致。
 * 群系配色不走本方法（直接读 climateSettings 字段），世界颜色稳定。
 *
 * <p>已知坑：Biome.getTemperature 有按位置的 ThreadLocal LRU 缓存且无
 * 失效机制——换季时须由 {@link com.eamon.bite.season.SeasonBiomeCaches}
 * 在 server / render 线程各清一次，否则旧季节的判定会缓存到 1024 个位置。
 * 换季过渡期内偏移逐 tick 变化，缓存会在过渡首 tick 失效一次后重新
 * 灌入插值中的值——进度推进带来的微小漂移可接受（阈值翻转在中段，
 * 恰好是缓存重新采样后）。
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @Inject(method = "getBaseTemperature", at = @At("RETURN"), cancellable = true)
    private void bite$seasonalTemperature(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValueF() + SeasonClock.currentOffset());
    }
}
