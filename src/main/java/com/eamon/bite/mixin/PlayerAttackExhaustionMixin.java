package com.eamon.bite.mixin;

import com.eamon.bite.config.ServerConfig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 攻击疲劳注入点：{@code Player.attack} 的 HEAD/RETURN 差值法按
 * {@code hunger_action_multiplier} 缩放原版攻击疲劳（0.1 → ×乘数）。
 * 与 {@link ServerPlayerMovementMixin} 同款差值法（退回再收）。
 */
@Mixin(Player.class)
public abstract class PlayerAttackExhaustionMixin {
    @Unique private float bite$exhaustionBefore;

    @Inject(method = "attack", at = @At("HEAD"))
    private void bite$beforeAttack(net.minecraft.world.entity.Entity target, CallbackInfo ci) {
        bite$exhaustionBefore = bite$exhaustionOf((Player) (Object) this);
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void bite$afterAttack(net.minecraft.world.entity.Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        float delta = bite$exhaustionOf(self) - bite$exhaustionBefore;
        if (delta <= 0.0f) return;
        float multiplier = (float) ServerConfig.get().hungerActionMultiplier();
        if (multiplier <= 1.0f) return;
        self.getFoodData().addExhaustion(-delta);
        self.getFoodData().addExhaustion(delta * multiplier);
    }

    @Unique
    private static float bite$exhaustionOf(Player player) {
        return ((FoodDataExhaustionAccess) player.getFoodData()).bite$exhaustionLevel();
    }
}
