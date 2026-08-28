package com.eamon.bite.mixin;

import com.eamon.bite.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 移动疲劳注入点：{@code ServerPlayer.checkMovementStatistics}。
 *
 * <p>两件事：
 * <ul>
 *   <li><b>走路计费</b>（原版走路/潜行疲劳恒 0）：地面非疾跑位移按
 *       0.005/米收疲劳——走一天路额外加速 ~25% 饥饿</li>
 *   <li><b>动作乘数</b>：HEAD/RETURN 差值法按 {@code hunger_action_multiplier}
 *       缩放原版非零疲劳（疾跑 0.1→0.13/米、游泳等同步）——退回再收，
 *       净额不受上限钳制影响</li>
 * </ul>
 * 挖矿/跳跃不在此方法内（代价已合理，不动）。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMovementMixin {
    /** 走路疲劳（每米）。原版为 0；0.005 ≈ 每小时闲逛多烧 20% 日代谢。 */
    private static final float WALK_COST_PER_METER = 0.005f;

    @Unique private float bite$exhaustionBefore;

    @Inject(method = "checkMovementStatistics", at = @At("HEAD"))
    private void bite$beforeMovement(double dx, double dy, double dz, CallbackInfo ci) {
        bite$exhaustionBefore = bite$exhaustionOf((ServerPlayer) (Object) this);
    }

    @Inject(method = "checkMovementStatistics", at = @At("RETURN"))
    private void bite$afterMovement(double dx, double dy, double dz, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        // 走路计费：地面、非疾跑、非潜行（潜行保持免费——潜行是低速侦察不是移动方式）
        float walkExhaustion = 0.0f;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (self.onGround() && !self.isSprinting() && !self.isCrouching() && !self.onClimbable()) {
            walkExhaustion = (float) (WALK_COST_PER_METER * horizontal);
        }
        float delta = bite$exhaustionOf(self) - bite$exhaustionBefore;
        float multiplier = (float) ServerConfig.get().hungerActionMultiplier();
        if (multiplier > 1.0f && delta > 0.0f) {
            self.getFoodData().addExhaustion(-delta);
            self.getFoodData().addExhaustion(delta * multiplier);
        }
        if (walkExhaustion > 0.0f) {
            self.getFoodData().addExhaustion(walkExhaustion);
        }
    }

    @Unique
    private static float bite$exhaustionOf(ServerPlayer player) {
        return ((FoodDataExhaustionAccess) player.getFoodData()).bite$exhaustionLevel();
    }
}
