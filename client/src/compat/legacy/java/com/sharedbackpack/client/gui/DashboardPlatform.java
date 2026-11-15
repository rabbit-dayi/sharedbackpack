package com.sharedbackpack.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.registry.Registry;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class DashboardPlatform {
    private DashboardPlatform() {}
    public static String itemId(ItemStack stack) { return Registry.ITEM.getId(stack.getItem()).toString(); }
    public static String language(MinecraftClient client) { return client.getLanguageManager().getLanguage().getCode(); }
    public static PlayerInventory inventory(MinecraftClient client) { return client.player.inventory; }
    public static Text literal(String value) { return new LiteralText(value); }
    public static void quickDeposit(MinecraftClient client, GenericContainerScreenHandler handler, int slot, int button) {
        client.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(handler.syncId, slot, button,
                SlotActionType.QUICK_MOVE, handler.getSlot(slot).getStack().copy(), handler.getNextActionId(client.player.inventory)));
    }
}
