package com.sharedbackpack.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.compat.MinecraftCompat;
import com.sharedbackpack.gui.BackpackMenu;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;

public class CCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("c")
            .executes(ctx -> openBackpack(ctx))
            .then(CommandManager.argument("search", StringArgumentType.greedyString())
                .executes(ctx -> openBackpackWithSearch(ctx)))
        );
    }

    private static int openBackpack(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = player(ctx.getSource());
        if (player == null) {
            ctx.getSource().sendError(MinecraftCompat.text("此命令只能由玩家使用"));
            return 0;
        }
        openGui(player, "");
        return 1;
    }

    private static int openBackpackWithSearch(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = player(ctx.getSource());
        if (player == null) {
            ctx.getSource().sendError(MinecraftCompat.text("此命令只能由玩家使用"));
            return 0;
        }
        String search = StringArgumentType.getString(ctx, "search");
        openGui(player, search);
        return 1;
    }

    public static void openGui(ServerPlayerEntity player, String searchFilter) {
        String primaryTeam = TeamResolver.resolvePrimaryTeam(player);
        int maxPages = com.sharedbackpack.SharedBackpackMod.database.getMaxPages(primaryTeam);
        int currentPage = 0;
        BackpackMenu.openForPlayer(player, searchFilter);
    }

    private static ServerPlayerEntity player(ServerCommandSource source) {
        Entity entity = source.getEntity();
        return entity instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity : null;
    }
}
