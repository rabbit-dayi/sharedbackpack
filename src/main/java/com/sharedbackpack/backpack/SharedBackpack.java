package com.sharedbackpack.backpack;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.compat.MinecraftCompat;
import com.sharedbackpack.database.DatabaseManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

public class SharedBackpack {

    public static final int SLOTS_PER_PAGE = 45;

    public static List<DatabaseManager.BackpackItem> getTeamItems(ServerPlayerEntity player) {
        List<String> teams = TeamResolver.resolveTeams(player);
        // Union: collect items from all teams, merge duplicates by item_id
        Map<String, DatabaseManager.BackpackItem> merged = new LinkedHashMap<>();
        for (String teamId : teams) {
            for (DatabaseManager.BackpackItem item : SharedBackpackMod.database.getItems(teamId)) {
                String key = item.itemId + ":" + (item.nbt != null ? item.nbt : "null");
                DatabaseManager.BackpackItem existing = merged.get(key);
                if (existing != null) {
                    // Merge counts (keep first team's metadata)
                    merged.put(key, new DatabaseManager.BackpackItem(
                        existing.slot, existing.itemId,
                        existing.count + item.count, existing.nbt,
                        existing.placedBy, existing.placedTime, 
                        existing.placedCount + item.placedCount,
                        existing.lastModifiedBy, existing.lastModifiedTime
                    ));
                } else {
                    merged.put(key, item);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    public static int getMaxPages(ServerPlayerEntity player) {
        String teamId = TeamResolver.resolvePrimaryTeam(player);
        return SharedBackpackMod.database.getMaxPages(teamId);
    }

    public static int getMaxSlots(ServerPlayerEntity player) {
        return getMaxPages(player) * SLOTS_PER_PAGE;
    }

    public static boolean addItem(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        String teamId = TeamResolver.resolvePrimaryTeam(player);
        String itemId = MinecraftCompat.getItemId(stack.getItem()).toString();
        String nbt = MinecraftCompat.hasNbt(stack) ? stripMetadata(MinecraftCompat.getNbt(stack).toString()) : null;
        return SharedBackpackMod.database.addItem(teamId, itemId, stack.getCount(), nbt, player.getEntityName());
    }

    public static String stripMetadata(String nbtStr) {
        if (nbtStr == null || nbtStr.isEmpty()) return null;
        try {
            NbtCompound tag = net.minecraft.nbt.StringNbtReader.parse(nbtStr);
            tag.remove("placedBy");
            tag.remove("placedTime");
            tag.remove("placedCount");
            tag.remove("lastModifiedBy");
            tag.remove("lastModifiedTime");
            if (tag.contains("display")) {
                NbtCompound display = tag.getCompound("display");
                display.remove("Lore");
                if (display.isEmpty()) tag.remove("display");
            }
            return tag.isEmpty() ? null : tag.toString();
        } catch (Exception e) {
            return nbtStr;
        }
    }

    public static boolean removeItem(ServerPlayerEntity player, String teamId, int slot, int count) {
        List<String> teams = TeamResolver.resolveTeams(player);
        if (!teams.contains(teamId) && !teams.contains(TeamResolver.GLOBAL_TEAM)) {
            return false;
        }
        return SharedBackpackMod.database.removeItem(teamId, slot, count);
    }

    public static boolean upgradePages(ServerPlayerEntity player) {
        String teamId = TeamResolver.resolvePrimaryTeam(player);
        return SharedBackpackMod.database.upgradePages(teamId, 1);
    }

    public static ItemStack toItemStack(DatabaseManager.BackpackItem bpItem) {
        Item item = MinecraftCompat.getItem(new Identifier(bpItem.itemId));
        // Fallback to dirt if item not found (mod not loaded)
        if (item == null || item == Items.AIR) {
            item = Items.DIRT;
        }
        ItemStack stack = new ItemStack(item, Math.min(bpItem.count, 64));
        if (bpItem.nbt != null && !bpItem.nbt.isEmpty()) {
            try {
                NbtCompound tag = net.minecraft.nbt.StringNbtReader.parse(bpItem.nbt);
                MinecraftCompat.setNbt(stack, tag);
            } catch (Exception ignored) {}
        }
        return stack;
    }

    public static int getTotalCount(ServerPlayerEntity player) {
        List<String> teams = TeamResolver.resolveTeams(player);
        int total = 0;
        for (String teamId : teams) {
            total += SharedBackpackMod.database.getTotalItemCount(teamId);
        }
        return total;
    }
}
