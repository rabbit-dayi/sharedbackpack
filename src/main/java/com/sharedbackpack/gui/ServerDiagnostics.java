package com.sharedbackpack.gui;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.protocol.BackpackProtocol;
import com.sharedbackpack.protocol.ServerStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ServerDiagnostics {
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final long[] TICKS = new long[100];
    private static long startedAt;
    private static long tickStartedAt;
    private static long totalTickNanos;
    private static int tickIndex;
    private static int tickCount;

    private ServerDiagnostics() {
    }

    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            startedAt = System.nanoTime();
            tickIndex = tickCount = 0;
            totalTickNanos = 0;
            java.util.Arrays.fill(TICKS, 0);
            LAST_REQUEST.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_REQUEST.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_REQUEST.remove(handler.player.getUuid()));
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartedAt = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long elapsed = Math.max(0, System.nanoTime() - tickStartedAt);
            totalTickNanos += elapsed - TICKS[tickIndex];
            TICKS[tickIndex] = elapsed;
            tickIndex = (tickIndex + 1) % TICKS.length;
            tickCount = Math.min(tickCount + 1, TICKS.length);
        });
        ServerPlayNetworking.registerGlobalReceiver(BackpackProtocol.STATUS_REQUEST, (server, player, handler, buf, sender) -> {
            final int sequence;
            final int syncId;
            try {
                if (buf.readVarInt() != BackpackProtocol.VERSION) return;
                sequence = buf.readVarInt();
                syncId = buf.readVarInt();
                if (sequence < 0 || buf.isReadable()) return;
            } catch (RuntimeException malformedPacket) {
                return;
            }
            server.execute(() -> {
                if (!(player.currentScreenHandler instanceof BackpackMenu)
                        || player.currentScreenHandler.syncId != syncId || !player.isAlive()
                        || !ServerPlayNetworking.canSend(player, BackpackProtocol.STATUS)) return;
                long now = System.nanoTime();
                Long last = LAST_REQUEST.get(player.getUuid());
                if (last != null && now - last < 1_000_000_000L) return;
                LAST_REQUEST.put(player.getUuid(), now);
                Runtime runtime = Runtime.getRuntime();
                ServerStatus snapshot = new ServerStatus(server.getVersion(), version(SharedBackpackMod.MOD_ID),
                        version("fabricloader"), Math.max(0, (now - startedAt) / 1_000_000_000L),
                        server.getPlayerManager().getCurrentPlayerCount(), server.getPlayerManager().getMaxPlayerCount(),
                        SharedBackpackMod.database != null && SharedBackpackMod.database.isReady(),
                        tickCount == 0 ? 0 : totalTickNanos / (float) tickCount / 1_000_000F,
                        runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory());
                PacketByteBuf response = PacketByteBufs.create();
                response.writeVarInt(BackpackProtocol.VERSION);
                response.writeVarInt(sequence);
                response.writeVarInt(syncId);
                snapshot.write(response);
                ServerPlayNetworking.send(player, BackpackProtocol.STATUS, response);
            });
        });
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }
}
