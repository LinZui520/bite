package com.eamon.bite.mixin;

import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 Biome 的温度位置缓存（换季失效用）。 */
@Mixin(Biome.class)
public interface BiomeTemperatureCacheAccess {
    @Accessor("temperatureCache")
    ThreadLocal<Long2FloatLinkedOpenHashMap> bite$temperatureCache();
}
