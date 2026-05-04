package com.sharedbackpack.gui;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.backpack.SharedBackpack;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.commands.PinyinSearch;
import com.sharedbackpack.database.DatabaseManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;

import java.util.*;

public class BackpackMenu extends AbstractContainerMenu {
    private static final int ITEMS_PER_PAGE = 45;
    private static final int GUI_SLOTS = 54;
    private static final int GUI_SLOTS_UNLOAD = 45;
    private static final int PREV_SLOT=45,PAGE_SLOT=46,COUNT_SLOT=47,TEAM_SLOT=48;
    private static final int MOD_SLOT=49,SEARCH_SLOT=50,SORT_SLOT=51,UPGRADE_SLOT=52,NEXT_SLOT=53;
    private static final int INV_START_NORMAL=GUI_SLOTS, INV_END_NORMAL=GUI_SLOTS+36;
    private static final int INV_START_UNLOAD=GUI_SLOTS_UNLOAD, INV_END_UNLOAD=GUI_SLOTS_UNLOAD+36;

    private final ItemStackHandler handler;
    private final String teamId;
    private final ServerPlayer player;
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
    private boolean loading;
    private final Map<Integer, Integer> slotMap = new HashMap<>();
    // slotTotalCount: gui slot index -> total stacked count (for stacked display)
    private final Map<Integer, Integer> slotTotalCount = new HashMap<>();

    public BackpackMenu(int id, ServerPlayer player, String teamId, int page, int maxPages,
                        String searchFilter, boolean unloadMode, boolean isBox, String boxOwner) {
        super(unloadMode ? MenuType.GENERIC_9x5 : MenuType.GENERIC_9x6, id);
        this.player = player; this.teamId = teamId; this.page = page; this.maxPages = maxPages;
        this.searchFilter = searchFilter; this.unloadMode = unloadMode;
        this.isBox = isBox; this.boxOwner = boxOwner;
        this.handler = new ItemStackHandler(unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS);

        int total = unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS;
        for (int i = 0; i < total; i++) addSlot(new BpSlot(i, 8 + (i%9)*18, 18 + (i/9)*18));

        int rows = unloadMode ? 5 : 6;
        int invY = 18 + rows*18 + 13, hotbarY = invY + 3*18 + 4;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(player.getInventory(), c+r*9+9, 8+c*18, invY+r*18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(player.getInventory(), c, 8+c*18, hotbarY));
        loadPage();
    }

