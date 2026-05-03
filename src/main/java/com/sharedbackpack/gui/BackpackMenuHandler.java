package com.sharedbackpack.gui;

import com.sharedbackpack.commands.BindManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BackpackMenuHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
            && BindManager.matches(player.getStringUUID(), event.getItemStack())) {
            event.setCanceled(true);
            player.getServer().execute(() -> BackpackMenu.openTeam(player, ""));
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
            && BindManager.matches(player.getStringUUID(), event.getItemStack())) {
            event.setCanceled(true);
            player.getServer().execute(() -> BackpackMenu.openTeam(player, ""));
        }
    }
}
