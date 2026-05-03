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
    private boolean showModMenu;
    private boolean showBoxMenu;
    private final Map<Integer, String> modMenuMap = new HashMap<>();
    private final Map<Integer, String> boxMenuMap = new HashMap<>();
    private final long[] sortClicks = new long[3];
    private int sortClickIdx;
    private int page;
    private boolean loading;

    public BackpackMenu(int windowId, ServerPlayer player, String teamId, int page, int maxPages,
                        String searchFilter, boolean unloadMode, boolean isBox, String boxOwner) {
        super(unloadMode ? MenuType.GENERIC_9x5 : MenuType.GENERIC_9x6, windowId);
        this.player = player; this.teamId = teamId; this.page = page; this.maxPages = maxPages;
        this.searchFilter = searchFilter; this.unloadMode = unloadMode;
        this.isBox = isBox; this.boxOwner = boxOwner;
        this.handler = new ItemStackHandler(unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS);

        int total = unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS;
        for (int i = 0; i < total; i++)
            addSlot(new BpSlot(i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));

        int rows = unloadMode ? 5 : 6;
        int invY = 18 + rows * 18 + 13, hotbarY = invY + 3 * 18 + 4;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(player.getInventory(), c + r * 9 + 9, 8 + c * 18, invY + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(player.getInventory(), c, 8 + c * 18, hotbarY));

        loadPage();
    }

    // DB access helpers
    private List<DatabaseManager.BackpackItem> dbItems() {
        if (unloadMode) return List.of();
        return isBox ? SharedBackpackMod.database.getBoxItems(boxOwner, teamId)
                     : SharedBackpackMod.database.getItems(teamId);
    }
    private int dbTotal() {
        return isBox ? SharedBackpackMod.database.getTotalBoxItemCount(boxOwner, teamId)
                     : SharedBackpackMod.database.getTotalItemCount(teamId);
    }
    private int dbMaxPages() { return maxPages; }
    private boolean dbAdd(String itemId, int count, String nbt, String name) {
        return isBox ? SharedBackpackMod.database.addBoxItem(boxOwner, teamId, itemId, count, nbt, name)
                     : SharedBackpackMod.database.addItem(teamId, itemId, count, nbt, name);
    }
    private boolean dbRemove(int slot, int count) {
        return isBox ? SharedBackpackMod.database.removeBoxItem(boxOwner, teamId, slot, count)
                     : SharedBackpackMod.database.removeItem(teamId, slot, count);
    }
    private boolean dbSet(int slot, String itemId, int count, String nbt, String name) {
        return isBox ? SharedBackpackMod.database.setBoxItem(boxOwner, teamId, slot, itemId, count, nbt, name)
                     : SharedBackpackMod.database.setItem(teamId, slot, itemId, count, nbt, name);
    }
    private boolean dbUpgrade() { return SharedBackpackMod.database.upgradePages(teamId, 1); }
    private void dbSort() {
        if (isBox) SharedBackpackMod.database.sortBoxItems(boxOwner, teamId);
        else SharedBackpackMod.database.sortItems(teamId);
    }

    private boolean isControlSlot(int slot) { return !unloadMode && slot >= ITEMS_PER_PAGE && slot < GUI_SLOTS; }
    private boolean inMenu() { return showModMenu || showBoxMenu; }
    private int realDbSlot(int localSlot) { return page * ITEMS_PER_PAGE + (localSlot < ITEMS_PER_PAGE ? localSlot : 0); }
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

    static void setMeta(ItemStack stack, DatabaseManager.BackpackItem item) {
        if (item.placedBy == null) return;
        Component name = stack.getHoverName();
        String nameStr = name.getString();
        int bracketIdx = nameStr.lastIndexOf(" §7[");
        if (bracketIdx > 0) {
            name = Component.literal(nameStr.substring(0, bracketIdx));
        }
        String meta = ChatFormatting.GRAY + "[" + item.placedBy;
        if (item.placedTime != null) {
            String time = item.placedTime;
            if (time.length() >= 16) time = time.substring(11, 16);
            meta += " " + time;
        }
        meta += " x" + item.placedCount + "]";
        stack.setHoverName(name.copy().append(Component.literal(" §7" + meta)));
    }

    private void loadPage() {
        loading = true;
        try {
            int totalSlots = unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS;
            for (int i = 0; i < totalSlots; i++) handler.setStackInSlot(i, ItemStack.EMPTY);

            int startSlot = page * ITEMS_PER_PAGE;
            List<DatabaseManager.BackpackItem> items = dbItems();

            if (modFilter != null) {
                items = items.stream().filter(it -> {
                    String ns = it.itemId.contains(":") ? it.itemId.split(":", 2)[0] : "minecraft";
                    return ns.equals(modFilter);
                }).collect(java.util.stream.Collectors.toList());
            }
            if (searchFilter != null && !searchFilter.isEmpty()) {
                items = PinyinSearch.search(items, searchFilter);
                List<DatabaseManager.BackpackItem> c = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    DatabaseManager.BackpackItem it = items.get(i);
                    c.add(new DatabaseManager.BackpackItem(startSlot + i, it.itemId, it.count, it.nbt,
                        it.placedBy, it.placedTime, it.placedCount, it.lastModifiedBy, it.lastModifiedTime));
                }
                items = c;
            }

            int loaded = 0;
            if (!inMenu()) {
                for (DatabaseManager.BackpackItem item : items) {
                    int loc = item.slot - startSlot;
                    if (loc >= 0 && loc < ITEMS_PER_PAGE) {
                        ItemStack st = SharedBackpack.toItemStack(item);
                        setMeta(st, item);
                        handler.setStackInSlot(loc, st);
                        loaded++;
                    }
                    if (loc >= ITEMS_PER_PAGE) break;
                }
            }

            if (!unloadMode) {
                if (showModMenu) populateModMenu();
                else if (showBoxMenu) populateBoxMenu();
                populateControlBar(loaded);
            }
        } finally { loading = false; }
    }

    private void populateModMenu() {
        modMenuMap.clear();
        List<DatabaseManager.BackpackItem> all = dbItems();
        LinkedHashMap<String, DatabaseManager.BackpackItem> mods = new LinkedHashMap<>();
        for (DatabaseManager.BackpackItem item : all) {
            String ns = item.itemId.contains(":") ? item.itemId.split(":", 2)[0] : "minecraft";
            mods.putIfAbsent(ns, item);
        }
        int i = 0;
        for (Map.Entry<String, DatabaseManager.BackpackItem> e : mods.entrySet()) {
            if (i >= ITEMS_PER_PAGE) break;
            ItemStack icon = SharedBackpack.toItemStack(e.getValue());
            long cnt = all.stream().filter(it -> {
                String ns = it.itemId.contains(":") ? it.itemId.split(":", 2)[0] : "minecraft";
                return ns.equals(e.getKey());
            }).mapToInt(it -> it.count).sum();
            icon.setHoverName(Component.literal("§6" + e.getKey() + " §7(" + cnt + " 件)"));
            modMenuMap.put(i, e.getKey());
            handler.setStackInSlot(i++, icon);
        }
    }

    private void populateBoxMenu() {
        boxMenuMap.clear();
        List<String> boxes = SharedBackpackMod.database.listBoxes(player.getStringUUID());
        int i = 0;
        for (String name : boxes) {
            if (i >= ITEMS_PER_PAGE - 1) break;
            ItemStack icon = new ItemStack(Items.CHEST);
            icon.setHoverName(Component.literal("§6" + name));
            boxMenuMap.put(i, name);
            handler.setStackInSlot(i++, icon);
        }
        // New box button
        ItemStack newBox = new ItemStack(Items.CRAFTING_TABLE);
        newBox.setHoverName(Component.literal("§a+ 新建盒子"));
        boxMenuMap.put(i, "__new__");
        handler.setStackInSlot(i, newBox);
    }

    private void populateControlBar(int itemCount) {
        if (page > 0) {
            ItemStack p = new ItemStack(Items.ARROW); p.setHoverName(Component.literal("§6◀ 上一页"));
            handler.setStackInSlot(PREV_SLOT, p);
        }
        ItemStack pi = new ItemStack(Items.PAPER);
        pi.setHoverName(Component.literal("§e第 " + (page + 1) + " / " + maxPages + " 页"));
        handler.setStackInSlot(PAGE_SLOT, pi);
        ItemStack ci = new ItemStack(Items.BOOK);
        ci.setHoverName(Component.literal("§e物品: " + dbTotal() + " 件"));
        handler.setStackInSlot(COUNT_SLOT, ci);
        String displayTeam = teamId;
        if (isBox && teamId.contains(":")) displayTeam = teamId.substring(teamId.lastIndexOf(':') + 1);
        ItemStack ti = new ItemStack(isBox ? Items.CHEST : Items.NAME_TAG);
        ti.setHoverName(Component.literal("§a" + (isBox ? "盒子:" + displayTeam + " §7(点击返回队伍)" : "队伍: " + displayTeam + " §7(点击管理盒子)")));
        handler.setStackInSlot(TEAM_SLOT, ti);

        // Mod/Box menu controls
        if (showModMenu) {
            ItemStack back = new ItemStack(Items.BARRIER);
            back.setHoverName(Component.literal("§c返回全部"));
            handler.setStackInSlot(MOD_SLOT, back);
        } else if (showBoxMenu) {
            ItemStack back = new ItemStack(Items.BARRIER);
            back.setHoverName(Component.literal("§c返回背包"));
            handler.setStackInSlot(MOD_SLOT, back);
        } else if (modFilter != null) {
            ItemStack ic = getModIcon(modFilter);
            ic.setHoverName(Component.literal("§6分类: " + modFilter + " §7(点击展开菜单)"));
            handler.setStackInSlot(MOD_SLOT, ic);
        } else {
            ItemStack all = new ItemStack(Items.BOOKSHELF);
            all.setHoverName(Component.literal("§6分类: 全部 §7(点击展开菜单)"));
            handler.setStackInSlot(MOD_SLOT, all);
        }

        if (searchFilter != null && !searchFilter.isEmpty()) {
            ItemStack si = new ItemStack(Items.COMPASS);
            si.setHoverName(Component.literal("§d搜索: " + searchFilter));
            handler.setStackInSlot(SEARCH_SLOT, si);
        }
        ItemStack sb = new ItemStack(Items.HOPPER);
        sb.setHoverName(Component.literal("§6整理物品 §7(合并相同物品)")); handler.setStackInSlot(SORT_SLOT, sb);
        ItemStack ug = new ItemStack(Items.DIAMOND);
        ug.setHoverName(Component.literal("§b升级背包 [+1页] §7需要 1 钻石")); handler.setStackInSlot(UPGRADE_SLOT, ug);
        if (page < maxPages - 1) {
            ItemStack nx = new ItemStack(Items.ARROW);
            nx.setHoverName(Component.literal("§6下一页 ▶")); handler.setStackInSlot(NEXT_SLOT, nx);
        }
    }

    private void refreshPage() { loadPage(); broadcastChanges(); }
    private void navigateToPage(int np) { stashCarried(); page = np; refreshPage(); }

    private void stashCarried() {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return;
        int maxSlot = unloadMode ? GUI_SLOTS_UNLOAD : ITEMS_PER_PAGE;
        for (int i = 0; i < maxSlot && !carried.isEmpty(); i++) {
            Slot s = getSlot(i);
            if (s == null || !s.hasItem()) continue;
            if (!tagsMatchIgnoreDisplay(carried, s.getItem())) continue;
            int canAdd = Math.min(carried.getCount(), 64 - s.getItem().getCount());
            if (canAdd <= 0) continue;
            ItemStack merged = s.getItem().copy(); merged.grow(canAdd);
            carried.shrink(canAdd); s.set(merged);
        }
        for (int i = 0; i < maxSlot && !carried.isEmpty(); i++) {
            Slot s = getSlot(i);
            if (s.hasItem()) continue;
            int toPlace = Math.min(carried.getCount(), 64);
            ItemStack cp = carried.copy(); cp.setCount(toPlace);
            carried.shrink(toPlace); s.set(cp);
        }
        if (!carried.isEmpty() && !player.getInventory().add(carried))
            player.drop(carried, false);
        setCarried(ItemStack.EMPTY);
    }

    private ItemStack getModIcon(String ns) {
        for (DatabaseManager.BackpackItem it : dbItems()) {
            String n = it.itemId.contains(":") ? it.itemId.split(":", 2)[0] : "minecraft";
            if (n.equals(ns)) return SharedBackpack.toItemStack(it);
        }
        return new ItemStack(Items.BOOKSHELF);
    }

    private void toggleModMenu() { stashCarried(); showModMenu = !showModMenu; showBoxMenu = false; modFilter = null; refreshPage(); }
    private void toggleBoxMenu() { stashCarried(); showBoxMenu = !showBoxMenu; showModMenu = false; modFilter = null; refreshPage(); }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clickPlayer) {
        // Box menu clicks
        if (showBoxMenu && slotId >= 0 && slotId < ITEMS_PER_PAGE) {
            String name = boxMenuMap.get(slotId);
            if (name != null) {
                stashCarried();
                if ("__new__".equals(name)) {
                    doCreateBox();
                } else {
                    String o = player.getStringUUID();
                    String n = name;
                    player.getServer().execute(() -> openForPlayer(player, searchFilter, 0, false, true, o + ":" + n));
                }
            }
            return;
        }

        // Mod menu clicks
        if (showModMenu && slotId >= 0 && slotId < ITEMS_PER_PAGE) {
            String ns = modMenuMap.get(slotId);
            if (ns != null) { stashCarried(); modFilter = ns; showModMenu = false; refreshPage(); }
            return;
        }

        if (!unloadMode && slotId >= 0 && slotId < GUI_SLOTS && isControlSlot(slotId)) {
            if (inMenu() && slotId == MOD_SLOT) { stashCarried(); showModMenu = false; showBoxMenu = false; refreshPage(); return; }
            if (!inMenu()) {
                if (slotId == PREV_SLOT && page > 0) { navigateToPage(page - 1); return; }
                if (slotId == NEXT_SLOT && page < maxPages - 1) { navigateToPage(page + 1); return; }
                if (slotId == UPGRADE_SLOT) { doUpgrade(); return; }
                if (slotId == SORT_SLOT) { doSort(); return; }
                if (slotId == MOD_SLOT) { toggleModMenu(); return; }
                if (slotId == TEAM_SLOT && isBox) {
                    stashCarried();
                    player.getServer().execute(() -> openTeam(player, searchFilter));
                    return;
                }
                if (slotId == TEAM_SLOT && !isBox) { toggleBoxMenu(); return; }
            }
            return;
        }

        int max = (unloadMode || showModMenu) ? GUI_SLOTS_UNLOAD : ITEMS_PER_PAGE;
        if (showModMenu) max = ITEMS_PER_PAGE;
        if (slotId >= 0 && slotId < max && clickType == ClickType.PICKUP) {
            Slot slot = getSlot(slotId);
            if (slot != null && slot.hasItem() && slot.mayPickup(clickPlayer)) {
                ItemStack cur = getCarried();
                if (button == 0 && cur.isEmpty()) {
                    ItemStack ex = slot.safeTake(1, 64, clickPlayer);
                    if (!ex.isEmpty()) setCarried(ex);
                    return;
                }
                if (button == 1 && cur.isEmpty()) {
                    int mx = slot.getItem().getMaxStackSize();
                    ItemStack ex = slot.safeTake(mx, mx, clickPlayer);
                    if (!ex.isEmpty()) setCarried(ex);
                    return;
                }
            }
        }
        super.clicked(slotId, button, clickType, clickPlayer);
    }

    private void doUpgrade() {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == Items.DIAMOND && s.getCount() >= 1) {
                s.shrink(1);
                dbUpgrade();
                player.sendSystemMessage(Component.literal("§a背包已升级！"));
                stashCarried();
                player.getServer().execute(() -> openForPlayer(player, searchFilter, page, false, isBox, boxOwner));
                return;
            }
        }
        player.sendSystemMessage(Component.literal("§c需要 1 个钻石来升级背包！"));
    }

    private void doSort() {
        long now = System.currentTimeMillis();
        sortClicks[sortClickIdx % 3] = now;
        sortClickIdx++;
        boolean triple = sortClickIdx >= 3 && (now - sortClicks[(sortClickIdx - 3) % 3]) < 1500;
        dbSort();
        player.sendSystemMessage(Component.literal(triple ? "§a全背包已整理！" : "§a物品已整理！"));
        refreshPage();
    }

    private void doCreateBox() {
        String owner = player.getStringUUID();
        List<String> existing = SharedBackpackMod.database.listBoxes(owner);
        int n = existing.size() + 1;
        String name;
        do { name = "盒子" + n; n++; } while (existing.contains(name));
        SharedBackpackMod.database.createBox(owner, name);
        player.sendSystemMessage(Component.literal("§a已创建盒子: " + name));
        showBoxMenu = false;
        stashCarried();
        String fn = name, fo = owner;
        player.getServer().execute(() -> openForPlayer(player, searchFilter, 0, false, true, fo + ":" + fn));
    }

    private static boolean tagsMatchIgnoreDisplay(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        CompoundTag t1 = a.hasTag() ? a.getTag().copy() : null;
        if (t1 != null) { t1.remove("display"); if (t1.isEmpty()) t1 = null; }
        CompoundTag t2 = b.hasTag() ? b.getTag().copy() : null;
        if (t2 != null) { t2.remove("display"); if (t2.isEmpty()) t2 = null; }
        return (t1 == null && t2 == null) || (t1 != null && t1.equals(t2));
    }

    @Override
    public ItemStack quickMoveStack(Player movePlayer, int index) {
        Slot slot = getSlot(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        if (!unloadMode && !inMenu() && isControlSlot(index)) return ItemStack.EMPTY;
        if (inMenu() && index < (unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS) && isControlSlot(index)) return ItemStack.EMPTY;

        ItemStack src = slot.getItem().copy();
        ItemStack orig = src.copy();
        int maxSlot = (unloadMode || showModMenu) ? GUI_SLOTS_UNLOAD : ITEMS_PER_PAGE;

        if (index < (unloadMode ? GUI_SLOTS_UNLOAD : GUI_SLOTS)) {
            // Backpack -> player
            stripDisplay(src);
            if (!moveItemStackTo(src, invStart(), invEnd(), true)) return ItemStack.EMPTY;
        } else {
            // Player -> backpack: custom merge ignoring display tags
            boolean moved = false;
            for (int i = 0; i < maxSlot && !src.isEmpty(); i++) {
                Slot tgt = getSlot(i);
                if (!tgt.hasItem()) continue;
                if (!tagsMatchIgnoreDisplay(src, tgt.getItem())) continue;
                int canAdd = Math.min(src.getCount(), 64 - tgt.getItem().getCount());
                if (canAdd <= 0) continue;
                ItemStack merged = tgt.getItem().copy(); merged.grow(canAdd);
                src.shrink(canAdd); tgt.set(merged); moved = true;
            }
            for (int i = 0; i < maxSlot && !src.isEmpty(); i++) {
                Slot tgt = getSlot(i);
                if (tgt.hasItem()) continue;
                int toPlace = Math.min(src.getCount(), 64);
                ItemStack cp = src.copy(); cp.setCount(toPlace);
                src.shrink(toPlace); tgt.set(cp); moved = true;
            }
            if (!moved) return ItemStack.EMPTY;
            refreshPage();
        }

        if (src.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.set(src);
        return orig;
    }

    @Override public boolean stillValid(Player p) { return true; }

    public static void openTeam(ServerPlayer player, String search) { openForPlayer(player, search, 0, false, false, null); }
    public static void openForPlayer(ServerPlayer player, String search) { openForPlayer(player, search, 0, false, false, null); }
    public static void openForPlayer(ServerPlayer player, String search, int page, boolean unload, boolean isBox, String boxOwner) {
        String tid = isBox ? (boxOwner != null ? boxOwner : player.getStringUUID()) : TeamResolver.resolvePrimaryTeam(player);
        int mp = isBox ? SharedBackpackMod.database.getBoxMaxPages(tid, tid) : SharedBackpackMod.database.getMaxPages(tid);
        int cp = Math.max(0, Math.min(page, mp - 1));
        String displayName = tid;
        if (isBox && tid.contains(":")) displayName = tid.substring(tid.lastIndexOf(':') + 1);
        String title = unload ? "卸货" : (isBox ? "盒子: " + displayName : "共享背包 - " + tid);
        if (search != null && !search.isEmpty()) title += " 搜索:" + search;
        String ft = title; int fp = cp; boolean fw = unload; boolean fb = isBox; String fo = boxOwner;
        player.openMenu(new MenuProvider() {
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new BackpackMenu(id, player, tid, fp, mp, search, fw, fb, fo);
            }
            @Override public Component getDisplayName() { return Component.literal(ft); }
        });
    }

    private class BpSlot extends Slot {
        final int ls;
        BpSlot(int ls, int x, int y) { super(new BpContainer(ls), 0, x, y); this.ls = ls; }
        @Override public boolean mayPickup(Player p) { return !isControlSlot(ls); }
        @Override public boolean mayPlace(ItemStack s) { return !isControlSlot(ls); }
        @Override public void onTake(Player t, ItemStack s) {
            if (!isControlSlot(ls)) { stripDisplay(s); s.resetHoverName(); dbRemove(realDbSlot(ls), s.getCount()); }
        }
        @Override public void set(ItemStack s) {
            if (!isControlSlot(ls)) {
                if (!s.isEmpty()) {
                    String iid = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                    String n = s.hasTag() ? SharedBackpack.stripMetadata(s.getTag().toString()) : null;
                    dbSet(realDbSlot(ls), iid, s.getCount(), n, player.getScoreboardName());
                } else { dbRemove(realDbSlot(ls), 99999); }
            }
            handler.setStackInSlot(ls, s);
        }
    }

    private class BpContainer implements Container {
        final int ls; BpContainer(int ls) { this.ls = ls; }
        @Override public int getContainerSize() { return 1; }
        @Override public boolean isEmpty() { return handler.getStackInSlot(ls).isEmpty(); }
        @Override public ItemStack getItem(int i) { return handler.getStackInSlot(ls); }
        @Override public ItemStack removeItem(int i, int c) {
            ItemStack s = handler.getStackInSlot(ls); if (s.isEmpty()) return ItemStack.EMPTY;
            ItemStack t = s.split(c); handler.setStackInSlot(ls, s); return t;
        }
        @Override public void setItem(int i, ItemStack s) { handler.setStackInSlot(ls, s); }
        @Override public void setChanged() {
            if (loading || isControlSlot(ls)) return;
            ItemStack s = handler.getStackInSlot(ls); int ds = realDbSlot(ls);
            if (s.isEmpty()) dbRemove(ds, 99999);
            else {
                String iid = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                String n = s.hasTag() ? SharedBackpack.stripMetadata(s.getTag().toString()) : null;
                dbSet(ds, iid, s.getCount(), n, player.getScoreboardName());
            }
        }
        @Override public boolean stillValid(Player p) { return true; }
        @Override public ItemStack removeItemNoUpdate(int i) { return handler.getStackInSlot(ls); }
        @Override public void clearContent() { handler.setStackInSlot(ls, ItemStack.EMPTY); }
    }
}
