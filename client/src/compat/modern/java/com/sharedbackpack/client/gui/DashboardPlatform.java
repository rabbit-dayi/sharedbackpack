package com.sharedbackpack.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;

public final class DashboardPlatform {
    private DashboardPlatform() {}
    public static String itemId(ItemStack stack) { return Registries.ITEM.getId(stack.getItem()).toString(); }
    public static String language(MinecraftClient client) { return client.getLanguageManager().getLanguage(); }
    public static PlayerInventory inventory(MinecraftClient client) { return client.player.getInventory(); }
    public static Text literal(String value) { return Text.literal(value); }
    public static void quickDeposit(MinecraftClient client, GenericContainerScreenHandler handler, int slot, int button) {
        client.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(handler.syncId, handler.getRevision(), slot, button,
                SlotActionType.QUICK_MOVE, handler.getCursorStack().copy(), Int2ObjectMaps.emptyMap()));
    }
}