    // ===== DB access helpers =====
    private List<DatabaseManager.BackpackItem> dbItems() {
        if (unloadMode) return List.of();
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
    private boolean dbUpgrade() { return SharedBackpackMod.database.upgradePages(teamId, 1); }
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
    // isAltView: items shown in non-positional order (search or mod-filter) - slotMap is active
    private boolean isAltView() { return modFilter != null || (searchFilter != null && !searchFilter.isEmpty()) || inMenu(); }
    private int realDbSlot(int s) {
        Integer m = slotMap.get(s);
        return m != null ? m : page * ITEMS_PER_PAGE + s;
    }
    private int invStart() { return unloadMode ? INV_START_UNLOAD : INV_START_NORMAL; }
    private int invEnd()   { return unloadMode ? INV_END_UNLOAD : INV_END_NORMAL; }

    static void stripDisplay(ItemStack stack) {
        CompoundTag t = stack.getTag(); if (t == null) return;
        boolean d = false;
        if (t.contains("display")) { CompoundTag dp = t.getCompound("display");
            if (dp.contains("Lore")) { dp.remove("Lore"); d = true; }
            if (dp.contains("Name")) { dp.remove("Name"); d = true; }
            if (dp.isEmpty()) { t.remove("display"); d = true; } }
        if (d && t.isEmpty()) stack.setTag(null);
    }

    // Strip display metadata (Name + Lore) from item NBT for DB storage
    static String nbtForStorage(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag copy = stack.getTag().copy();
        if (copy.contains("display")) {
            CompoundTag dp = copy.getCompound("display");
            dp.remove("Name"); dp.remove("Lore");
            if (dp.isEmpty()) copy.remove("display");
        }
        return copy.isEmpty() ? null : copy.toString();
    }

    static void setMeta(ItemStack stack, DatabaseManager.BackpackItem item) {
        if (item.placedBy == null) return;
        Component name = stack.getHoverName();
        String ns = name.getString();
        int bi = ns.lastIndexOf(" \u00a77[");
        if (bi > 0) name = Component.literal(ns.substring(0, bi));
        String meta = ChatFormatting.GRAY + "[" + item.placedBy;
        if (item.placedTime != null) {
            String t = item.placedTime;
            if (t.length() >= 16) t = t.substring(11, 16);
            meta += " " + t;
        }
        meta += " x" + item.placedCount + "]";
        stack.setHoverName(name.copy().append(Component.literal(" \u00a77" + meta)));
    }

    // ===== Page loading =====

    private void loadPage() {
        loading = true;
        try {
            int ts = unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS;
            for (int i = 0; i < ts; i++) handler.setStackInSlot(i, ItemStack.EMPTY);

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
                slotMap.clear(); slotTotalCount.clear();
                List<DatabaseManager.BackpackItem> c = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    var it = items.get(i);
                    slotMap.put(i, it.slot);
                    c.add(new DatabaseManager.BackpackItem(i, it.itemId, it.count, it.nbt,
                        it.placedBy, it.placedTime, it.placedCount, it.lastModifiedBy, it.lastModifiedTime));
                }
                items = c;
            } else {
                slotMap.clear(); slotTotalCount.clear();
            }

            int loaded = 0;
            if (!inMenu()) {
                if (searchFilter != null && !searchFilter.isEmpty()) {
                    // Search view: all results on one page
                    viewMaxPages = 1;
                    for (var item : items) {
                        int loc = item.slot;
                        if (loc >= 0 && loc < ITEMS_PER_PAGE) {
                            ItemStack st = SharedBackpack.toItemStack(item);
                            setMeta(st, item);
                            handler.setStackInSlot(loc, st);
                            loaded++;
                        }
                    }
                } else if (modFilter != null) {
                    // Mod-filter view: paginated compact list
                    slotMap.clear(); slotTotalCount.clear();
                    viewMaxPages = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
                    if (page >= viewMaxPages) page = viewMaxPages - 1;
                    // Group by key for stacked display
                    Map<String, Integer> totalByKey = new LinkedHashMap<>();
                    for (var item : items) {
                        String key = item.itemId + "\0" + (item.nbt != null ? item.nbt : "");
                        totalByKey.merge(key, item.count, Integer::sum);
                    }
                    int start = page * ITEMS_PER_PAGE;
                    int loc = 0;
                    for (int ii = start; ii < items.size() && loc < ITEMS_PER_PAGE; ii++) {
                        var item = items.get(ii);
                        slotMap.put(loc, item.slot);
                        String key = item.itemId + "\0" + (item.nbt != null ? item.nbt : "");
                        int total = totalByKey.getOrDefault(key, item.count);
                        ItemStack st = SharedBackpack.toItemStack(item);
                        setMeta(st, item);
                        if (total > item.count) {
                            slotTotalCount.put(loc, total);
                            setStackedCountLore(st, total);
                        }
                        handler.setStackInSlot(loc, st);
                        loaded++; loc++;
                    }
                } else {
                    // Normal view: aggregate same items for stacked display
                    viewMaxPages = maxPages;
                    Map<String, Integer> totalByKey = new LinkedHashMap<>();
                    for (var item : items) {
                        int loc = item.slot - startSlot;
                        if (loc < 0) continue; if (loc >= ITEMS_PER_PAGE) break;
                        String key = item.itemId + "\0" + (item.nbt != null ? item.nbt : "");
                        totalByKey.merge(key, item.count, Integer::sum);
                    }
                    for (var item : items) {
                        int loc = item.slot - startSlot;
                        if (loc < 0) continue; if (loc >= ITEMS_PER_PAGE) break;
                        String key = item.itemId + "\0" + (item.nbt != null ? item.nbt : "");
                        int total = totalByKey.getOrDefault(key, item.count);
                        ItemStack st = SharedBackpack.toItemStack(item);
                        setMeta(st, item);
                        if (total > item.count) {
                            slotTotalCount.put(loc, total);
                            setStackedCountLore(st, total);
                        }
                        handler.setStackInSlot(loc, st);
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

    static void setStackedCountLore(ItemStack stack, int total) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag display = tag.contains("display") ? tag.getCompound("display") : new CompoundTag();
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        String loreJson = "{\"text\":\"\u00a77\u5171 " + total + " \u4e2a\",\"italic\":false}";
        lore.add(net.minecraft.nbt.StringTag.valueOf(loreJson));
        display.put("Lore", lore);
        tag.put("display", display);
    }

    private void populateControlBar(int loaded) {
        if (page > 0) {
            ItemStack p = new ItemStack(Items.ARROW); p.setHoverName(Component.literal("\u00a76\u25c0 \u4e0a\u4e00\u9875"));
            handler.setStackInSlot(PREV_SLOT, p);
        }
        ItemStack pi = new ItemStack(Items.PAPER);
        pi.setHoverName(Component.literal("\u00a7e\u7b2c " + (page+1) + " / " + viewMaxPages + " \u9875"));
        handler.setStackInSlot(PAGE_SLOT, pi);
        ItemStack ci = new ItemStack(Items.BOOK);
        ci.setHoverName(Component.literal("\u00a7e\u7269\u54c1: " + dbTotal() + " \u4ef6"));
        handler.setStackInSlot(COUNT_SLOT, ci);

        String dn = teamId;
        if (isBox && teamId.contains(":")) dn = teamId.substring(teamId.lastIndexOf(':')+1);
        ItemStack ti = new ItemStack(isBox ? Items.CHEST : Items.NAME_TAG);
        ti.setHoverName(Component.literal("\u00a7a" + (isBox ? "\u76d2\u5b50:"+dn+" \u00a77(\u70b9\u51fb\u8fd4\u56de\u961f\u4f0d)" : "\u961f\u4f0d: "+dn+" \u00a77(\u70b9\u51fb\u7ba1\u7406\u76d2\u5b50)")));
        handler.setStackInSlot(TEAM_SLOT, ti);

        if (currentMenu instanceof ModMenuPage) {
            ItemStack bk = new ItemStack(Items.BARRIER); bk.setHoverName(Component.literal("\u00a7c\u8fd4\u56de\u5168\u90e8"));
            handler.setStackInSlot(MOD_SLOT, bk);
        } else if (currentMenu instanceof BoxMenuPage) {
            ItemStack bk = new ItemStack(Items.BARRIER); bk.setHoverName(Component.literal("\u00a7c\u8fd4\u56de\u80cc\u5305"));
            handler.setStackInSlot(MOD_SLOT, bk);
        } else if (modFilter != null) {
            ItemStack ic = getModIcon(modFilter);
            ic.setHoverName(Component.literal("\u00a76\u5206\u7c7b: " + modFilter + " \u00a77(\u70b9\u51fb\u5c55\u5f00\u83dc\u5355)"));
            handler.setStackInSlot(MOD_SLOT, ic);
        } else {
            ItemStack am = new ItemStack(Items.BOOKSHELF);
            am.setHoverName(Component.literal("\u00a76\u5206\u7c7b: \u5168\u90e8 \u00a77(\u70b9\u51fb\u5c55\u5f00\u83dc\u5355)"));
            handler.setStackInSlot(MOD_SLOT, am);
        }

        if (searchFilter != null && !searchFilter.isEmpty()) {
            ItemStack si = new ItemStack(Items.COMPASS);
            si.setHoverName(Component.literal("\u00a7d\u641c\u7d22: " + searchFilter));
            handler.setStackInSlot(SEARCH_SLOT, si);
        }
        ItemStack sb = new ItemStack(Items.HOPPER);
        sb.setHoverName(Component.literal("\u00a76\u6574\u7406\u7269\u54c1 \u00a77(\u5de6\u952e=\u5168\u90e8 \u53f3\u952e=\u5f53\u524d\u9875)"));
        handler.setStackInSlot(SORT_SLOT, sb);
        ItemStack ug = new ItemStack(Items.DIAMOND);
        ug.setHoverName(Component.literal("\u00a7b\u5347\u7ea7\u80cc\u5305 [+1\u9875] \u00a77\u9700\u8981 1 \u94bb\u77f3"));
        handler.setStackInSlot(UPGRADE_SLOT, ug);
        if (page < viewMaxPages - 1) {
            ItemStack nx = new ItemStack(Items.ARROW);
            nx.setHoverName(Component.literal("\u00a76\u4e0b\u4e00\u9875 \u25b6"));
            handler.setStackInSlot(NEXT_SLOT, nx);
        }
    }

    private ItemStack getModIcon(String ns) {
        for (var it : dbItems()) {
            String n = it.itemId.contains(":")?it.itemId.split(":",2)[0]:"minecraft";
            if (n.equals(ns)) return SharedBackpack.toItemStack(it);
        }
        return new ItemStack(Items.BOOKSHELF);
    }

    private void refreshPage() { loadPage(); broadcastChanges(); }
    private void navigateToPage(int np) { stashCarried(); page = np; refreshPage(); }

    private void stashCarried() {
        ItemStack c = getCarried(); if (c.isEmpty()) return;
        int ms = unloadMode ? GUI_SLOTS_UNLOAD : ITEMS_PER_PAGE;
        for (int i = 0; i < ms && !c.isEmpty(); i++) {
            Slot s = getSlot(i); if (s == null || !s.hasItem()) continue;
            if (!tagsMatchIgnore(c, s.getItem())) continue;
            int ca = Math.min(c.getCount(), 64 - s.getItem().getCount());
            if (ca <= 0) continue;
            ItemStack m = s.getItem().copy(); m.grow(ca); c.shrink(ca); s.set(m);
        }
        for (int i = 0; i < ms && !c.isEmpty(); i++) {
            Slot s = getSlot(i); if (s.hasItem()) continue;
            int tp = Math.min(c.getCount(), 64);
            ItemStack cp = c.copy(); cp.setCount(tp); c.shrink(tp); s.set(cp);
        }
        if (!c.isEmpty() && !player.getInventory().add(c)) player.drop(c, false);
        setCarried(ItemStack.EMPTY);
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

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clickPlayer) {
        if (currentMenu != null && slotId >= 0 && slotId < ITEMS_PER_PAGE) {
            currentMenu.onClick(slotId, this);
            return;
        }

        if (!unloadMode && slotId >= 0 && slotId < GUI_SLOTS && isControlSlot(slotId)) {
            if (inMenu() && slotId == MOD_SLOT) { stashCarried(); currentMenu = null; refreshPage(); return; }
            if (!inMenu()) {
                if (slotId == PREV_SLOT && page > 0) { navigateToPage(page-1); return; }
                if (slotId == NEXT_SLOT && page < viewMaxPages-1) { navigateToPage(page+1); return; }
                if (slotId == UPGRADE_SLOT) { doUpgrade(); return; }
                if (slotId == SORT_SLOT) { handleSort(button); return; }
                if (slotId == MOD_SLOT) { toggleModMenu(); return; }
                if (slotId == TEAM_SLOT && isBox) {
                    stashCarried(); player.getServer().execute(() -> openTeam(player, searchFilter)); return;
                }
                if (slotId == TEAM_SLOT && !isBox) { toggleBoxMenu(); return; }
            }
            return;
        }

        int max = (unloadMode||inMenu())?GUI_SLOTS_UNLOAD:ITEMS_PER_PAGE;
        if (slotId >= 0 && slotId < max && clickType == ClickType.PICKUP) {
            Slot s = getSlot(slotId);
            ItemStack cur = getCarried();
            // In menu view (mod/box picker), item slots are UI - no interaction
            if (isMenuView()) return;
            if (s != null && s.hasItem() && s.mayPickup(clickPlayer)) {
                if (button == 0 && cur.isEmpty()) {
                    ItemStack ex = s.safeTake(1, 64, clickPlayer);
                    if (!ex.isEmpty()) setCarried(ex);
                    return;
                }
                if (button == 1 && cur.isEmpty()) {
                    int mx = s.getItem().getMaxStackSize();
                    ItemStack ex = s.safeTake(mx, mx, clickPlayer);
                    if (!ex.isEmpty()) setCarried(ex);
                    return;
                }
            }
            // Placing carried item: in alt view (search/mod-filter), use dbAdd instead of slot-based set
            if (!cur.isEmpty() && isAltView()) {
                String iid = BuiltInRegistries.ITEM.getKey(cur.getItem()).toString();
                String nbt = nbtForStorage(cur);
                if (dbAdd(iid, cur.getCount(), nbt, player.getScoreboardName())) {
                    setCarried(ItemStack.EMPTY);
                    refreshPage();
                }
                return;
            }
        }
        super.clicked(slotId, button, clickType, clickPlayer);
    }

    private void handleSort(int button) {
        if (button == 1) {
            long now = System.currentTimeMillis();
            sortClicks[sortClickIdx%3] = now; sortClickIdx++;
            boolean t3 = sortClickIdx >= 3 && (now - sortClicks[(sortClickIdx-3)%3]) < 1500;
            if (t3) { dbSortAll(); player.sendSystemMessage(Component.literal("\u00a7a\u5168\u80cc\u5305\u5df2\u6574\u7406\uff01")); }
            else { dbSortPage(page); player.sendSystemMessage(Component.literal("\u00a7a\u5f53\u524d\u9875\u5df2\u6574\u7406\uff01")); }
        } else {
            dbSortAll();
            player.sendSystemMessage(Component.literal("\u00a7a\u7269\u54c1\u5df2\u6574\u7406\uff01"));
        }
        refreshPage();
    }

    private void doUpgrade() {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem()==Items.DIAMOND && s.getCount()>=1) {
                s.shrink(1); dbUpgrade();
                player.sendSystemMessage(Component.literal("\u00a7a\u80cc\u5305\u5df2\u5347\u7ea7\uff01"));
                stashCarried();
                player.getServer().execute(() -> openForPlayer(player, searchFilter, page, false, isBox, boxOwner));
                return;
            }
        }
        player.sendSystemMessage(Component.literal("\u00a7c\u9700\u8981 1 \u4e2a\u94bb\u77f3\u6765\u5347\u7ea7\u80cc\u5305\uff01"));
    }

    private void doCreateBox() {
        String owner = TeamResolver.resolvePrimaryTeam(player);
        var exist = SharedBackpackMod.database.listBoxes(owner);
        int n = exist.size()+1; String name;
        do { name = "\u76d2\u5b50" + n; n++; } while (exist.contains(name));
        SharedBackpackMod.database.createBox(owner, name);
        player.sendSystemMessage(Component.literal("\u00a7a\u5df2\u521b\u5efa\u76d2\u5b50: " + name));
        currentMenu = null; stashCarried();
        String fn = name, fo = owner;
        player.getServer().execute(() -> openForPlayer(player, searchFilter, 0, false, true, fo+":"+fn));
    }

    // ===== Quick move =====

    static boolean tagsMatchIgnore(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        CompoundTag t1 = a.hasTag()?a.getTag().copy():null;
        if (t1!=null){t1.remove("display");if(t1.isEmpty())t1=null;}
        CompoundTag t2 = b.hasTag()?b.getTag().copy():null;
        if (t2!=null){t2.remove("display");if(t2.isEmpty())t2=null;}
        return (t1==null&&t2==null)||(t1!=null&&t1.equals(t2));
    }

    @Override
    public ItemStack quickMoveStack(Player mp, int index) {
        Slot s = getSlot(index);
        if (s == null || !s.hasItem()) return ItemStack.EMPTY;
        if (!unloadMode && !inMenu() && isControlSlot(index)) return ItemStack.EMPTY;
        if (inMenu() && isControlSlot(index)) return ItemStack.EMPTY;

        ItemStack src = s.getItem().copy();
        ItemStack orig = src.copy();

        if (index < (unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS)) {
            // Shift-click from backpack -> player inventory
            ItemStack toMove = src.copy();
            stripDisplay(toMove);
            if (!moveItemStackTo(toMove, invStart(), invEnd(), true)) return ItemStack.EMPTY;
            // How many were moved?
            int moved = src.getCount() - toMove.getCount();
            if (moved <= 0) return ItemStack.EMPTY;
            // Remove from DB
            int dbSlot = realDbSlot(index);
            dbRemove(dbSlot, moved);
            // Update handler
            if (moved >= src.getCount()) {
                handler.setStackInSlot(index, ItemStack.EMPTY);
            } else {
                ItemStack rem = src.copy(); rem.setCount(src.getCount() - moved);
                handler.setStackInSlot(index, rem);
            }
        } else {
            // Shift-click from player inventory -> backpack (works in all views)
            String iid = BuiltInRegistries.ITEM.getKey(src.getItem()).toString();
            String nbt = nbtForStorage(src);
            boolean added = dbAdd(iid, src.getCount(), nbt, player.getScoreboardName());
            if (!added) return ItemStack.EMPTY;
            s.set(ItemStack.EMPTY);
            refreshPage();
            return orig;
        }
        // Sync source slot for backpack->inventory direction
        if (handler.getStackInSlot(index).isEmpty()) {
            s.set(ItemStack.EMPTY);
        } else {
            // force client sync
            broadcastChanges();
        }
        refreshPage();
        return orig;
    }

    @Override public boolean stillValid(Player p) { return true; }

    // ===== Static factory =====

    public static void openTeam(ServerPlayer player, String search) { openForPlayer(player, search, 0, false, false, null); }
    public static void openForPlayer(ServerPlayer player, String search) { openForPlayer(player, search, 0, false, false, null); }
    public static void openForPlayer(ServerPlayer player, String search, int page, boolean unload, boolean isBox, String boxOwner) {
        String tid = isBox ? (boxOwner!=null?boxOwner:TeamResolver.resolvePrimaryTeam(player)) : TeamResolver.resolvePrimaryTeam(player);
        int mp = isBox ? SharedBackpackMod.database.getBoxMaxPages(tid, tid) : SharedBackpackMod.database.getMaxPages(tid);
        int cp = Math.max(0, Math.min(page, mp-1));
        String dn = tid; if (isBox && tid.contains(":")) dn = tid.substring(tid.lastIndexOf(':')+1);
        String title = unload ? "\u5378\u8d27" : (isBox ? "\u76d2\u5b50: "+dn : "\u5171\u4eab\u80cc\u5305 - "+tid);
        if (search != null && !search.isEmpty()) title += " \u641c\u7d22:"+search;
        String ft = title; int fp = cp; boolean fw = unload; boolean fb = isBox; String fo = boxOwner;
        player.openMenu(new MenuProvider() {
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new BackpackMenu(id, player, tid, fp, mp, search, fw, fb, fo);
            }
            public Component getDisplayName() { return Component.literal(ft); }
        });
    }

