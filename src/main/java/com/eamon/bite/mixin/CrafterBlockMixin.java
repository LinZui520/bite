package com.eamon.bite.mixin;

import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessStamper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 自动合成器继承新鲜度：{@code CrafterBlock.dispenseFrom} 红石触发合成时，
 * 产物在 {@code assemble} 之后、{@code dispenseItem} 喷出之前做继承打标。
 *
 * <p>此前边界：合成器不走 {@code ResultSlot.onTake}（玩家取件路径），
 * 产物无戳被懒扫描当全新——机器合成 = 低配保鲜（用户口径：任何生产
 * 食物的地方都要继承）。
 *
 * <p>注入点选 {@code onCraftedBySystem} 调用处（assemble 之后、原料
 * shrink 之前）：此刻产物栈是局部变量，{@code ModifyVariable} 修改
 * 「下一个 ItemStack 局部变量」即产物本身；原料从 blockEntity 读
 * （参数可捕获）。
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

    @ModifyVariable(
        method = "dispenseFrom",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;onCraftedBySystem(Lnet/minecraft/world/level/Level;)V"),
        ordinal = 0  // results 是该方法唯一的 ItemStack 局部变量
    )
    private ItemStack bite$stampCrafterResult(ItemStack results, BlockState state, ServerLevel level, BlockPos pos) {
        if (results.isEmpty()) return results;
        if (level.getBlockEntity(pos) instanceof CrafterBlockEntity entity) {
            // 与 ResultSlotMixin 同语义：全部原料平均腐坏减半
            java.util.List<ItemStack> ingredients = new java.util.ArrayList<>();
            for (int i = 0; i < entity.getContainerSize(); i++) {
                ingredients.add(entity.getItem(i));
            }
            FreshnessStamper.stampCrafted(results, ingredients, FreshnessClock.now());
        }
        return results;
    }
}
