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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * 饥渴 HUD：饥饿条上方一排 💧（10 滴，0~20），布局与低水抖动完全对齐
 * 原版食物条（右侧锚定 guiWidth/2+91、每滴 8px、9×9 贴图）。
 *
 * <p><b>低水抖动（原版 extractFood 同款语义）</b>：水合 ≤6（疾跑线，
 * 对应原版饱和度归零的条件）时，{@code yo += random.nextInt(3) - 1}——
 * 每滴 ±1px 上下抖，节拍 {@code gameTime % (thirst*3+1) == 0} 随剩余值
 * 变频（水越少抖得越密）；随机源每帧重播种 {@code gameTime * 312871}
 * ——与原版 tickCount 同构，保证同一 tick 内所有滴的抖动模式稳定、
 * 帧间自然变化。无额外闪烁效果（原版饥饿条也没有）。
 *
 * <p><b>固定第二行</b>：饥饿/心 = 第一行（-39）、水滴 = 第二行（-49）、
 * 氧气 = 第三行（-59，由 HudAirMixin 把原版氧气基线无条件下移一行）。
 */
@Environment(EnvType.CLIENT)
public final class ThirstHud implements HudElement {
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("bite", "hud/thirst_empty");
    private static final Identifier HALF = Identifier.fromNamespaceAndPath("bite", "hud/thirst_half");
    private static final Identifier FULL = Identifier.fromNamespaceAndPath("bite", "hud/thirst_full");

    /** 抖动阈值（= 疾跑线，对应原版「饱和度归零才开始抖」的临界语义）。 */
    private static final float JITTER_THRESHOLD = 6.0f;

    private final RandomSource random = RandomSource.create();

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
        long gameTime = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        // 每帧重播种（原版 setSeed(tickCount * 312871) 同构）：同 tick 稳定、帧间变化
        random.setSeed(gameTime * 312871L);

        // 固定第二行（guiHeight-49）：饥饿/心第一行、水滴第二行、氧气第三行
        int baseY = gui.guiHeight() - 49;
        int xRight = gui.guiWidth() / 2 + 91;

        // 抖动节拍：thirst*3+1（原版 food*3+1 同式，水越少抖得越频繁）
        int jitterBeat = Math.max((int) thirst, 1) * 3 + 1;
        boolean jitter = thirst <= JITTER_THRESHOLD;

        for (int i = 0; i < 10; i++) {
            int yo = baseY;
            if (jitter && gameTime % jitterBeat == 0) {
                yo += random.nextInt(3) - 1;
            }
            int xo = xRight - i * 8 - 9;
            gui.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, xo, yo, 9, 9);
            if (i * 2 + 1 < thirst) {
                gui.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, xo, yo, 9, 9);
            } else if (i * 2 + 1 <= thirst + 0.5f && i * 2 + 0.5f <= thirst) {
                gui.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, xo, yo, 9, 9);
            }
        }
    }
}
