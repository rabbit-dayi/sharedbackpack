package com.sharedbackpack.commands;

import com.sharedbackpack.database.DatabaseManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.*;
import java.util.stream.Collectors;

public class PinyinSearch {

    /**
     * Search items by query matching against:
     * - Pinyin of Chinese display name
     * - Pinyin initials
     * - English item ID
     * - Raw display name
     */
    public static List<DatabaseManager.BackpackItem> search(
            List<DatabaseManager.BackpackItem> items, String query) {
        if (query == null || query.isBlank()) return items;

        String q = query.toLowerCase().trim();

        return items.stream()
            .filter(item -> matches(item, q))
            .collect(Collectors.toList());
    }

    private static boolean matches(DatabaseManager.BackpackItem item, String query) {
        // Match against item ID directly
        if (item.itemId.toLowerCase().contains(query)) return true;

        // Match against item path part (after the colon)
        String path = item.itemId.contains(":") ? item.itemId.split(":", 2)[1] : item.itemId;
        if (path.toLowerCase().contains(query)) return true;

        // Get display name
        Item mcItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.itemId));
        if (mcItem != null) {
            String displayName = mcItem.getDescription().getString().toLowerCase();
            // Direct name match
            if (displayName.contains(query)) return true;
            // Pinyin match
            String pinyin = PinyinUtil.toPinyin(displayName);
            if (pinyin.contains(query)) return true;
            // Pinyin initials match
            String initials = PinyinUtil.toPinyinInitials(displayName);
            if (initials.contains(query)) return true;
        }

        return false;
    }

    /**
     * Search all registered items (for /c search command to find items to add).
     */
    public static List<ItemSearchResult> searchAllItems(String query) {
        if (query == null || query.isBlank()) return List.of();

        String q = query.toLowerCase().trim();
        List<ItemSearchResult> results = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;

            String itemId = id.toString().toLowerCase();
            String displayName = item.getDescription().getString().toLowerCase();

            boolean matched = itemId.contains(q)
                || displayName.contains(q)
                || PinyinUtil.toPinyin(displayName).contains(q)
                || PinyinUtil.toPinyinInitials(displayName).contains(q);

            if (matched) {
                results.add(new ItemSearchResult(id.toString(), item.getDescription().getString()));
                if (results.size() >= 20) break;
            }
        }
        return results;
    }

    public record ItemSearchResult(String itemId, String displayName) {}
}