    // ===== Menu System =====

    interface MenuPage {
        void populate(ItemStackHandler handler, int maxSlots);
        String onClick(int slotId, BackpackMenu menu);
    }

    class ModMenuPage implements MenuPage {
        final Map<Integer, String> map = new HashMap<>();
        public void populate(ItemStackHandler h, int max) {
            map.clear();
            var all = dbItems();
            var uniq = new LinkedHashMap<String, DatabaseManager.BackpackItem>();
            for (var it : all) { String ns = it.itemId.contains(":")?it.itemId.split(":",2)[0]:"minecraft"; uniq.putIfAbsent(ns, it); }
            int i = 0;
            for (var e : uniq.entrySet()) {
                if (i >= max) break;
                ItemStack icon = SharedBackpack.toItemStack(e.getValue());
                long cnt = all.stream().filter(it -> (it.itemId.contains(":")?it.itemId.split(":",2)[0]:"minecraft").equals(e.getKey())).mapToInt(it -> it.count).sum();
                icon.setHoverName(Component.literal("\u00a76"+e.getKey()+" \u00a77("+cnt+" \u4ef6)"));
                map.put(i, e.getKey()); h.setStackInSlot(i++, icon);
            }
        }
        public String onClick(int slotId, BackpackMenu m) {
            String ns = map.get(slotId); if (ns == null) return null;
            m.modFilter = ns; m.currentMenu = null; m.page = 0; m.refreshPage(); return "FILTER";
        }
    }

