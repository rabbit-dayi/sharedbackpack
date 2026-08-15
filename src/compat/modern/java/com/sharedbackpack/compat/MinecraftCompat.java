package com.sharedbackpack.compat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    public static Text text(String value) {
        return Text.literal(value);
    }

    public static Item getItem(Identifier id) {
        return Registries.ITEM.get(id);
    }

    public static Identifier getItemId(Item item) {
        return Registries.ITEM.getId(item);
    }

    public static String getItemDisplayName(Item item) {
        return item.getName().getString();
    }

    public static Iterable<Item> items() {
        return Registries.ITEM;
    }

    public static void sendFeedback(ServerCommandSource source, Text message) {
        source.sendFeedback(() -> message, false);
    }

    public static MinecraftServer getServer(ServerCommandSource source) {
        return source.getServer();
    }

    public static boolean hasNbt(ItemStack stack) {
        return stack.hasNbt();
    }

    public static NbtCompound getNbt(ItemStack stack) {
        return stack.getNbt();
    }

    public static NbtCompound getOrCreateNbt(ItemStack stack) {
        return stack.getOrCreateNbt();
    }

    public static void setNbt(ItemStack stack, NbtCompound nbt) {
        stack.setNbt(nbt);
    }

    public static PlayerInventory inventory(ServerPlayerEntity player) {
        return player.getInventory();
    }
}
