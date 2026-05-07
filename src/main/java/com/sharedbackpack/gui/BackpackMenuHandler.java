package com.sharedbackpack.gui;

import com.sharedbackpack.commands.BindManager;
import com.sharedbackpack.backpack.TeamResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BackpackMenuHandler {

    private boolean tryOpen(ServerPlayer player) {
        if (!BindManager.matches(player.getStringUUID(), player.getMainHandItem())) return false;
        String boxTarget = BindManager.getBoxTarget(player.getStringUUID());
        if (boxTarget != null) {
            String team = TeamResolver.resolvePrimaryTeam(player);
            String boxOwner = team + ":" + boxTarget;
            player.getServer().execute(() -> BackpackMenu.openForPlayer(player, "", 0, false, true, boxOwner));
        } else {
            player.getServer().execute(() -> BackpackMenu.openTeam(player, ""));
        }
        return true;
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getEntity() instanceof ServerPlayer player && tryOpen(player))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getEntity() instanceof ServerPlayer player && tryOpen(player))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (event.getEntity() instanceof ServerPlayer player && tryOpen(player))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && tryOpen(player))
            event.setCanceled(true);
    }
}
