package com.sharedbackpack.database;

import com.sharedbackpack.SharedBackpackMod;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DatabaseManager {
    private final File dbFile;
    private final File backupDir;
    private Connection connection;
    private static final int MAX_BACKUPS = 5;
    private static final int SLOTS_PER_PAGE = 45;

    public DatabaseManager(MinecraftServer server) {
        this(new File(server.getRunDirectory(), "config/sharedbackpack"));
    }

    DatabaseManager(File modDir) {
        modDir.mkdirs();
        this.dbFile = new File(modDir, "backpack.db");
        this.backupDir = new File(modDir, "backups");
        this.backupDir.mkdirs();
    }

    public void init() {
        try {
            backupBeforeInit();
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            connection.setAutoCommit(true);
            createTables();
            SharedBackpackMod.LOGGER.info("SQLite database ready: {}", dbFile.getAbsolutePath());
        } catch (Exception e) {
            SharedBackpackMod.LOGGER.error("Failed to init database", e);
            connection = null;
        }
    }

    public boolean isReady() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private void backupBeforeInit() {
        if (!dbFile.exists()) return;
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File backup = new File(backupDir, "backpack_backup_" + timestamp + ".db");
            Files.copy(dbFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            cleanupOldBackups();
            SharedBackpackMod.LOGGER.info("Database backup created: {}", backup.getName());
        } catch (Exception e) {
            SharedBackpackMod.LOGGER.warn("Failed to create backup", e);
        }
    }

    private void cleanupOldBackups() {
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("backpack_backup_") && name.endsWith(".db"));
        if (backups == null || backups.length <= MAX_BACKUPS) return;
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
            backups[i].delete();
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS backpack_meta ("
                    + "team_id TEXT PRIMARY KEY, max_pages INTEGER NOT NULL DEFAULT 1, "
                    + "created_at TEXT DEFAULT (datetime('now','localtime')), "
                    + "updated_at TEXT DEFAULT (datetime('now','localtime')))" );
            stmt.execute("CREATE TABLE IF NOT EXISTS backpack_items ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, team_id TEXT NOT NULL, item_id TEXT NOT NULL, "
                    + "count INTEGER NOT NULL DEFAULT 0, slot INTEGER NOT NULL DEFAULT 0, nbt TEXT, "
                    + "placed_by TEXT, placed_time TEXT, placed_count INTEGER DEFAULT 0, "
                    + "last_modified_by TEXT, last_modified_time TEXT, UNIQUE(team_id, slot))");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_team_items ON backpack_items(team_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_item_search ON backpack_items(item_id)");

            // Add columns if not exist (for existing databases)
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN placed_by TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN placed_time TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN placed_count INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN last_modified_by TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN last_modified_time TEXT"); } catch (SQLException ignored) {}

            // Player boxes
            stmt.execute("CREATE TABLE IF NOT EXISTS player_box_meta ("
                    + "owner_uuid TEXT NOT NULL, box_name TEXT NOT NULL, max_pages INTEGER NOT NULL DEFAULT 1, "
                    + "created_at TEXT DEFAULT (datetime('now','localtime')), "
                    + "updated_at TEXT DEFAULT (datetime('now','localtime')), "
                    + "PRIMARY KEY(owner_uuid, box_name))");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_box_items ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uuid TEXT NOT NULL, box_name TEXT NOT NULL, "
                    + "item_id TEXT NOT NULL, count INTEGER NOT NULL DEFAULT 0, slot INTEGER NOT NULL DEFAULT 0, "
                    + "nbt TEXT, placed_by TEXT, placed_time TEXT, placed_count INTEGER DEFAULT 0, "
                    + "last_modified_by TEXT, last_modified_time TEXT, UNIQUE(owner_uuid, box_name, slot))");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_box_owner ON player_box_items(owner_uuid, box_name)");

            // Player binds (persistent across restarts)
            stmt.execute("CREATE TABLE IF NOT EXISTS player_binds ("
                    + "player_uuid TEXT PRIMARY KEY, item_id TEXT NOT NULL, box_target TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS storage_view_preferences ("
                    + "player_uuid TEXT NOT NULL, storage_owner TEXT NOT NULL, storage_type INTEGER NOT NULL, "
                    + "box_name TEXT NOT NULL, page INTEGER NOT NULL DEFAULT 0, sort INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY(player_uuid, storage_owner, storage_type, box_name))");
        }
    }

    public synchronized List<BackpackItem> getItems(String teamId) {
        if (!isReady()) return Collections.emptyList();
        List<BackpackItem> items = new ArrayList<>();
        String sql = "SELECT slot, item_id, count, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time FROM backpack_items WHERE team_id = ? ORDER BY slot";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, teamId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new BackpackItem(
                    rs.getInt("slot"),
                    rs.getString("item_id"),
                    rs.getInt("count"),
                    rs.getString("nbt"),
                    rs.getString("placed_by"),
                    rs.getString("placed_time"),
                    rs.getInt("placed_count"),
                    rs.getString("last_modified_by"),
                    rs.getString("last_modified_time")
                ));
            }
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to get items for team {}", teamId, e);
        }
        return items;
    }

    public synchronized Map<String, List<BackpackItem>> getUnionItems(List<String> teamIds) {
        Map<String, List<BackpackItem>> result = new LinkedHashMap<>();
        for (String teamId : teamIds) {
            result.put(teamId, getItems(teamId));
        }
        return result;
    }

    public synchronized boolean addItem(String teamId, String itemId, int count, String nbt, String playerName) {
        return depositAtomic(teamId, null, itemId, count, nbt, playerName);
    }

    public synchronized boolean depositAtomic(String owner, String box, String itemId, int count,
                                              String nbt, String playerName) {
        if (!isReady() || owner == null || itemId == null || count <= 0) return false;
        boolean transactionStarted = false;
        boolean committed = false;
        try {
            if (!connection.getAutoCommit()) return false;
            connection.setAutoCommit(false);
            transactionStarted = true;
            String table = box == null ? "backpack_items" : "player_box_items";
            String scope = box == null ? "team_id=?" : "owner_uuid=? AND box_name=?";
            String metaTable = box == null ? "backpack_meta" : "player_box_meta";
            int maxPages = 1;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT max_pages FROM " + metaTable + " WHERE " + scope)) {
                bindStorage(ps, 1, owner, box);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) maxPages = rs.getInt(1);
                }
            }
            int maxSlots = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) maxPages * SLOTS_PER_PAGE));
            Set<Integer> used = new HashSet<>();
            List<int[]> stacks = new ArrayList<>();
            long available = 0;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT slot, item_id, count, nbt, placed_count FROM " + table + " WHERE " + scope + " ORDER BY slot")) {
                bindStorage(ps, 1, owner, box);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int slot = rs.getInt("slot");
                        if (slot >= 0 && slot < maxSlots) used.add(slot);
                        int existing = rs.getInt("count");
                        String storedNbt = rs.getString("nbt");
                        if (available < count && itemId.equals(rs.getString("item_id")) && existing >= 0 && existing < 64
                                && Objects.equals(nbt == null ? "" : nbt, storedNbt == null ? "" : storedNbt)) {
                            stacks.add(new int[] {slot, existing, rs.getInt("placed_count")});
                            available += 64 - existing;
                        }
                    }
                }
            }
            available += ((long) maxSlots - used.size()) * 64;
            if (available < count) return false;

            String now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            int remaining = count;
            if (!stacks.isEmpty()) {
                String placementUpdate = box == null ? ", placed_count=?" : "";
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + table + " SET count=count+?" + placementUpdate + ", last_modified_by=?, last_modified_time=? WHERE " + scope + " AND slot=?")) {
                    for (int[] stack : stacks) {
                        if (remaining == 0) break;
                        int added = Math.min(remaining, 64 - stack[1]);
                        int next = 1;
                        ps.setInt(next++, added);
                        if (box == null) ps.setInt(next++, (int) Math.min((long) stack[2] + added, 999_999_999L));
                        ps.setString(next++, playerName);
                        ps.setString(next++, now);
                        next = bindStorage(ps, next, owner, box);
                        ps.setInt(next, stack[0]);
                        if (ps.executeUpdate() != 1) throw new SQLException("Deposit target row changed");
                        remaining -= added;
                    }
                }
            }
            if (remaining > 0) {
                String ownerColumns = box == null ? "team_id" : "owner_uuid, box_name";
                String ownerValues = box == null ? "?" : "?, ?";
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + table + " (" + ownerColumns + ", item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES ("
                                + ownerValues + ", ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    int slot = 0;
                    while (remaining > 0) {
                        while (slot < maxSlots && used.contains(slot)) slot++;
                        if (slot >= maxSlots) throw new SQLException("Deposit capacity changed");
                        int added = Math.min(remaining, 64);
                        int next = bindStorage(ps, 1, owner, box);
                        ps.setString(next++, itemId);
                        ps.setInt(next++, added);
                        ps.setInt(next++, slot++);
                        ps.setString(next++, nbt);
                        ps.setString(next++, playerName);
                        ps.setString(next++, now);
                        ps.setInt(next++, added);
                        ps.setString(next++, playerName);
                        ps.setString(next, now);
                        if (ps.executeUpdate() != 1) throw new SQLException("Deposit row was not inserted");
                        remaining -= added;
                    }
                }
            }
            touchStorageTimestamp(owner, box);
            connection.commit();
            committed = true;
            return true;
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to deposit item {} into {}/{}", itemId, owner, box, e);
            return false;
        } finally {
            finishStorageTransaction(transactionStarted, committed);
        }
    }

    public synchronized boolean removeMatchingAtomic(String owner, String box, String itemId, String nbt, int count) {
        if (!isReady() || owner == null || itemId == null || count <= 0) return false;
        final NbtCompound target;
        try {
            target = parseStoredNbt(nbt);
        } catch (CommandSyntaxException e) {
            SharedBackpackMod.LOGGER.warn("Cannot remove item with invalid NBT from {}/{}", owner, box, e);
            return false;
        }
        boolean transactionStarted = false;
        boolean committed = false;
        try {
            if (!connection.getAutoCommit()) return false;
            connection.setAutoCommit(false);
            transactionStarted = true;
            String table = box == null ? "backpack_items" : "player_box_items";
            String scope = box == null ? "team_id=?" : "owner_uuid=? AND box_name=?";
            List<int[]> matches = new ArrayList<>();
            long available = 0;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT slot, count, nbt FROM " + table + " WHERE " + scope + " AND item_id=? ORDER BY slot")) {
                int next = bindStorage(ps, 1, owner, box);
                ps.setString(next, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int existing = rs.getInt("count");
                        if (existing <= 0) continue;
                        try {
                            if (!Objects.equals(target, parseStoredNbt(rs.getString("nbt")))) continue;
                        } catch (CommandSyntaxException invalidStoredNbt) {
                            // A corrupt row must not match or prevent access to other valid rows.
                            continue;
                        }
                        matches.add(new int[] {rs.getInt("slot"), existing});
                        available += existing;
                        if (available >= count) break;
                    }
                }
            }
            if (available < count) return false;
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE " + scope + " AND slot=?");
                 PreparedStatement update = connection.prepareStatement(
                    "UPDATE " + table + " SET count=count-? WHERE " + scope + " AND slot=?")) {
                int remaining = count;
                for (int[] row : matches) {
                    int removed = Math.min(remaining, row[1]);
                    PreparedStatement ps = removed == row[1] ? delete : update;
                    int next = 1;
                    if (ps == update) ps.setInt(next++, removed);
                    next = bindStorage(ps, next, owner, box);
                    ps.setInt(next, row[0]);
                    if (ps.executeUpdate() != 1) throw new SQLException("Removal target row changed");
                    remaining -= removed;
                    if (remaining == 0) break;
                }
            }
            touchStorageTimestamp(owner, box);
            connection.commit();
            committed = true;
            return true;
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to remove matching item {} from {}/{}", itemId, owner, box, e);
            return false;
        } finally {
            finishStorageTransaction(transactionStarted, committed);
        }
    }

    private static NbtCompound parseStoredNbt(String nbt) throws CommandSyntaxException {
        if (nbt == null || nbt.isEmpty()) return null;
        NbtCompound parsed = StringNbtReader.parse(nbt);
        return parsed.isEmpty() ? null : parsed;
    }

    private static int bindStorage(PreparedStatement ps, int next, String owner, String box) throws SQLException {
        ps.setString(next++, owner);
        if (box != null) ps.setString(next++, box);
        return next;
    }

    private void touchStorageTimestamp(String owner, String box) throws SQLException {
        String sql = box == null
                ? "INSERT INTO backpack_meta (team_id, max_pages, created_at, updated_at) VALUES (?, 1, datetime('now','localtime'), datetime('now','localtime')) ON CONFLICT(team_id) DO UPDATE SET updated_at=datetime('now','localtime')"
                : "INSERT INTO player_box_meta (owner_uuid, box_name, max_pages, created_at, updated_at) VALUES (?, ?, 1, datetime('now','localtime'), datetime('now','localtime')) ON CONFLICT(owner_uuid, box_name) DO UPDATE SET updated_at=datetime('now','localtime')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindStorage(ps, 1, owner, box);
            if (ps.executeUpdate() != 1) throw new SQLException("Storage metadata was not updated");
        }
    }

    private void finishStorageTransaction(boolean started, boolean committed) {
        if (!started) return;
        try {
            if (!committed) connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            // Never allow a later operation to commit a failed transaction's partial changes.
            SharedBackpackMod.LOGGER.error("Failed to finish storage transaction; closing database", e);
            close();
            connection = null;
        }
    }

    public synchronized boolean removeItem(String teamId, int slot, int count) {
        if (!isReady()) return false;
        try {
            String getSql = "SELECT item_id, count FROM backpack_items WHERE team_id = ? AND slot = ?";
            try (PreparedStatement ps = connection.prepareStatement(getSql)) {
                ps.setString(1, teamId);
                ps.setInt(2, slot);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int existing = rs.getInt("count");
                    if (count >= existing) {
                        String delSql = "DELETE FROM backpack_items WHERE team_id = ? AND slot = ?";
                        try (PreparedStatement del = connection.prepareStatement(delSql)) {
                            del.setString(1, teamId);
                            del.setInt(2, slot);
                            del.executeUpdate();
                        }
                    } else {
                        String updSql = "UPDATE backpack_items SET count = count - ? WHERE team_id = ? AND slot = ?";
                        try (PreparedStatement upd = connection.prepareStatement(updSql)) {
                            upd.setInt(1, count);
                            upd.setString(2, teamId);
                            upd.setInt(3, slot);
                            upd.executeUpdate();
                        }
                    }
                    updateTimestamp(teamId);
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to remove item slot {} for team {}", slot, teamId, e);
            return false;
        }
    }

    public synchronized int getMaxPages(String teamId) {
        if (!isReady()) return 1;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT max_pages FROM backpack_meta WHERE team_id = ?")) {
            ps.setString(1, teamId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("max_pages");
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to get max_pages for team {}", teamId, e);
        }
        return 1;
    }

    public synchronized boolean upgradePages(String teamId, int additionalPages) {
        if (!isReady()) return false;
        try {
            String upsertSql = "INSERT INTO backpack_meta (team_id, max_pages, created_at, updated_at) "
                    + "VALUES (?, 1 + ?, datetime('now','localtime'), datetime('now','localtime')) "
                    + "ON CONFLICT(team_id) DO UPDATE SET max_pages = max_pages + ?, "
                    + "updated_at = datetime('now','localtime')";
            try (PreparedStatement ps = connection.prepareStatement(upsertSql)) {
                ps.setString(1, teamId);
                ps.setInt(2, additionalPages);
                ps.setInt(3, additionalPages);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to upgrade pages for team {}", teamId, e);
            return false;
        }
    }

    public synchronized int getTotalItemCount(String teamId) {
        if (!isReady()) return 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(SUM(count), 0) FROM backpack_items WHERE team_id = ?")) {
            ps.setString(1, teamId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to get total count for team {}", teamId, e);
        }
        return 0;
    }

    public synchronized String getBackpackInfo(String teamId) {
        if (!isReady()) return "Database not ready";
        int pages = getMaxPages(teamId);
        int total = getTotalItemCount(teamId);
        int maxSlots = pages * SLOTS_PER_PAGE;
        int[] usedSlots = {0};
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(DISTINCT slot) FROM backpack_items WHERE team_id = ?")) {
            ps.setString(1, teamId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) usedSlots[0] = rs.getInt(1);
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to get slot count", e);
        }
        return String.format("Team: %s | Pages: %d | Items: %d | Slots: %d/%d",
                teamId, pages, total, usedSlots[0], maxSlots);
    }

    public synchronized boolean setItem(String teamId, int slot, String itemId, int count, String nbt, String playerName) {
        if (!isReady()) return false;
        String now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            String delSql = "DELETE FROM backpack_items WHERE team_id = ? AND slot = ?";
            try (PreparedStatement ps = connection.prepareStatement(delSql)) {
                ps.setString(1, teamId);
                ps.setInt(2, slot);
                ps.executeUpdate();
            }
            if (count > 0) {
                String insSql = "INSERT INTO backpack_items (team_id, item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insSql)) {
                    ps.setString(1, teamId);
                    ps.setString(2, itemId);
                    ps.setInt(3, count);
                    ps.setInt(4, slot);
                    ps.setString(5, nbt);
                    ps.setString(6, playerName);
                    ps.setString(7, now);
                    ps.setInt(8, count);
                    ps.setString(9, playerName);
                    ps.setString(10, now);
                    ps.executeUpdate();
                }
            }
            updateTimestamp(teamId);
            return true;
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to set item {} slot {} for team {}", itemId, slot, teamId, e);
            return false;
        }
    }

    public synchronized void sortItems(String teamId) {
        if (!isReady()) return;
        try {
            List<BackpackItem> all = getItems(teamId);
            Map<String, BackpackItem> groups = new LinkedHashMap<>();
            for (BackpackItem item : all) {
                String key = item.itemId + "\0" + (item.nbt != null ? item.nbt : "");
                BackpackItem existing = groups.get(key);
                if (existing != null) {
                    groups.put(key, new BackpackItem(
                        0, item.itemId, existing.count + item.count, item.nbt,
                        existing.placedBy, existing.placedTime,
                        (int) Math.min((long)existing.placedCount + item.placedCount, 999_999_999L),
                        item.lastModifiedBy, item.lastModifiedTime));
                } else {
                    groups.put(key, new BackpackItem(
                        0, item.itemId, item.count, item.nbt,
                        item.placedBy, item.placedTime, item.placedCount,
                        item.lastModifiedBy, item.lastModifiedTime));
                }
            }

            connection.setAutoCommit(false);
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM backpack_items WHERE team_id = ?")) {
                del.setString(1, teamId);
                del.executeUpdate();
            }

            String insSql = "INSERT INTO backpack_items (team_id, item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            int slot = 0;
            try (PreparedStatement ps = connection.prepareStatement(insSql)) {
                for (BackpackItem item : groups.values()) {
                    int remaining = item.count;
                    while (remaining > 0) {
                        int stack = Math.min(remaining, 64);
                        ps.setString(1, teamId);
                        ps.setString(2, item.itemId);
                        ps.setInt(3, stack);
                        ps.setInt(4, slot);
                        ps.setString(5, item.nbt);
                        ps.setString(6, item.placedBy);
                        ps.setString(7, item.placedTime);
                        ps.setInt(8, item.placedCount);
                        ps.setString(9, item.lastModifiedBy);
                        ps.setString(10, item.lastModifiedTime);
                        ps.addBatch();
                        remaining -= stack;
                        slot++;
                    }
                }
                ps.executeBatch();
            }

            connection.commit();
            connection.setAutoCommit(true);
            updateTimestamp(teamId);
            SharedBackpackMod.LOGGER.info("Sorted {} groups into {} slots for team {}", groups.size(), slot, teamId);
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to sort items for team {}", teamId, e);
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /** Sort only items within a page range, consolidating identical items */
    public synchronized void sortPageItems(String teamId, int startSlot, int endSlot) {
        if (!isReady()) return;
        sortSlots("backpack_items", "team_id", teamId, startSlot, endSlot);
    }

    private void sortSlots(String table, String idCol, String idVal, int start, int end) {
        try {
            List<BackpackItem> items = new ArrayList<>();
            String sel = "SELECT slot, item_id, count, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time FROM " + table + " WHERE " + idCol + " = ? AND slot >= ? AND slot < ? ORDER BY slot";
            try (PreparedStatement ps = connection.prepareStatement(sel)) {
                ps.setString(1, idVal); ps.setInt(2, start); ps.setInt(3, end);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) items.add(new BackpackItem(rs.getInt("slot"), rs.getString("item_id"),
                    rs.getInt("count"), rs.getString("nbt"), rs.getString("placed_by"),
                    rs.getString("placed_time"), rs.getInt("placed_count"),
                    rs.getString("last_modified_by"), rs.getString("last_modified_time")));
            }
            Map<String, BackpackItem> groups = new LinkedHashMap<>();
            for (BackpackItem it : items) {
                String key = it.itemId + "\0" + (it.nbt != null ? it.nbt : "");
                BackpackItem ex = groups.get(key);
                if (ex != null) {
                    groups.put(key, new BackpackItem(0, it.itemId, ex.count + it.count, it.nbt,
                        ex.placedBy, ex.placedTime, (int) Math.min((long)ex.placedCount + it.placedCount, 999_999_999L),
                        it.lastModifiedBy, it.lastModifiedTime));
                } else groups.put(key, it);
            }
            connection.setAutoCommit(false);
            try (PreparedStatement d = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE " + idCol + " = ? AND slot >= ? AND slot < ?")) {
                d.setString(1, idVal); d.setInt(2, start); d.setInt(3, end); d.executeUpdate();
            }
            int slot = start;
            String ins = "INSERT INTO " + table + " (" + idCol + ", item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(ins)) {
                for (BackpackItem it : groups.values()) {
                    int rem = it.count;
                    while (rem > 0 && slot < end) {
                        int st = Math.min(rem, 64);
                        ps.setString(1, idVal); ps.setString(2, it.itemId);
                        ps.setInt(3, st); ps.setInt(4, slot); ps.setString(5, it.nbt);
                        ps.setString(6, it.placedBy); ps.setString(7, it.placedTime);
                        ps.setInt(8, it.placedCount); ps.setString(9, it.lastModifiedBy);
                        ps.setString(10, it.lastModifiedTime); ps.addBatch();
                        rem -= st; slot++;
                    }
                    if (rem > 0) break;
                }
                ps.executeBatch();
            }
            connection.commit(); connection.setAutoCommit(true);
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed page sort", e);
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // ========== Player Box Methods ==========

    private void updateBoxTimestamp(String owner, String box) {
        if (!isReady()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_box_meta (owner_uuid, box_name, max_pages, created_at, updated_at) VALUES (?, ?, 1, datetime('now','localtime'), datetime('now','localtime')) ON CONFLICT(owner_uuid, box_name) DO UPDATE SET updated_at = datetime('now','localtime')")) {
            ps.setString(1, owner); ps.setString(2, box); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized void createBox(String owner, String box) {
        updateBoxTimestamp(owner, box);
    }

    public synchronized void deleteBox(String owner, String box) {
        if (!isReady()) return;
        try {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_box_items WHERE owner_uuid=? AND box_name=?")) {
                ps.setString(1, owner); ps.setString(2, box); ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_box_meta WHERE owner_uuid=? AND box_name=?")) {
                ps.setString(1, owner); ps.setString(2, box); ps.executeUpdate();
            }
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed deleteBox", e); }
    }

    public synchronized List<String> listBoxes(String owner) {
        List<String> boxes = new ArrayList<>();
        if (!isReady()) return boxes;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT box_name FROM player_box_meta WHERE owner_uuid=? ORDER BY box_name")) {
            ps.setString(1, owner);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) boxes.add(rs.getString("box_name"));
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to list boxes for {}", owner, e);
        }
        return boxes;
    }

    public synchronized List<BackpackItem> getBoxItems(String owner, String box) {
        if (!isReady()) return Collections.emptyList();
        List<BackpackItem> items = new ArrayList<>();
        String sql = "SELECT slot, item_id, count, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time FROM player_box_items WHERE owner_uuid=? AND box_name=? ORDER BY slot";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner); ps.setString(2, box);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) items.add(new BackpackItem(rs.getInt("slot"), rs.getString("item_id"),
                rs.getInt("count"), rs.getString("nbt"), rs.getString("placed_by"), rs.getString("placed_time"),
                rs.getInt("placed_count"), rs.getString("last_modified_by"), rs.getString("last_modified_time")));
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed getBoxItems {}/{}", owner, box, e); }
        return items;
    }

    public synchronized int getTotalBoxItemCount(String owner, String box) {
        if (!isReady()) return 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(SUM(count),0) FROM player_box_items WHERE owner_uuid=? AND box_name=?")) {
            ps.setString(1, owner); ps.setString(2, box);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed getTotalBoxItemCount", e); }
        return 0;
    }

    public synchronized int getBoxMaxPages(String owner, String box) {
        if (!isReady()) return 1;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT max_pages FROM player_box_meta WHERE owner_uuid=? AND box_name=?")) {
            ps.setString(1, owner); ps.setString(2, box);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("max_pages");
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed getBoxMaxPages", e); }
        return 1;
    }

    public synchronized boolean upgradeBoxPages(String owner, String box, int additionalPages) {
        if (!isReady()) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_box_meta (owner_uuid, box_name, max_pages, created_at, updated_at) "
                        + "VALUES (?, ?, 1 + ?, datetime('now','localtime'), datetime('now','localtime')) "
                        + "ON CONFLICT(owner_uuid, box_name) DO UPDATE SET max_pages = max_pages + ?, "
                        + "updated_at = datetime('now','localtime')")) {
            ps.setString(1, owner);
            ps.setString(2, box);
            ps.setInt(3, additionalPages);
            ps.setInt(4, additionalPages);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to upgrade box {}/{}", owner, box, e);
            return false;
        }
    }

    public synchronized boolean addBoxItem(String owner, String box, String itemId, int count, String nbt, String playerName) {
        if (box == null) return false;
        return depositAtomic(owner, box, itemId, count, nbt, playerName);
    }

    public synchronized boolean removeBoxItem(String owner, String box, int slot, int count) {
        if (!isReady()) return false;
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT count FROM player_box_items WHERE owner_uuid=? AND box_name=? AND slot=?")) {
                ps.setString(1, owner); ps.setString(2, box); ps.setInt(3, slot);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int existing = rs.getInt("count");
                    if (count >= existing) {
                        try (PreparedStatement d = connection.prepareStatement(
                                "DELETE FROM player_box_items WHERE owner_uuid=? AND box_name=? AND slot=?")) {
                            d.setString(1, owner); d.setString(2, box); d.setInt(3, slot); d.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement u = connection.prepareStatement(
                                "UPDATE player_box_items SET count=count-? WHERE owner_uuid=? AND box_name=? AND slot=?")) {
                            u.setInt(1, count); u.setString(2, owner); u.setString(3, box); u.setInt(4, slot); u.executeUpdate();
                        }
                    }
                    updateBoxTimestamp(owner, box);
                    return true;
                }
            }
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed removeBoxItem", e); }
        return false;
    }

    public synchronized boolean setBoxItem(String owner, String box, int slot, String itemId, int count, String nbt, String playerName) {
        if (!isReady()) return false;
        String now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM player_box_items WHERE owner_uuid=? AND box_name=? AND slot=?")) {
                ps.setString(1, owner); ps.setString(2, box); ps.setInt(3, slot); ps.executeUpdate();
            }
            if (count > 0) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO player_box_items (owner_uuid, box_name, item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, owner); ps.setString(2, box); ps.setString(3, itemId);
                    ps.setInt(4, count); ps.setInt(5, slot); ps.setString(6, nbt);
                    ps.setString(7, playerName); ps.setString(8, now); ps.setInt(9, count);
                    ps.setString(10, playerName); ps.setString(11, now); ps.executeUpdate();
                }
            }
            updateBoxTimestamp(owner, box);
            return true;
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed setBoxItem", e); return false; }
    }

    public synchronized void sortBoxItems(String owner, String box) {
        if (!isReady()) return;
        try {
            List<BackpackItem> all = getBoxItems(owner, box);
            Map<String, BackpackItem> groups = new LinkedHashMap<>();
            for (BackpackItem item : all) {
                String key = item.itemId + "\0" + (item.nbt != null ? item.nbt : "");
                BackpackItem ex = groups.get(key);
                if (ex != null) {
                    groups.put(key, new BackpackItem(0, item.itemId, ex.count + item.count, item.nbt,
                        ex.placedBy, ex.placedTime, (int) Math.min((long)ex.placedCount + item.placedCount, 999_999_999L),
                        item.lastModifiedBy, item.lastModifiedTime));
                } else groups.put(key, item);
            }
            connection.setAutoCommit(false);
            try (PreparedStatement d = connection.prepareStatement(
                    "DELETE FROM player_box_items WHERE owner_uuid=? AND box_name=?")) {
                d.setString(1, owner); d.setString(2, box); d.executeUpdate();
            }
            int slot = 0;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_box_items (owner_uuid, box_name, item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                for (BackpackItem item : groups.values()) {
                    int rem = item.count;
                    while (rem > 0) {
                        int st = Math.min(rem, 64);
                        ps.setString(1, owner); ps.setString(2, box); ps.setString(3, item.itemId);
                        ps.setInt(4, st); ps.setInt(5, slot); ps.setString(6, item.nbt);
                        ps.setString(7, item.placedBy); ps.setString(8, item.placedTime);
                        ps.setInt(9, item.placedCount); ps.setString(10, item.lastModifiedBy);
                        ps.setString(11, item.lastModifiedTime); ps.addBatch();
                        rem -= st; slot++;
                    }
                }
                ps.executeBatch();
            }
            connection.commit(); connection.setAutoCommit(true);
            updateBoxTimestamp(owner, box);
            SharedBackpackMod.LOGGER.info("Sorted box {}/{}: {} groups, {} slots", owner, box, groups.size(), slot);
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed sortBoxItems {}/{}", owner, box, e);
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public synchronized void sortPageBoxItems(String owner, String box, int start) {
        if (!isReady()) return;
        int end = start + SLOTS_PER_PAGE;
        try {
            List<BackpackItem> items = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT slot, item_id, count, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time FROM player_box_items WHERE owner_uuid=? AND box_name=? AND slot>=? AND slot<? ORDER BY slot")) {
                ps.setString(1, owner); ps.setString(2, box); ps.setInt(3, start); ps.setInt(4, end);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) items.add(new BackpackItem(rs.getInt("slot"), rs.getString("item_id"),
                    rs.getInt("count"), rs.getString("nbt"), rs.getString("placed_by"),
                    rs.getString("placed_time"), rs.getInt("placed_count"),
                    rs.getString("last_modified_by"), rs.getString("last_modified_time")));
            }
            Map<String, BackpackItem> groups = new LinkedHashMap<>();
            for (BackpackItem it : items) {
                String key = it.itemId + "\0" + (it.nbt != null ? it.nbt : "");
                BackpackItem ex = groups.get(key);
                if (ex != null) groups.put(key, new BackpackItem(0, it.itemId, ex.count + it.count, it.nbt,
                    ex.placedBy, ex.placedTime, (int) Math.min((long)ex.placedCount + it.placedCount, 999_999_999L),
                    it.lastModifiedBy, it.lastModifiedTime));
                else groups.put(key, it);
            }
            connection.setAutoCommit(false);
            try (PreparedStatement d = connection.prepareStatement(
                    "DELETE FROM player_box_items WHERE owner_uuid=? AND box_name=? AND slot>=? AND slot<?")) {
                d.setString(1, owner); d.setString(2, box); d.setInt(3, start); d.setInt(4, end); d.executeUpdate();
            }
            int slot = start;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_box_items (owner_uuid, box_name, item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                for (BackpackItem it : groups.values()) {
                    int rem = it.count;
                    while (rem > 0 && slot < end) {
                        int st = Math.min(rem, 64);
                        ps.setString(1, owner); ps.setString(2, box); ps.setString(3, it.itemId);
                        ps.setInt(4, st); ps.setInt(5, slot); ps.setString(6, it.nbt);
                        ps.setString(7, it.placedBy); ps.setString(8, it.placedTime);
                        ps.setInt(9, it.placedCount); ps.setString(10, it.lastModifiedBy);
                        ps.setString(11, it.lastModifiedTime); ps.addBatch();
                        rem -= st; slot++;
                    }
                    if (rem > 0) break;
                }
                ps.executeBatch();
            }
            connection.commit(); connection.setAutoCommit(true);
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed sortPageBoxItems", e);
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to close database", e);
        }
    }

    // ========== Player Bind Persistence ==========

    public synchronized void saveBind(String uuid, String itemId, String boxTarget) {
        if (!isReady()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_binds(player_uuid, item_id, box_target) VALUES(?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET item_id=excluded.item_id, box_target=excluded.box_target")) {
            ps.setString(1, uuid); ps.setString(2, itemId); ps.setString(3, boxTarget); ps.executeUpdate();
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed saveBind", e); }
    }

    public synchronized void deleteBind(String uuid) {
        if (!isReady()) return;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_binds WHERE player_uuid=?")) {
            ps.setString(1, uuid); ps.executeUpdate();
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed deleteBind", e); }
    }

    public static class BindEntry { public final String itemId; public final String boxTarget;
        BindEntry(String i, String b) { itemId=i; boxTarget=b; } }

    public synchronized Map<String, BindEntry> loadAllBinds() {
        Map<String, BindEntry> map = new HashMap<>();
        if (!isReady()) return map;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT player_uuid, item_id, box_target FROM player_binds")) {
            while (rs.next()) map.put(rs.getString("player_uuid"), new BindEntry(rs.getString("item_id"), rs.getString("box_target")));
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed loadAllBinds", e); }
        return map;
    }

    public synchronized int[] loadViewPreferences(String playerUuid, String owner, String box) {
        if (!isReady()) return new int[] {0, 0};
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT page, sort FROM storage_view_preferences WHERE player_uuid=? AND storage_owner=? AND storage_type=? AND box_name=?")) {
            ps.setString(1, playerUuid);
            ps.setString(2, owner);
            ps.setInt(3, box == null ? 0 : 1);
            ps.setString(4, box == null ? "" : box);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new int[] {Math.max(0, rs.getInt("page")), Math.max(0, Math.min(5, rs.getInt("sort")))};
            }
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to load view preferences for {} in {}/{}", playerUuid, owner, box, e);
        }
        return new int[] {0, 0};
    }

    public synchronized void saveViewPreferences(String playerUuid, String owner, String box, int page, int sort) {
        if (!isReady()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO storage_view_preferences (player_uuid, storage_owner, storage_type, box_name, page, sort) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(player_uuid, storage_owner, storage_type, box_name) DO UPDATE SET page=excluded.page, sort=excluded.sort")) {
            ps.setString(1, playerUuid);
            ps.setString(2, owner);
            ps.setInt(3, box == null ? 0 : 1);
            ps.setString(4, box == null ? "" : box);
            ps.setInt(5, Math.max(0, page));
            ps.setInt(6, Math.max(0, Math.min(5, sort)));
            ps.executeUpdate();
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to save view preferences for {} in {}/{}", playerUuid, owner, box, e);
        }
    }

    private void updateTimestamp(String teamId) {
        if (!isReady()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO backpack_meta (team_id, max_pages, created_at, updated_at) VALUES (?, 1, datetime('now','localtime'), datetime('now','localtime')) ON CONFLICT(team_id) DO UPDATE SET updated_at = datetime('now','localtime')")) {
            ps.setString(1, teamId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public static class BackpackItem {
        public final int slot;
        public final String itemId;
        public final int count;
        public final String nbt;
        public final String placedBy;
        public final String placedTime;
        public final int placedCount;
        public final String lastModifiedBy;
        public final String lastModifiedTime;

        public BackpackItem(int slot, String itemId, int count, String nbt,
                           String placedBy, String placedTime, int placedCount,
                           String lastModifiedBy, String lastModifiedTime) {
            this.slot = slot;
            this.itemId = itemId;
            this.count = count;
            this.nbt = nbt;
            this.placedBy = placedBy;
            this.placedTime = placedTime;
            this.placedCount = placedCount;
            this.lastModifiedBy = lastModifiedBy;
            this.lastModifiedTime = lastModifiedTime;
        }
    }
}
