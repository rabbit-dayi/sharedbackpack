package com.sharedbackpack.gui;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.backpack.TeamResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BackpackMenuHandler {

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Allow diamond upgrade when using command
        }
    }

    public static boolean tryUpgradeWithDiamond(ServerPlayer player) {
        String teamId = TeamResolver.resolvePrimaryTeam(player);
        // Check if player has diamond in inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.DIAMOND && stack.getCount() >= 1) {
                stack.shrink(1);
                if (SharedBackpackMod.database.upgradePages(teamId, 1)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a背包已升级，增加1页容量！"));
                    return true;
                }
            }
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c需要1个钻石来升级背包！"));
        return false;
    }
}
