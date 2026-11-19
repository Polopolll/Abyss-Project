package com.andy.abyss.client;

import com.andy.abyss.client.render.Dust;
import com.andy.abyss.data.Dissolving;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLivingEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Unmade by the abyss (Ronova's ash, in the abyss's terms). Over the 20 ticks a dead body stays in the world:
 * <ol>
 *   <li>crush (0-4): the body is squashed flat and wide by an unseen weight, and goes black;</li>
 *   <li>dissolve (4-16): it shrinks into itself while it sheds black flecks, abyssal ash and a few embers from its
 *   whole volume, drawn up and toward a point just above it;</li>
 *   <li>gone (16-20): nothing is left but a breath of darkness.</li>
 * </ol>
 * The vanilla tip-over, red flash and white poof are replaced.
 */
public final class Disintegrate {
    private static final Map<Integer, Long> START = new HashMap<>();
    private static boolean inside;

    public static void start(int id) {
        START.put(id, (long) ClientState.ticks);
        Dissolving.IDS.add(id);
        var mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        if (e != null) {
            AABB b = e.getBoundingBox();
            Vec3 c = b.getCenter();
            // the blow that unmakes it: a tight ring of pressure and a spit of flecks
            Dust.burst(c, (int) (10 + b.getXsize() * b.getYsize() * 14), 0.22, 0.09f, true);
        }
    }

    public static float progress(Entity e, float pt) {
        Long s = START.get(e.getId());
        return s == null ? -1 : (ClientState.ticks - s + pt) / 20f;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) { START.clear(); Dissolving.IDS.clear(); return; }
        START.entrySet().removeIf(en -> {
            Entity e = mc.level.getEntity(en.getKey());
            long age = ClientState.ticks - en.getValue();
            if (e == null || age > 40) { Dissolving.IDS.remove(en.getKey()); return true; }
            AABB b = e.getBoundingBox();
            float t = age / 20f;
            Vec3 top = b.getCenter().add(0, b.getYsize() * 0.6, 0);
            if (age >= 3 && age <= 16) {
                // shed from the whole (shrinking) volume
                double k = 1 - Math.min(1, Math.max(0, (t - 0.2) / 0.6));
                int n = (int) (6 + b.getXsize() * b.getZsize() * b.getYsize() * 10 * (0.4 + k));
                for (int i = 0; i < n; i++) {
                    Vec3 p = new Vec3(b.minX + Dust.R.nextDouble() * b.getXsize(), b.minY + Dust.R.nextDouble() * b.getYsize() * Math.max(0.15, 0.55 * k + 0.05), b.minZ + Dust.R.nextDouble() * b.getZsize());
                    Vec3 v = Dust.rnd().scale(0.03).add(0, 0.02 + Dust.R.nextDouble() * 0.04, 0);
                    float r = Dust.R.nextFloat();
                    if (r < 0.5f) Dust.add(Dust.Kind.ASH, p, v, 26 + Dust.R.nextInt(22), 0.07f + Dust.R.nextFloat() * 0.08f, r < 0.3f ? Dust.BLACK : Dust.CHAR)
                            .gravity(-0.0015f).drag(0.95f).spin(Dust.R.nextFloat() * 0.3f - 0.15f).pull(top.add(0, 1.2, 0), 0.0025f);
                    else if (r < 0.88f) Dust.add(Dust.Kind.FLECK, p, v.scale(1.4), 14 + Dust.R.nextInt(14), 0.05f + Dust.R.nextFloat() * 0.06f, Dust.R.nextFloat() < 0.75f ? Dust.BLACK : Dust.INDIGO)
                            .drag(0.93f).spin(Dust.R.nextFloat() * 0.5f - 0.25f).pull(top, 0.004f);
                    else Dust.add(Dust.Kind.EMBER, p, v.scale(1.8), 8 + Dust.R.nextInt(8), 0.05f + Dust.R.nextFloat() * 0.04f, Dust.R.nextFloat() < 0.7f ? Dust.VIOLET : Dust.PALE).drag(0.9f).streak();
                }
            }
            if (age == 16) Dust.smoke(b.getCenter().subtract(0, b.getYsize() * 0.25, 0), (int) (4 + b.getXsize() * 4), Math.max(0.3, b.getXsize() * 0.5), 0.5f + (float) b.getXsize() * 0.4f, 22);
            return false;
        });
    }

    /** Replace the body's draw while it is being unmade: crushed, blackened, shrunk, no death tilt or red flash. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRender(RenderLivingEvent.Pre<?, ?> ev) {
        if (inside) return;
        LivingEntity e = ev.getEntity();
        float t = progress(e, ev.getPartialTick());
        if (t < 0) return;
        ev.setCanceled(true);
        if (t >= 0.82f) return; // gone
        float crush = Math.min(1, t / 0.2f), shrink = Math.max(0, Math.min(1, (t - 0.2f) / 0.6f));
        crush = 1 - (1 - crush) * (1 - crush);
        float sy = (1 - 0.55f * crush) * (1 - 0.9f * shrink), sxz = (1 + 0.28f * crush) * (1 - 0.75f * shrink);
        float dark = Math.min(1, t / 0.15f);
        PoseStack ps = ev.getPoseStack();
        ps.pushPose();
        ps.scale(sxz, sy, sxz);
        int dt = e.deathTime, ht = e.hurtTime;
        e.deathTime = 0; e.hurtTime = 0; // no tip-over, no red flash: the abyss holds it upright while it crushes
        inside = true;
        try {
            MultiBufferSource src = ev.getMultiBufferSource();
            MultiBufferSource tinted = rt -> new Tint(src.getBuffer(rt), 1 - dark * 0.93f, 1 - dark * 0.95f, 1 - dark * 0.9f);
            ((net.minecraft.client.renderer.entity.LivingEntityRenderer) ev.getRenderer()).render(e, e.getYRot(), ev.getPartialTick(), ps, tinted, ev.getPackedLight());
        } finally {
            inside = false;
            e.deathTime = dt; e.hurtTime = ht;
            ps.popPose();
        }
    }

    /** Multiplies colour and kills the hurt overlay. */
    private record Tint(VertexConsumer v, float r, float g, float b) implements VertexConsumer {
        public VertexConsumer vertex(double x, double y, double z) { v.vertex(x, y, z); return this; }
        public VertexConsumer color(int cr, int cg, int cb, int ca) { v.color((int) (cr * r), (int) (cg * g), (int) (cb * b), ca); return this; }
        public VertexConsumer uv(float u, float vv) { v.uv(u, vv); return this; }
        public VertexConsumer overlayCoords(int u, int vv) { v.overlayCoords(OverlayTexture.NO_OVERLAY); return this; }
        public VertexConsumer uv2(int u, int vv) { v.uv2(u, vv); return this; }
        public VertexConsumer normal(float x, float y, float z) { v.normal(x, y, z); return this; }
        public void endVertex() { v.endVertex(); }
        public void defaultColor(int cr, int cg, int cb, int ca) { v.defaultColor(cr, cg, cb, ca); }
        public void unsetDefaultColor() { v.unsetDefaultColor(); }
        public void vertex(float x, float y, float z, float cr, float cg, float cb, float ca, float u, float vv, int ov, int lm, float nx, float ny, float nz) {
            v.vertex(x, y, z, cr * r, cg * g, cb * b, ca, u, vv, OverlayTexture.NO_OVERLAY, lm, nx, ny, nz);
        }
    }

    public static void clear() { START.clear(); Dissolving.IDS.clear(); }
    private Disintegrate() {}
}
