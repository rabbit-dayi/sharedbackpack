package com.sharedbackpack.gui;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.backpack.SharedBackpack;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.commands.PinyinSearch;
import com.sharedbackpack.compat.MinecraftCompat;
import com.sharedbackpack.database.DatabaseManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

// Version-specific subclasses adapt ScreenHandler methods whose signatures changed after 1.16.
abstract class BackpackMenuBase extends ScreenHandler {
    private static final int ITEMS_PER_PAGE = 45;
    private static final int GUI_SLOTS = 54;
    private static final int GUI_SLOTS_UNLOAD = 45;
    private static final int PREV_SLOT=45,PAGE_SLOT=46,COUNT_SLOT=47,TEAM_SLOT=48;
    private static final int MOD_SLOT=49,SEARCH_SLOT=50,SORT_SLOT=51,UPGRADE_SLOT=52,NEXT_SLOT=53;
    private static final int INV_START_NORMAL=GUI_SLOTS, INV_END_NORMAL=GUI_SLOTS+36;
    private static final int INV_START_UNLOAD=GUI_SLOTS_UNLOAD, INV_END_UNLOAD=GUI_SLOTS_UNLOAD+36;

    private static final Map<String, Integer> PLAYER_SORT_PREF = new HashMap<>();

    private final SimpleInventory handler;
    private final String teamId;
    protected final ServerPlayerEntity player;
    private final int maxPages;
    private final String searchFilter;
    private final boolean unloadMode;
    private final boolean isBox;
    private final String boxOwner;
    private String modFilter;
    private MenuPage currentMenu;
    private final long[] sortClicks = new long[3];
    private int sortClickIdx;
    private int page;
    private int viewMaxPages = 1;
    private int displaySort = 0; // 0=default 1=count_desc 2=count_asc 3=time_desc 4=time_asc
    private boolean loading;
    private final Map<Integer, Integer> slotMap = new HashMap<>();

    protected BackpackMenuBase(int id, ServerPlayerEntity player, String teamId, int page, int maxPages,
                               String searchFilter, boolean unloadMode, boolean isBox, String boxOwner) {
        super(unloadMode ? ScreenHandlerType.GENERIC_9X5 : ScreenHandlerType.GENERIC_9X6, id);
        this.player = player; this.teamId = teamId; this.page = page; this.maxPages = maxPages;
        this.searchFilter = searchFilter; this.unloadMode = unloadMode;
        this.isBox = isBox; this.boxOwner = boxOwner;
        this.handler = new SimpleInventory(unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS);
        this.displaySort = PLAYER_SORT_PREF.getOrDefault(player.getUuidAsString(), 0);

        int total = unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS;
        for (int i = 0; i < total; i++) addSlot(new BpSlot(i, 8 + (i%9)*18, 18 + (i/9)*18));

        int rows = unloadMode ? 5 : 6;
        int invY = 18 + rows*18 + 13, hotbarY = invY + 3*18 + 4;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(MinecraftCompat.inventory(player), c+r*9+9, 8+c*18, invY+r*18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(MinecraftCompat.inventory(player), c, 8+c*18, hotbarY));
        loadPage();
    }

