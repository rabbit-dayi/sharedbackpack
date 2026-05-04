package com.sharedbackpack.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.database.DatabaseManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class DebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var debug = Commands.literal("ccdebug")
            .requires(src -> src.hasPermission(2))
            // 1. Check DB connection
            .then(Commands.literal("dbcheck")
                .executes(ctx -> dbCheck(ctx.getSource())))
            // 2. Show DB info
            .then(Commands.literal("dbinfo")
                .executes(ctx -> dbInfo(ctx.getSource())))
            // 3. Table row counts
            .then(Commands.literal("tablecounts")
                .executes(ctx -> tableCounts(ctx.getSource())))
            // 4. Count items in team
            .then(Commands.literal("count")
                .then(Commands.argument("team", StringArgumentType.string())
                    .executes(ctx -> countItems(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 5. List slots on a page
            .then(Commands.literal("listslots")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("page", IntegerArgumentType.integer(0))
                        .executes(ctx -> listSlots(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "page"))))))
            // 6. Add item to backpack
            .then(Commands.literal("additem")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("item_id", StringArgumentType.string())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 6400))
                            .executes(ctx -> addItem(ctx.getSource(),
                                StringArgumentType.getString(ctx, "team"),
                                StringArgumentType.getString(ctx, "item_id"),
                                IntegerArgumentType.getInteger(ctx, "count")))))))
            // 7. Remove item from slot
            .then(Commands.literal("removeitem")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                            .executes(ctx -> removeItem(ctx.getSource(),
                                StringArgumentType.getString(ctx, "team"),
                                IntegerArgumentType.getInteger(ctx, "slot"),
                                IntegerArgumentType.getInteger(ctx, "count")))))))
            // 8. Set item at specific slot
            .then(Commands.literal("setitem")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                        .then(Commands.argument("item_id", StringArgumentType.string())
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> setItem(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "team"),
                                    IntegerArgumentType.getInteger(ctx, "slot"),
                                    StringArgumentType.getString(ctx, "item_id"),
                                    IntegerArgumentType.getInteger(ctx, "count"))))))))
            // 9. Look up item registry info
            .then(Commands.literal("lookup")
                .then(Commands.argument("item_id", StringArgumentType.string())
                    .executes(ctx -> lookupItem(ctx.getSource(), StringArgumentType.getString(ctx, "item_id")))))
            // 10. Test pinyin matching
            .then(Commands.literal("pinyin")
                .then(Commands.argument("text", StringArgumentType.string())
                    .then(Commands.argument("query", StringArgumentType.string())
                        .executes(ctx -> testPinyin(ctx.getSource(),
                            StringArgumentType.getString(ctx, "text"),
                            StringArgumentType.getString(ctx, "query"))))))
            // 11. Search backpack items
            .then(Commands.literal("search")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("query", StringArgumentType.greedyString())
                        .executes(ctx -> searchItems(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            StringArgumentType.getString(ctx, "query"))))))
            // 12. Show max pages
            .then(Commands.literal("maxpages")
                .then(Commands.argument("team", StringArgumentType.string())
                    .executes(ctx -> maxPages(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 13. Upgrade backpack pages
            .then(Commands.literal("upgrade")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("pages", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> upgradePages(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "pages"))))))
            // 14. Show free slots
            .then(Commands.literal("freeslots")
                .then(Commands.argument("team", StringArgumentType.string())
                    .executes(ctx -> freeSlots(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 15. Show box info
            .then(Commands.literal("boxinfo")
                .then(Commands.argument("owner", StringArgumentType.string())
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> boxInfo(ctx.getSource(),
                            StringArgumentType.getString(ctx, "owner"),
                            StringArgumentType.getString(ctx, "name"))))))
            // 16. List all boxes for owner
            .then(Commands.literal("boxlist")
                .then(Commands.argument("owner", StringArgumentType.string())
                    .executes(ctx -> boxList(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
            // 17. Fill a page with single item type
            .then(Commands.literal("fillpage")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("page", IntegerArgumentType.integer(0))
                        .then(Commands.argument("item_id", StringArgumentType.string())
                            .executes(ctx -> fillPage(ctx.getSource(),
                                StringArgumentType.getString(ctx, "team"),
                                IntegerArgumentType.getInteger(ctx, "page"),
                                StringArgumentType.getString(ctx, "item_id")))))))
            // 18. Stress test: add many items
            .then(Commands.literal("stress")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("total", IntegerArgumentType.integer(1, 10000))
                        .executes(ctx -> stressTest(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "total"))))))
            // 19. Verify data integrity
            .then(Commands.literal("verify")
                .then(Commands.argument("team", StringArgumentType.string())
                    .executes(ctx -> verifyIntegrity(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 20. Read a specific slot
            .then(Commands.literal("slottest")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                        .executes(ctx -> slotTest(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "slot"))))))
            // 21. Show full backpack info
            .then(Commands.literal("teaminfo")
                .then(Commands.argument("team", StringArgumentType.string())
                    .executes(ctx -> teamInfo(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 22. Dump all slots raw
            .then(Commands.literal("allslots")
                .then(Commands.argument("team", StringArgumentType.string())
                    .executes(ctx -> allSlots(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 23. Clear a specific slot
            .then(Commands.literal("clearslot")
                .then(Commands.argument("team", StringArgumentType.string())
                    .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                        .executes(ctx -> clearSlot(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "slot"))))))
            // 24. Force backup
            .then(Commands.literal("backup")
                .executes(ctx -> forceBackup(ctx.getSource())))
            // 25. Reload database
            .then(Commands.literal("reloaddb")
                .executes(ctx -> reloadDb(ctx.getSource())))
            // 26. Clear all items for a team
            .then(Commands.literal("clearall")
                .then(Commands.argument("team", StringArgumentType.string())
                    .executes(ctx -> clearAll(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
        ;

        dispatcher.register(debug);
    }

    // ===== 1. dbcheck =====
    private static int dbCheck(CommandSourceStack src) {
        boolean ok = SharedBackpackMod.database != null && SharedBackpackMod.database.isReady();
        final String msg = ok ? "§aDB connection: OK" : "§cDB connection: FAILED";
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ===== 2. dbinfo =====
    private static int dbInfo(CommandSourceStack src) {
        final File dbFile = new File(src.getServer().getServerDirectory(), "config/sharedbackpack/backpack.db");
        final String path = dbFile.getAbsolutePath();
        final String exists = dbFile.exists() ? "true" : "false";
        final String size = dbFile.exists() ? (dbFile.length() / 1024) + " KB" : "N/A";
        src.sendSuccess(() -> Component.literal("§6DB path: " + path), false);
        src.sendSuccess(() -> Component.literal("§6Exists: " + exists + " | Size: " + size), false);
        final File wal = new File(dbFile.getParent(), "backpack.db-wal");
        final File shm = new File(dbFile.getParent(), "backpack.db-shm");
        final String walInfo = wal.exists() ? (wal.length() + " bytes") : "N/A";
        final String shmInfo = shm.exists() ? (shm.length() + " bytes") : "N/A";
        src.sendSuccess(() -> Component.literal("§6WAL: " + wal.exists() + " (" + walInfo + ")"), false);
        src.sendSuccess(() -> Component.literal("§6SHM: " + shm.exists() + " (" + shmInfo + ")"), false);
        final File backupDir = new File(dbFile.getParent(), "backups");
        if (backupDir.exists()) {
            final File[] bu = backupDir.listFiles((d, n) -> n.endsWith(".db"));
            final int buCount = bu != null ? bu.length : 0;
            src.sendSuccess(() -> Component.literal("§6Backups: " + buCount + " files"), false);
        }
        return 1;
    }

    // ===== 3. tablecounts =====
    private static int tableCounts(CommandSourceStack src) {
        if (!checkDb(src)) return 0;
        src.sendSuccess(() -> Component.literal("§6=== Row counts ==="), false);
        src.sendSuccess(() -> Component.literal("§eUse §6/ccdebug count <team> §eto check specific team"), false);
        src.sendSuccess(() -> Component.literal("§eUse §6/ccdebug teaminfo <team> §efor full info"), false);
        return 1;
    }

    // ===== 4. count =====
    private static int countItems(CommandSourceStack src, String team) {
        if (!checkDb(src)) return 0;
        int total = SharedBackpackMod.database.getTotalItemCount(team);
        int slots = SharedBackpackMod.database.getItems(team).size();
        final String msg = "§6Team " + team + ": §e" + total + " items §7across §e" + slots + " slots";
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ===== 5. listslots =====
    private static int listSlots(CommandSourceStack src, String team, int page) {
        if (!checkDb(src)) return 0;
        int perPage = 45;
        int start = page * perPage;
        List<DatabaseManager.BackpackItem> all = SharedBackpackMod.database.getItems(team);
        final String header = "§6=== Page " + page + " (slots " + start + "-" + (start + perPage - 1) + ") ===";
        src.sendSuccess(() -> Component.literal(header), false);
        int shown = 0;
        for (var it : all) {
            if (it.slot >= start && it.slot < start + perPage) {
                final String name = getDisplayName(it.itemId);
                final String line = " §e[" + it.slot + "] " + name + " §7x" + it.count;
                src.sendSuccess(() -> Component.literal(line), false);
                shown++;
            }
        }
        final int s = shown;
        if (s == 0) src.sendSuccess(() -> Component.literal(" §7(empty)"), false);
        else {
            final String footer = "§7Shown " + s + " slots";
            src.sendSuccess(() -> Component.literal(footer), false);
        }
        return 1;
    }

    // ===== 6. additem =====
    private static int addItem(CommandSourceStack src, String team, String itemId, int count) {
        if (!checkDb(src)) return 0;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item == null || item == Items.AIR) {
            src.sendFailure(Component.literal("§cItem not found: " + itemId));
            return 0;
        }
        long t0 = System.currentTimeMillis();
        boolean ok = SharedBackpackMod.database.addItem(team, itemId, count, null, "DEBUG");
        long ms = System.currentTimeMillis() - t0;
        if (ok) {
            final String name = getDisplayName(itemId);
            final String msg = "§aAdded " + count + "x " + name + " to " + team + " (" + ms + "ms)";
            src.sendSuccess(() -> Component.literal(msg), false);
            final int newTotal = SharedBackpackMod.database.getTotalItemCount(team);
            final String msg2 = "§7New total: " + newTotal;
            src.sendSuccess(() -> Component.literal(msg2), false);
        } else {
            src.sendFailure(Component.literal("§cFailed to add. Backpack may be full."));
        }
        return 1;
    }

    // ===== 7. removeitem =====
    private static int removeItem(CommandSourceStack src, String team, int slot, int count) {
        if (!checkDb(src)) return 0;
        long t0 = System.currentTimeMillis();
        boolean ok = SharedBackpackMod.database.removeItem(team, slot, count);
        long ms = System.currentTimeMillis() - t0;
        if (ok) {
            final String msg = "§aRemoved " + count + " from slot " + slot + " in " + team + " (" + ms + "ms)";
            src.sendSuccess(() -> Component.literal(msg), false);
        } else {
            src.sendFailure(Component.literal("§cSlot " + slot + " not found or empty in team " + team));
        }
        return 1;
    }

    // ===== 8. setitem =====
    private static int setItem(CommandSourceStack src, String team, int slot, String itemId, int count) {
        if (!checkDb(src)) return 0;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item == null || item == Items.AIR) {
            src.sendFailure(Component.literal("§cItem not found: " + itemId));
            return 0;
        }
        long t0 = System.currentTimeMillis();
        boolean ok = SharedBackpackMod.database.setItem(team, slot, itemId, count, null, "DEBUG");
        long ms = System.currentTimeMillis() - t0;
        if (ok) {
            final String name = getDisplayName(itemId);
            final String msg = "§aSet slot " + slot + " = " + count + "x " + name + " (" + ms + "ms)";
            src.sendSuccess(() -> Component.literal(msg), false);
        } else {
            src.sendFailure(Component.literal("§cFailed to set item"));
        }
        return 1;
    }

    // ===== 9. lookup =====
    private static int lookupItem(CommandSourceStack src, String itemId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item == null || item == Items.AIR) {
            src.sendFailure(Component.literal("§cItem not found: " + itemId));
            return 0;
        }
        final String regName = BuiltInRegistries.ITEM.getKey(item).toString();
        final String displayName = item.getDescription().getString();
        final String descId = item.getDescriptionId();
        final String cnName = ChineseNames.get(descId);
        final int maxStack = item.getMaxStackSize();
        src.sendSuccess(() -> Component.literal("§6ID: §e" + regName), false);
        src.sendSuccess(() -> Component.literal("§6Name: §e" + displayName), false);
        src.sendSuccess(() -> Component.literal("§6CN: §e" + (cnName != null ? cnName : "N/A")), false);
        src.sendSuccess(() -> Component.literal("§6DescId: §e" + descId), false);
        src.sendSuccess(() -> Component.literal("§6MaxStack: §e" + maxStack), false);
        if (cnName != null) {
            final boolean pyMatch = PinyinUtil.matches(cnName, "shi");
            final String pyMsg = "§6Pinyin match test('shi'): §e" + pyMatch;
            src.sendSuccess(() -> Component.literal(pyMsg), false);
        }
        return 1;
    }

    // ===== 10. pinyin =====
    private static int testPinyin(CommandSourceStack src, String text, String query) {
        boolean match = PinyinUtil.matches(text, query);
        final String msg = "§6matches(\"" + text + "\", \"" + query + "\") = §e" + match;
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ===== 11. search =====
    private static int searchItems(CommandSourceStack src, String team, String query) {
        if (!checkDb(src)) return 0;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(team);
        List<DatabaseManager.BackpackItem> results = PinyinSearch.search(items, query);
        final int resultCount = results.size();
        final String header = "§6Search '" + query + "' in " + team + ": §e" + resultCount + " matches";
        src.sendSuccess(() -> Component.literal(header), false);
        int show = Math.min(resultCount, 20);
        for (int i = 0; i < show; i++) {
            final var it = results.get(i);
            final String name = getDisplayName(it.itemId);
            final String line = " §e[" + it.slot + "] " + name + " §7x" + it.count;
            src.sendSuccess(() -> Component.literal(line), false);
        }
        if (resultCount > show) {
            final int remaining = resultCount - show;
            final String tail = " §7... and " + remaining + " more";
            src.sendSuccess(() -> Component.literal(tail), false);
        }
        return 1;
    }

    // ===== 12. maxpages =====
    private static int maxPages(CommandSourceStack src, String team) {
        if (!checkDb(src)) return 0;
        int pages = SharedBackpackMod.database.getMaxPages(team);
        int maxSlots = pages * 45;
        final String msg = "§6Team " + team + ": §e" + pages + " pages §7(" + maxSlots + " slots)";
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ===== 13. upgrade =====
    private static int upgradePages(CommandSourceStack src, String team, int pages) {
        if (!checkDb(src)) return 0;
        long t0 = System.currentTimeMillis();
        boolean ok = SharedBackpackMod.database.upgradePages(team, pages);
        long ms = System.currentTimeMillis() - t0;
        if (ok) {
            int newPages = SharedBackpackMod.database.getMaxPages(team);
            final String msg = "§aUpgraded " + team + " by " + pages + " pages. Now: " + newPages + " pages (" + ms + "ms)";
            src.sendSuccess(() -> Component.literal(msg), false);
        } else {
            src.sendFailure(Component.literal("§cUpgrade failed"));
        }
        return 1;
    }

    // ===== 14. freeslots =====
    private static int freeSlots(CommandSourceStack src, String team) {
        if (!checkDb(src)) return 0;
        int pages = SharedBackpackMod.database.getMaxPages(team);
        int maxSlots = pages * 45;
        List<DatabaseManager.BackpackItem> allItems = SharedBackpackMod.database.getItems(team);
        Set<Integer> used = new HashSet<>();
        int itemCount = 0;
        int fullSlots = 0;
        for (var it : allItems) {
            used.add(it.slot);
            itemCount += it.count;
            if (it.count >= 64) fullSlots++;
        }
        int usedSlots = used.size();
        int free = maxSlots - usedSlots;
        double pct = maxSlots > 0 ? (100.0 * usedSlots / maxSlots) : 0;
        final String line1 = "§6Team " + team + ": §e" + usedSlots + "/" + maxSlots + " slots used §7(" + String.format("%.1f", pct) + "%)";
        src.sendSuccess(() -> Component.literal(line1), false);
        final String line2 = "§6Free slots: §e" + free + " §7| Items: " + itemCount + " | Full stacks: " + fullSlots;
        src.sendSuccess(() -> Component.literal(line2), false);
        return 1;
    }

    // ===== 15. boxinfo =====
    private static int boxInfo(CommandSourceStack src, String owner, String name) {
        if (!checkDb(src)) return 0;
        int pages = SharedBackpackMod.database.getBoxMaxPages(owner, name);
        int total = SharedBackpackMod.database.getTotalBoxItemCount(owner, name);
        int maxSlots = pages * 45;
        List<DatabaseManager.BackpackItem> boxItems = SharedBackpackMod.database.getBoxItems(owner, name);
        final String msg = "§6Box " + owner + "/" + name + ": §e" + pages + " pages | " + boxItems.size() + " slots | " + total + " items";
        src.sendSuccess(() -> Component.literal(msg), false);
        final String msg2 = "§6Max slots: §e" + maxSlots;
        src.sendSuccess(() -> Component.literal(msg2), false);
        return 1;
    }

    // ===== 16. boxlist =====
    private static int boxList(CommandSourceStack src, String owner) {
        if (!checkDb(src)) return 0;
        List<String> boxes = SharedBackpackMod.database.listBoxes(owner);
        if (boxes.isEmpty()) {
            final String msg = "§7No boxes for " + owner;
            src.sendSuccess(() -> Component.literal(msg), false);
        } else {
            final int boxCount = boxes.size();
            final String header = "§6Boxes for " + owner + " (" + boxCount + "):";
            src.sendSuccess(() -> Component.literal(header), false);
            for (String b : boxes) {
                final int itemTotal = SharedBackpackMod.database.getTotalBoxItemCount(owner, b);
                final String line = " §e- " + b + " §7(" + itemTotal + " items)";
                src.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    // ===== 17. fillpage =====
    private static int fillPage(CommandSourceStack src, String team, int page, String itemId) {
        if (!checkDb(src)) return 0;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item == null || item == Items.AIR) {
            src.sendFailure(Component.literal("§cItem not found: " + itemId));
            return 0;
        }
        int perPage = 45;
        int startSlot = page * perPage;
        final String name = getDisplayName(itemId);
        final String header = "§6Filling page " + page + " (slots " + startSlot + "-" + (startSlot + perPage - 1) + ") with " + name;
        src.sendSuccess(() -> Component.literal(header), false);
        long t0 = System.currentTimeMillis();
        int filled = 0;
        for (int s = startSlot; s < startSlot + perPage; s++) {
            if (SharedBackpackMod.database.setItem(team, s, itemId, 64, null, "DEBUG")) filled++;
        }
        long ms = System.currentTimeMillis() - t0;
        final String msg = "§aFilled " + filled + "/" + perPage + " slots (" + ms + "ms)";
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ===== 18. stress =====
    private static int stressTest(CommandSourceStack src, String team, int total) {
        if (!checkDb(src)) return 0;
        final String header = "§6Starting stress test: adding " + total + " items to " + team + "...";
        src.sendSuccess(() -> Component.literal(header), false);
        long t0 = System.currentTimeMillis();
        int added = 0;
        int idx = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (SharedBackpackMod.database.addItem(team, id, 1, null, "STRESS")) {
                added++;
                if (added >= total) break;
            } else {
                break;
            }
            idx++;
            if (idx > total * 10) break;
        }
        long ms = System.currentTimeMillis() - t0;
        final int a = added;
        final long m = ms;
        final String rate = ms > 0 ? String.valueOf(1000L * added / ms) : "?";
        final String msg = "§aAdded " + a + "/" + total + " items in " + m + "ms §7(" + rate + " items/sec)";
        src.sendSuccess(() -> Component.literal(msg), false);
        final int newTotal = SharedBackpackMod.database.getTotalItemCount(team);
        final String msg2 = "§7Total items now: " + newTotal;
        src.sendSuccess(() -> Component.literal(msg2), false);
        return 1;
    }

    // ===== 19. verify =====
    private static int verifyIntegrity(CommandSourceStack src, String team) {
        if (!checkDb(src)) return 0;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(team);
        long t0 = System.currentTimeMillis();
        int totalCount = 0;
        Set<Integer> seenSlots = new HashSet<>();
        int duplicateSlots = 0;
        int zeroCount = 0;
        int negativeCount = 0;

        for (var it : items) {
            totalCount += it.count;
            if (!seenSlots.add(it.slot)) duplicateSlots++;
            if (it.count <= 0) {
                if (it.count == 0) zeroCount++;
                else negativeCount++;
            }
        }
        long ms = System.currentTimeMillis() - t0;

        final boolean consistent = (duplicateSlots == 0 && zeroCount == 0 && negativeCount == 0);
        src.sendSuccess(() -> Component.literal(consistent ? "§a=== Integrity check PASSED ===" : "§c=== Integrity check FAILED ==="), false);
        final String info = "§6Slots: §e" + items.size() + " | Total items: §e" + totalCount + " | Unique slots: §e" + seenSlots.size();
        src.sendSuccess(() -> Component.literal(info), false);
        if (duplicateSlots > 0) {
            final int d = duplicateSlots;
            src.sendSuccess(() -> Component.literal("§cDuplicate slots: " + d), false);
        }
        if (zeroCount > 0) {
            final int z = zeroCount;
            src.sendSuccess(() -> Component.literal("§6Zero-count entries: " + z), false);
        }
        if (negativeCount > 0) {
            final int n = negativeCount;
            src.sendSuccess(() -> Component.literal("§cNegative count entries: " + n), false);
        }

        int dbTotal = SharedBackpackMod.database.getTotalItemCount(team);
        final boolean sumOk = (totalCount == dbTotal);
        final int tc = totalCount;
        final int dt = dbTotal;
        src.sendSuccess(() -> Component.literal(sumOk ? "§aSum check: OK (" + tc + " == " + dt + ")" : "§cSum check: MISMATCH (calc=" + tc + " vs db=" + dt + ")"), false);
        final String footer = "§7Verified in " + ms + "ms";
        src.sendSuccess(() -> Component.literal(footer), false);
        return 1;
    }

    // ===== 20. slottest =====
    private static int slotTest(CommandSourceStack src, String team, int slot) {
        if (!checkDb(src)) return 0;
        List<DatabaseManager.BackpackItem> allItems = SharedBackpackMod.database.getItems(team);
        DatabaseManager.BackpackItem found = null;
        for (var it : allItems) {
            if (it.slot == slot) { found = it; break; }
        }
        if (found == null) {
            final String msg = "§7Slot " + slot + " in " + team + " is empty";
            src.sendSuccess(() -> Component.literal(msg), false);
        } else {
            final String name = getDisplayName(found.itemId);
            final String line1 = "§6Slot " + slot + ": §e" + name + " §7x" + found.count;
            src.sendSuccess(() -> Component.literal(line1), false);
            final String nbtPreview = found.nbt != null ? found.nbt.substring(0, Math.min(100, found.nbt.length())) + "..." : "null";
            final String line2 = "§6NBT: §e" + nbtPreview;
            src.sendSuccess(() -> Component.literal(line2), false);
            final String line3 = "§6Placed by: §e" + found.placedBy + " @ " + found.placedTime;
            src.sendSuccess(() -> Component.literal(line3), false);
        }
        return 1;
    }

    // ===== 21. teaminfo =====
    private static int teamInfo(CommandSourceStack src, String team) {
        if (!checkDb(src)) return 0;
        String info = SharedBackpackMod.database.getBackpackInfo(team);
        src.sendSuccess(() -> Component.literal("§6=== Team Info ==="), false);
        final String infoMsg = "§e" + info;
        src.sendSuccess(() -> Component.literal(infoMsg), false);

        int pages = SharedBackpackMod.database.getMaxPages(team);
        final String header = "§6Per-page breakdown:";
        src.sendSuccess(() -> Component.literal(header), false);
        for (int p = 0; p < pages; p++) {
            int ps = p * 45;
            int pe = ps + 45;
            int pi = 0;
            List<DatabaseManager.BackpackItem> pageItems = SharedBackpackMod.database.getItems(team);
            for (var it : pageItems) {
                if (it.slot >= ps && it.slot < pe) pi++;
            }
            final int pageIdx = p;
            final int usedCount = pi;
            final String line = "  §7Page " + pageIdx + ": §e" + usedCount + " slots used";
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // ===== 22. allslots =====
    private static int allSlots(CommandSourceStack src, String team) {
        if (!checkDb(src)) return 0;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(team);
        final String header = "§6=== All " + items.size() + " slots for " + team + " ===";
        src.sendSuccess(() -> Component.literal(header), false);
        for (var it : items) {
            final String name = getDisplayName(it.itemId);
            final String nbtFlag = it.nbt != null ? " §7[NBT]" : "";
            final String line = "§e[" + it.slot + "] §f" + name + " §7x" + it.count + nbtFlag + " §8by:" + it.placedBy;
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // ===== 23. clearslot =====
    private static int clearSlot(CommandSourceStack src, String team, int slot) {
        if (!checkDb(src)) return 0;
        boolean ok = SharedBackpackMod.database.removeItem(team, slot, 99999);
        if (ok) {
            final String msg = "§aSlot " + slot + " cleared in " + team;
            src.sendSuccess(() -> Component.literal(msg), false);
        } else {
            final String msg = "§7Slot " + slot + " was already empty";
            src.sendSuccess(() -> Component.literal(msg), false);
        }
        return 1;
    }

    // ===== 24. backup =====
    private static int forceBackup(CommandSourceStack src) {
        File dbFile = new File(src.getServer().getServerDirectory(), "config/sharedbackpack/backpack.db");
        if (!dbFile.exists()) {
            src.sendFailure(Component.literal("§cDB file not found: " + dbFile.getAbsolutePath()));
            return 0;
        }
        try {
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File backupDir = new File(dbFile.getParent(), "backups");
            backupDir.mkdirs();
            File backup = new File(backupDir, "backpack_debug_backup_" + timestamp + ".db");
            java.nio.file.Files.copy(dbFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            final String name = backup.getName();
            final long sizeKb = backup.length() / 1024;
            final String msg = "§aDebug backup created: " + name + " (" + sizeKb + " KB)";
            src.sendSuccess(() -> Component.literal(msg), false);
        } catch (Exception e) {
            src.sendFailure(Component.literal("§cBackup failed: " + e.getMessage()));
        }
        return 1;
    }

    // ===== 25. reloaddb =====
    private static int reloadDb(CommandSourceStack src) {
        if (SharedBackpackMod.database != null) {
            SharedBackpackMod.database.close();
        }
        SharedBackpackMod.database = new DatabaseManager(src.getServer());
        SharedBackpackMod.database.init();
        boolean ok = SharedBackpackMod.database.isReady();
        final String msg = ok ? "§aDatabase reloaded successfully" : "§cDatabase reload FAILED";
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ===== 26. clearall =====
    private static int clearAll(CommandSourceStack src, String team) {
        if (!checkDb(src)) return 0;
        long t0 = System.currentTimeMillis();
        int cleared = 0;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(team);
        for (var it : items) {
            SharedBackpackMod.database.removeItem(team, it.slot, 99999);
            cleared++;
        }
        long ms = System.currentTimeMillis() - t0;
        final int c = cleared;
        final String msg = "§aCleared " + c + " slots from team " + team + " (" + ms + "ms)";
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ===== Helpers =====
    private static boolean checkDb(CommandSourceStack src) {
        if (SharedBackpackMod.database == null || !SharedBackpackMod.database.isReady()) {
            src.sendFailure(Component.literal("§cDatabase not ready. Server still starting?"));
            return false;
        }
        return true;
    }

    private static String getDisplayName(String itemId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item == null || item == Items.AIR) return itemId;
        String cn = ChineseNames.get(item.getDescriptionId());
        if (cn != null) return cn + " (" + itemId + ")";
        return item.getDescription().getString() + " (" + itemId + ")";
    }
}
