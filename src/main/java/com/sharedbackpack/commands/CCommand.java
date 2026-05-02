package com.sharedbackpack.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharedbackpack.backpack.SharedBackpack;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.gui.BackpackMenu;
import com.sharedbackpack.network.NetworkHandler;
import com.sharedbackpack.network.OpenBackpackPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

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
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new OpenBackpackPacket(primaryTeam, searchFilter)
        );
    }
}