    // ===== DB access helpers =====
    private List<DatabaseManager.BackpackItem> dbItems() {
        if (unloadMode) return Collections.emptyList();
        return isBox ? SharedBackpackMod.database.getBoxItems(boxOwner, teamId)
                     : SharedBackpackMod.database.getItems(teamId);
    }
    private int dbTotal() {
        return isBox ? SharedBackpackMod.database.getTotalBoxItemCount(boxOwner, teamId)
                     : SharedBackpackMod.database.getTotalItemCount(teamId);
    }
    private boolean dbAdd(String iid, int cnt, String nbt, String name) {
        return isBox ? SharedBackpackMod.database.addBoxItem(boxOwner, teamId, iid, cnt, nbt, name)
                     : SharedBackpackMod.database.addItem(teamId, iid, cnt, nbt, name);
    }
    private boolean dbRemove(int slot, int count) {
        return isBox ? SharedBackpackMod.database.removeBoxItem(boxOwner, teamId, slot, count)
                     : SharedBackpackMod.database.removeItem(teamId, slot, count);
    }
    private boolean dbSet(int slot, String iid, int cnt, String nbt, String name) {
        return isBox ? SharedBackpackMod.database.setBoxItem(boxOwner, teamId, slot, iid, cnt, nbt, name)
                     : SharedBackpackMod.database.setItem(teamId, slot, iid, cnt, nbt, name);
    }
    private boolean dbUpgrade() {
        return isBox ? SharedBackpackMod.database.upgradeBoxPages(boxOwner, teamId, 1)
                : SharedBackpackMod.database.upgradePages(teamId, 1);
    }
    private void dbSortAll() {
        if (isBox) SharedBackpackMod.database.sortBoxItems(boxOwner, teamId);
        else SharedBackpackMod.database.sortItems(teamId);
    }
    private void dbSortPage(int pg) {
        int s = pg * ITEMS_PER_PAGE;
        if (isBox) SharedBackpackMod.database.sortPageBoxItems(boxOwner, teamId, s);
        else SharedBackpackMod.database.sortPageItems(teamId, s, s + ITEMS_PER_PAGE);
    }

    private boolean isControlSlot(int s) { return !unloadMode && s >= ITEMS_PER_PAGE && s < GUI_SLOTS; }
    private boolean inMenu() { return currentMenu != null; }
    // isMenuView: showing a selection menu (mod/box picker) - slots are UI buttons, no item interaction
    private boolean isMenuView() { return currentMenu != null; }
    // isAltView: items shown in non-positional order (search/mod-filter/sort) - slotMap is active
    private boolean isAltView() { return modFilter != null || (searchFilter != null && !searchFilter.isEmpty()) || displaySort != 0 || inMenu(); }
    private int realDbSlot(int s) {
        Integer m = slotMap.get(s);
        return m != null ? m : page * ITEMS_PER_PAGE + s;
    }
    private int invStart() { return unloadMode ? INV_START_UNLOAD : INV_START_NORMAL; }
    private int invEnd()   { return unloadMode ? INV_END_UNLOAD : INV_END_NORMAL; }

    // Store every original tag. CTM maps commonly use display, map, BlockEntityTag, and custom mod tags.
    static String nbtForStorage(ItemStack stack) {
        if (!MinecraftCompat.hasNbt(stack)) return null;
        return MinecraftCompat.getNbt(stack).copy().toString();
    }

    private List<DatabaseManager.BackpackItem> applySortOrder(List<DatabaseManager.BackpackItem> items) {
        List<DatabaseManager.BackpackItem> sorted = new ArrayList<>(items);
        switch (displaySort) {
            case 1:
                sorted.sort((a, b) -> Integer.compare(b.count, a.count));
                break;
            case 2:
                sorted.sort(Comparator.comparingInt(it -> it.count));
                break;
            case 3:
                sorted.sort((a, b) -> {
                String ta = a.lastModifiedTime != null ? a.lastModifiedTime : (a.placedTime != null ? a.placedTime : "");
                String tb = b.lastModifiedTime != null ? b.lastModifiedTime : (b.placedTime != null ? b.placedTime : "");
                return tb.compareTo(ta);
            });
                break;
            case 4:
                sorted.sort((a, b) -> {
                String ta = a.lastModifiedTime != null ? a.lastModifiedTime : (a.placedTime != null ? a.placedTime : "");
                String tb = b.lastModifiedTime != null ? b.lastModifiedTime : (b.placedTime != null ? b.placedTime : "");
                return ta.compareTo(tb);
            });
                break;
            default:
                break;
        }
        return sorted;
    }

    // ===== Page loading =====

