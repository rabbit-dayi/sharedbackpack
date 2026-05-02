package com.sharedbackpack.gui;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.backpack.SharedBackpack;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.database.DatabaseManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class BackpackMenu extends AbstractContainerMenu {
    private static final int SLOTS_PER_PAGE = 54;
    private final ItemStackHandler handler;
    private final int page;
    private final int maxPages;
    private final String teamId;
    private final ServerPlayer player;

    public BackpackMenu(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        this(windowId, playerInv, buf.readUtf(), buf.readInt(), buf.readInt());
    }

    public BackpackMenu(int windowId, Inventory playerInv, String teamId, int page, int maxPages) {
        super(MenuType.GENERIC_9x6, windowId);
        this.teamId = teamId;
        this.page = page;
        this.maxPages = maxPages;
        this.player = (ServerPlayer) playerInv.player;
        this.handler = new ItemStackHandler(SLOTS_PER_PAGE);

        loadItems();
        
        // Backpack slots
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            final int slot = i;
            addSlot(new Slot(new Container() {
                @Override public int getContainerSize() { return 1; }
                @Override public boolean isEmpty() { return handler.getStackInSlot(slot).isEmpty(); }
                @Override public ItemStack getItem(int index) { return handler.getStackInSlot(slot); }
                @Override public ItemStack removeItem(int index, int count) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (stack.isEmpty()) return ItemStack.EMPTY;
                    ItemStack taken = stack.split(count);
                    handler.setStackInSlot(slot, stack);
                    return taken;
                }
                @Override public void setItem(int index, ItemStack stack) { handler.setStackInSlot(slot, stack); }
                @Override public void setChanged() {}
                @Override public boolean stillValid(Player p) { return true; }
                @Override public ItemStack removeItemNoUpdate(int index) { return handler.getStackInSlot(slot); }
                @Override public void clearContent() { handler.setStackInSlot(slot, ItemStack.EMPTY); }
            }, 0, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                @Override
                public boolean mayPickup(Player player) { return true; }
                @Override
                public void onTake(Player player, ItemStack stack) {
                    if (player instanceof ServerPlayer sp) {
                        int realSlot = page * SLOTS_PER_PAGE + slot;
                        SharedBackpackMod.database.removeItem(teamId, realSlot, stack.getCount());
                    }
                }
            });
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    private void loadItems() {
        int startSlot = page * SLOTS_PER_PAGE;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(teamId);
        for (DatabaseManager.BackpackItem item : items) {
            if (item.slot >= startSlot && item.slot < startSlot + SLOTS_PER_PAGE) {
                ItemStack stack = SharedBackpack.toItemStack(item);
                // Store metadata in NBT for tooltip
                if (item.placedBy != null) {
                    net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
                    tag.putString("placedBy", item.placedBy);
                    if (item.placedTime != null) tag.putString("placedTime", item.placedTime);
                    tag.putInt("placedCount", item.placedCount);
                    if (item.lastModifiedBy != null) tag.putString("lastModifiedBy", item.lastModifiedBy);
                }
                handler.setStackInSlot(item.slot - startSlot, stack);
            }
        }
    }

    public static void openForPlayer(ServerPlayer player, String search) {
        String teamId = TeamResolver.resolvePrimaryTeam(player);
        int maxPages = SharedBackpackMod.database.getMaxPages(teamId);
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new BackpackMenu(id, inv, teamId, 0, maxPages);
            }
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.literal("共享背包 - " + teamId);
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < SLOTS_PER_PAGE) {
            Slot slot = this.getSlot(index);
            if (slot != null && slot.hasItem()) {
                ItemStack stack = slot.getItem().copy();
                if (player instanceof ServerPlayer sp) {
                    boolean added = player.getInventory().add(stack);
                    if (added) {
                        slot.set(ItemStack.EMPTY);
                        SharedBackpackMod.database.removeItem(teamId, 
                            page * SLOTS_PER_PAGE + index, stack.getCount());
                        return stack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public String getTeamId() { return teamId; }
    public int getCurrentPage() { return page; }
    public int getMaxPages() { return maxPages; }
    
    public boolean canUpgrade() {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == net.minecraft.world.item.Items.DIAMOND && stack.getCount() >= 1) {
                return true;
            }
        }
        return false;
    }
    
    public boolean upgradeWithDiamond() {
        if (!canUpgrade()) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == net.minecraft.world.item.Items.DIAMOND && stack.getCount() >= 1) {
                stack.shrink(1);
                if (SharedBackpackMod.database.upgradePages(teamId, 1)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a背包已升级，增加1页容量！"));
                    return true;
                }
            }
        }
        return false;
    }
}
