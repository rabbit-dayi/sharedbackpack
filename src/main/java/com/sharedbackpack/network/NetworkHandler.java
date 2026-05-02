package com.sharedbackpack.network;

import com.sharedbackpack.SharedBackpackMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SharedBackpackMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, OpenBackpackPacket.class,
                OpenBackpackPacket::encode,
                OpenBackpackPacket::decode,
                OpenBackpackPacket::handle);
        CHANNEL.registerMessage(id++, SyncBackpackPacket.class,
                SyncBackpackPacket::encode,
                SyncBackpackPacket::decode,
                SyncBackpackPacket::handle);
        CHANNEL.registerMessage(id++, TakeItemPacket.class,
                TakeItemPacket::encode,
                TakeItemPacket::decode,
                TakeItemPacket::handle);
    }
}