    private void loadPage() {
        loading = true;
        try {
            int ts = unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS;
            for (int i = 0; i < ts; i++) handler.setStack(i, ItemStack.EMPTY);

            int startSlot = page * ITEMS_PER_PAGE;
            List<DatabaseManager.BackpackItem> items = dbItems();

            if (modFilter != null) {
                items = items.stream().filter(it -> {
                    String ns = it.itemId.contains(":")?it.itemId.split(":",2)[0]:"minecraft";
                    return ns.equals(modFilter);
                }).collect(java.util.stream.Collectors.toList());
            }
            if (searchFilter != null && !searchFilter.isEmpty()) {
                items = PinyinSearch.search(items, searchFilter);
            }
            if (displaySort != 0) {
                items = applySortOrder(items);
            }
            slotMap.clear();

            // compactView: items rendered as a compact paginated list using slotMap
            boolean compactView = (searchFilter != null && !searchFilter.isEmpty()) || modFilter != null || displaySort != 0;

            int loaded = 0;
            if (!inMenu()) {
                if (compactView) {
                    // Compact view: search / mod-filter / sort — build slotMap from filtered+sorted list
                    viewMaxPages = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
                    if (page >= viewMaxPages) page = viewMaxPages - 1;
                    int start = page * ITEMS_PER_PAGE;
                    int loc = 0;
                    for (int ii = start; ii < items.size() && loc < ITEMS_PER_PAGE; ii++) {
                        DatabaseManager.BackpackItem item = items.get(ii);
                        slotMap.put(loc, item.slot);
                        ItemStack st = SharedBackpack.toItemStack(item);
                        handler.setStack(loc, st);
                        loaded++; loc++;
                    }
                } else {
                    // Normal view: positional rendering by DB slot
                    viewMaxPages = maxPages;
                    for (DatabaseManager.BackpackItem item : items) {
                        int loc = item.slot - startSlot;
                        if (loc < 0) continue; if (loc >= ITEMS_PER_PAGE) break;
                        ItemStack st = SharedBackpack.toItemStack(item);
                        handler.setStack(loc, st);
                        loaded++;
                    }
                }
            }

            if (!unloadMode) {
                if (currentMenu != null) currentMenu.populate(handler, ITEMS_PER_PAGE);
                populateControlBar(loaded);
            }
        } finally { loading = false; }
    }

