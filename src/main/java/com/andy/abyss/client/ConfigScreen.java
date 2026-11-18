package com.andy.abyss.client;

import com.andy.abyss.client.gui.SettingsScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-only: the Mods menu "Config" button opens the Abyss settings. */
public final class ConfigScreen {
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
    }
    private ConfigScreen() {}
}
