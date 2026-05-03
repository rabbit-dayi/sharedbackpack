package com.sharedbackpack.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.gui.BackpackMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("c")
            .executes(ctx -> openBackpack(ctx))
            .then(Commands.argument("search", StringArgumentType.greedyString())
                .executes(ctx -> openBackpackWithSearch(ctx)))
        );
    }

    private static int openBackpack(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("此命令只能由玩家使用"));
            return 0;
        }
        openGui(player, "");
        return 1;
    }

    private static int openBackpackWithSearch(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("此命令只能由玩家使用"));
            return 0;
        }
        String search = StringArgumentType.getString(ctx, "search");
        openGui(player, search);
        return 1;
    }

    public static void openGui(ServerPlayer player, String searchFilter) {
        String primaryTeam = TeamResolver.resolvePrimaryTeam(player);
        int maxPages = com.sharedbackpack.SharedBackpackMod.database.getMaxPages(primaryTeam);
        int currentPage = 0;
        BackpackMenu.openForPlayer(player, searchFilter);
    }
}
