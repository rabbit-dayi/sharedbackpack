package com.sharedbackpack;

import com.sharedbackpack.commands.CCommand;
import com.sharedbackpack.commands.CCCommand;
import com.sharedbackpack.commands.ChineseNames;
import com.sharedbackpack.database.DatabaseManager;
import com.sharedbackpack.gui.BackpackMenu;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SharedBackpackMod.MOD_ID)
public class SharedBackpackMod {
    public static final String MOD_ID = "sharedbackpack";
    public static final Logger LOGGER = LogManager.getLogger();
    public static DatabaseManager database;

    public SharedBackpackMod() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new com.sharedbackpack.gui.BackpackMenuHandler());
        LOGGER.info("Shared Backpack mod loaded (server-only)");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        database = new DatabaseManager(event.getServer());
        database.init();
        ChineseNames.load(event.getServer());
        if (database.isReady()) {
            LOGGER.info("Shared Backpack database initialized successfully");
        } else {
            LOGGER.error("Shared Backpack database FAILED to initialize - check errors above");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (database != null) {
            database.close();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CCommand.register(event.getDispatcher());
        CCCommand.register(event.getDispatcher());
    }
}
