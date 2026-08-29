package com.eamon.bite.mixin;

import com.eamon.bite.freshness.EatSnapshot;
import com.eamon.bite.thirst.ThirstController;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进食结算注入点：{@code ItemStack.finishUsingItem} 的 HEAD 捕获、RETURN
 * 结算——新鲜度惩罚（快照-重算模式，语义见
 * {@link com.eamon.bite.freshness.SpoiledFoodHandler}）与饥渴补水。
 *
 * <p>两条结算<b>相互独立</b>：补水不依赖新鲜度快照——水瓶/牛奶/汤等
 * 饮品没有 FRESHNESS 戳（snap 为 null 是常态），曾被 snap 判空提前
 * return 挡掉（真实 bug：吃任何东西都不回水）。补水只需 entity 是
 * Player；且只在服务端执行（客户端镜像字段无需修改，值由
 * ThirstSyncPacket 同步）。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackFinishMixin {
    @Unique private EatSnapshot bite$snap;

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void bite$capture(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        bite$snap = EatSnapshot.capture((ItemStack) (Object) this, entity);
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void bite$applyPenalty(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        // 新鲜度惩罚：仅对已打标食物（snap 非 null）
        if (bite$snap != null) {
            bite$snap.applyPenalty((Player) entity);
            bite$snap = null;
        }
        // 饮食补水：与新鲜度无关，任何饮食都结算（仅服务端——客户端
        // 不跑 completeUsingItem，此处双保险判 isClientSide）
        if (!level.isClientSide() && entity instanceof Player player) {
            ItemStack self = (ItemStack) (Object) this;
            ThirstController.onConsume(player, self.getItem());
        }
    }
}
