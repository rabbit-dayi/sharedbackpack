package com.sharedbackpack.database;

import com.sharedbackpack.SharedBackpackMod;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final int SLOTS_PER_PAGE = 54;

    public DatabaseManager(MinecraftServer server) {
        File modDir = new File(server.getServerDirectory(), "config/sharedbackpack");
        modDir.mkdirs();
        this.dbFile = new File(modDir, "backpack.db");
        this.backupDir = new File(modDir, "backups");
        this.backupDir.mkdirs();
    }

    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            backupBeforeInit();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            connection.setAutoCommit(true);
            createTables();
            SharedBackpackMod.LOGGER.info("SQLite database ready: {}", dbFile.getAbsolutePath());
        } catch (Exception e) {
            SharedBackpackMod.LOGGER.error("Failed to init database", e);
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
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS backpack_meta (
                    team_id TEXT PRIMARY KEY,
                    max_pages INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now','localtime')),
                    updated_at TEXT DEFAULT (datetime('now','localtime'))
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS backpack_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    team_id TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    count INTEGER NOT NULL DEFAULT 0,
                    slot INTEGER NOT NULL DEFAULT 0,
                    nbt TEXT,
                    placed_by TEXT,
                    placed_time TEXT,
                    placed_count INTEGER DEFAULT 0,
                    last_modified_by TEXT,
                    last_modified_time TEXT,
                    UNIQUE(team_id, slot)
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_team_items ON backpack_items(team_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_item_search ON backpack_items(item_id)");
            
            // Add columns if not exist (for existing databases)
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN placed_by TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN placed_time TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN placed_count INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN last_modified_by TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE backpack_items ADD COLUMN last_modified_time TEXT"); } catch (SQLException ignored) {}
        }
    }

    public synchronized List<BackpackItem> getItems(String teamId) {
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
        int maxPages = getMaxPages(teamId);
        int maxSlots = maxPages * SLOTS_PER_PAGE;
        String now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

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
                        int newPlacedCount = rs.getInt("placed_count") + canAdd;
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
        try {
            String upsertSql = """
                INSERT INTO backpack_meta (team_id, max_pages, created_at, updated_at)
                VALUES (?, 1 + ?, datetime('now','localtime'), datetime('now','localtime'))
                ON CONFLICT(team_id) DO UPDATE SET
                    max_pages = max_pages + ?,
                    updated_at = datetime('now','localtime')
            """;
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

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            SharedBackpackMod.LOGGER.error("Failed to close database", e);
        }
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
