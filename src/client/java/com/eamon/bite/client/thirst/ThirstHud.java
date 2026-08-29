package com.eamon.bite.client.thirst;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;

/**
 * 饥渴 HUD：饥饿条上方一排水滴（10 滴，0~20），布局对齐原版食物条
 * （右侧锚定 guiWidth/2+91、每滴 8px、9×9 贴图）。
 *
 * <p><b>与氧气条的让位</b>：原版氧气条平时画在 guiHeight-49（Hud.java
 * 的 yLineAir = yLineBase-10）——与水滴行重合。水下或氧气不满时
 * （extractAirBubbles 的显示条件），水滴上移一行到 -59 让位；平时
 * 回到 -49。这是原版自己的让位语言（氧气条对坐骑心脏也是上移让位）。
 *
 * <p>渲染顺序：attachElementBefore(FOOD_BAR)——与原版饥饿条同层叠序。
 */
@Environment(EnvType.CLIENT)
public final class ThirstHud implements HudElement {
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("bite", "hud/thirst_empty");
    private static final Identifier HALF = Identifier.fromNamespaceAndPath("bite", "hud/thirst_half");
    private static final Identifier FULL = Identifier.fromNamespaceAndPath("bite", "hud/thirst_full");

    /** 注册入口（onInitializeClient 调用）。 */
    public static void register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.FOOD_BAR,
            Identifier.fromNamespaceAndPath("bite", "thirst_bar"),
            new ThirstHud());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.isCreative() || player.isSpectator()) return;

        float thirst = ThirstClientStore.get();
        // 氧气条可见时（水下/缺氧，原版显示条件）上移一行让位
        boolean airBarVisible = player.isEyeInFluid(FluidTags.WATER)
            || player.getAirSupply() < player.getMaxAirSupply();
        int y = gui.guiHeight() - (airBarVisible ? 59 : 49);
        int xRight = gui.guiWidth() / 2 + 91;

        for (int i = 0; i < 10; i++) {
            int xo = xRight - i * 8 - 9;
            gui.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, xo, y, 9, 9);
            if (i * 2 + 1 < thirst) {
                gui.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, xo, y, 9, 9);
            } else if (i * 2 + 1 <= thirst + 0.5f && i * 2 + 0.5f <= thirst) {
                gui.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, xo, y, 9, 9);
            }
        }
    }
}
