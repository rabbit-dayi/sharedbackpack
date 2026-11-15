package com.sharedbackpack.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DashboardMenu extends DashboardMenuBase {
    public DashboardMenu(int id, ServerPlayerEntity player, String owner, String box) { super(id, player, owner, box); }
    @Override public void onSlotClick(int slotId, int button, SlotActionType type, PlayerEntity player) {
        if (!blockClick(slotId, type)) super.onSlotClick(slotId, button, type, player);
    }
    @Override public ItemStack quickMove(PlayerEntity player, int index) { return move(player, index); }
    @Override public void onClosed(PlayerEntity player) { releaseInventory(); super.onClosed(player); }
    @Override protected ItemStack cursor() { return getCursorStack(); }
    @Override protected void cursor(ItemStack stack) { setCursorStack(stack); }
}