    private void populateControlBar(int loaded) {
        if (page > 0) {
            ItemStack p = new ItemStack(Items.ARROW); p.setCustomName(MinecraftCompat.text("\u00a76\u25c0 \u4e0a\u4e00\u9875"));
            handler.setStack(PREV_SLOT, p);
        }
        ItemStack pi = new ItemStack(Items.PAPER);
        pi.setCustomName(MinecraftCompat.text("\u00a7e\u7b2c " + (page+1) + " / " + viewMaxPages + " \u9875"));
        handler.setStack(PAGE_SLOT, pi);
        ItemStack ci = new ItemStack(Items.BOOK);
        ci.setCustomName(MinecraftCompat.text("\u00a7e\u7269\u54c1: " + dbTotal() + " \u4ef6"));
        handler.setStack(COUNT_SLOT, ci);

        String dn = teamId;
        if (isBox && teamId.contains(":")) dn = teamId.substring(teamId.lastIndexOf(':')+1);
        ItemStack ti = new ItemStack(isBox ? Items.CHEST : Items.NAME_TAG);
        ti.setCustomName(MinecraftCompat.text("\u00a7a" + (isBox ? "\u76d2\u5b50:"+dn+" \u00a77(\u70b9\u51fb\u8fd4\u56de\u961f\u4f0d)" : "\u961f\u4f0d: "+dn+" \u00a77(\u70b9\u51fb\u7ba1\u7406\u76d2\u5b50)")));
        handler.setStack(TEAM_SLOT, ti);

        if (currentMenu instanceof ModMenuPage) {
            ItemStack bk = new ItemStack(Items.BARRIER); bk.setCustomName(MinecraftCompat.text("\u00a7c\u8fd4\u56de\u5168\u90e8"));
            handler.setStack(MOD_SLOT, bk);
        } else if (currentMenu instanceof BoxMenuPage) {
            ItemStack bk = new ItemStack(Items.BARRIER); bk.setCustomName(MinecraftCompat.text("\u00a7c\u8fd4\u56de\u80cc\u5305"));
            handler.setStack(MOD_SLOT, bk);
        } else if (modFilter != null) {
            ItemStack ic = getModIcon(modFilter);
            ic.setCustomName(MinecraftCompat.text("\u00a76\u5206\u7c7b: " + modFilter + " \u00a77(\u70b9\u51fb\u5c55\u5f00\u83dc\u5355)"));
            handler.setStack(MOD_SLOT, ic);
        } else {
            ItemStack am = new ItemStack(Items.BOOKSHELF);
            am.setCustomName(MinecraftCompat.text("\u00a76\u5206\u7c7b: \u5168\u90e8 \u00a77(\u70b9\u51fb\u5c55\u5f00\u83dc\u5355)"));
            handler.setStack(MOD_SLOT, am);
        }

        // SEARCH_SLOT: sort-order toggle (shows search info too if active)
        String[] sortLabels = {"\u00a77\u9ed8\u8ba4\u987a\u5e8f", "\u00a7a\u6570\u91cf\u2193(\u591a\u2192\u5c11)", "\u00a7b\u6570\u91cf\u2191(\u5c11\u2192\u591a)", "\u00a7e\u6700\u65b0\u653e\u5165\u2193", "\u00a76\u6700\u65e9\u653e\u5165\u2191"};
        Item[] sortIcons = {Items.COMPARATOR, Items.GOLD_INGOT, Items.IRON_INGOT, Items.CLOCK, Items.FEATHER};
        if (searchFilter != null && !searchFilter.isEmpty()) {
            ItemStack si = new ItemStack(Items.COMPASS);
            String suf = displaySort != 0 ? " \u00a77[" + sortLabels[displaySort] + "\u00a77]" : "";
            si.setCustomName(MinecraftCompat.text("\u00a7d\u641c\u7d22: " + searchFilter + suf + " \u00a78(\u70b9\u51fb\u5207\u6362\u6392\u5e8f)"));
            handler.setStack(SEARCH_SLOT, si);
        } else {
            ItemStack si = new ItemStack(sortIcons[displaySort]);
            si.setCustomName(MinecraftCompat.text(sortLabels[displaySort] + " \u00a77(\u70b9\u51fb\u5207\u6362)"));
            handler.setStack(SEARCH_SLOT, si);
        }
        ItemStack sb = new ItemStack(Items.HOPPER);
        sb.setCustomName(MinecraftCompat.text("\u00a76\u6574\u7406\u7269\u54c1 \u00a77(\u5de6\u952e=\u5168\u90e8 \u53f3\u952e=\u5f53\u524d\u9875)"));
        handler.setStack(SORT_SLOT, sb);
        ItemStack ug = new ItemStack(Items.DIAMOND);
        ug.setCustomName(MinecraftCompat.text("\u00a7b\u5347\u7ea7\u80cc\u5305 [+1\u9875] \u00a77\u9700\u8981 1 \u94bb\u77f3"));
        handler.setStack(UPGRADE_SLOT, ug);
        if (page < viewMaxPages - 1) {
            ItemStack nx = new ItemStack(Items.ARROW);
            nx.setCustomName(MinecraftCompat.text("\u00a76\u4e0b\u4e00\u9875 \u25b6"));
            handler.setStack(NEXT_SLOT, nx);
        }
    }

    private ItemStack getModIcon(String ns) {
        for (DatabaseManager.BackpackItem it : dbItems()) {
            String n = it.itemId.contains(":")?it.itemId.split(":",2)[0]:"minecraft";
            if (n.equals(ns)) return SharedBackpack.toItemStack(it);
        }
        return new ItemStack(Items.BOOKSHELF);
    }

