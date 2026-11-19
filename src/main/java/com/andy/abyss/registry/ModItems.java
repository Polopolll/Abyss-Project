package com.andy.abyss.registry;

import com.andy.abyss.Abyss;
import com.andy.abyss.item.AbyssalCoreItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Abyss.MODID);
    public static final RegistryObject<Item> ABYSSAL_CORE = ITEMS.register("abyssal_core", () -> new AbyssalCoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** Creative only: toggles infinite output. */
    public static final RegistryObject<Item> ABYSSAL_UPGRADE = ITEMS.register("abyssal_upgrade", () -> new com.andy.abyss.item.AbyssalUpgradeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> ABYSSAL_CRYSTAL = ITEMS.register("abyssal_crystal", () -> new BlockItem(ModBlocks.ABYSSAL_CRYSTAL.get(), new Item.Properties()));

    public static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.COMBAT || e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(ABYSSAL_CORE);
        if (e.getTabKey() == CreativeModeTabs.COMBAT || e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(ABYSSAL_UPGRADE);
        if (e.getTabKey() == CreativeModeTabs.OP_BLOCKS && e.hasPermissions()) e.accept(ABYSSAL_CRYSTAL);
    }
    private ModItems() {}
}
