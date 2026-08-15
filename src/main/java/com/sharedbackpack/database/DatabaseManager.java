package com.sharedbackpack.database;

import com.sharedbackpack.SharedBackpackMod;
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
        File modDir = new File(server.getRunDirectory(), "config/sharedbackpack");
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
        if (!isReady()) return false;
        int maxPages = getMaxPages(teamId);
        int maxSlots = maxPages * SLOTS_PER_PAGE;
        String now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            // First try to stack with existing slot (STRICT: must match item_id AND nbt exactly)
            String findSql = "SELECT slot, count, placed_count FROM backpack_items WHERE team_id = ? AND item_id = ? AND ";
            if (nbt != null) {
                findSql += "nbt = ?";
            } else {
                findSql += "(nbt IS NULL OR nbt = '')";
            }

            try (PreparedStatement ps = connection.prepareStatement(findSql)) {
                ps.setString(1, teamId);
                ps.setString(2, itemId);
                if (nbt != null) ps.setString(3, nbt);

                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    int existing = rs.getInt("count");
                    if (existing < 64) {
                        int canAdd = Math.min(count, 64 - existing);
                        int newPlacedCount = (int) Math.min((long)rs.getInt("placed_count") + canAdd, 999_999_999L);
                        String updSql = "UPDATE backpack_items SET count = count + ?, placed_count = ?, last_modified_by = ?, last_modified_time = ? WHERE team_id = ? AND slot = ?";
                        try (PreparedStatement upd = connection.prepareStatement(updSql)) {
                            upd.setInt(1, canAdd);
                            upd.setInt(2, newPlacedCount);
                            upd.setString(3, playerName);
                            upd.setString(4, now);
                            upd.setString(5, teamId);
                            upd.setInt(6, slot);
                            upd.executeUpdate();
                        }
                        count -= canAdd;
                        if (count <= 0) {
                            updateTimestamp(teamId);
                            return true;
                        }
                    }
                }
            }

            // Find empty slots
            while (count > 0) {
                int emptySlot = findEmptySlot(teamId, maxSlots);
                if (emptySlot < 0) return false;

                int toAdd = Math.min(count, 64);
                String insSql = "INSERT INTO backpack_items (team_id, item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insSql)) {
                    ps.setString(1, teamId);
                    ps.setString(2, itemId);
                    ps.setInt(3, toAdd);
                    ps.setInt(4, emptySlot);
                    ps.setString(5, nbt);
                    ps.setString(6, playerName);
                    ps.setString(7, now);
                    ps.setInt(8, toAdd);
                    ps.setString(9, playerName);
                    ps.setString(10, now);
                    ps.executeUpdate();
                }
                count -= toAdd;
            }
            updateTimestamp(teamId);
            return true;
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to add item {} for team {}", itemId, teamId, e);
            return false;
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

    private int findEmptyBoxSlot(String owner, String box, int maxSlots) throws SQLException {
        Set<Integer> used = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT slot FROM player_box_items WHERE owner_uuid=? AND box_name=? ORDER BY slot")) {
            ps.setString(1, owner); ps.setString(2, box);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) used.add(rs.getInt("slot"));
        }
        for (int i = 0; i < maxSlots; i++) if (!used.contains(i)) return i;
        return -1;
    }

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
        if (!isReady()) return false;
        int maxPages = getBoxMaxPages(owner, box);
        int maxSlots = maxPages * SLOTS_PER_PAGE;
        String now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            String findSql = "SELECT slot, count FROM player_box_items WHERE owner_uuid=? AND box_name=? AND item_id=? AND ";
            findSql += (nbt != null) ? "nbt = ?" : "(nbt IS NULL OR nbt='')";
            try (PreparedStatement ps = connection.prepareStatement(findSql)) {
                ps.setString(1, owner); ps.setString(2, box); ps.setString(3, itemId);
                if (nbt != null) ps.setString(4, nbt);
                ResultSet rs = ps.executeQuery();
                while (rs.next() && count > 0) {
                    int slot = rs.getInt("slot"); int existing = rs.getInt("count");
                    if (existing < 64) {
                        int canAdd = Math.min(count, 64 - existing);
                        try (PreparedStatement up = connection.prepareStatement(
                                "UPDATE player_box_items SET count=count+?, last_modified_by=?, last_modified_time=? WHERE owner_uuid=? AND box_name=? AND slot=?")) {
                            up.setInt(1, canAdd); up.setString(2, playerName); up.setString(3, now);
                            up.setString(4, owner); up.setString(5, box); up.setInt(6, slot);
                            up.executeUpdate();
                        }
                        count -= canAdd;
                    }
                }
            }
            while (count > 0) {
                int es = findEmptyBoxSlot(owner, box, maxSlots);
                if (es < 0) return false;
                int toAdd = Math.min(count, 64);
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO player_box_items (owner_uuid, box_name, item_id, count, slot, nbt, placed_by, placed_time, placed_count, last_modified_by, last_modified_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, owner); ps.setString(2, box); ps.setString(3, itemId);
                    ps.setInt(4, toAdd); ps.setInt(5, es); ps.setString(6, nbt);
                    ps.setString(7, playerName); ps.setString(8, now); ps.setInt(9, toAdd);
                    ps.setString(10, playerName); ps.setString(11, now); ps.executeUpdate();
                }
                count -= toAdd;
            }
            updateBoxTimestamp(owner, box);
            return true;
        } catch (SQLException e) { SharedBackpackMod.LOGGER.error("Failed addBoxItem", e); return false; }
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

    private int findEmptySlot(String teamId, int maxSlots) throws SQLException {
        String sql = "SELECT slot FROM backpack_items WHERE team_id = ? ORDER BY slot";
        Set<Integer> used = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, teamId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) used.add(rs.getInt("slot"));
        }
        for (int i = 0; i < maxSlots; i++) {
            if (!used.contains(i)) return i;
        }
        return -1;
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
