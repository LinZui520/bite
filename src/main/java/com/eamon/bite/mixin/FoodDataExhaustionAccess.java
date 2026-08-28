package com.eamon.bite.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 FoodData 的疲劳值（移动疲劳乘数需要读取当前值算差值）。 */
@Mixin(FoodData.class)
public interface FoodDataExhaustionAccess {
    @Accessor("exhaustionLevel")
    float bite$exhaustionLevel();
}