    private void refreshPage() { loadPage(); sendContentUpdates(); }
    private void navigateToPage(int np) { stashCarried(); page = np; refreshPage(); }
    private void cycleSortMode() { displaySort = (displaySort + 1) % 5; PLAYER_SORT_PREF.put(player.getUuidAsString(), displaySort); page = 0; refreshPage(); }

    private void stashCarried() {
        ItemStack c = getCursorStackCompat(); if (c.isEmpty()) return;
        int ms = unloadMode ? GUI_SLOTS_UNLOAD : ITEMS_PER_PAGE;
        for (int i = 0; i < ms && !c.isEmpty(); i++) {
            Slot s = getSlot(i); if (s == null || !s.hasStack()) continue;
            if (!tagsMatchExactly(c, s.getStack())) continue;
            int ca = Math.min(c.getCount(), 64 - s.getStack().getCount());
            if (ca <= 0) continue;
            ItemStack m = s.getStack().copy(); m.increment(ca); c.decrement(ca); s.setStack(m);
        }
        for (int i = 0; i < ms && !c.isEmpty(); i++) {
            Slot s = getSlot(i); if (s.hasStack()) continue;
            int tp = Math.min(c.getCount(), 64);
            ItemStack cp = c.copy(); cp.setCount(tp); c.decrement(tp); s.setStack(cp);
        }
        if (!c.isEmpty() && !MinecraftCompat.inventory(player).insertStack(c)) player.dropItem(c, false);
        setCursorStackCompat(ItemStack.EMPTY);
    }

    private void toggleModMenu() {
        stashCarried();
        currentMenu = (currentMenu instanceof ModMenuPage) ? null : new ModMenuPage();
        modFilter = null; refreshPage();
    }
    private void toggleBoxMenu() {
        stashCarried();
        currentMenu = (currentMenu instanceof BoxMenuPage) ? null : new BoxMenuPage();
        modFilter = null; refreshPage();
    }

    // ===== Click handling =====

    protected boolean handleSlotClick(int slotId, int button, SlotActionType clickType, PlayerEntity clickPlayer) {
        if (currentMenu != null && slotId >= 0 && slotId < ITEMS_PER_PAGE) {
            currentMenu.onClick(slotId, this);
            return true;
        }

        if (!unloadMode && slotId >= 0 && slotId < GUI_SLOTS && isControlSlot(slotId)) {
            if (inMenu() && slotId == MOD_SLOT) { stashCarried(); currentMenu = null; refreshPage(); return true; }
            if (!inMenu()) {
                if (slotId == PREV_SLOT && page > 0) { navigateToPage(page-1); return true; }
                if (slotId == NEXT_SLOT && page < viewMaxPages-1) { navigateToPage(page+1); return true; }
                if (slotId == SEARCH_SLOT) { cycleSortMode(); return true; }
                if (slotId == UPGRADE_SLOT) { doUpgrade(); return true; }
                if (slotId == SORT_SLOT) { handleSort(button); return true; }
                if (slotId == MOD_SLOT) { toggleModMenu(); return true; }
                if (slotId == TEAM_SLOT && isBox) {
                    stashCarried(); player.server.execute(() -> openTeam(player, searchFilter)); return true;
                }
                if (slotId == TEAM_SLOT && !isBox) { toggleBoxMenu(); return true; }
            }
            return true;
        }

        int max = (unloadMode||inMenu())?GUI_SLOTS_UNLOAD:ITEMS_PER_PAGE;
        if (slotId >= 0 && slotId < max && clickType == SlotActionType.PICKUP) {
            Slot s = getSlot(slotId);
            ItemStack cur = getCursorStackCompat();
            // In menu view (mod/box picker), item slots are UI - no interaction
            if (isMenuView()) return true;
            if (s != null && s.hasStack() && s.canTakeItems(clickPlayer)) {
                if (button == 0 && cur.isEmpty()) {
                    ItemStack ex = s.takeStack(1);
                    if (!ex.isEmpty()) {
                        afterTake(slotId, ex);
                        setCursorStackCompat(ex);
                    }
                    return true;
                }
                if (button == 1 && cur.isEmpty()) {
                    int mx = s.getStack().getMaxCount();
                    ItemStack ex = s.takeStack(mx);
                    if (!ex.isEmpty()) {
                        afterTake(slotId, ex);
                        setCursorStackCompat(ex);
                    }
                    return true;
                }
            }
            // Placing carried item: in alt view (search/mod-filter), use dbAdd instead of slot-based set
            if (!cur.isEmpty() && isAltView()) {
                String iid = MinecraftCompat.getItemId(cur.getItem()).toString();
                String nbt = nbtForStorage(cur);
                if (dbAdd(iid, cur.getCount(), nbt, player.getEntityName())) {
                    setCursorStackCompat(ItemStack.EMPTY);
                    refreshPage();
                }
                return true;
            }
        }
        return false;
    }

