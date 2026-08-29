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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * 饥渴 HUD：饥饿条上方一排 💧（10 滴，0~20），布局对齐原版食物条
 * （右侧锚定 guiWidth/2+91、每滴 8px、9×9 贴图）。
 *
 * <p><b>生动性</b>（对齐原版饥饿条的行为语言）：
 * <ul>
 *   <li>低水抖动：水合 ≤6（疾跑线）时每滴上下随机 1px 抖动——
 *       原版「饥饿 0 饱和度时食物图标抖动」同款（含按剩余值变频的节拍）</li>
 *   <li>临界闪烁：水合 ≤3 时整条按 0.5s 周期闪「熄灭」，干渴临头的
 *       紧迫感（相位用 gameTime 保证确定性）</li>
 * </ul>
 *
 * <p><b>固定第二行</b>：饥饿/心 = 第一行（-39）、水滴 = 第二行（-49）、
 * 氧气 = 第三行（-59，由 HudAirMixin 把原版氧气基线无条件下移一行）。
 */
@Environment(EnvType.CLIENT)
public final class ThirstHud implements HudElement {
    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("bite", "hud/thirst_empty");
    private static final Identifier HALF = Identifier.fromNamespaceAndPath("bite", "hud/thirst_half");
    private static final Identifier FULL = Identifier.fromNamespaceAndPath("bite", "hud/thirst_full");

    /** 抖动阈值（= 疾跑线）。 */
    private static final float JITTER_THRESHOLD = 6.0f;
    /** 闪烁阈值（临近干渴）。 */
    private static final float BLINK_THRESHOLD = 3.0f;

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

        Minecraft client = Minecraft.getInstance();
        float thirst = ThirstClientStore.get();
        long gameTime = client.level == null ? 0 : client.level.getGameTime();

        // 固定第二行（guiHeight-49）：饥饿/心第一行、水滴第二行、氧气第三行
        // （氧气条由 HudAirMixin 无条件下移一行，不再需要让位逻辑）
        int baseY = gui.guiHeight() - 49;
        int xRight = gui.guiWidth() / 2 + 91;

        // 临界闪烁：≤3 时 0.5s 周期的「熄灭」相（10 tick 半周期）
        boolean blinkOff = thirst <= BLINK_THRESHOLD
            && Mth.sin(gameTime * (float) (Math.PI * 2 / 10)) < 0.0f;

        // 抖动节拍：剩余越少抖得越频繁（原版 food*3+1 变频的反向运用）
        int jitterBeat = Math.max((int) thirst, 1) * 3 + 1;

        for (int i = 0; i < 10; i++) {
            int yo = baseY;
            if (thirst <= JITTER_THRESHOLD && gameTime % jitterBeat == 0) {
                yo += random.nextInt(3) - 1;
            }
            int xo = xRight - i * 8 - 9;
            if (blinkOff) {
                continue; // 熄灭相：整滴跳过
            }
            gui.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, xo, yo, 9, 9);
            if (i * 2 + 1 < thirst) {
                gui.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, xo, yo, 9, 9);
            } else if (i * 2 + 1 <= thirst + 0.5f && i * 2 + 0.5f <= thirst) {
                gui.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, xo, yo, 9, 9);
            }
        }
    }
}
