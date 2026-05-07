package com.sharedbackpack.commands;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public class BindManager {
    private static final Map<String, String> BINDS = new HashMap<>();
    private static final Map<String, String> BOX_TARGETS = new HashMap<>(); // uuid -> boxName (null = main backpack)

    public static void bind(String playerId, ItemStack stack) {
        BOX_TARGETS.remove(playerId);
        if (stack.isEmpty()) {
            BINDS.remove(playerId);
        } else {
            BINDS.put(playerId, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
    }

    public static void bindToBox(String playerId, ItemStack stack, String boxName) {
        if (stack.isEmpty()) return;
        BINDS.put(playerId, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        BOX_TARGETS.put(playerId, boxName);
    }

    public static void unbind(String playerId) {
        BINDS.remove(playerId);
        BOX_TARGETS.remove(playerId);
    }

    public static boolean matches(String playerId, ItemStack stack) {
        String bound = BINDS.get(playerId);
        if (bound == null) return stack.getItem() == Items.CARROT;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(bound));
        return item != null && stack.getItem() == item;
    }

    public static String getBound(String playerId) {
        return BINDS.get(playerId);
    }

    public static String getBoxTarget(String playerId) {
        return BOX_TARGETS.get(playerId);
    }
}
