package com.andy.abyss.client;

import com.andy.abyss.Abyss;
import com.andy.abyss.client.gui.Hud;
import com.andy.abyss.client.render.*;
import com.andy.abyss.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Abyss.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { Keys.register(e); }
    @SubscribeEvent public static void shaders(RegisterShadersEvent e) { AbyssShaders.register(e); }
    @SubscribeEvent public static void sky(RegisterDimensionSpecialEffectsEvent e) { e.register(Abyss.id("the_abyss"), new AbyssSky()); }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) { e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "abyss_hud", Hud::render); }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.BODY.get(), BodyRenderer::new);
        e.registerEntityRenderer(ModEntities.RIFT.get(), RiftRenderer::new);
    }

    /** Corrosion cracks on every living thing, from every mod. */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void layers(EntityRenderersEvent.AddLayers e) {
        for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES.getValues()) {
            try {
                EntityRenderer<?> r = e.getRenderer((EntityType) t);
                if (r instanceof LivingEntityRenderer lr && !(r instanceof BodyRenderer)) lr.addLayer(new CorrosionLayer(lr));
            } catch (Exception ignored) {}
        }
        for (String skin : e.getSkins()) {
            var r = e.getSkin(skin);
            if (r instanceof PlayerRenderer pr) pr.addLayer(new CorrosionLayer<>(pr));
        }
    }
    private ClientSetup() {}
}
