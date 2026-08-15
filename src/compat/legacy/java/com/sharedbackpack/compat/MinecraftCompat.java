package com.sharedbackpack.compat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    public static Text text(String value) {
        return new LiteralText(value);
    }

    public static Item getItem(Identifier id) {
        return Registry.ITEM.get(id);
    }

    public static Identifier getItemId(Item item) {
        return Registry.ITEM.getId(item);
    }

    public static String getItemDisplayName(Item item) {
        return item.getName().getString();
    }

    public static Iterable<Item> items() {
        return Registry.ITEM;
    }

    public static void sendFeedback(ServerCommandSource source, Text message) {
        source.sendFeedback(message, false);
    }

    public static MinecraftServer getServer(ServerCommandSource source) {
        return source.getMinecraftServer();
    }

    public static boolean hasNbt(ItemStack stack) {
        return stack.hasTag();
    }

    public static NbtCompound getNbt(ItemStack stack) {
        return stack.getTag();
    }

    public static NbtCompound getOrCreateNbt(ItemStack stack) {
        return stack.getOrCreateTag();
    }

    public static void setNbt(ItemStack stack, NbtCompound nbt) {
        stack.setTag(nbt);
    }

    public static PlayerInventory inventory(ServerPlayerEntity player) {
        return player.inventory;
    }
}
