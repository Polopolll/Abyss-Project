package com.andy.abyss.client.render;

import com.andy.abyss.combat.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The abyssal constructs as procedural crystal meshes (units: blocks; the grip is at the origin, the weapon points
 * along +Y). Drawn twice: a galaxy body (the void shader, so you look into space through the blade) and violet light
 * along every crystal edge.
 */
public final class WeaponModel {
    record Tri(Vector3f a, Vector3f b, Vector3f c, float galaxy) {}
    record Edge(Vector3f a, Vector3f b) {}
    static final class Mesh { final List<Tri> tris = new ArrayList<>(); final List<Edge> edges = new ArrayList<>(); }
    private static final Map<Weapon, Mesh> MESHES = new EnumMap<>(Weapon.class);
    private static Mesh SHIELD;

    private static Vector3f v(float x, float y, float z) { return new Vector3f(x, y, z); }

    /** A crystal blade: rhombus cross-section (width w, thickness t) from y0 to y1, narrowing to a point. */
    private static void blade(Mesh m, float y0, float y1, float w, float t, float tipStart, float curve, float g) {
        int seg = 8;
        Vector3f[][] ring = new Vector3f[seg + 1][];
        for (int i = 0; i <= seg; i++) {
            float k = i / (float) seg;
            float y = y0 + (y1 - y0) * k;
            float ww = k < tipStart ? w : w * (1 - (k - tipStart) / (1 - tipStart));
            float bend = curve * k * k;
            ring[i] = new Vector3f[]{v(ww / 2 + bend, y, 0), v(bend, y, t / 2), v(-ww / 2 + bend * 0.6f, y, 0), v(bend, y, -t / 2)};
        }
        for (int i = 0; i < seg; i++) for (int j = 0; j < 4; j++) {
            Vector3f a = ring[i][j], b = ring[i][(j + 1) % 4], c = ring[i + 1][(j + 1) % 4], d = ring[i + 1][j];
            m.tris.add(new Tri(a, b, c, g)); m.tris.add(new Tri(a, c, d, g));
        }
        for (int j = 0; j < 4; j += 2) for (int i = 0; i < seg; i++) m.edges.add(new Edge(ring[i][j], ring[i + 1][j]));
        m.edges.add(new Edge(ring[0][0], ring[0][2]));
    }

