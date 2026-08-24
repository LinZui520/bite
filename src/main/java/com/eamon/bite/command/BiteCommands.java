package com.eamon.bite.command;

import com.eamon.bite.BiteMod;
import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.freshness.FreshnessMath;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

/**
 * /bite freshness 调试命令（OP 2 级）。
 *
 * <p>输出手持食物的 stamp / shelfLife / fraction：
 * <ul>
 *   <li>无 FRESHNESS 且无（或永不腐坏的）SHELF_LIFE → 提示「手持食物后再查询」</li>
 *   <li>有数据时 → 标题行（物品名）+ 生产刻 + 保质期天数 + 当前新鲜度百分比</li>
 *   <li>有 stamp 但 SHELF_LIFE 永不腐坏 → 标题行 + 生产刻 + 永不腐坏</li>
 * </ul>
 *
 * <p>26.2 适配：
 * <ul>
 *   <li>{@link CommandRegistrationCallback#EVENT} 三参回调
 *       {@code (CommandDispatcher, CommandBuildContext, Command.RegistrationEnvironment)}，
 *       26.2 沿用 Fabric API v2 的三参形态。</li>
 *   <li>{@link CommandSourceStack#sendSuccess} 在 26.2 仍为
 *       {@code sendSuccess(Supplier<Component>, boolean)}（Supplier 重载），与 brief 一致。</li>
 *   <li>{@link CommandSourceStack#getPlayerOrException()} 抛受检异常
 *       {@link com.mojang.brigadier.exceptions.CommandSyntaxException}，故 {@link #reportFreshness}
 *       显式声明 {@code throws}（brief 原文缺此声明，无法编译）。</li>
 *   <li><b>权限门控改写</b>：brief 用 {@code source.hasPermission(2)}，26.2 已移除该方法——
 *       改为 {@code Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER))}
 *       （GAMEMASTERS 即 OP level 2）。{@link Commands#hasPermission} 返回的
 *       {@code PermissionProviderCheck} 即 {@code Predicate<CommandSourceStack>}，可直接传入
 *       {@code requires}。与 {@link net.minecraft.server.commands.GameModeCommand} 的写法一致。</li>
 * </ul>
 */
public final class BiteCommands {
    private BiteCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal(BiteMod.MOD_ID)
                .requires(Commands.hasPermission(
                    new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                .then(Commands.literal("freshness").executes(BiteCommands::reportFreshness))));
    }

    private static int reportFreshness(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        CommandSourceStack source = ctx.getSource();
        if (stamp == null && (life == null || life.spoilTicks() <= 0)) {
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.no_food"), false);
            return 0;
        }
        String itemName = stack.getHoverName().getString();
        source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.header", itemName), false);
        if (stamp != null) {
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.stamp", stamp.creationGameTick()), false);
        }
        if (life == null || life.spoilTicks() <= 0) {
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.never"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.life", life.spoilTicks() / 24000.0), false);
            long now = source.getLevel().getGameTime();
            double fraction = stamp == null ? 1.0 : FreshnessMath.fraction(now, stamp, life);
            int pct = (int) Math.round(fraction * 100);
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.fraction", pct), false);
        }
        return 1;
    }
}
