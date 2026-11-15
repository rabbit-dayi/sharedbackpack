package com.sharedbackpack.database;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/** Real SQLite regressions for inventory-visible failures: partial deposits, NBT loss and lost preferences. */
public class StorageTransactionsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File directory;
    private DatabaseManager database;

    @Before public void open() throws Exception {
        directory = temporary.newFolder("storage");
        database = new DatabaseManager(directory);
        database.init();
        assertTrue(database.isReady());
    }

    @After public void close() { if (database != null) database.close(); }

    private List<DatabaseManager.BackpackItem> items(String box) {
        return box == null ? database.getItems("team") : database.getBoxItems("team", box);
    }

    private long total(String box) {
        long total = 0;
        for (DatabaseManager.BackpackItem item : items(box)) total += item.count;
        return total;
    }

    @Test public void capacityFailureNeverPartiallyDepositsThroughEitherApi() {
        for (String box : new String[] {null, "box"}) {
            assertTrue(database.depositAtomic("team", box, "minecraft:stone", 45 * 64 - 4, null, "Alice"));
            assertFalse(database.depositAtomic("team", box, "minecraft:stone", 8, null, "Bob"));
            assertEquals(45 * 64 - 4, total(box));
            boolean legacy = box == null
                    ? database.addItem("team", "minecraft:stone", 8, null, "Bob")
                    : database.addBoxItem("team", box, "minecraft:stone", 8, null, "Bob");
            assertFalse(legacy);
            assertEquals(45 * 64 - 4, total(box));
            assertTrue(database.depositAtomic("team", box, "minecraft:stone", 4, null, "Bob"));
            assertEquals(45 * 64, total(box));
            for (DatabaseManager.BackpackItem item : items(box)) assertEquals(64, item.count);
        }
    }

    @Test public void sqlFailureRollsBackMergeBeforeFailedInsert() throws Exception {
        for (String box : new String[] {null, "box"}) {
            assertTrue(database.depositAtomic("team", box, "minecraft:stone", 60, null, "Alice"));
            String table = box == null ? "backpack_items" : "player_box_items";
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + new File(directory, "backpack.db"));
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_" + table + " BEFORE INSERT ON " + table
                        + " BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END");
            }
            assertFalse(database.depositAtomic("team", box, "minecraft:stone", 12, null, "Bob"));
            assertEquals(60, total(box));
            assertEquals("Alice", items(box).get(0).lastModifiedBy);
            assertTrue(database.isReady());
        }
    }

    @Test public void groupedRemovalUsesFullStructuralNbtAndChecksTotalBeforeWrites() {
        for (String box : new String[] {null, "box"}) {
            String a = "{map:4,display:{Name:'\"Original\"'},nested:{a:1,b:2}}";
            String reordered = "{nested:{b:2,a:1},display:{Name:'\"Original\"'},map:4}";
            String other = "{map:5,display:{Name:'\"Original\"'},nested:{a:1,b:2}}";
            assertTrue(database.depositAtomic("team", box, "minecraft:filled_map", 64, a, "Alice"));
            assertTrue(database.depositAtomic("team", box, "minecraft:filled_map", 64, reordered, "Alice"));
            assertTrue(database.depositAtomic("team", box, "minecraft:filled_map", 20, other, "Alice"));
            assertTrue(database.removeMatchingAtomic("team", box, "minecraft:filled_map", a, 100));
            assertEquals(48, total(box));
            assertFalse(database.removeMatchingAtomic("team", box, "minecraft:filled_map", a, 29));
            assertEquals(48, total(box));
            assertTrue(database.removeMatchingAtomic("team", box, "minecraft:filled_map", reordered, 28));
            assertEquals(20, total(box));
            assertEquals(other, items(box).get(0).nbt);
        }
    }

    @Test public void nbtTypesAndListOrderRemainSeparate() {
        String original = "{values:[1,2],flag:1b}";
        assertTrue(database.depositAtomic("team", null, "minecraft:stone", 8, original, "Alice"));
        assertFalse(database.removeMatchingAtomic("team", null, "minecraft:stone", "{values:[2,1],flag:1b}", 1));
        assertFalse(database.removeMatchingAtomic("team", null, "minecraft:stone", "{values:[1,2],flag:1}", 1));
        assertEquals(8, total(null));
    }

    @Test public void pageAndSortSurviveRestartAndStayScopedToPlayerAndStorage() {
        database.saveViewPreferences("Alice", "team", null, 7, 3);
        database.saveViewPreferences("Alice", "team", "box", 2, 5);
        database.saveViewPreferences("Bob", "team", null, 1, 1);
        database.close();
        database = new DatabaseManager(directory);
        database.init();
        assertArrayEquals(new int[] {7, 3}, database.loadViewPreferences("Alice", "team", null));
        assertArrayEquals(new int[] {2, 5}, database.loadViewPreferences("Alice", "team", "box"));
        assertArrayEquals(new int[] {1, 1}, database.loadViewPreferences("Bob", "team", null));
        assertArrayEquals(new int[] {0, 0}, database.loadViewPreferences("Alice", "other", null));
        assertArrayEquals(new int[] {0, 0}, database.loadViewPreferences("Alice", "team", ""));
    }
}
