package com.sharedbackpack.client;

import com.sharedbackpack.client.gui.DashboardPlatform;
import me.towdium.pinin.PinIn;
import net.minecraft.item.ItemStack;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Client-thread catalog and local view. Item identities always come from the server snapshot. */
public final class DashboardModel {
    public static final int PAGE_SIZE = 45;
    private static final PinIn PININ = new PinIn();
    static { PININ.config().fZh2Z(true).fCh2C(true).fSh2S(true).commit(); }

    public static final class Entry {
        public final int key;
        public final ItemStack icon;
        public final long count;
        public final String modified;
        public final String countLabel;
        public final String id;
        private String name;
        private String search;
        public Entry(int key, ItemStack icon, long count, String modified) {
            this.key = key;
            this.icon = icon;
            this.count = count;
            this.modified = modified;
            this.countLabel = com.sharedbackpack.client.gui.DashboardView.compact(count);
            this.id = DashboardPlatform.itemId(icon);
        }
        private void localize() {
            name = icon.getName().getString();
            search = (name + " " + id).toLowerCase(Locale.ROOT);
        }
    }

    public static final class Snapshot {
        public final int syncId, revision, maxPages, usedSlots, savedPage, savedSort;
        public final String storage;
        public final List<String> boxes;
        public final List<Entry> entries;
        public Snapshot(int syncId, int revision, String storage, int maxPages, int usedSlots,
                        int savedPage, int savedSort, List<String> boxes, List<Entry> entries) {
            this.syncId = syncId;
            this.revision = revision;
            this.storage = storage;
            this.maxPages = maxPages;
            this.usedSlots = usedSlots;
            this.savedPage = savedPage;
            this.savedSort = savedSort;
            this.boxes = Collections.unmodifiableList(boxes);
            this.entries = Collections.unmodifiableList(entries);
        }
    }

    private Snapshot snapshot;
    private final List<Entry> visible = new ArrayList<>();
    private String query = "";
    private String language = "";
    private String namespace = "";
    private int page;
    private int rememberedPage;
    private int sort;
    private long total;

    public Snapshot snapshot() { return snapshot; }
    public String query() { return query; }
    public int page() { return page; }
    public int sort() { return sort; }
    public int rememberedPage() { return rememberedPage; }
    public int pages() { return Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    public long total() { return total; }
    private boolean unfiltered() { return query.trim().isEmpty() && namespace.isEmpty(); }

    public String namespace() { return namespace; }
    public void cycleNamespace() {
        List<String> namespaces = new ArrayList<>();
        namespaces.add("");
        for (Entry entry : snapshot.entries) {
            String id = entry.id;
            String value = id.substring(0, id.indexOf(':'));
            if (!namespaces.contains(value)) namespaces.add(value);
        }
        namespace = namespaces.get((Math.max(0, namespaces.indexOf(namespace)) + 1) % namespaces.size());
        page = namespace.isEmpty() ? rememberedPage : 0;
        rebuild();
    }

    public void accept(Snapshot next) {
        boolean changedStorage = snapshot == null || !snapshot.storage.equals(next.storage);
        snapshot = next;
        if (changedStorage) {
            rememberedPage = Math.max(0, next.savedPage);
            sort = Math.max(0, Math.min(5, next.savedSort));
            page = rememberedPage;
        }
        total = 0;
        for (Entry entry : next.entries) {
            entry.localize();
            total = Long.MAX_VALUE - total < entry.count ? Long.MAX_VALUE : total + entry.count;
        }
        rebuild();
    }

    /** Language/resource changes invalidate localized names, never NBT identity. */
    public void relocalize(String token) {
        language = token;
        if (snapshot != null) {
            for (Entry entry : snapshot.entries) entry.localize();
            rebuild();
        }
    }

    public void search(String value) {
        if (query.equals(value)) return;
        boolean clearing = value.trim().isEmpty();
        query = value;
        page = clearing && namespace.isEmpty() ? rememberedPage : 0;
        rebuild();
    }

    public void sort(int value) {
        sort = value;
        rebuild();
    }

    public void page(int value) {
        page = Math.max(0, Math.min(pages() - 1, value));
        if (unfiltered()) rememberedPage = page;
    }

    public Entry entry(int cell) {
        int index = page * PAGE_SIZE + cell;
        return index >= 0 && index < visible.size() ? visible.get(index) : null;
    }

    private void rebuild() {
        visible.clear();
        if (snapshot == null) return;
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        for (Entry entry : snapshot.entries) {
            if (!namespace.isEmpty() && !entry.id.startsWith(namespace + ":")) continue;
            boolean matches = true;
            for (String word : words) {
                if (!entry.search.contains(word) && !PININ.contains(entry.search, word)) {
                    matches = false;
                    break;
                }
            }
            if (matches) visible.add(entry);
        }
        Comparator<Entry> comparator = null;
        switch (sort) {
            case 1: comparator = Comparator.comparingLong((Entry e) -> e.count).reversed(); break;
            case 2: comparator = Comparator.comparingLong(e -> e.count); break;
            case 3: comparator = Comparator.comparing((Entry e) -> e.modified).reversed(); break;
            case 4: comparator = Comparator.comparing(e -> e.modified); break;
            case 5:
                Collator collator = Collator.getInstance(Locale.forLanguageTag(language.replace('_', '-')));
                comparator = (a, b) -> collator.compare(a.name, b.name);
                break;
            default: break;
        }
        if (comparator != null) visible.sort(comparator.thenComparingInt(e -> e.key));
        page = Math.max(0, Math.min(pages() - 1, unfiltered() ? rememberedPage : page));
    }
}
