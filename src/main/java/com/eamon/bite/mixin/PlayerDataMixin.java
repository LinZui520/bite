package com.eamon.bite.mixin;

import com.eamon.bite.thirst.ThirstData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 饥渴值存储注入点：Player 上的 {@code @Unique} float 字段 + NBT 持久化。
 *
 * <p>不用 DataTracker（26.2 Player 槽位已满，defineId 越界）——同步由
 * {@link com.eamon.bite.thirst.ThirstSyncPacket} 每 tick 推送。
 */
@Mixin(Player.class)
public abstract class PlayerDataMixin extends LivingEntity implements ThirstData.Holder {
    @Unique
    private float bite$thirst = ThirstData.MAX_THIRST;
    /** 干渴伤害计时器（不持久化：重生/重进后从 0 起数，对齐原版 tickTimer 的会话语义）。 */
    @Unique
    private int bite$thirstTimer;

    protected PlayerDataMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public float bite$getThirst() {
        return bite$thirst;
    }

    @Override
    public void bite$setThirst(float value) {
        bite$thirst = value;
    }

    @Override
    public int bite$thirstTimer() {
        return bite$thirstTimer;
    }

    @Override
    public void bite$setThirstTimer(int value) {
        bite$thirstTimer = value;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void bite$saveThirst(ValueOutput output, CallbackInfo ci) {
        output.putFloat("bite:thirst", bite$thirst);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void bite$loadThirst(ValueInput input, CallbackInfo ci) {
        bite$thirst = input.getFloatOr("bite:thirst", ThirstData.MAX_THIRST);
    }
}