    class BoxMenuPage implements MenuPage {
        final Map<Integer, String> map = new HashMap<>();
        public void populate(ItemStackHandler h, int max) {
            map.clear();
            var list = SharedBackpackMod.database.listBoxes(TeamResolver.resolvePrimaryTeam(player));
            int i = 0;
            for (String name : list) {
                if (i >= max-1) break;
                ItemStack icon = new ItemStack(Items.CHEST);
                icon.setHoverName(Component.literal("\u00a76"+name));
                map.put(i, name); h.setStackInSlot(i++, icon);
            }
            if (i < max) {
                ItemStack nb = new ItemStack(Items.CRAFTING_TABLE);
                nb.setHoverName(Component.literal("\u00a7a+ \u65b0\u5efa\u76d2\u5b50"));
                map.put(i, "__new__"); h.setStackInSlot(i, nb);
            }
        }
        public String onClick(int slotId, BackpackMenu m) {
            String name = map.get(slotId); if (name == null) return null;
            if ("__new__".equals(name)) { m.doCreateBox(); return "CREATE"; }
            String team = TeamResolver.resolvePrimaryTeam(player);
            m.stashCarried();
            player.getServer().execute(() -> openForPlayer(player, searchFilter, 0, false, true, team+":"+name));
            return "OPEN";
        }
    }

