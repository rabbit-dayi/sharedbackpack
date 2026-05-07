package com.sharedbackpack.commands;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.database.DatabaseManager;
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

    /** Called on server start to reload persisted binds from DB. */
    public static void loadAll() {
        BINDS.clear(); BOX_TARGETS.clear();
        if (SharedBackpackMod.database == null) return;
        for (var e : SharedBackpackMod.database.loadAllBinds().entrySet()) {
            BINDS.put(e.getKey(), e.getValue().itemId);
            if (e.getValue().boxTarget != null) BOX_TARGETS.put(e.getKey(), e.getValue().boxTarget);
        }
    }

    public static void bind(String playerId, ItemStack stack) {
        BOX_TARGETS.remove(playerId);
        if (stack.isEmpty()) {
            BINDS.remove(playerId);
            SharedBackpackMod.database.deleteBind(playerId);
        } else {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            BINDS.put(playerId, itemId);
            SharedBackpackMod.database.saveBind(playerId, itemId, null);
        }
    }

    public static void bindToBox(String playerId, ItemStack stack, String boxName) {
        if (stack.isEmpty()) return;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        BINDS.put(playerId, itemId);
        BOX_TARGETS.put(playerId, boxName);
        SharedBackpackMod.database.saveBind(playerId, itemId, boxName);
    }

    public static void unbind(String playerId) {
        BINDS.remove(playerId);
        BOX_TARGETS.remove(playerId);
        SharedBackpackMod.database.deleteBind(playerId);
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
