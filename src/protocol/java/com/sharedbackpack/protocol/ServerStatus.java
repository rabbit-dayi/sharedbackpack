package com.sharedbackpack.protocol;

import net.minecraft.network.PacketByteBuf;

/** A bounded snapshot containing aggregate server health, without paths or player identities. */
public final class ServerStatus {
    public final String minecraftVersion;
    public final String modVersion;
    public final String loaderVersion;
    public final long uptimeSeconds;
    public final int onlinePlayers;
    public final int maxPlayers;
    public final boolean databaseReady;
    public final float tickMillis;
    public final long usedHeap;
    public final long maxHeap;

    public ServerStatus(String minecraftVersion, String modVersion, String loaderVersion, long uptimeSeconds,
                        int onlinePlayers, int maxPlayers, boolean databaseReady, float tickMillis,
                        long usedHeap, long maxHeap) {
        this.minecraftVersion = minecraftVersion;
        this.modVersion = modVersion;
        this.loaderVersion = loaderVersion;
        this.uptimeSeconds = uptimeSeconds;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.databaseReady = databaseReady;
        this.tickMillis = tickMillis;
        this.usedHeap = usedHeap;
        this.maxHeap = maxHeap;
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(bounded(minecraftVersion));
        buf.writeString(bounded(modVersion));
        buf.writeString(bounded(loaderVersion));
        buf.writeLong(uptimeSeconds);
        buf.writeVarInt(onlinePlayers);
        buf.writeVarInt(maxPlayers);
        buf.writeBoolean(databaseReady);
        buf.writeFloat(tickMillis);
        buf.writeLong(usedHeap);
        buf.writeLong(maxHeap);
    }

    public static ServerStatus read(PacketByteBuf buf) {
        ServerStatus status = new ServerStatus(buf.readString(64), buf.readString(64), buf.readString(64),
                buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readFloat(),
                buf.readLong(), buf.readLong());
        if (status.uptimeSeconds < 0 || status.onlinePlayers < 0 || status.maxPlayers < 0
                || !Float.isFinite(status.tickMillis) || status.tickMillis < 0
                || status.usedHeap < 0 || status.maxHeap < status.usedHeap) {
            throw new IllegalArgumentException("Invalid server status");
        }
        return status;
    }

    private static String bounded(String value) {
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
