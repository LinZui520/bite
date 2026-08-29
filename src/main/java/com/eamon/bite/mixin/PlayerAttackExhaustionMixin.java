package com.eamon.bite.mixin;

import com.eamon.bite.hunger.ExhaustionScaling;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 攻击疲劳注入点：{@code Player.attack} 的 HEAD/RETURN 差值法按
 * {@code hunger_action_multiplier} 缩放原版攻击疲劳（0.1 → ×乘数）。
 * 公共逻辑见 {@link ExhaustionScaling}。
 */
@Mixin(Player.class)
public abstract class PlayerAttackExhaustionMixin {
    @Unique private float bite$exhaustionBefore;

    @Inject(method = "attack", at = @At("HEAD"))
    private void bite$beforeAttack(Entity target, CallbackInfo ci) {
        bite$exhaustionBefore = ExhaustionScaling.capture((Player) (Object) this);
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void bite$afterAttack(Entity target, CallbackInfo ci) {
        ExhaustionScaling.scaleDelta((Player) (Object) this, bite$exhaustionBefore);
    }
}
