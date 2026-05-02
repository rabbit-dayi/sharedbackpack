package com.sharedbackpack;

import com.sharedbackpack.commands.CCommand;
import com.sharedbackpack.commands.CCCommand;
import com.sharedbackpack.database.DatabaseManager;
import com.sharedbackpack.gui.BackpackMenu;
import com.sharedbackpack.gui.BackpackScreen;
import com.sharedbackpack.gui.MenuTypeRegistry;
import com.sharedbackpack.network.NetworkHandler;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
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
        MenuTypeRegistry.MENUS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        NetworkHandler.register();
        LOGGER.info("Shared Backpack mod loaded");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // Menu registration will be done differently
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        database = new DatabaseManager(event.getServer());
        database.init();
        LOGGER.info("Shared Backpack database initialized");
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
