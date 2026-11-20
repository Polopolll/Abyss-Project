package com.andy.abyss.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;

/**
 * Real glow. The additive effects are drawn into their own target (sharing the world's depth, so walls still hide
 * them), blurred at half and quarter resolution, and added back over the frame: a soft halo that bleeds past the
 * shape, like bloom in a modern game. Off with the client setting, or automatically if anything goes wrong.
 */
public final class Bloom {
    public static ShaderInstance BLUR, COMPOSITE;
    private static TextureTarget src, h1, v1, h2, v2;
    private static boolean broken;

    public static boolean available() {
        return !broken && BLUR != null && COMPOSITE != null && com.andy.abyss.Config.bloom.get();
    }

    private static TextureTarget make(int w, int h, boolean depth) {
        TextureTarget t = new TextureTarget(Math.max(1, w), Math.max(1, h), depth, Minecraft.ON_OSX);
        t.setFilterMode(GL11.GL_LINEAR);
        t.setClearColor(0, 0, 0, 0);
        return t;
    }

    private static void ensure(RenderTarget main) {
        int w = main.width, h = main.height;
        if (src != null && src.width == w && src.height == h) return;
        for (TextureTarget t : new TextureTarget[]{src, h1, v1, h2, v2}) if (t != null) t.destroyBuffers();
        src = make(w, h, true);
        h1 = make(w / 2, h / 2, false); v1 = make(w / 2, h / 2, false);
        h2 = make(w / 4, h / 4, false); v2 = make(w / 4, h / 4, false);
    }

    /** Switch drawing to the glow target (cleared, with the world's depth). False = draw glow straight into the frame. */
    public static boolean begin() {
        if (!available()) return false;
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            ensure(main);
            src.clear(Minecraft.ON_OSX);
            src.copyDepthFrom(main);
            src.bindWrite(false);
            return true;
        } catch (Throwable t) {
            broken = true;
            com.andy.abyss.Abyss.LOG.warn("Abyss glow disabled: {}", t.toString());
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            return false;
        }
    }

    /** Blur the glow and add it over the frame. */
    public static void end() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            pass(src, h1, 1.2f / src.width, 0);
            pass(h1, v1, 0, 1.2f / h1.height);
            pass(v1, h2, 1.6f / v1.width, 0);
            pass(h2, v2, 0, 1.6f / h2.height);
            main.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            COMPOSITE.setSampler("Sampler0", src.getColorTextureId());
            COMPOSITE.setSampler("Sampler1", v1.getColorTextureId());
            COMPOSITE.setSampler("Sampler2", v2.getColorTextureId());
            COMPOSITE.safeGetUniform("Intensity").set(com.andy.abyss.Config.bloomStrength.get().floatValue());
            RenderSystem.setShader(() -> COMPOSITE);
            quad();
        } catch (Throwable t) {
            broken = true;
            com.andy.abyss.Abyss.LOG.warn("Abyss glow disabled: {}", t.toString());
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            main.bindWrite(true);
        }
    }

    private static void pass(RenderTarget from, TextureTarget to, float dx, float dy) {
        to.clear(Minecraft.ON_OSX);
        to.bindWrite(true);
        BLUR.setSampler("Sampler0", from.getColorTextureId());
        BLUR.safeGetUniform("Dir").set(dx, dy);
        RenderSystem.setShader(() -> BLUR);
        quad();
    }

    private static void quad() {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1, -1, 0).uv(0, 0).endVertex();
        b.vertex(1, -1, 0).uv(1, 0).endVertex();
        b.vertex(1, 1, 0).uv(1, 1).endVertex();
        b.vertex(-1, 1, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    private Bloom() {}
}
