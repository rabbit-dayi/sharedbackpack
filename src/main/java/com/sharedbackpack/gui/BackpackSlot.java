package com.sharedbackpack.gui;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

public class BackpackSlot extends Slot {
    private final ItemStackHandler handler;
    private final int index;

    public BackpackSlot(ItemStackHandler handler, int index, int x, int y) {
        super(new Container() {
            @Override public int getContainerSize() { return handler.getSlots(); }
            @Override public boolean isEmpty() { return handler.getStackInSlot(0).isEmpty(); }
            @Override public ItemStack getItem(int slot) { return handler.getStackInSlot(slot); }
            @Override public ItemStack removeItem(int slot, int amount) { return handler.extractItem(slot, amount, false); }
            @Override public void setItem(int slot, ItemStack stack) { handler.setStackInSlot(slot, stack); }
            @Override public void setChanged() {}
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
            @Override public ItemStack removeItemNoUpdate(int slot) { return handler.getStackInSlot(slot); }
            @Override public void clearContent() {}
        }, index, x, y);
        this.handler = handler;
        this.index = index;
    }

    @Override
    public void set(ItemStack stack) {
        handler.setStackInSlot(index, stack);
    }

    @Override
    public ItemStack getItem() {
        return handler.getStackInSlot(index);
    }
}
