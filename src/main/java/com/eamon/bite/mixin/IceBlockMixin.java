package com.eamon.bite.mixin;

import com.eamon.bite.season.SeasonClock;
import com.eamon.bite.season.SeasonTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 季节性融冰：与 {@link SnowLayerBlockMixin} 同判定——暖季随机刻把
 * 天气结的冰化回水。化冰动作复刻 {@code IceBlock.melt} 主体
 * （protected 不可直调；含下界 WATER_EVAPORATES 分支）。
 */
@Mixin(IceBlock.class)
public abstract class IceBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void bite$meltSeasonalIce(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        float seasonalTemp = level.getBiome(pos).value().getBaseTemperature();
        if (!SeasonTemperature.shouldMeltSnow(seasonalTemp, SeasonClock.currentOffset())) return;
        if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, IceBlock.meltsInto());
            level.neighborChanged(pos, IceBlock.meltsInto().getBlock(), null);
        }
        ci.cancel();
    }
}