    private void afterTake(int slotId, ItemStack stack) {
        if (!isControlSlot(slotId)) {
            dbRemove(realDbSlot(slotId), stack.getCount());
        }
    }

    private void handleSort(int button) {
        if (button == 1) {
            long now = System.currentTimeMillis();
            sortClicks[sortClickIdx%3] = now; sortClickIdx++;
            boolean t3 = sortClickIdx >= 3 && (now - sortClicks[(sortClickIdx-3)%3]) < 1500;
            if (t3) { dbSortAll(); player.sendMessage(MinecraftCompat.text("\u00a7a\u5168\u80cc\u5305\u5df2\u6574\u7406\uff01"), false); }
            else { dbSortPage(page); player.sendMessage(MinecraftCompat.text("\u00a7a\u5f53\u524d\u9875\u5df2\u6574\u7406\uff01"), false); }
        } else {
            dbSortAll();
            player.sendMessage(MinecraftCompat.text("\u00a7a\u7269\u54c1\u5df2\u6574\u7406\uff01"), false);
        }
        refreshPage();
    }

    private void doUpgrade() {
        for (int i = 0; i < MinecraftCompat.inventory(player).size(); i++) {
            ItemStack s = MinecraftCompat.inventory(player).getStack(i);
            if (s.getItem()==Items.DIAMOND && s.getCount()>=1) {
                s.decrement(1); dbUpgrade();
                player.sendMessage(MinecraftCompat.text("\u00a7a\u80cc\u5305\u5df2\u5347\u7ea7\uff01"), false);
                stashCarried();
                player.server.execute(() -> openForPlayer(player, searchFilter, page, false, isBox, boxOwner));
                return;
            }
        }
        player.sendMessage(MinecraftCompat.text("\u00a7c\u9700\u8981 1 \u4e2a\u94bb\u77f3\u6765\u5347\u7ea7\u80cc\u5305\uff01"), false);
    }

    private void doCreateBox() {
        String owner = TeamResolver.resolvePrimaryTeam(player);
        List<String> exist = SharedBackpackMod.database.listBoxes(owner);
        int n = exist.size()+1; String name;
        do { name = "\u76d2\u5b50" + n; n++; } while (exist.contains(name));
        SharedBackpackMod.database.createBox(owner, name);
        player.sendMessage(MinecraftCompat.text("\u00a7a\u5df2\u521b\u5efa\u76d2\u5b50: " + name), false);
        currentMenu = null; stashCarried();
        String fn = name, fo = owner;
        player.server.execute(() -> openForPlayer(player, searchFilter, 0, false, true, fo+":"+fn));
    }

    // ===== Quick move =====

    static boolean tagsMatchExactly(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        NbtCompound t1 = MinecraftCompat.hasNbt(a) ? MinecraftCompat.getNbt(a) : null;
        NbtCompound t2 = MinecraftCompat.hasNbt(b) ? MinecraftCompat.getNbt(b) : null;
        return (t1==null&&t2==null)||(t1!=null&&t1.equals(t2));
    }

