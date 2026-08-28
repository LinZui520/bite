package com.eamon.bite.mixin;

import com.eamon.bite.season.SeasonClock;
import com.eamon.bite.season.SeasonTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 季节性融雪：随机刻里，若当前位置已足够暖（且非原生积雪群系），
 * 天气降下的雪直接消失——春天回温后雪自然渐融（原版雪只看方块光照，
 * 太阳不融雪，冬天积的雪不会自己消失）。
 *
 * <p>不产生掉落物：天气雪的量级远大于玩家放置的雪，整片融化时掉落物
 * 会刷屏（原版光照融雪有掉落，但那是火把旁的小规模场景）。
 */
@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void bite$meltSeasonalSnow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        float seasonalTemp = level.getBiome(pos).value().getBaseTemperature();
        if (!SeasonTemperature.shouldMeltSnow(seasonalTemp, SeasonClock.current())) return;
        level.removeBlock(pos, false);
        ci.cancel();
    }
}
