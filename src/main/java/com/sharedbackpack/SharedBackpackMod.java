package com.sharedbackpack;

import com.sharedbackpack.commands.BindManager;
import com.sharedbackpack.commands.CCCommand;
import com.sharedbackpack.commands.CCommand;
import com.sharedbackpack.commands.ChineseNames;
import com.sharedbackpack.commands.DebugCommand;
import com.sharedbackpack.compat.CommandRegistrar;
import com.sharedbackpack.database.DatabaseManager;
import com.sharedbackpack.gui.BackpackMenuHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SharedBackpackMod implements ModInitializer {
    public static final String MOD_ID = "sharedbackpack";
    public static final Logger LOGGER = LogManager.getLogger();
    public static DatabaseManager database;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            database = new DatabaseManager(server);
            database.init();
            BindManager.loadAll();
            ChineseNames.load(server);
            if (database.isReady()) {
                LOGGER.info("Shared Backpack database initialized successfully");
            } else {
                LOGGER.error("Shared Backpack database failed to initialize");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (database != null) {
                database.close();
            }
        });
        CommandRegistrar.register();
        BackpackMenuHandler.register();
        LOGGER.info("Shared Backpack Fabric mod loaded (server-only)");
    }
}
