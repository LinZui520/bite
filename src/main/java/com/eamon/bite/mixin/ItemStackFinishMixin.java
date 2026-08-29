package com.eamon.bite.mixin;

import com.eamon.bite.freshness.EatSnapshot;
import com.eamon.bite.thirst.ThirstController;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进食结算注入点：{@code ItemStack.finishUsingItem} 的 HEAD 捕获、RETURN
 * 结算——新鲜度惩罚（快照-重算模式）与饥渴补水。
 *
 * <p>两条结算<b>相互独立</b>：补水不依赖新鲜度快照——饮品没有 FRESHNESS
 * 戳（snap 为 null 是常态）。补水只需 entity 是 Player 且在服务端。
 *
 * <p>补水的物品在 <b>HEAD 时记录</b>：吃 stackSize=1 的东西（水瓶/牛奶/
 * 汤）后 {@code consume(1)} 使 count 归零，RETURN 时 {@code getItem()}
 * 会返回 AIR（isEmpty 时 typeHolder 切到 AIR）——在 RETURN 取物品会
 * 全部漏掉 1 堆叠的补水主力（真实 bug，实测吃西瓜能回水而水瓶不能）。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackFinishMixin {
    @Unique private EatSnapshot bite$snap;
    @Unique private Item bite$item;

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void bite$capture(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack self = (ItemStack) (Object) this;
        bite$snap = EatSnapshot.capture(self, entity);
        bite$item = self.getItem();
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void bite$applyPenalty(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        // 新鲜度惩罚：仅对已打标食物（snap 非 null）
        if (bite$snap != null) {
            bite$snap.applyPenalty((Player) entity);
            bite$snap = null;
        }
        // 饮食补水：与新鲜度无关（HEAD 已记录物品，规避空栈 AIR 问题）
        if (!level.isClientSide() && entity instanceof Player player) {
            ThirstController.onConsume(player, bite$item);
        }
        bite$item = null;
    }
}
