package com.sharedbackpack.client;

import com.sharedbackpack.protocol.BackpackProtocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public final class SharedBackpackClient implements ClientModInitializer {
    private static int activeSyncId = -1;
    private static String activeQuery = "";
    private static boolean unload;

    @Override
    public void onInitializeClient() {
        ClientDiagnostics.register();
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sharedbackpack.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.sharedbackpack"));
        ClientPlayNetworking.registerGlobalReceiver(BackpackProtocol.STATE, (client, handler, buf, sender) -> {
            int size = buf.readableBytes();
            final int syncId;
            final String query;
            final boolean unloadMode;
            try {
                if (buf.readVarInt() != BackpackProtocol.VERSION) return;
                syncId = buf.readVarInt();
                query = buf.readString(BackpackProtocol.MAX_QUERY_LENGTH);
                unloadMode = buf.readBoolean();
                if (buf.isReadable()) return;
            } catch (RuntimeException malformedPacket) {
                return;
            }
            client.execute(() -> {
                ClientDiagnostics.received(size);
                if (client.currentScreen instanceof GenericContainerScreen
                        && ((GenericContainerScreen) client.currentScreen).getScreenHandler().syncId == syncId) {
                    activeSyncId = syncId;
                    activeQuery = query;
                    unload = unloadMode;
                    ((BackpackScreenExtension) client.currentScreen).sharedbackpack$updateSearch(query, unloadMode);
                }
            });
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientDiagnostics.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearScreen();
            ClientDiagnostics.reset();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientDiagnostics.tick();
            if (client.currentScreen instanceof BackpackScreenExtension) {
                ((BackpackScreenExtension) client.currentScreen).sharedbackpack$tickSearch();
            }
            while (open.wasPressed()) {
                if (client.player != null && client.currentScreen == null
                        && ClientPlayNetworking.canSend(BackpackProtocol.REQUEST)) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeVarInt(BackpackProtocol.VERSION);
                    buf.writeVarInt(BackpackProtocol.OPEN);
                    ClientDiagnostics.sent(buf.readableBytes());
                    ClientPlayNetworking.send(BackpackProtocol.REQUEST, buf);
                }
            }
        });
    }

    public static void restoreScreen(BackpackScreenExtension screen, int syncId) {
        if (syncId == activeSyncId) screen.sharedbackpack$updateSearch(activeQuery, unload);
    }

    public static void clearScreen() {
        activeSyncId = -1;
        activeQuery = "";
        unload = false;
    }

    public static void search(int syncId, String query) {
        if (!ClientPlayNetworking.canSend(BackpackProtocol.REQUEST)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(BackpackProtocol.VERSION);
        buf.writeVarInt(BackpackProtocol.SEARCH);
        buf.writeVarInt(syncId);
        buf.writeString(query);
        ClientDiagnostics.sent(buf.readableBytes());
        ClientPlayNetworking.send(BackpackProtocol.REQUEST, buf);
    }
}
