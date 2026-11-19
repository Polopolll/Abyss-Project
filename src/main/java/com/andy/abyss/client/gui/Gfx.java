package com.andy.abyss.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** GUI drawing: ring segments (0 = up, clockwise), colour helpers, tinted icons. */
public final class Gfx {
    public static void arc(Matrix4f m, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        float a = ((argb >>> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        int steps = Math.max(2, (int) (Math.abs(a1 - a0) * 24));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            float ang = a0 + (a1 - a0) * i / steps;
            float s = Mth.sin(ang), c = -Mth.cos(ang);
            bb.vertex(m, cx + s * r1, cy + c * r1, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, cx + s * r0, cy + c * r0, 0).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
    }

    public static void ring(Matrix4f m, float cx, float cy, float r0, float r1, int argb) { arc(m, cx, cy, r0, r1, 0, Mth.TWO_PI, argb); }

    public static int argb(float alpha, int rgb) { return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF); }

    public static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    public static void icon(GuiGraphics g, ResourceLocation tex, float cx, float cy, int size, float bright, int tint, float tintAmt, float alpha) {
        int col = mix(0xFFFFFF, tint, tintAmt);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((col >> 16) & 255) / 255f * bright, ((col >> 8) & 255) / 255f * bright, (col & 255) / 255f * bright, alpha);
        g.blit(tex, Math.round(cx - size / 2f), Math.round(cy - size / 2f), 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    public static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }
    private Gfx() {}
}
