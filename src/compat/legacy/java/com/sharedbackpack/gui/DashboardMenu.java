package com.sharedbackpack.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DashboardMenu extends DashboardMenuBase {
    public DashboardMenu(int id, ServerPlayerEntity player, String owner, String box) { super(id, player, owner, box); }
    @Override public ItemStack onSlotClick(int slotId, int button, SlotActionType type, PlayerEntity player) {
        return blockClick(slotId, type) ? ItemStack.EMPTY : super.onSlotClick(slotId, button, type, player);
    }
    @Override public ItemStack transferSlot(PlayerEntity player, int index) { return move(player, index); }
    @Override public void close(PlayerEntity player) { releaseInventory(); super.close(player); }
    @Override protected ItemStack cursor() { return this.player.inventory.getCursorStack(); }
    @Override protected void cursor(ItemStack stack) {
        this.player.inventory.setCursorStack(stack);
        this.player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket(-1, -1, stack));
    }
}