    /** A hexagonal prism along Y (radius r, from y0 to y1), optionally tapering to r2. */
    private static void prism(Mesh m, float y0, float y1, float r, float r2, float g, float ox, float oz) {
        int n = 6;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            Vector3f p0 = v(ox + (float) Math.cos(a0) * r, y0, oz + (float) Math.sin(a0) * r), p1 = v(ox + (float) Math.cos(a1) * r, y0, oz + (float) Math.sin(a1) * r);
            Vector3f q0 = v(ox + (float) Math.cos(a0) * r2, y1, oz + (float) Math.sin(a0) * r2), q1 = v(ox + (float) Math.cos(a1) * r2, y1, oz + (float) Math.sin(a1) * r2);
            m.tris.add(new Tri(p0, p1, q1, g)); m.tris.add(new Tri(p0, q1, q0, g));
            m.tris.add(new Tri(v(ox, y0, oz), p1, p0, g)); m.tris.add(new Tri(v(ox, y1, oz), q0, q1, g));
            m.edges.add(new Edge(p0, q0));
        }
    }

    /** A box (half sizes) at a centre, faceted. */
    private static void box(Mesh m, float cx, float cy, float cz, float hx, float hy, float hz, float g) {
        Vector3f[] c = new Vector3f[8];
        for (int i = 0; i < 8; i++) c[i] = v(cx + ((i & 1) == 0 ? -hx : hx), cy + ((i & 2) == 0 ? -hy : hy), cz + ((i & 4) == 0 ? -hz : hz));
        int[][] f = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] q : f) { m.tris.add(new Tri(c[q[0]], c[q[1]], c[q[2]], g)); m.tris.add(new Tri(c[q[0]], c[q[2]], c[q[3]], g)); }
        int[][] e = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] q : e) m.edges.add(new Edge(c[q[0]], c[q[1]]));
    }

    /** A flat crescent (scythe blade) in the XY plane from a centre, radius r, width w, angles a0..a1 (deg). */
    private static void crescent(Mesh m, float cx, float cy, float r, float w, float a0, float a1, float t, float g) {
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            float k0 = i / (float) seg, k1 = (i + 1) / (float) seg;
            double t0 = Math.toRadians(a0 + (a1 - a0) * k0), t1 = Math.toRadians(a0 + (a1 - a0) * k1);
            float w0 = w * (float) Math.sin(Math.PI * Math.min(1, k0 * 1.1)), w1 = w * (float) Math.sin(Math.PI * Math.min(1, k1 * 1.1));
            Vector3f o0 = v(cx + (float) Math.cos(t0) * r, cy + (float) Math.sin(t0) * r, 0), o1 = v(cx + (float) Math.cos(t1) * r, cy + (float) Math.sin(t1) * r, 0);
            Vector3f i0 = v(cx + (float) Math.cos(t0) * (r - w0), cy + (float) Math.sin(t0) * (r - w0), 0), i1 = v(cx + (float) Math.cos(t1) * (r - w1), cy + (float) Math.sin(t1) * (r - w1), 0);
            Vector3f u0 = v(i0.x, i0.y, t), u1 = v(i1.x, i1.y, t), d0 = v(i0.x, i0.y, -t), d1 = v(i1.x, i1.y, -t);
            m.tris.add(new Tri(o0, o1, u1, g)); m.tris.add(new Tri(o0, u1, u0, g));
            m.tris.add(new Tri(o0, d1, o1, g)); m.tris.add(new Tri(o0, d0, d1, g));
            m.edges.add(new Edge(o0, o1));
        }
    }

    private static void hexShield(Mesh m, float r, float t, float g) {
        for (int i = 0; i < 6; i++) {
            double a0 = i * Math.PI / 3, a1 = (i + 1) * Math.PI / 3;
            Vector3f p0 = v((float) Math.cos(a0) * r, (float) Math.sin(a0) * r, 0), p1 = v((float) Math.cos(a1) * r, (float) Math.sin(a1) * r, 0);
            Vector3f f = v(0, 0, t), b = v(0, 0, -t * 0.5f);
            m.tris.add(new Tri(f, p0, p1, 1)); m.tris.add(new Tri(b, p1, p0, 0.4f));
            m.edges.add(new Edge(p0, p1)); m.edges.add(new Edge(f, p0));
        }
    }

    static Mesh mesh(Weapon w) {
        return MESHES.computeIfAbsent(w, k -> {
            Mesh m = new Mesh();
            switch (k) {
                case SWORD -> { prism(m, -0.22f, 0.02f, 0.03f, 0.03f, 0.2f, 0, 0); box(m, 0, 0.04f, 0, 0.17f, 0.025f, 0.04f, 0.3f); blade(m, 0.06f, 1.25f, 0.17f, 0.045f, 0.78f, 0.04f, 1); }
                case GREATSWORD -> { prism(m, -0.42f, 0.02f, 0.045f, 0.045f, 0.2f, 0, 0); box(m, 0, 0.05f, 0, 0.3f, 0.04f, 0.06f, 0.4f); blade(m, 0.08f, 1.95f, 0.28f, 0.06f, 0.82f, 0, 1); blade(m, 0.4f, 1.3f, 0.12f, 0.08f, 0.6f, 0, 0.6f); }
                case HAMMER -> { prism(m, -0.35f, 1.0f, 0.035f, 0.035f, 0.2f, 0, 0); prism(m, 0.95f, 1.35f, 0.2f, 0.2f, 1, 0, 0); box(m, 0, 1.15f, 0, 0.3f, 0.12f, 0.12f, 1); prism(m, 1.35f, 1.55f, 0.12f, 0.0f, 0.8f, 0, 0); }
                case SCYTHE -> { prism(m, -0.5f, 1.4f, 0.03f, 0.03f, 0.2f, 0, 0); crescent(m, -0.55f, 1.25f, 0.75f, 0.2f, 10, 120, 0.025f, 1); }
                case SPEAR -> { prism(m, -0.7f, 1.4f, 0.028f, 0.028f, 0.2f, 0, 0); blade(m, 1.35f, 1.85f, 0.14f, 0.04f, 0.35f, 0, 1); crescent(m, 0, 1.4f, 0.18f, 0.06f, 200, 340, 0.02f, 0.8f); }
                case BULWARK -> { prism(m, -0.18f, 0.02f, 0.028f, 0.028f, 0.2f, 0, 0); box(m, 0, 0.03f, 0, 0.12f, 0.02f, 0.035f, 0.3f); blade(m, 0.05f, 0.85f, 0.14f, 0.04f, 0.7f, 0, 1); }
                case GAUNTLETS -> { box(m, 0, 0.05f, 0, 0.11f, 0.12f, 0.11f, 1); prism(m, 0.12f, 0.3f, 0.07f, 0.0f, 0.9f, 0.06f, 0); prism(m, 0.12f, 0.26f, 0.06f, 0.0f, 0.9f, -0.06f, 0); }
            }
            return m;
        });
    }

    static Mesh shield() { if (SHIELD == null) { SHIELD = new Mesh(); hexShield(SHIELD, 0.42f, 0.07f, 1); } return SHIELD; }

    /** Draw a weapon with the pose stack's current transform. alpha fades it in and out (forging). */
    public static void render(PoseStack ps, MultiBufferSource buf, Weapon w, float alpha, float glow) { render(ps, buf, mesh(w), alpha, glow); }
    public static void renderShield(PoseStack ps, MultiBufferSource buf, float alpha, float glow) { render(ps, buf, shield(), alpha, glow); }

    private static void render(PoseStack ps, MultiBufferSource buf, Mesh m, float alpha, float glow) {
        if (AbyssShaders.VOID == null || alpha <= 0.01f) return;
        Matrix4f mat = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(Rt.voidTri(Rt.BODY_CORE));
        for (Tri t : m.tris) {
            Vector3f n = new Vector3f(t.b()).sub(t.a()).cross(new Vector3f(t.c()).sub(t.a())).normalize();
            float shade = 0.75f + 0.25f * Math.abs(n.y);
            float u = t.galaxy() >= 0.9f ? 0.25f : 0.75f;
            for (Vector3f p : new Vector3f[]{t.a(), t.b(), t.c()})
                vc.vertex(mat, p.x, p.y, p.z).color(shade, shade, shade, alpha).uv(u, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nm, n.x, n.y, n.z).endVertex();
        }
        VertexConsumer g = buf.getBuffer(Rt.add(Rt.WHITE));
        float a = alpha * glow;
        for (Edge e : m.edges) {
            Vector3f d = new Vector3f(e.b()).sub(e.a());
            Vector3f side = new Vector3f(d).cross(0, 0, 1);
            if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
            side.normalize(0.012f);
            Vector3f side2 = new Vector3f(d).cross(1, 0, 0);
            if (side2.lengthSquared() < 1e-6f) side2.set(0, 0, 1);
            side2.normalize(0.012f);
            for (Vector3f s : new Vector3f[]{side, side2}) {
                g.vertex(mat, e.a().x - s.x, e.a().y - s.y, e.a().z - s.z).color(0.6f, 0.32f, 1f, a).uv(0.5f, 0.5f).endVertex();
                g.vertex(mat, e.a().x + s.x, e.a().y + s.y, e.a().z + s.z).color(0.6f, 0.32f, 1f, a).uv(0.5f, 0.5f).endVertex();
                g.vertex(mat, e.b().x + s.x, e.b().y + s.y, e.b().z + s.z).color(0.78f, 0.6f, 1f, a).uv(0.5f, 0.5f).endVertex();
                g.vertex(mat, e.b().x - s.x, e.b().y - s.y, e.b().z - s.z).color(0.78f, 0.6f, 1f, a).uv(0.5f, 0.5f).endVertex();
            }
        }
    }
    private WeaponModel() {}
}
