package com.eamon.bite.mixin;

import com.eamon.bite.hunger.HungerMetabolism;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 睡觉消耗注入点：{@code ServerLevel.tick} 的睡眠分支（全员睡够 →
 * 时钟 moveToTimeMarker 瞬移到次日 0 刻 → wakeUpAllPlayers）。
 *
 * <p>时钟跳变使被跳过的 tick 不会自然发生——饿腹代谢（按 tick 结算）
 * 会漏掉整晚。本 mixin 在跳变前记录时钟、wakeUpAllPlayers 后结算差值，
 * 按睡眠代谢率（清醒的 40%）对参与睡觉的玩家补算疲劳。
 *
 * <p>26.2 签名（genSources 验证）：睡眠分支位于 tick 内，先
 * {@code clockManager().moveToTimeMarker(..., WAKE_UP_FROM_SLEEP)} 后
 * {@code wakeUpAllPlayers()}——两处各挂一个注入点即可夹住跳变。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSleepMixin {
    @Unique private long bite$clockBeforeSleep;

    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z"))
    private void bite$captureClock(CallbackInfo ci) {
        bite$clockBeforeSleep = ((ServerLevel) (Object) this).getOverworldClockTime();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;wakeUpAllPlayers()V"))
    private void bite$chargeSleepMetabolism(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        long skipped = self.getOverworldClockTime() - bite$clockBeforeSleep;
        if (skipped > 0) {
            HungerMetabolism.onSleptThroughTicks(self.players(), skipped);
            com.eamon.bite.thirst.ThirstController.onSleptThroughTicks(self.players(), skipped);
        }
    }
}
