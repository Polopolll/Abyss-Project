package com.andy.abyss.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The sky of the Abyss (the Realm): an endless dark, a cold haze far off at eye level, monoliths standing in it. */
public class AbyssSky extends DimensionSpecialEffects {
    public AbyssSky() { super(Float.NaN, false, SkyType.NONE, false, false); }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 c, float b) { return new Vec3(0.004, 0.005, 0.011); }
    @Override public boolean isFoggyAt(int x, int z) { return false; }
    @Override public float[] getSunriseColor(float a, float b) { return null; }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float pt, PoseStack ps, Camera camera, Matrix4f proj, boolean foggy, Runnable setupFog) {
        if (AbyssShaders.VOID == null) return false;
        drawGalaxy(ps, 1f, SKY);
        return true;
    }

    /** The void shader reads this texture (R = 1, B = 1) as "draw the Realm's horizon": the deep below, haze, monoliths. */
    public static final net.minecraft.resources.ResourceLocation SKY = com.andy.abyss.Abyss.id("textures/fx/void_sky.png");

    /** The abyss around the camera (also used as the sky inside an open domain, faded in by alpha). */
    public static void drawGalaxy(PoseStack ps, float alpha) { drawGalaxy(ps, alpha, Rt.FILL); }

    public static void drawGalaxy(PoseStack ps, float alpha, net.minecraft.resources.ResourceLocation tex) {
        if (AbyssShaders.VOID == null) return;
        // a small cube with no depth test: it can never cross the far plane (a big one gets clipped into flat shards
        // at short render distances); the galaxy is looked up by direction, so its size doesn't matter
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> AbyssShaders.VOID);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        Matrix4f m = ps.last().pose();
        float s = 8;
        float[][] faces = {
                {-s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s}, {-s, s, -s, -s, s, s, s, s, s, s, s, -s},
                {-s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s}, {-s, -s, s, s, -s, s, s, s, s, -s, s, s},
                {-s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s}, {s, -s, -s, s, s, -s, s, s, s, s, -s, s}};
        for (float[] f : faces) for (int i = 0; i < 4; i++)
            b.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(1f, 1f, 1f, alpha).uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionShader);
    }

    @Override public boolean renderClouds(ClientLevel l, int t, float pt, PoseStack ps, double x, double y, double z, Matrix4f proj) { return true; }
    @Override public boolean renderSnowAndRain(ClientLevel l, int t, float pt, net.minecraft.client.renderer.LightTexture lt, double x, double y, double z) { return true; }
}
