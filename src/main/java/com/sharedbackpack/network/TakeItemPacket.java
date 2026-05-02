package com.sharedbackpack.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TakeItemPacket {
    private final int slot;
    private final int count;
    private final String teamId;

    public TakeItemPacket(int slot, int count, String teamId) {
        this.slot = slot;
        this.count = count;
        this.teamId = teamId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(slot);
        buf.writeInt(count);
        buf.writeUtf(teamId);
    }

    public static TakeItemPacket decode(FriendlyByteBuf buf) {
        return new TakeItemPacket(buf.readInt(), buf.readInt(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Handle on server
        });
        ctx.get().setPacketHandled(true);
    }

    public int getSlot() { return slot; }
    public int getCount() { return count; }
    public String getTeamId() { return teamId; }
}