    protected ItemStack quickMoveInternal(PlayerEntity mp, int index) {
        Slot s = getSlot(index);
        if (s == null || !s.hasStack()) return ItemStack.EMPTY;
        if (!unloadMode && !inMenu() && isControlSlot(index)) return ItemStack.EMPTY;
        if (inMenu() && isControlSlot(index)) return ItemStack.EMPTY;

        ItemStack src = s.getStack().copy();
        ItemStack orig = src.copy();

        if (index < (unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS)) {
            // Shift-click from backpack -> player inventory without altering item NBT.
            ItemStack toMove = src.copy();
            if (!insertItem(toMove, invStart(), invEnd(), true)) return ItemStack.EMPTY;
            // How many were moved?
            int moved = src.getCount() - toMove.getCount();
            if (moved <= 0) return ItemStack.EMPTY;
            // Remove from DB
            int dbSlot = realDbSlot(index);
            dbRemove(dbSlot, moved);
            // Update handler
            if (moved >= src.getCount()) {
                handler.setStack(index, ItemStack.EMPTY);
            } else {
                ItemStack rem = src.copy(); rem.setCount(src.getCount() - moved);
                handler.setStack(index, rem);
            }
        } else {
            // Shift-click from player inventory -> backpack (works in all views)
            String iid = MinecraftCompat.getItemId(src.getItem()).toString();
            String nbt = nbtForStorage(src);
            boolean added = dbAdd(iid, src.getCount(), nbt, player.getEntityName());
            if (!added) return ItemStack.EMPTY;
            s.setStack(ItemStack.EMPTY);
            refreshPage();
            return orig;
        }
        // Sync source slot for backpack->inventory direction
        if (handler.getStack(index).isEmpty()) {
            s.setStack(ItemStack.EMPTY);
        } else {
            // force client sync
            sendContentUpdates();
        }
        refreshPage();
        return orig;
    }

    protected abstract ItemStack getCursorStackCompat();

    protected abstract void setCursorStackCompat(ItemStack stack);

    @Override public boolean canUse(PlayerEntity p) { return true; }

    // ===== Static factory =====

