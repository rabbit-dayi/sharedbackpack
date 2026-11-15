package com.sharedbackpack.gui;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.protocol.BackpackProtocol;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

/** Optional enhancement. Every inventory still uses the vanilla generic container protocol. */
public final class OptionalClientNetworking {
    private OptionalClientNetworking() {
    }

    public static void register() {
        DashboardMenuBase.register();
        ServerDiagnostics.register();
        ServerPlayNetworking.registerGlobalReceiver(BackpackProtocol.REQUEST, (server, player, handler, buf, sender) -> {
            final int action;
            final int syncId;
            final String query;
            try {
                if (buf.readVarInt() != BackpackProtocol.VERSION) return;
                action = buf.readVarInt();
                if (action != BackpackProtocol.OPEN && action != BackpackProtocol.SEARCH) return;
                syncId = action == BackpackProtocol.SEARCH ? buf.readVarInt() : -1;
                query = action == BackpackProtocol.SEARCH ? buf.readString(BackpackProtocol.MAX_QUERY_LENGTH) : "";
                if (buf.isReadable()) return;
            } catch (RuntimeException malformedPacket) {
                return;
            }
            server.execute(() -> {
                if (SharedBackpackMod.database == null || !SharedBackpackMod.database.isReady()
                        || !player.isAlive() || player.isSpectator()) return;
                if (action == BackpackProtocol.OPEN) {
                    if (player.currentScreenHandler == player.playerScreenHandler) {
                        BackpackMenu.openTeam(player, "");
                    }
                } else if (player.currentScreenHandler instanceof BackpackMenu
                        && player.currentScreenHandler.syncId == syncId) {
                    ((BackpackMenu) player.currentScreenHandler).applyClientSearch(query.trim());
                }
            });
        });
    }

    public static void sendState(ServerPlayerEntity player, int syncId, String search, boolean unload) {
        // No custom packet is ever sent to clients that do not advertise this channel.
        if (!ServerPlayNetworking.canSend(player, BackpackProtocol.STATE)) return;
        String query = search == null ? "" : search;
        if (query.length() > BackpackProtocol.MAX_QUERY_LENGTH) {
            query = query.substring(0, BackpackProtocol.MAX_QUERY_LENGTH);
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(BackpackProtocol.VERSION);
        buf.writeVarInt(syncId);
        buf.writeString(query);
        buf.writeBoolean(unload);
        ServerPlayNetworking.send(player, BackpackProtocol.STATE, buf);
    }
}
