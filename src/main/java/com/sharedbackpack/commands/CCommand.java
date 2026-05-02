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
import net.minecraftforge.network.NetworkHooks;

public class CCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("c")
            .executes(CCommand::openBackpack)
            .then(Commands.argument("search", StringArgumentType.greedyString())
                .executes(CCommand::openBackpackWithSearch))
        );
    }

    private static int openBackpack(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        openGui(player, "");
        return 1;
    }

    private static int openBackpackWithSearch(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        String search = StringArgumentType.getString(ctx, "search");
        openGui(player, search);
        return 1;
    }

    public static void openGui(ServerPlayer player, String searchFilter) {
        String primaryTeam = TeamResolver.resolvePrimaryTeam(player);
        int maxPages = com.sharedbackpack.SharedBackpackMod.database.getMaxPages(primaryTeam);
        // TODO: Apply search filter to show only matching items
        NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player p) {
                return new BackpackMenu(id, inv, primaryTeam, 0, maxPages);
            }
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.literal("共享背包 - " + primaryTeam);
            }
        });
    }
}
