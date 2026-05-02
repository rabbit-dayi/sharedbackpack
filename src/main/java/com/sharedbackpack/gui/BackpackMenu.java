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
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;

public class BackpackMenu extends AbstractContainerMenu {
    private static final int SLOTS_PER_PAGE = 54;
    private final ItemStackHandler handler;
    private final int page;
    private final int maxPages;
    private final String teamId;
    private final ServerPlayer player;

    public static MenuType<BackpackMenu> TYPE;

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

        int startSlot = page * SLOTS_PER_PAGE;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(teamId);
        for (DatabaseManager.BackpackItem item : items) {
            if (item.slot >= startSlot && item.slot < startSlot + SLOTS_PER_PAGE) {
                ItemStack stack = SharedBackpack.toItemStack(item);
                // Add lore with metadata
                if (item.placedBy != null) {
                    List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
                    lore.add(net.minecraft.network.chat.Component.literal("§7放入者: " + item.placedBy));
                    lore.add(net.minecraft.network.chat.Component.literal("§7时间: " + (item.placedTime != null ? item.placedTime : "未知")));
                    lore.add(net.minecraft.network.chat.Component.literal("§7数量: " + item.placedCount));
                    if (item.lastModifiedBy != null) {
                        lore.add(net.minecraft.network.chat.Component.literal("§7最后修改: " + item.lastModifiedBy));
                    }
                    // Store metadata in NBT for display
                    net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
                    tag.putString("placedBy", item.placedBy);
                    tag.putString("placedTime", item.placedTime != null ? item.placedTime : "");
                    tag.putInt("placedCount", item.placedCount);
                }
                handler.setStackInSlot(item.slot - startSlot, stack);
            }
        }

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
                    // Will be handled by server
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    public static void openForPlayer(ServerPlayer player, String search) {
        String teamId = TeamResolver.resolvePrimaryTeam(player);
        int maxPages = SharedBackpackMod.database.getMaxPages(teamId);
        NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                return new BackpackMenu(windowId, inv, teamId, 0, maxPages);
            }
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.literal("共享背包 - " + teamId);
            }
        }, buf -> {
            buf.writeUtf(teamId);
            buf.writeInt(0);
            buf.writeInt(maxPages);
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public static boolean handleTake(ServerPlayer player, int slot, int count, String teamId) {
        return SharedBackpackMod.database.removeItem(teamId, slot, count);
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < SLOTS_PER_PAGE) {
            ItemStack stack = handler.getStackInSlot(slotId);
            if (!stack.isEmpty() && player instanceof ServerPlayer) {
                int takeCount = 1;
                if (button == 1) { // Right click - take full stack
                    takeCount = Math.min(stack.getMaxStackSize(), stack.getCount());
                }
                if (SharedBackpackMod.database.removeItem(teamId, 
                    page * SLOTS_PER_PAGE + slotId, takeCount)) {
                    ItemStack taken = stack.copy();
                    taken.setCount(takeCount);
                    player.getInventory().add(taken);
                    handler.setStackInSlot(slotId, stack.isEmpty() ? ItemStack.EMPTY : stack);
                }
            }
        }
        super.clicked(slotId, button, clickType, player);
    }
}
