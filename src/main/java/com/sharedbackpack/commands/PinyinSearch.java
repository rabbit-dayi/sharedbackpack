package com.sharedbackpack.commands;

import com.sharedbackpack.compat.MinecraftCompat;
import com.sharedbackpack.database.DatabaseManager;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;

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
        if (query == null || query.trim().isEmpty()) return items;

        String q = query.toLowerCase().trim();
        String[] words = q.split("\\s+");

        return items.stream()
            .filter(item -> matches(item, q, words))
            .collect(Collectors.toList());
    }

    private static boolean matches(DatabaseManager.BackpackItem item, String fullQuery, String[] words) {
        for (String word : words) {
            if (matchesWord(item, word)) return true;
        }
        return false;
    }

    private static boolean matchesWord(DatabaseManager.BackpackItem item, String query) {
        // Namespace:search fuzzy match (e.g. forge:stone, minecraft:diamond)
        if (query.contains(":")) {
            String[] parts = query.split(":", 2);
            String ns = parts[0].toLowerCase();
            String term = parts[1].toLowerCase();
            String itemNs = item.itemId.contains(":") ? item.itemId.split(":", 2)[0].toLowerCase() : "minecraft";
            if (!itemNs.contains(ns)) return false;
            if (term.isEmpty()) return true;
            // Match term against path, name, pinyin
            String path = item.itemId.contains(":") ? item.itemId.split(":", 2)[1].toLowerCase() : item.itemId.toLowerCase();
            if (path.contains(term)) return true;
            Item mcItem = MinecraftCompat.getItem(new Identifier(item.itemId));
            if (mcItem != null) {
                String cnName = ChineseNames.get(mcItem.getTranslationKey());
                if (cnName != null && PinyinUtil.matches(cnName, term)) return true;
                String name = MinecraftCompat.getItemDisplayName(mcItem).toLowerCase();
                if (name.contains(term)) return true;
                if (PinyinUtil.matches(name, term)) return true;
            }
            return false;
        }

        // Plain term: match everywhere
        if (item.itemId.toLowerCase().contains(query)) return true;

        String path = item.itemId.contains(":") ? item.itemId.split(":", 2)[1] : item.itemId;
        if (path.toLowerCase().contains(query)) return true;

        // Match against namespace (e.g. just "tfc" finds all TFC items)
        String itemNs = item.itemId.contains(":") ? item.itemId.split(":", 2)[0].toLowerCase() : "minecraft";
        if (itemNs.contains(query)) return true;

        Item mcItem = MinecraftCompat.getItem(new Identifier(item.itemId));
        if (mcItem != null) {
            String cnName = ChineseNames.get(mcItem.getTranslationKey());
            if (cnName != null && PinyinUtil.matches(cnName, query)) return true;
            String name = MinecraftCompat.getItemDisplayName(mcItem).toLowerCase();
            if (name.contains(query)) return true;
            if (PinyinUtil.matches(name, query)) return true;
        }

        return false;
    }

    /**
     * Search all registered items (for /c search command to find items to add).
     */
    public static List<ItemSearchResult> searchAllItems(String query) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();

        String q = query.toLowerCase().trim();
        List<ItemSearchResult> results = new ArrayList<>();

        for (Item item : MinecraftCompat.items()) {
            Identifier id = MinecraftCompat.getItemId(item);
            if (id == null) continue;

            String itemId = id.toString().toLowerCase();
            String name = MinecraftCompat.getItemDisplayName(item).toLowerCase();
            String cnName = ChineseNames.get(item.getTranslationKey());

            boolean matched = itemId.contains(q) || name.contains(q)
                || PinyinUtil.matches(name, q);

            if (!matched && cnName != null) {
                matched = cnName.toLowerCase().contains(q)
                    || PinyinUtil.matches(cnName, q);
            }

            if (matched) {
                results.add(new ItemSearchResult(id.toString(), cnName != null ? cnName : name));
                if (results.size() >= 20) break;
            }
        }
        return results;
    }

    public static final class ItemSearchResult {
        private final String itemId;
        private final String displayName;

        public ItemSearchResult(String itemId, String displayName) {
            this.itemId = itemId;
            this.displayName = displayName;
        }

        public String itemId() {
            return itemId;
        }

        public String displayName() {
            return displayName;
        }
    }
}
