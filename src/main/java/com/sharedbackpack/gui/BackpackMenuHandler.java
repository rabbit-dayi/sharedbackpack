package com.sharedbackpack.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BackpackMenuHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
            && event.getItemStack().getItem() == Items.CARROT) {
            event.setCanceled(true);
            BackpackMenu.openTeam(player, "");
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
            && event.getItemStack().getItem() == Items.CARROT) {
            event.setCanceled(true);
            BackpackMenu.openTeam(player, "");
        }
    }
}
