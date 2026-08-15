package com.sharedbackpack.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.database.DatabaseManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.registry.Registry;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class DebugCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(createCommand("ccdebug"));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> createCommand(String root) {
        return CommandManager.literal(root)
            .requires(src -> src.hasPermissionLevel(2))
            // 1. Check DB connection
            .then(CommandManager.literal("dbcheck")
                .executes(ctx -> dbCheck(ctx.getSource())))
            // 2. Show DB info
            .then(CommandManager.literal("dbinfo")
                .executes(ctx -> dbInfo(ctx.getSource())))
            // 3. Table row counts
            .then(CommandManager.literal("tablecounts")
                .executes(ctx -> tableCounts(ctx.getSource())))
            // 4. Count items in team
            .then(CommandManager.literal("count")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .executes(ctx -> countItems(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 5. List slots on a page
            .then(CommandManager.literal("listslots")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("page", IntegerArgumentType.integer(0))
                        .executes(ctx -> listSlots(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "page"))))))
            // 6. Add item to backpack
            .then(CommandManager.literal("additem")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("item_id", StringArgumentType.string())
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 6400))
                            .executes(ctx -> addItem(ctx.getSource(),
                                StringArgumentType.getString(ctx, "team"),
                                StringArgumentType.getString(ctx, "item_id"),
                                IntegerArgumentType.getInteger(ctx, "count")))))))
            // 7. Remove item from slot
            .then(CommandManager.literal("removeitem")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("slot", IntegerArgumentType.integer(0))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                            .executes(ctx -> removeItem(ctx.getSource(),
                                StringArgumentType.getString(ctx, "team"),
                                IntegerArgumentType.getInteger(ctx, "slot"),
                                IntegerArgumentType.getInteger(ctx, "count")))))))
            // 8. Set item at specific slot
            .then(CommandManager.literal("setitem")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("slot", IntegerArgumentType.integer(0))
                        .then(CommandManager.argument("item_id", StringArgumentType.string())
                            .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> setItem(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "team"),
                                    IntegerArgumentType.getInteger(ctx, "slot"),
                                    StringArgumentType.getString(ctx, "item_id"),
                                    IntegerArgumentType.getInteger(ctx, "count"))))))))
            // 9. Look up item registry info
            .then(CommandManager.literal("lookup")
                .then(CommandManager.argument("item_id", StringArgumentType.string())
                    .executes(ctx -> lookupItem(ctx.getSource(), StringArgumentType.getString(ctx, "item_id")))))
            // 10. Test pinyin matching
            .then(CommandManager.literal("pinyin")
                .then(CommandManager.argument("text", StringArgumentType.string())
                    .then(CommandManager.argument("query", StringArgumentType.string())
                        .executes(ctx -> testPinyin(ctx.getSource(),
                            StringArgumentType.getString(ctx, "text"),
                            StringArgumentType.getString(ctx, "query"))))))
            // 11. Search backpack items
            .then(CommandManager.literal("search")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("query", StringArgumentType.greedyString())
                        .executes(ctx -> searchItems(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            StringArgumentType.getString(ctx, "query"))))))
            // 12. Show max pages
            .then(CommandManager.literal("maxpages")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .executes(ctx -> maxPages(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 13. Upgrade backpack pages
            .then(CommandManager.literal("upgrade")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("pages", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> upgradePages(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "pages"))))))
            // 14. Show free slots
            .then(CommandManager.literal("freeslots")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .executes(ctx -> freeSlots(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 15. Show box info
            .then(CommandManager.literal("boxinfo")
                .then(CommandManager.argument("owner", StringArgumentType.string())
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(ctx -> boxInfo(ctx.getSource(),
                            StringArgumentType.getString(ctx, "owner"),
                            StringArgumentType.getString(ctx, "name"))))))
            // 16. List all boxes for owner
            .then(CommandManager.literal("boxlist")
                .then(CommandManager.argument("owner", StringArgumentType.string())
                    .executes(ctx -> boxList(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
            // 17. Fill a page with single item type
            .then(CommandManager.literal("fillpage")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("page", IntegerArgumentType.integer(0))
                        .then(CommandManager.argument("item_id", StringArgumentType.string())
                            .executes(ctx -> fillPage(ctx.getSource(),
                                StringArgumentType.getString(ctx, "team"),
                                IntegerArgumentType.getInteger(ctx, "page"),
                                StringArgumentType.getString(ctx, "item_id")))))))
            // 18. Stress test: add many items
            .then(CommandManager.literal("stress")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("total", IntegerArgumentType.integer(1, 10000))
                        .executes(ctx -> stressTest(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "total"))))))
            // 19. Verify data integrity
            .then(CommandManager.literal("verify")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .executes(ctx -> verifyIntegrity(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 20. Read a specific slot
            .then(CommandManager.literal("slottest")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("slot", IntegerArgumentType.integer(0))
                        .executes(ctx -> slotTest(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "slot"))))))
            // 21. Show full backpack info
            .then(CommandManager.literal("teaminfo")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .executes(ctx -> teamInfo(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 22. Dump all slots raw
            .then(CommandManager.literal("allslots")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .executes(ctx -> allSlots(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
            // 23. Clear a specific slot
            .then(CommandManager.literal("clearslot")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .then(CommandManager.argument("slot", IntegerArgumentType.integer(0))
                        .executes(ctx -> clearSlot(ctx.getSource(),
                            StringArgumentType.getString(ctx, "team"),
                            IntegerArgumentType.getInteger(ctx, "slot"))))))
            // 24. Force backup
            .then(CommandManager.literal("backup")
                .executes(ctx -> forceBackup(ctx.getSource())))
            // 25. Reload database
            .then(CommandManager.literal("reloaddb")
                .executes(ctx -> reloadDb(ctx.getSource())))
            // 26. Clear all items for a team
            .then(CommandManager.literal("clearall")
                .then(CommandManager.argument("team", StringArgumentType.string())
                    .executes(ctx -> clearAll(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
        ;
    }

    // ===== 1. dbcheck =====
    private static int dbCheck(ServerCommandSource src) {
        boolean ok = SharedBackpackMod.database != null && SharedBackpackMod.database.isReady();
        final String msg = ok ? "§aDB connection: OK" : "§cDB connection: FAILED";
        sendDebugFeedback(src, msg);
        return dbInfo(src);
    }

    // ===== 2. dbinfo =====
    private static int dbInfo(ServerCommandSource src) {
        final File dbFile = new File(src.getMinecraftServer().getRunDirectory(), "config/sharedbackpack/backpack.db");
        final String path = dbFile.getAbsolutePath();
        final String exists = dbFile.exists() ? "true" : "false";
        final String size = dbFile.exists() ? (dbFile.length() / 1024) + " KB" : "N/A";
        sendDebugFeedback(src, "§6DB path: " + path);
        sendDebugFeedback(src, "§6Exists: " + exists + " | Size: " + size);
        final File wal = new File(dbFile.getParent(), "backpack.db-wal");
        final File shm = new File(dbFile.getParent(), "backpack.db-shm");
        final String walInfo = wal.exists() ? (wal.length() + " bytes") : "N/A";
        final String shmInfo = shm.exists() ? (shm.length() + " bytes") : "N/A";
        sendDebugFeedback(src, "§6WAL: " + wal.exists() + " (" + walInfo + ")");
        sendDebugFeedback(src, "§6SHM: " + shm.exists() + " (" + shmInfo + ")");
        final File backupDir = new File(dbFile.getParent(), "backups");
        if (backupDir.exists()) {
            final File[] bu = backupDir.listFiles((d, n) -> n.endsWith(".db"));
            final int buCount = bu != null ? bu.length : 0;
            sendDebugFeedback(src, "§6Backups: " + buCount + " files");
        }
        return 1;
    }

    private static void sendDebugFeedback(ServerCommandSource src, String message) {
        Entity entity = src.getEntity();
        if (entity instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity) entity).sendMessage(new LiteralText(message), false);
        } else {
            src.sendFeedback(new LiteralText(message), false);
        }
    }

    // ===== 3. tablecounts =====
    private static int tableCounts(ServerCommandSource src) {
        if (!checkDb(src)) return 0;
        src.sendFeedback(new LiteralText("§6=== Row counts ==="), false);
        src.sendFeedback(new LiteralText("§eUse §6/ccdebug count <team> §eto check specific team"), false);
        src.sendFeedback(new LiteralText("§eUse §6/ccdebug teaminfo <team> §efor full info"), false);
        return 1;
    }

    // ===== 4. count =====
    private static int countItems(ServerCommandSource src, String team) {
        if (!checkDb(src)) return 0;
        int total = SharedBackpackMod.database.getTotalItemCount(team);
        int slots = SharedBackpackMod.database.getItems(team).size();
        final String msg = "§6Team " + team + ": §e" + total + " items §7across §e" + slots + " slots";
        src.sendFeedback(new LiteralText(msg), false);
        return 1;
    }

    // ===== 5. listslots =====
    private static int listSlots(ServerCommandSource src, String team, int page) {
        if (!checkDb(src)) return 0;
        int perPage = 45;
        int start = page * perPage;
        List<DatabaseManager.BackpackItem> all = SharedBackpackMod.database.getItems(team);
        final String header = "§6=== Page " + page + " (slots " + start + "-" + (start + perPage - 1) + ") ===";
        src.sendFeedback(new LiteralText(header), false);
        int shown = 0;
        for (DatabaseManager.BackpackItem it : all) {
            if (it.slot >= start && it.slot < start + perPage) {
                final String name = getDisplayName(it.itemId);
                final String line = " §e[" + it.slot + "] " + name + " §7x" + it.count;
                src.sendFeedback(new LiteralText(line), false);
                shown++;
            }
        }
        final int s = shown;
        if (s == 0) src.sendFeedback(new LiteralText(" §7(empty)"), false);
        else {
            final String footer = "§7Shown " + s + " slots";
            src.sendFeedback(new LiteralText(footer), false);
        }
        return 1;
    }

    // ===== 6. additem =====
    private static int addItem(ServerCommandSource src, String team, String itemId, int count) {
        if (!checkDb(src)) return 0;
        Item item = Registry.ITEM.get(new Identifier(itemId));
        if (item == null || item == Items.AIR) {
            src.sendError(new LiteralText("§cItem not found: " + itemId));
            return 0;
        }
        long t0 = System.currentTimeMillis();
        boolean ok = SharedBackpackMod.database.addItem(team, itemId, count, null, "DEBUG");
        long ms = System.currentTimeMillis() - t0;
        if (ok) {
            final String name = getDisplayName(itemId);
            final String msg = "§aAdded " + count + "x " + name + " to " + team + " (" + ms + "ms)";
            src.sendFeedback(new LiteralText(msg), false);
            final int newTotal = SharedBackpackMod.database.getTotalItemCount(team);
            final String msg2 = "§7New total: " + newTotal;
            src.sendFeedback(new LiteralText(msg2), false);
        } else {
            src.sendError(new LiteralText("§cFailed to add. Backpack may be full."));
        }
        return 1;
    }

    // ===== 7. removeitem =====
    private static int removeItem(ServerCommandSource src, String team, int slot, int count) {
        if (!checkDb(src)) return 0;
        long t0 = System.currentTimeMillis();
        boolean ok = SharedBackpackMod.database.removeItem(team, slot, count);
        long ms = System.currentTimeMillis() - t0;
        if (ok) {
            final String msg = "§aRemoved " + count + " from slot " + slot + " in " + team + " (" + ms + "ms)";
            src.sendFeedback(new LiteralText(msg), false);
        } else {
            src.sendError(new LiteralText("§cSlot " + slot + " not found or empty in team " + team));
        }
        return 1;
    }

    // ===== 8. setitem =====
    private static int setItem(ServerCommandSource src, String team, int slot, String itemId, int count) {
        if (!checkDb(src)) return 0;
        Item item = Registry.ITEM.get(new Identifier(itemId));
        if (item == null || item == Items.AIR) {
            src.sendError(new LiteralText("§cItem not found: " + itemId));
            return 0;
        }
        long t0 = System.currentTimeMillis();
        boolean ok = SharedBackpackMod.database.setItem(team, slot, itemId, count, null, "DEBUG");
        long ms = System.currentTimeMillis() - t0;
        if (ok) {
            final String name = getDisplayName(itemId);
            final String msg = "§aSet slot " + slot + " = " + count + "x " + name + " (" + ms + "ms)";
            src.sendFeedback(new LiteralText(msg), false);
        } else {
            src.sendError(new LiteralText("§cFailed to set item"));
        }
        return 1;
    }

    // ===== 9. lookup =====
    private static int lookupItem(ServerCommandSource src, String itemId) {
        Item item = Registry.ITEM.get(new Identifier(itemId));
        if (item == null || item == Items.AIR) {
            src.sendError(new LiteralText("§cItem not found: " + itemId));
            return 0;
        }
        final String regName = Registry.ITEM.getId(item).toString();
        final String displayName = item.getName().getString();
        final String descId = item.getTranslationKey();
        final String cnName = ChineseNames.get(descId);
        final int maxStack = item.getMaxCount();
        src.sendFeedback(new LiteralText("§6ID: §e" + regName), false);
        src.sendFeedback(new LiteralText("§6Name: §e" + displayName), false);
        src.sendFeedback(new LiteralText("§6CN: §e" + (cnName != null ? cnName : "N/A")), false);
        src.sendFeedback(new LiteralText("§6DescId: §e" + descId), false);
        src.sendFeedback(new LiteralText("§6MaxStack: §e" + maxStack), false);
        if (cnName != null) {
            final boolean pyMatch = PinyinUtil.matches(cnName, "shi");
            final String pyMsg = "§6Pinyin match test('shi'): §e" + pyMatch;
            src.sendFeedback(new LiteralText(pyMsg), false);
        }
        return 1;
    }

    // ===== 10. pinyin =====
    private static int testPinyin(ServerCommandSource src, String text, String query) {
        boolean match = PinyinUtil.matches(text, query);
        final String msg = "§6matches(\"" + text + "\", \"" + query + "\") = §e" + match;
        src.sendFeedback(new LiteralText(msg), false);
        return 1;
    }

    // ===== 11. search =====
    private static int searchItems(ServerCommandSource src, String team, String query) {
        if (!checkDb(src)) return 0;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(team);
        List<DatabaseManager.BackpackItem> results = PinyinSearch.search(items, query);
        final int resultCount = results.size();
        final String header = "§6Search '" + query + "' in " + team + ": §e" + resultCount + " matches";
        src.sendFeedback(new LiteralText(header), false);
        int show = Math.min(resultCount, 20);
        for (int i = 0; i < show; i++) {
            final DatabaseManager.BackpackItem it = results.get(i);
            final String name = getDisplayName(it.itemId);
            final String line = " §e[" + it.slot + "] " + name + " §7x" + it.count;
            src.sendFeedback(new LiteralText(line), false);
        }
        if (resultCount > show) {
            final int remaining = resultCount - show;
            final String tail = " §7... and " + remaining + " more";
            src.sendFeedback(new LiteralText(tail), false);
        }
        return 1;
    }

    // ===== 12. maxpages =====
    private static int maxPages(ServerCommandSource src, String team) {
        if (!checkDb(src)) return 0;
        int pages = SharedBackpackMod.database.getMaxPages(team);
        int maxSlots = pages * 45;
        final String msg = "§6Team " + team + ": §e" + pages + " pages §7(" + maxSlots + " slots)";
        src.sendFeedback(new LiteralText(msg), false);
        return 1;
    }

    // ===== 13. upgrade =====
    private static int upgradePages(ServerCommandSource src, String team, int pages) {
        if (!checkDb(src)) return 0;
        long t0 = System.currentTimeMillis();
        boolean ok = SharedBackpackMod.database.upgradePages(team, pages);
        long ms = System.currentTimeMillis() - t0;
        if (ok) {
            int newPages = SharedBackpackMod.database.getMaxPages(team);
            final String msg = "§aUpgraded " + team + " by " + pages + " pages. Now: " + newPages + " pages (" + ms + "ms)";
            src.sendFeedback(new LiteralText(msg), false);
        } else {
            src.sendError(new LiteralText("§cUpgrade failed"));
        }
        return 1;
    }

    // ===== 14. freeslots =====
    private static int freeSlots(ServerCommandSource src, String team) {
        if (!checkDb(src)) return 0;
        int pages = SharedBackpackMod.database.getMaxPages(team);
        int maxSlots = pages * 45;
        List<DatabaseManager.BackpackItem> allItems = SharedBackpackMod.database.getItems(team);
        Set<Integer> used = new HashSet<>();
        int itemCount = 0;
        int fullSlots = 0;
        for (DatabaseManager.BackpackItem it : allItems) {
            used.add(it.slot);
            itemCount += it.count;
            if (it.count >= 64) fullSlots++;
        }
        int usedSlots = used.size();
        int free = maxSlots - usedSlots;
        double pct = maxSlots > 0 ? (100.0 * usedSlots / maxSlots) : 0;
        final String line1 = "§6Team " + team + ": §e" + usedSlots + "/" + maxSlots + " slots used §7(" + String.format("%.1f", pct) + "%)";
        src.sendFeedback(new LiteralText(line1), false);
        final String line2 = "§6Free slots: §e" + free + " §7| Items: " + itemCount + " | Full stacks: " + fullSlots;
        src.sendFeedback(new LiteralText(line2), false);
        return 1;
    }

    // ===== 15. boxinfo =====
    private static int boxInfo(ServerCommandSource src, String owner, String name) {
        if (!checkDb(src)) return 0;
        int pages = SharedBackpackMod.database.getBoxMaxPages(owner, name);
        int total = SharedBackpackMod.database.getTotalBoxItemCount(owner, name);
        int maxSlots = pages * 45;
        List<DatabaseManager.BackpackItem> boxItems = SharedBackpackMod.database.getBoxItems(owner, name);
        final String msg = "§6Box " + owner + "/" + name + ": §e" + pages + " pages | " + boxItems.size() + " slots | " + total + " items";
        src.sendFeedback(new LiteralText(msg), false);
        final String msg2 = "§6Max slots: §e" + maxSlots;
        src.sendFeedback(new LiteralText(msg2), false);
        return 1;
    }

    // ===== 16. boxlist =====
    private static int boxList(ServerCommandSource src, String owner) {
        if (!checkDb(src)) return 0;
        List<String> boxes = SharedBackpackMod.database.listBoxes(owner);
        if (boxes.isEmpty()) {
            final String msg = "§7No boxes for " + owner;
            src.sendFeedback(new LiteralText(msg), false);
        } else {
            final int boxCount = boxes.size();
            final String header = "§6Boxes for " + owner + " (" + boxCount + "):";
            src.sendFeedback(new LiteralText(header), false);
            for (String b : boxes) {
                final int itemTotal = SharedBackpackMod.database.getTotalBoxItemCount(owner, b);
                final String line = " §e- " + b + " §7(" + itemTotal + " items)";
                src.sendFeedback(new LiteralText(line), false);
            }
        }
        return 1;
    }

    // ===== 17. fillpage =====
    private static int fillPage(ServerCommandSource src, String team, int page, String itemId) {
        if (!checkDb(src)) return 0;
        Item item = Registry.ITEM.get(new Identifier(itemId));
        if (item == null || item == Items.AIR) {
            src.sendError(new LiteralText("§cItem not found: " + itemId));
            return 0;
        }
        int perPage = 45;
        int startSlot = page * perPage;
        final String name = getDisplayName(itemId);
        final String header = "§6Filling page " + page + " (slots " + startSlot + "-" + (startSlot + perPage - 1) + ") with " + name;
        src.sendFeedback(new LiteralText(header), false);
        long t0 = System.currentTimeMillis();
        int filled = 0;
        for (int s = startSlot; s < startSlot + perPage; s++) {
            if (SharedBackpackMod.database.setItem(team, s, itemId, 64, null, "DEBUG")) filled++;
        }
        long ms = System.currentTimeMillis() - t0;
        final String msg = "§aFilled " + filled + "/" + perPage + " slots (" + ms + "ms)";
        src.sendFeedback(new LiteralText(msg), false);
        return 1;
    }

    // ===== 18. stress =====
    private static int stressTest(ServerCommandSource src, String team, int total) {
        if (!checkDb(src)) return 0;
        final String header = "§6Starting stress test: adding " + total + " items to " + team + "...";
        src.sendFeedback(new LiteralText(header), false);
        long t0 = System.currentTimeMillis();
        int added = 0;
        int idx = 0;
        for (Item item : Registry.ITEM) {
            if (item == Items.AIR) continue;
            String id = Registry.ITEM.getId(item).toString();
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
        src.sendFeedback(new LiteralText(msg), false);
        final int newTotal = SharedBackpackMod.database.getTotalItemCount(team);
        final String msg2 = "§7Total items now: " + newTotal;
        src.sendFeedback(new LiteralText(msg2), false);
        return 1;
    }

    // ===== 19. verify =====
    private static int verifyIntegrity(ServerCommandSource src, String team) {
        if (!checkDb(src)) return 0;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(team);
        long t0 = System.currentTimeMillis();
        int totalCount = 0;
        Set<Integer> seenSlots = new HashSet<>();
        int duplicateSlots = 0;
        int zeroCount = 0;
        int negativeCount = 0;

        for (DatabaseManager.BackpackItem it : items) {
            totalCount += it.count;
            if (!seenSlots.add(it.slot)) duplicateSlots++;
            if (it.count <= 0) {
                if (it.count == 0) zeroCount++;
                else negativeCount++;
            }
        }
        long ms = System.currentTimeMillis() - t0;

        final boolean consistent = (duplicateSlots == 0 && zeroCount == 0 && negativeCount == 0);
        src.sendFeedback(new LiteralText(consistent ? "§a=== Integrity check PASSED ===" : "§c=== Integrity check FAILED ==="), false);
        final String info = "§6Slots: §e" + items.size() + " | Total items: §e" + totalCount + " | Unique slots: §e" + seenSlots.size();
        src.sendFeedback(new LiteralText(info), false);
        if (duplicateSlots > 0) {
            final int d = duplicateSlots;
            src.sendFeedback(new LiteralText("§cDuplicate slots: " + d), false);
        }
        if (zeroCount > 0) {
            final int z = zeroCount;
            src.sendFeedback(new LiteralText("§6Zero-count entries: " + z), false);
        }
        if (negativeCount > 0) {
            final int n = negativeCount;
            src.sendFeedback(new LiteralText("§cNegative count entries: " + n), false);
        }

        int dbTotal = SharedBackpackMod.database.getTotalItemCount(team);
        final boolean sumOk = (totalCount == dbTotal);
        final int tc = totalCount;
        final int dt = dbTotal;
        src.sendFeedback(new LiteralText(sumOk ? "§aSum check: OK (" + tc + " == " + dt + ")" : "§cSum check: MISMATCH (calc=" + tc + " vs db=" + dt + ")"), false);
        final String footer = "§7Verified in " + ms + "ms";
        src.sendFeedback(new LiteralText(footer), false);
        return 1;
    }

    // ===== 20. slottest =====
    private static int slotTest(ServerCommandSource src, String team, int slot) {
        if (!checkDb(src)) return 0;
        List<DatabaseManager.BackpackItem> allItems = SharedBackpackMod.database.getItems(team);
        DatabaseManager.BackpackItem found = null;
        for (DatabaseManager.BackpackItem it : allItems) {
            if (it.slot == slot) { found = it; break; }
        }
        if (found == null) {
            final String msg = "§7Slot " + slot + " in " + team + " is empty";
            src.sendFeedback(new LiteralText(msg), false);
        } else {
            final String name = getDisplayName(found.itemId);
            final String line1 = "§6Slot " + slot + ": §e" + name + " §7x" + found.count;
            src.sendFeedback(new LiteralText(line1), false);
            final String nbtPreview = found.nbt != null ? found.nbt.substring(0, Math.min(100, found.nbt.length())) + "..." : "null";
            final String line2 = "§6NBT: §e" + nbtPreview;
            src.sendFeedback(new LiteralText(line2), false);
            final String line3 = "§6Placed by: §e" + found.placedBy + " @ " + found.placedTime;
            src.sendFeedback(new LiteralText(line3), false);
        }
        return 1;
    }

    // ===== 21. teaminfo =====
    private static int teamInfo(ServerCommandSource src, String team) {
        if (!checkDb(src)) return 0;
        String info = SharedBackpackMod.database.getBackpackInfo(team);
        src.sendFeedback(new LiteralText("§6=== Team Info ==="), false);
        final String infoMsg = "§e" + info;
        src.sendFeedback(new LiteralText(infoMsg), false);

        int pages = SharedBackpackMod.database.getMaxPages(team);
        final String header = "§6Per-page breakdown:";
        src.sendFeedback(new LiteralText(header), false);
        for (int p = 0; p < pages; p++) {
            int ps = p * 45;
            int pe = ps + 45;
            int pi = 0;
            List<DatabaseManager.BackpackItem> pageItems = SharedBackpackMod.database.getItems(team);
            for (DatabaseManager.BackpackItem it : pageItems) {
                if (it.slot >= ps && it.slot < pe) pi++;
            }
            final int pageIdx = p;
            final int usedCount = pi;
            final String line = "  §7Page " + pageIdx + ": §e" + usedCount + " slots used";
            src.sendFeedback(new LiteralText(line), false);
        }
        return 1;
    }

    // ===== 22. allslots =====
    private static int allSlots(ServerCommandSource src, String team) {
        if (!checkDb(src)) return 0;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(team);
        final String header = "§6=== All " + items.size() + " slots for " + team + " ===";
        src.sendFeedback(new LiteralText(header), false);
        for (DatabaseManager.BackpackItem it : items) {
            final String name = getDisplayName(it.itemId);
            final String nbtFlag = it.nbt != null ? " §7[NBT]" : "";
            final String line = "§e[" + it.slot + "] §f" + name + " §7x" + it.count + nbtFlag + " §8by:" + it.placedBy;
            src.sendFeedback(new LiteralText(line), false);
        }
        return 1;
    }

    // ===== 23. clearslot =====
    private static int clearSlot(ServerCommandSource src, String team, int slot) {
        if (!checkDb(src)) return 0;
        boolean ok = SharedBackpackMod.database.removeItem(team, slot, 99999);
        if (ok) {
            final String msg = "§aSlot " + slot + " cleared in " + team;
            src.sendFeedback(new LiteralText(msg), false);
        } else {
            final String msg = "§7Slot " + slot + " was already empty";
            src.sendFeedback(new LiteralText(msg), false);
        }
        return 1;
    }

    // ===== 24. backup =====
    private static int forceBackup(ServerCommandSource src) {
        File dbFile = new File(src.getMinecraftServer().getRunDirectory(), "config/sharedbackpack/backpack.db");
        if (!dbFile.exists()) {
            src.sendError(new LiteralText("§cDB file not found: " + dbFile.getAbsolutePath()));
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
            src.sendFeedback(new LiteralText(msg), false);
        } catch (Exception e) {
            src.sendError(new LiteralText("§cBackup failed: " + e.getMessage()));
        }
        return 1;
    }

    // ===== 25. reloaddb =====
    private static int reloadDb(ServerCommandSource src) {
        if (SharedBackpackMod.database != null) {
            SharedBackpackMod.database.close();
        }
        SharedBackpackMod.database = new DatabaseManager(src.getMinecraftServer());
        SharedBackpackMod.database.init();
        boolean ok = SharedBackpackMod.database.isReady();
        final String msg = ok ? "§aDatabase reloaded successfully" : "§cDatabase reload FAILED";
        src.sendFeedback(new LiteralText(msg), false);
        return 1;
    }

    // ===== 26. clearall =====
    private static int clearAll(ServerCommandSource src, String team) {
        if (!checkDb(src)) return 0;
        long t0 = System.currentTimeMillis();
        int cleared = 0;
        List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(team);
        for (DatabaseManager.BackpackItem it : items) {
            SharedBackpackMod.database.removeItem(team, it.slot, 99999);
            cleared++;
        }
        long ms = System.currentTimeMillis() - t0;
        final int c = cleared;
        final String msg = "§aCleared " + c + " slots from team " + team + " (" + ms + "ms)";
        src.sendFeedback(new LiteralText(msg), false);
        return 1;
    }

    // ===== Helpers =====
    private static boolean checkDb(ServerCommandSource src) {
        if (SharedBackpackMod.database == null || !SharedBackpackMod.database.isReady()) {
            src.sendError(new LiteralText("§cDatabase not ready. Server still starting?"));
            return false;
        }
        return true;
    }

    private static String getDisplayName(String itemId) {
        Item item = Registry.ITEM.get(new Identifier(itemId));
        if (item == null || item == Items.AIR) return itemId;
        String cn = ChineseNames.get(item.getTranslationKey());
        if (cn != null) return cn + " (" + itemId + ")";
        return item.getName().getString() + " (" + itemId + ")";
    }
}
