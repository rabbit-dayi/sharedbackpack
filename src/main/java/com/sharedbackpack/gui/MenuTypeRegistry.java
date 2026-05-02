package com.sharedbackpack.gui;

import com.sharedbackpack.SharedBackpackMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MenuTypeRegistry {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SharedBackpackMod.MOD_ID);
    
    public static final RegistryObject<MenuType<BackpackMenu>> BACKPACK = MENUS.register("backpack",
        () -> IForgeMenuType.create(BackpackMenu::new));
}
