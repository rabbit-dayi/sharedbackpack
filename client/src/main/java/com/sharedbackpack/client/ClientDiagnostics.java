package com.sharedbackpack.client;

import com.sharedbackpack.protocol.BackpackProtocol;
import com.sharedbackpack.protocol.ServerStatus;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

/** Client-thread state; counters measure addon payloads for this connection, in the client's direction. */
public final class ClientDiagnostics {
    private static long uploaded;
    private static long downloaded;
    private static long sampleUploaded;
    private static long sampleDownloaded;
    private static long sampleAt = System.nanoTime();
    private static long uploadRate;
    private static long downloadRate;
    private static final long[] UPLOAD_SAMPLES = new long[4];
    private static final long[] DOWNLOAD_SAMPLES = new long[4];
    private static final long[] SAMPLE_INTERVALS = new long[4];
    private static int sampleIndex;
    private static long requestedAt;
    private static long receivedAt;
    private static int sequence;
    private static int pendingSequence = -1;
    private static int pendingSyncId = -1;
    private static long roundTripMillis;
    private static ServerStatus status;

    private ClientDiagnostics() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BackpackProtocol.STATUS, (client, handler, buf, sender) -> {
            int size = buf.readableBytes();
            final int responseSequence;
            final int syncId;
            final ServerStatus snapshot;
            try {
                if (buf.readVarInt() != BackpackProtocol.VERSION) return;
                responseSequence = buf.readVarInt();
                syncId = buf.readVarInt();
                snapshot = ServerStatus.read(buf);
                if (buf.isReadable()) return;
            } catch (RuntimeException malformedPacket) {
                return;
            }
            client.execute(() -> {
                received(size);
                if (responseSequence != pendingSequence || syncId != pendingSyncId
                        || client.player == null || client.player.currentScreenHandler.syncId != syncId) return;
                receivedAt = System.nanoTime();
                roundTripMillis = Math.max(0, (receivedAt - requestedAt) / 1_000_000L);
                pendingSequence = -1;
                status = snapshot;
            });
        });
    }

    public static void tick() {
        long now = System.nanoTime();
        long elapsed = now - sampleAt;
        if (elapsed < 1_000_000_000L) return;
        UPLOAD_SAMPLES[sampleIndex] = uploaded - sampleUploaded;
        DOWNLOAD_SAMPLES[sampleIndex] = downloaded - sampleDownloaded;
        SAMPLE_INTERVALS[sampleIndex] = elapsed;
        sampleIndex = (sampleIndex + 1) % SAMPLE_INTERVALS.length;
        long up = 0, down = 0, interval = 0;
        for (int i = 0; i < SAMPLE_INTERVALS.length; i++) {
            up += UPLOAD_SAMPLES[i];
            down += DOWNLOAD_SAMPLES[i];
            interval += SAMPLE_INTERVALS[i];
        }
        uploadRate = Math.round(up * 1_000_000_000D / interval);
        downloadRate = Math.round(down * 1_000_000_000D / interval);
        sampleUploaded = uploaded;
        sampleDownloaded = downloaded;
        sampleAt = now;
    }

    public static void poll(int syncId) {
        if (!supported()) return;
        long now = System.nanoTime();
        if (requestedAt != 0 && now - requestedAt < 2_000_000_000L) return;
        if (pendingSequence != -1 && now - requestedAt < 6_000_000_000L) return;
        pendingSequence = sequence = (sequence + 1) & Integer.MAX_VALUE;
        pendingSyncId = syncId;
        requestedAt = now;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(BackpackProtocol.VERSION);
        buf.writeVarInt(sequence);
        buf.writeVarInt(syncId);
        sent(buf.readableBytes());
        ClientPlayNetworking.send(BackpackProtocol.STATUS_REQUEST, buf);
    }

    public static boolean supported() { return ClientPlayNetworking.canSend(BackpackProtocol.STATUS_REQUEST); }
    public static ServerStatus status() { return status; }
    public static long roundTripMillis() { return roundTripMillis; }
    public static boolean stale() { return receivedAt == 0 || System.nanoTime() - receivedAt > 6_000_000_000L; }
    public static long uploadRate() { return uploadRate; }
    public static long downloadRate() { return downloadRate; }
    public static long uploaded() { return uploaded; }
    public static long downloaded() { return downloaded; }
    public static void sent(int bytes) { uploaded += bytes; }
    public static void received(int bytes) { downloaded += bytes; }

    public static void reset() {
        uploaded = downloaded = sampleUploaded = sampleDownloaded = uploadRate = downloadRate = 0;
        requestedAt = receivedAt = roundTripMillis = 0;
        sampleAt = System.nanoTime();
        pendingSequence = pendingSyncId = -1;
        sequence = 0;
        sampleIndex = 0;
        java.util.Arrays.fill(UPLOAD_SAMPLES, 0);
        java.util.Arrays.fill(DOWNLOAD_SAMPLES, 0);
        java.util.Arrays.fill(SAMPLE_INTERVALS, 0);
        status = null;
    }
}
