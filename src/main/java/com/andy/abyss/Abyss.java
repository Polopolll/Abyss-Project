package com.andy.abyss;

import com.andy.abyss.net.Net;
import com.andy.abyss.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Abyss.MODID)
public class Abyss {
    public static final String MODID = "abyss";
    public static final Logger LOG = LoggerFactory.getLogger("Abyss");

    public Abyss() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEffects.EFFECTS.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModEntities.ENTITIES.register(bus);
        bus.addListener(ModItems::creativeTab);
        bus.addListener(ModEntities::attributes);
        bus.addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> com.andy.abyss.client.ConfigScreen::register);
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::register);
    }

    public static ResourceLocation id(String path) { return new ResourceLocation(MODID, path); }
}
