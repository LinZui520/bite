package com.eamon.bite.mixin;

import com.eamon.bite.season.SeasonScope;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 维度标记注入点：{@code ServerLevel.tick} 的 HEAD/RETURN 设置/清除
 * {@link SeasonScope} 的当前维度标记。
 *
 * <p>MinecraftServer.tick 逐维度调 {@code level.tick(…)}——本 mixin 让
 * 每个 ServerLevel 的整个 tick 期间，BiomeMixin 等无 Level 上下文的
 * 钩子能判定「当前是否在主世界维度」（游戏逻辑单线程，ThreadLocal
 * 即调用点语义）。季节影响由此隔离到主世界：下界的 Biome 温度
 * 不吃冬季偏移（否则下界也会被拖到下雪）、方块随机刻同维度生效。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void bite$markDimension(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
        SeasonScope.setCurrentDimension(((ServerLevel) (Object) this).dimension());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void bite$clearDimension(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
        SeasonScope.clear();
    }
}
