package com.andy.abyss.client.render;

import com.andy.abyss.Abyss;
import com.andy.abyss.client.ClientState;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Impact frames (as in hand-drawn fight animation): the instant a heavy blow lands, the whole screen snaps to its
 * negative with a white star burst at the point of impact, then holds a beat as a hard red-and-white freeze, then
 * lets go. About a sixth of a second, on your own big hits only.
 */
@Mod.EventBusSubscriber(modid = Abyss.MODID, value = Dist.CLIENT)
public final class ImpactFrame {
    private static float start = -100, strength;
    /** Seconds-ish: the negative, then the red hold. In ticks. */
    private static final float NEG = 1.2f, HOLD = 2.6f;

    public static void trigger(float s) {
        if (com.andy.abyss.Config.screenShake.get() <= 0) return;
        start = ClientState.ticks + Minecraft.getInstance().getFrameTime();
        strength = s;
    }

    @SubscribeEvent
    public static void draw(RenderGuiEvent.Pre e) {
        float t = (ClientState.ticks + e.getPartialTick() - start) * MoveFx.slow(); // (slow motion stretches it too)
        if (t < 0 || t > NEG + HOLD) return;
        var win = Minecraft.getInstance().getWindow();
        float w = win.getGuiScaledWidth(), h = win.getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        if (t < NEG) {
            // the negative: everything inverts
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            quad(0, 0, w, h, 1, 1, 1, 1);
            // and a star burst at the crosshair: a hard white core with four long spikes
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            float cx = w / 2, cy = h / 2, k = 1 - t / NEG, r = (18 + 26 * strength) * (0.6f + 0.4f * k);
            quad(cx - r * 0.12f, cy - r * 0.12f, cx + r * 0.12f, cy + r * 0.12f, 1, 1, 1, k);
            quad(cx - r * 2.2f, cy - 1.2f, cx + r * 2.2f, cy + 1.2f, 1, 1, 1, 0.9f * k);
            quad(cx - 1.2f, cy - r * 1.4f, cx + 1.2f, cy + r * 1.4f, 1, 1, 1, 0.9f * k);
        } else {
            // the hold: the frame burns down to red and white, then fades back to the world
            float k = 1 - (t - NEG) / HOLD;
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            quad(0, 0, w, h, 1, 1 - 0.8f * k, 1 - 0.75f * k, 1);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            quad(0, 0, w, h, 0.35f, 0.02f, 0.04f, 0.5f * k * k);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    private static void quad(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(x0, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(x1, y1, 0).color(r, g, b, a).endVertex();
        bb.vertex(x1, y0, 0).color(r, g, b, a).endVertex();
        bb.vertex(x0, y0, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private ImpactFrame() {}
}
