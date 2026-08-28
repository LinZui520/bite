package com.eamon.bite.season;

import com.eamon.bite.mixin.BiomeTemperatureCacheAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

/**
 * 换季时清空 Biome 的温度位置缓存（{@code getTemperature} 的
 * ThreadLocal LRU，1024 条/群系/线程，原版无失效机制）。
 *
 * <p>ThreadLocal 只能清「当前线程」的副本——调用方必须分别在
 * server 线程（SeasonWeatherController）与 render 线程（客户端 tick）
 * 各调一次。世界生成工作线程的残留只影响生成期采样，可接受。
 */
public final class SeasonBiomeCaches {
    private SeasonBiomeCaches() {}

    /** 清空注册表里全部群系在当前线程的温度缓存。 */
    public static void clear(RegistryAccess registryAccess) {
        Registry<Biome> biomes = registryAccess.lookupOrThrow(Registries.BIOME);
        biomes.listElements().forEach(holder ->
            ((BiomeTemperatureCacheAccess) (Object) holder.value()).bite$temperatureCache().remove());
    }
}