    public static void openTeam(ServerPlayerEntity player, String search) { openForPlayer(player, search, 0, false, false, null); }
    public static void openForPlayer(ServerPlayerEntity player, String search) { openForPlayer(player, search, 0, false, false, null); }
    public static void openForPlayer(ServerPlayerEntity player, String search, int page, boolean unload, boolean isBox, String boxOwner) {
        String owner = TeamResolver.resolvePrimaryTeam(player);
        String tid = owner;
        String storageOwner = null;
        if (isBox) {
            String target = boxOwner != null ? boxOwner : owner;
            int separator = target.indexOf(':');
            if (separator > 0) {
                storageOwner = target.substring(0, separator);
                tid = target.substring(separator + 1);
            } else {
                storageOwner = owner;
                tid = target;
            }
        }
        int mp = isBox ? SharedBackpackMod.database.getBoxMaxPages(storageOwner, tid) : SharedBackpackMod.database.getMaxPages(tid);
        int cp = Math.max(0, Math.min(page, mp-1));
        String dn = tid; if (isBox && tid.contains(":")) dn = tid.substring(tid.lastIndexOf(':')+1);
        String title = unload ? "\u5378\u8d27" : (isBox ? "\u76d2\u5b50: "+dn : "\u5171\u4eab\u80cc\u5305 - "+tid);
        if (search != null && !search.isEmpty()) title += " \u641c\u7d22:"+search;
        final String ft = title;
        final String menuId = tid;
        final int fp = cp;
        final int menuPages = mp;
        final boolean fw = unload;
        final boolean fb = isBox;
        final String fo = storageOwner;
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                return new BackpackMenu(id, player, menuId, fp, menuPages, search, fw, fb, fo);
            }
            public Text getDisplayName() { return MinecraftCompat.text(ft); }
        });
    }

    // ===== Menu System =====

    interface MenuPage {
        void populate(SimpleInventory handler, int maxSlots);
        String onClick(int slotId, BackpackMenuBase menu);
    }

    class ModMenuPage implements MenuPage {
        final Map<Integer, String> map = new HashMap<>();
        public void populate(SimpleInventory h, int max) {
            map.clear();
            List<DatabaseManager.BackpackItem> all = dbItems();
            Map<String, DatabaseManager.BackpackItem> uniq = new LinkedHashMap<String, DatabaseManager.BackpackItem>();
            for (DatabaseManager.BackpackItem it : all) { String ns = it.itemId.contains(":")?it.itemId.split(":",2)[0]:"minecraft"; uniq.putIfAbsent(ns, it); }
            int i = 0;
            for (Map.Entry<String, DatabaseManager.BackpackItem> e : uniq.entrySet()) {
                if (i >= max) break;
                ItemStack icon = SharedBackpack.toItemStack(e.getValue());
                long cnt = all.stream().filter(it -> (it.itemId.contains(":")?it.itemId.split(":",2)[0]:"minecraft").equals(e.getKey())).mapToInt(it -> it.count).sum();
                icon.setCustomName(MinecraftCompat.text("\u00a76"+e.getKey()+" \u00a77("+cnt+" \u4ef6)"));
                map.put(i, e.getKey()); h.setStack(i++, icon);
            }
        }
        public String onClick(int slotId, BackpackMenuBase m) {
            String ns = map.get(slotId); if (ns == null) return null;
            m.modFilter = ns; m.currentMenu = null; m.page = 0; m.refreshPage(); return "FILTER";
        }
    }

    class BoxMenuPage implements MenuPage {
        final Map<Integer, String> map = new HashMap<>();
        public void populate(SimpleInventory h, int max) {
            map.clear();
            List<String> list = SharedBackpackMod.database.listBoxes(TeamResolver.resolvePrimaryTeam(player));
            int i = 0;
            for (String name : list) {
                if (i >= max-1) break;
                ItemStack icon = new ItemStack(Items.CHEST);
                icon.setCustomName(MinecraftCompat.text("\u00a76"+name));
                map.put(i, name); h.setStack(i++, icon);
            }
            if (i < max) {
                ItemStack nb = new ItemStack(Items.CRAFTING_TABLE);
                nb.setCustomName(MinecraftCompat.text("\u00a7a+ \u65b0\u5efa\u76d2\u5b50"));
                map.put(i, "__new__"); h.setStack(i, nb);
            }
        }
        public String onClick(int slotId, BackpackMenuBase m) {
            String name = map.get(slotId); if (name == null) return null;
            if ("__new__".equals(name)) { m.doCreateBox(); return "CREATE"; }
            String team = TeamResolver.resolvePrimaryTeam(player);
            m.stashCarried();
            player.server.execute(() -> openForPlayer(player, searchFilter, 0, false, true, team+":"+name));
            return "OPEN";
        }
    }

    // ===== Slot / Container =====

    class BpSlot extends Slot {
        final int ls;
        BpSlot(int ls, int x, int y) { super(handler, ls, x, y); this.ls = ls; }
        public boolean canTakeItems(PlayerEntity p) { return !isControlSlot(ls); }
        public boolean canInsert(ItemStack s) { return !isControlSlot(ls) && !isMenuView(); }
        public void setStack(ItemStack s) {
            if (!isControlSlot(ls)) {
                if (!s.isEmpty()) {
                    String iid = MinecraftCompat.getItemId(s.getItem()).toString();
                    String n = nbtForStorage(s);
                    dbSet(realDbSlot(ls), iid, s.getCount(), n, player.getEntityName());
                } else { dbRemove(realDbSlot(ls), 99999); }
            }
            super.setStack(s);
        }
    }
}
