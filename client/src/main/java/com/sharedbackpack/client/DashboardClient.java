package com.sharedbackpack.client;

import com.sharedbackpack.client.gui.DashboardScreen;
import com.sharedbackpack.client.gui.DashboardPlatform;
import com.sharedbackpack.protocol.DashboardProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Receives complete catalog snapshots; no client-side inventory mutation is performed here. */
public final class DashboardClient {
    private static DashboardModel model;
    private static Assembly pending;
    private static Language language;

    private DashboardClient() {}

    private static final class Assembly {
        int syncId, revision, maxPages, usedSlots, page, sort;
        String storage;
        String initialQuery;
        List<String> boxes;
        final List<DashboardModel.Entry> entries = new ArrayList<>();
        final Set<Integer> keys = new HashSet<>();
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DashboardProtocol.STATE, (client, handler, buf, sender) -> {
            int bytes = buf.readableBytes();
            final int syncId, revision;
            final boolean first, last;
            final Assembly chunk = new Assembly();
            try {
                if (buf.readVarInt() != DashboardProtocol.VERSION) return;
                syncId = buf.readVarInt();
                revision = buf.readVarInt();
                first = buf.readBoolean();
                last = buf.readBoolean();
                if (first) {
                    chunk.storage = buf.readString(DashboardProtocol.MAX_TEXT);
                    chunk.initialQuery = buf.readString(DashboardProtocol.MAX_TEXT);
                    chunk.maxPages = buf.readVarInt();
                    chunk.usedSlots = buf.readVarInt();
                    chunk.page = buf.readVarInt();
                    chunk.sort = buf.readVarInt();
                    int boxes = buf.readVarInt();
                    if (boxes < 0 || boxes > buf.readableBytes()) return;
                    chunk.boxes = new ArrayList<>();
                    for (int i = 0; i < boxes; i++) chunk.boxes.add(buf.readString(DashboardProtocol.MAX_TEXT));
                    if (chunk.maxPages < 1 || chunk.usedSlots < 0 || chunk.page < 0 || chunk.sort < 0 || chunk.sort > 5) return;
                }
                int count = buf.readVarInt();
                if (count < 0 || count > DashboardProtocol.CHUNK_SIZE) return;
                for (int i = 0; i < count; i++) {
                    int key = buf.readVarInt();
                    ItemStack icon = buf.readItemStack();
                    long aggregate = buf.readVarLong();
                    String modified = buf.readString(DashboardProtocol.MAX_TEXT);
                    if (key < 0 || icon.isEmpty() || aggregate < 1) return;
                    chunk.entries.add(new DashboardModel.Entry(key, icon, aggregate, modified));
                }
                if (buf.isReadable()) return;
            } catch (RuntimeException malformed) { return; }
            client.execute(() -> {
                ClientDiagnostics.received(bytes);
                if (!matches(client, syncId)) return;
                if (first) {
                    pending = chunk;
                    pending.syncId = syncId;
                    pending.revision = revision;
                } else {
                    if (pending == null || pending.syncId != syncId || pending.revision != revision) return;
                    pending.entries.addAll(chunk.entries);
                }
                for (DashboardModel.Entry entry : chunk.entries) {
                    if (!pending.keys.add(entry.key)) { pending = null; return; }
                }
                if (!last) return;
                Assembly done = pending;
                pending = null;
                boolean opening = model == null || model.snapshot().syncId != syncId;
                if (opening) model = new DashboardModel();
                model.accept(new DashboardModel.Snapshot(syncId, revision, done.storage, done.maxPages,
                        done.usedSlots, done.page, done.sort, done.boxes, done.entries));
                if (opening) model.search(done.initialQuery);
                tick(client);
            });
        });
    }

    private static boolean matches(MinecraftClient client, int syncId) {
        if (client.player == null || client.player.currentScreenHandler.syncId != syncId) return false;
        return client.currentScreen instanceof DashboardScreen
                || client.currentScreen instanceof GenericContainerScreen;
    }

    public static void tick(MinecraftClient client) {
        if (model == null) {
            if (pending != null && !matches(client, pending.syncId)) pending = null;
            return;
        }
        if (!matches(client, model.snapshot().syncId)) { clear(); return; }
        ClientDiagnostics.poll(model.snapshot().syncId);
        if (language != Language.getInstance()) {
            language = Language.getInstance();
            model.relocalize(DashboardPlatform.language(client));
        }
        if (client.currentScreen instanceof GenericContainerScreen) {
            GenericContainerScreen previous = (GenericContainerScreen) client.currentScreen;
            DashboardScreen replacement = new DashboardScreen(previous.getScreenHandler(), DashboardPlatform.inventory(client),
                    previous.getTitle(), model);
            SharedBackpackClient.clearScreen();
            // setScreen calls HandledScreen.removed(), which closes this live handler on 1.16.5.
            // Transfer ownership directly; the replacement alone performs normal close/removal.
            client.currentScreen = replacement;
            replacement.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        }
    }

    public static void clear() { model = null; pending = null; language = null; }

    private static void send(int syncId, int action, Consumer<PacketByteBuf> payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!matches(client, syncId) || !ClientPlayNetworking.canSend(DashboardProtocol.REQUEST)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(DashboardProtocol.VERSION);
        buf.writeVarInt(syncId);
        buf.writeVarInt(action);
        payload.accept(buf);
        ClientDiagnostics.sent(buf.readableBytes());
        ClientPlayNetworking.send(DashboardProtocol.REQUEST, buf);
    }

    public static void take(DashboardModel model, DashboardModel.Entry entry, boolean stack, boolean inventory) {
        send(model.snapshot().syncId, DashboardProtocol.TAKE, buf -> {
            buf.writeVarInt(model.snapshot().revision);
            buf.writeVarInt(entry.key);
            buf.writeBoolean(stack);
            buf.writeBoolean(inventory);
        });
    }
    public static void switchBox(DashboardModel model, String name) {
        send(model.snapshot().syncId, DashboardProtocol.SWITCH, buf -> buf.writeString(name));
    }
    public static void createBox(DashboardModel model, String name) {
        send(model.snapshot().syncId, DashboardProtocol.CREATE_BOX, buf -> buf.writeString(name));
    }
    public static void preferences(DashboardModel model) {
        send(model.snapshot().syncId, DashboardProtocol.PREFERENCES, buf -> {
            buf.writeVarInt(model.rememberedPage());
            buf.writeVarInt(model.sort());
        });
    }
    public static void upgrade(DashboardModel model) {
        send(model.snapshot().syncId, DashboardProtocol.UPGRADE, buf -> {});
    }
    public static void refresh(DashboardModel model) {
        send(model.snapshot().syncId, DashboardProtocol.REFRESH, buf -> {});
    }
    public static void depositCursor(DashboardModel model) {
        send(model.snapshot().syncId, DashboardProtocol.DEPOSIT_CURSOR, buf -> {});
    }
}
