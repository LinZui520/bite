package com.eamon.bite.client.mixin;

import com.eamon.bite.client.thirst.ThirstClientStore;
import com.eamon.bite.thirst.ThirstData;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 饥渴禁疾跑（客户端预测拦截）：{@code LocalPlayer.isSprintingPossible}——
 * 原版疾跑门槛（饥饿 >6）在这里判定。饥渴 ≤6 时同样拒绝起跑；
 * 服务端 tick 的 setSprinting(false) 兜底。值读 {@link ThirstClientStore}
 * （最近同步包的镜像）。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintMixin {
    @Inject(method = "isSprintingPossible", at = @At("HEAD"), cancellable = true)
    private void bite$thirstBlocksSprint(boolean allowedInShallowWater, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (ThirstClientStore.get() <= ThirstData.SPRINT_THRESHOLD
            && !self.getAbilities().mayfly) {
            cir.setReturnValue(false);
        }
    }
}
