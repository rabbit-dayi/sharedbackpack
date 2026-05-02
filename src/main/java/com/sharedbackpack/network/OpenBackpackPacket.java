package com.sharedbackpack.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenBackpackPacket {
    private final String teamId;
    private final String searchFilter;

    public OpenBackpackPacket(String teamId, String searchFilter) {
        this.teamId = teamId;
        this.searchFilter = searchFilter;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(teamId);
        buf.writeUtf(searchFilter);
    }

    public static OpenBackpackPacket decode(FriendlyByteBuf buf) {
        return new OpenBackpackPacket(buf.readUtf(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().setPacketHandled(true);
    }
}