    // ===== Slot / Container =====

    class BpSlot extends Slot {
        final int ls;
        BpSlot(int ls, int x, int y) { super(new BpContainer(ls), 0, x, y); this.ls = ls; }
        public boolean mayPickup(Player p) { return !isControlSlot(ls); }
        public boolean mayPlace(ItemStack s) { return !isControlSlot(ls) && !isMenuView(); }
        public void onTake(Player t, ItemStack s) {
            if (!isControlSlot(ls)) { stripDisplay(s); s.resetHoverName(); dbRemove(realDbSlot(ls), s.getCount()); }
        }
        public void set(ItemStack s) {
            if (!isControlSlot(ls)) {
                if (!s.isEmpty()) {
                    String iid = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                    String n = nbtForStorage(s);
                    dbSet(realDbSlot(ls), iid, s.getCount(), n, player.getScoreboardName());
                } else { dbRemove(realDbSlot(ls), 99999); }
            }
            handler.setStackInSlot(ls, s);
        }
    }

    class BpContainer implements Container {
        final int ls; BpContainer(int ls) { this.ls = ls; }
        public int getContainerSize() { return 1; }
        public boolean isEmpty() { return handler.getStackInSlot(ls).isEmpty(); }
        public ItemStack getItem(int i) { return handler.getStackInSlot(ls); }
        public ItemStack removeItem(int i, int c) {
            ItemStack s = handler.getStackInSlot(ls); if (s.isEmpty()) return ItemStack.EMPTY;
            ItemStack t = s.split(c); handler.setStackInSlot(ls, s); return t;
        }
        public void setItem(int i, ItemStack s) { handler.setStackInSlot(ls, s); }
        // setChanged is intentionally a no-op: all DB writes are handled explicitly
        // by BpSlot.set() and BpSlot.onTake() to prevent double-writes.
        public void setChanged() {}
        public boolean stillValid(Player p) { return true; }
        public ItemStack removeItemNoUpdate(int i) { return handler.getStackInSlot(ls); }
        public void clearContent() { handler.setStackInSlot(ls, ItemStack.EMPTY); }
    }
}
