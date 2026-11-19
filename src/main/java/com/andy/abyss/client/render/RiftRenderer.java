package com.andy.abyss.client.render;

import com.andy.abyss.entity.Rift;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** A standing tear of galaxy with jagged purple edges, opening and closing. */
public class RiftRenderer extends EntityRenderer<Rift> {
    public RiftRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public void render(Rift r, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (AbyssShaders.VOID == null) return;
        float age = r.tickCount + pt;
        float open = Math.min(1f, age / 8f) * Math.min(1f, (r.life() - pt) / 10f);
        if (open <= 0) return;
        ps.pushPose();
        try {
            ps.translate(0, 1.3, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-r.getYRot()));
            float w = 0.95f * open, h = 1.35f * (0.4f + 0.6f * open);
            float wob = (float) Math.sin(age * 0.3) * 0.03f;
            VertexConsumer vc = buf.getBuffer(Rt.voidFx(Rt.RIFT));
            Matrix4f m = ps.last().pose();
            quad(vc, m, w + wob, h, 1f);
            // glow edge
            VertexConsumer g = buf.getBuffer(Rt.add(Rt.GLOW));
            float gw = w * 1.7f, gh = h * 1.3f;
            g.vertex(m, -gw, -gh, -0.01f).color(0.55f, 0.3f, 1f, 0.5f * open).uv(0, 1).endVertex();
            g.vertex(m, gw, -gh, -0.01f).color(0.55f, 0.3f, 1f, 0.5f * open).uv(1, 1).endVertex();
            g.vertex(m, gw, gh, -0.01f).color(0.55f, 0.3f, 1f, 0.5f * open).uv(1, 0).endVertex();
            g.vertex(m, -gw, gh, -0.01f).color(0.55f, 0.3f, 1f, 0.5f * open).uv(0, 0).endVertex();
        } finally { ps.popPose(); }
        super.render(r, yaw, pt, ps, buf, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float w, float h, float a) {
        v(vc, m, -w, -h, 0, 1, a); v(vc, m, w, -h, 1, 1, a); v(vc, m, w, h, 1, 0, a); v(vc, m, -w, h, 0, 0, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float u, float vv, float a) {
        vc.vertex(m, x, y, 0).color(1f, 1f, 1f, a).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 0, 1).endVertex();
    }

    @Override public ResourceLocation getTextureLocation(Rift r) { return Rt.RIFT; }
    @Override protected int getBlockLightLevel(Rift r, net.minecraft.core.BlockPos p) { return 15; }
}
