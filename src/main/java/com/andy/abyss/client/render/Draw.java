package com.andy.abyss.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** World-space drawing: billboards, ribbons, rings, spheres, in both sprite and void (galaxy) styles. */
public final class Draw {
    public final Matrix4f m;
    public final Vec3 cam;
    public final float pt;
    public final FxBuffers buf;
    public final Vector3f left = new Vector3f(), up = new Vector3f(), fwd = new Vector3f();
    public final double time;

    public Draw(PoseStack ps, Camera camera, float pt, FxBuffers buf, double time) {
        this.m = ps.last().pose();
        this.cam = camera.getPosition();
        this.pt = pt;
        this.buf = buf;
        this.time = time;
        Quaternionf q = camera.rotation();
        left.set(1, 0, 0).rotate(q);
        up.set(0, 1, 0).rotate(q);
        fwd.set(0, 0, -1).rotate(q);
    }

    private static float c(float v) { return Math.max(0, Math.min(1, v)); }

    public void sv(VertexConsumer vc, double x, double y, double z, int rgb, float a, float u, float v) {
        vc.vertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, c(a)).uv(u, v).endVertex();
    }

    public void vv(VertexConsumer vc, double x, double y, double z, int rgb, float a, float u, float v) {
        vc.vertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, c(a)).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 1, 0).endVertex();
    }

    /** Camera-facing sprite. */
    public void billboard(ResourceLocation tex, boolean additive, Vec3 p, float size, int rgb, float a, float rot) {
        VertexConsumer vc = buf.getBuffer(additive ? Rt.add(tex) : Rt.alpha(tex));
        float cs = (float) Math.cos(rot) * size, sn = (float) Math.sin(rot) * size;
        float lx = left.x * cs + up.x * sn, ly = left.y * cs + up.y * sn, lz = left.z * cs + up.z * sn;
        float ux = -left.x * sn + up.x * cs, uy = -left.y * sn + up.y * cs, uz = -left.z * sn + up.z * cs;
        sv(vc, p.x - lx - ux, p.y - ly - uy, p.z - lz - uz, rgb, a, 0, 1);
        sv(vc, p.x + lx - ux, p.y + ly - uy, p.z + lz - uz, rgb, a, 1, 1);
        sv(vc, p.x + lx + ux, p.y + ly + uy, p.z + lz + uz, rgb, a, 1, 0);
        sv(vc, p.x - lx + ux, p.y - ly + uy, p.z - lz + uz, rgb, a, 0, 0);
    }

    /** A camera-facing ribbon from a to b. */
    public void beam(ResourceLocation tex, boolean additive, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        VertexConsumer vc = buf.getBuffer(additive ? Rt.add(tex) : Rt.alpha(tex));
        Vec3 d = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 toCam = cam.subtract(mid);
        Vec3 side = d.cross(toCam);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width * 0.5);
        sv(vc, a.x - side.x, a.y - side.y, a.z - side.z, rgb, alpha, 0, 1);
        sv(vc, a.x + side.x, a.y + side.y, a.z + side.z, rgb, alpha, 0, 0);
        sv(vc, b.x + side.x, b.y + side.y, b.z + side.z, rgb, alpha, 1, 0);
        sv(vc, b.x - side.x, b.y - side.y, b.z - side.z, rgb, alpha, 1, 1);
    }

    /** A flat textured ring (or disc) lying in the plane with normal n. */
    public void flat(ResourceLocation tex, boolean additive, Vec3 c, Vec3 n, float r, int rgb, float a, float rot) {
        VertexConsumer vc = buf.getBuffer(additive ? Rt.add(tex) : Rt.alpha(tex));
        Vec3[] b = basis(n);
        Vec3 u = b[0].scale(r), v = b[1].scale(r);
        double cs = Math.cos(rot), sn = Math.sin(rot);
        Vec3 uu = u.scale(cs).add(v.scale(sn)), vv = v.scale(cs).subtract(u.scale(sn));
        sv(vc, c.x - uu.x - vv.x, c.y - uu.y - vv.y, c.z - uu.z - vv.z, rgb, a, 0, 0);
        sv(vc, c.x + uu.x - vv.x, c.y + uu.y - vv.y, c.z + uu.z - vv.z, rgb, a, 1, 0);
        sv(vc, c.x + uu.x + vv.x, c.y + uu.y + vv.y, c.z + uu.z + vv.z, rgb, a, 1, 1);
        sv(vc, c.x - uu.x + vv.x, c.y - uu.y + vv.y, c.z - uu.z + vv.z, rgb, a, 0, 1);
    }

    public static Vec3[] basis(Vec3 n) {
        n = n.normalize();
        Vec3 t = Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = n.cross(t).normalize();
        Vec3 v = n.cross(u).normalize();
        return new Vec3[]{u, v};
    }

    /** A galaxy sphere. rim: alpha boost at the silhouette (0 = solid ball, 1 = a shell that is clear in the middle). */
    public void voidSphere(Vec3 c, double r, float alpha, float rim, int rgb) {
        // from inside, a void sphere would replace the whole screen with galaxy: it thins out as your eye reaches it
        double dc = c.distanceTo(cam);
        alpha *= (float) Math.max(0, Math.min(1, (dc - r * 1.02) / Math.max(0.4, r * 0.5)));
        if (alpha < 0.01f) return;
        VertexConsumer vc = buf.getBuffer(Rt.voidFx(Rt.FILL));
        int lat = r > 20 ? 28 : 16, lon = r > 20 ? 48 : 24;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = 2 * Math.PI * j / lon, p1 = 2 * Math.PI * (j + 1) / lon;
                sphereV(vc, c, r, t0, p0, alpha, rim, rgb);
                sphereV(vc, c, r, t1, p0, alpha, rim, rgb);
                sphereV(vc, c, r, t1, p1, alpha, rim, rgb);
                sphereV(vc, c, r, t0, p1, alpha, rim, rgb);
            }
        }
    }

    private void sphereV(VertexConsumer vc, Vec3 c, double r, double th, double ph, float alpha, float rim, int rgb) {
        double nx = Math.sin(th) * Math.cos(ph), ny = Math.cos(th), nz = Math.sin(th) * Math.sin(ph);
        double x = c.x + nx * r, y = c.y + ny * r, z = c.z + nz * r;
        Vec3 view = new Vec3(x - cam.x, y - cam.y, z - cam.z).normalize();
        double facing = Math.abs(view.x * nx + view.y * ny + view.z * nz);
        float a = (float) (alpha * ((1 - rim) + rim * Math.pow(1 - facing, 2.2)));
        vv(vc, x, y, z, rgb, a, 0.5f, 0.5f);
    }

    /** An additive glowing shell: bright at the silhouette, invisible face-on. */
    public void glowShell(Vec3 c, double r, int rgb, float alpha) {
        VertexConsumer vc = buf.getBuffer(Rt.add(Rt.WHITE));
        int lat = 20, lon = 32;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = 2 * Math.PI * j / lon, p1 = 2 * Math.PI * (j + 1) / lon;
                shellV(vc, c, r, t0, p0, rgb, alpha); shellV(vc, c, r, t1, p0, rgb, alpha);
                shellV(vc, c, r, t1, p1, rgb, alpha); shellV(vc, c, r, t0, p1, rgb, alpha);
            }
        }
    }

    private void shellV(VertexConsumer vc, Vec3 c, double r, double th, double ph, int rgb, float alpha) {
        double nx = Math.sin(th) * Math.cos(ph), ny = Math.cos(th), nz = Math.sin(th) * Math.sin(ph);
        double x = c.x + nx * r, y = c.y + ny * r, z = c.z + nz * r;
        Vec3 view = new Vec3(x - cam.x, y - cam.y, z - cam.z).normalize();
        double facing = Math.abs(view.x * nx + view.y * ny + view.z * nz);
        sv(vc, x, y, z, rgb, (float) (alpha * Math.pow(1 - facing, 4)), 0.5f, 0.5f);
    }

    /** A void (galaxy) quad facing the camera, textured with tex's channels. */
    public void voidBillboard(ResourceLocation tex, Vec3 p, float w, float h, float alpha, float rot) {
        VertexConsumer vc = buf.getBuffer(Rt.voidFx(tex));
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        Vector3f l = new Vector3f(left).mul(cs).add(new Vector3f(up).mul(sn)).mul(w);
        Vector3f u = new Vector3f(up).mul(cs).sub(new Vector3f(left).mul(sn)).mul(h);
        vv(vc, p.x - l.x - u.x, p.y - l.y - u.y, p.z - l.z - u.z, 0xFFFFFF, alpha, 0, 1);
        vv(vc, p.x + l.x - u.x, p.y + l.y - u.y, p.z + l.z - u.z, 0xFFFFFF, alpha, 1, 1);
        vv(vc, p.x + l.x + u.x, p.y + l.y + u.y, p.z + l.z + u.z, 0xFFFFFF, alpha, 1, 0);
        vv(vc, p.x - l.x + u.x, p.y - l.y + u.y, p.z - l.z + u.z, 0xFFFFFF, alpha, 0, 0);
    }

    /** A void-shaded crystal shard (octahedron-ish spike) pointing along dir. */
    public void voidShard(Vec3 base, Vec3 dir, float len, float width, float alpha) {
        VertexConsumer vc = buf.getBuffer(Rt.voidTri(Rt.FILL));
        Vec3[] b = basis(dir);
        Vec3 tip = base.add(dir.normalize().scale(len)), tail = base.subtract(dir.normalize().scale(len * 0.25));
        Vec3[] ring = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            ring[i] = base.add(b[0].scale(Math.cos(a) * width)).add(b[1].scale(Math.sin(a) * width));
        }
        for (int i = 0; i < 4; i++) {
            Vec3 p = ring[i], q = ring[(i + 1) % 4];
            vv(vc, tip.x, tip.y, tip.z, 0xFFFFFF, alpha, .5f, .5f); vv(vc, p.x, p.y, p.z, 0xFFFFFF, alpha, .5f, .5f); vv(vc, q.x, q.y, q.z, 0xFFFFFF, alpha, .5f, .5f);
            vv(vc, tail.x, tail.y, tail.z, 0xFFFFFF, alpha, .5f, .5f); vv(vc, q.x, q.y, q.z, 0xFFFFFF, alpha, .5f, .5f); vv(vc, p.x, p.y, p.z, 0xFFFFFF, alpha, .5f, .5f);
        }
    }

    /** A camera-facing ribbon of galaxy from a to b. */
    public void voidRibbon(Vec3 a, Vec3 b, float width, float alpha) {
        VertexConsumer vc = buf.getBuffer(Rt.voidFx(Rt.FILL));
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = b.subtract(a).cross(cam.subtract(mid));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width * 0.5);
        vv(vc, a.x - side.x, a.y - side.y, a.z - side.z, 0xFFFFFF, alpha, 0, 1);
        vv(vc, a.x + side.x, a.y + side.y, a.z + side.z, 0xFFFFFF, alpha, 0, 0);
        vv(vc, b.x + side.x, b.y + side.y, b.z + side.z, 0xFFFFFF, alpha, 1, 0);
        vv(vc, b.x - side.x, b.y - side.y, b.z - side.z, 0xFFFFFF, alpha, 1, 1);
    }

    public Vec3 fwdVec() { return new Vec3(fwd.x, fwd.y, fwd.z); }

    public float near(Vec3 p, float r) {
        double d = p.distanceTo(cam);
        return (float) Math.max(0, Math.min(1, (d - 0.3) / Math.max(0.01, r)));
    }
}
