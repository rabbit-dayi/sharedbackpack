package com.sharedbackpack.compat;

import com.sharedbackpack.commands.CCCommand;
import com.sharedbackpack.commands.CCommand;
import com.sharedbackpack.commands.DebugCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class CommandRegistrar {
    private CommandRegistrar() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CCommand.register(dispatcher);
            CCCommand.register(dispatcher);
            DebugCommand.register(dispatcher);
        });
    }
}
