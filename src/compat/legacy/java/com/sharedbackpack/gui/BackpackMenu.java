package com.sharedbackpack.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public class BackpackMenu extends BackpackMenuBase {
    public BackpackMenu(int id, ServerPlayerEntity player, String teamId, int page, int maxPages,
                        String searchFilter, boolean unloadMode, boolean isBox, String boxOwner) {
        super(id, player, teamId, page, maxPages, searchFilter, unloadMode, isBox, boxOwner);
    }

    @Override
    public ItemStack onSlotClick(int slotId, int button, SlotActionType clickType, PlayerEntity player) {
        return handleSlotClick(slotId, button, clickType, player)
                ? ItemStack.EMPTY
                : super.onSlotClick(slotId, button, clickType, player);
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        return quickMoveInternal(player, index);
    }

    @Override
    protected ItemStack getCursorStackCompat() {
        return this.player.inventory.getCursorStack();
    }

    @Override
    protected void setCursorStackCompat(ItemStack stack) {
        this.player.inventory.setCursorStack(stack);
    }
}
