package com.eamon.bite.client.biome;

import com.eamon.bite.client.hud.FadingHudText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * 群系播报：进入新群系时 HUD 显示群系名（与季节播报共用
 * {@link FadingHudText} 的单位置播放队列——同位置依次播放，跨日撞上
 * 换群系时季节晚几秒跟上）。
 *
 * <p>显示名走原版翻译键 {@code biome.minecraft.plains}——原版 67 个
 * 群系自带中英翻译，自动跟随游戏语言。
 *
 * <p><b>两级防抖</b>（用户口径 2026-08-30——边界横跳不刷屏）：
 * <ol>
 *   <li><b>冷却 100 tick（5 秒）</b>＝一条播报动画总时长——上一条
 *       播完才可能出下一条，视觉上不连续闪</li>
 *   <li><b>回退防抖 60 tick（3 秒）</b>：横跳指纹是 A→B→A——重新进入
 *       刚离开的群系须停留满 3 秒才播；边界来回踱步时 B 播一次、
 *       回到 A 因没住满而不播，循环终止</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class BiomeHud {
    /** 播报冷却（tick）＝动画总时长：一条播完才可能出下一条。 */
    private static final int COOLDOWN_TICKS = 100;
    /** 回退防抖（tick）：重新进入刚离开的群系需停留此时长才播。 */
    private static final int RETURN_DWELL = 60;

    private static ResourceKey<Biome> currentBiome;
    private static ResourceKey<Biome> previousBiome;
    private static ResourceKey<Biome> lastAnnouncedBiome;
    private static long enteredAtTick;
    private static long lastAnnounceTick;

    /** 注册入口（onInitializeClient 调用一次）。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                currentBiome = null;
                previousBiome = null;
                lastAnnouncedBiome = null;
                return;
            }
            BlockPos pos = client.player.blockPosition();
            client.level.getBiome(pos).unwrapKey().ifPresent(biomeKey -> {
                long now = client.level.getGameTime();
                if (!biomeKey.equals(currentBiome)) {
                    // 换群系：记录切换与进入时间
                    previousBiome = currentBiome;
                    currentBiome = biomeKey;
                    enteredAtTick = now;
                }
                // 首个群系不播（对齐季节播报口径：进世界不重播已知信息）
                if (previousBiome == null) return;
                // 停留中：不重复播同一个群系
                if (biomeKey.equals(lastAnnouncedBiome)) return;
                // 冷却：上一条播完（含动画时长）才可能出下一条
                if (now - lastAnnounceTick < COOLDOWN_TICKS) return;
                // 回退防抖：重新进入刚离开的群系，须停留满 DWELL 才播
                if (biomeKey.equals(previousBiome) && now - enteredAtTick < RETURN_DWELL) return;
                lastAnnounceTick = now;
                lastAnnouncedBiome = biomeKey;
                FadingHudText.show(biomeName(biomeKey));
            });
        });
    }

    /** 群系显示名（原版翻译键，自动本地化）。 */
    private static Component biomeName(ResourceKey<Biome> key) {
        return Component.translatable("biome." + key.identifier().getNamespace()
            + "." + key.identifier().getPath());
    }
}
