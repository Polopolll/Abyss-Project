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
    private static final Matrix4f ID = new Matrix4f();

    // ------------------------------------------------------------------------------------------------ primitives
    // Every primitive is built in its own local frame and placed with a transform, so parts can be turned and
    // positioned freely. All solids are closed, so with depth writing on they read as real objects.

    private static Vector3f at(Matrix4f t, float x, float y, float z) { return t.transformPosition(new Vector3f(x, y, z)); }
    private static void tri(Mesh m, Vector3f a, Vector3f b, Vector3f c, float g) { m.tris.add(new Tri(a, b, c, g)); }
    private static void quad(Mesh m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float g) { tri(m, a, b, c, g); tri(m, a, c, d, g); }

    /**
     * A faceted crystal plate in the local XY plane: an outline from left edge points L to right edge points R, with a
     * ridge down the spine S raised to +th/-th on the two faces (a diamond cross-section, like a forged blade).
     * The base is capped; a point (L == R) closes itself.
     */
    private static void plate(Mesh m, Matrix4f t, float[][] L, float[][] S, float[][] R, float[] th, float g, boolean outline, boolean ridge) {
        int n = L.length;
        Vector3f[] l = new Vector3f[n], r = new Vector3f[n], f = new Vector3f[n], b = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            l[i] = at(t, L[i][0], L[i][1], 0); r[i] = at(t, R[i][0], R[i][1], 0);
            f[i] = at(t, S[i][0], S[i][1], th[i]); b[i] = at(t, S[i][0], S[i][1], -th[i]);
        }
        for (int i = 0; i < n - 1; i++) {
            quad(m, l[i], l[i + 1], f[i + 1], f[i], g); quad(m, f[i], f[i + 1], r[i + 1], r[i], g);
            quad(m, l[i], b[i], b[i + 1], l[i + 1], g); quad(m, b[i], r[i], r[i + 1], b[i + 1], g);
            if (outline) { m.edges.add(new Edge(l[i], l[i + 1])); m.edges.add(new Edge(r[i], r[i + 1])); }
            if (ridge) { m.edges.add(new Edge(f[i], f[i + 1])); m.edges.add(new Edge(b[i], b[i + 1])); }
        }
        quad(m, l[0], f[0], r[0], b[0], g);
        if (l[n - 1].distanceSquared(r[n - 1]) > 1e-8f) quad(m, l[n - 1], b[n - 1], r[n - 1], f[n - 1], g);
        if (outline) m.edges.add(new Edge(l[0], r[0]));
    }

    /** An n-sided prism along local Y, radius r0 at y0 to r1 at y1 (r1 = 0 is a point), capped. */
    private static void prism(Mesh m, Matrix4f t, int n, float y0, float y1, float r0, float r1, float rot, float g, boolean edges) {
        Vector3f c0 = at(t, 0, y0, 0), c1 = at(t, 0, y1, 0);
        for (int i = 0; i < n; i++) {
            double a0 = rot + i * Math.PI * 2 / n, a1 = rot + (i + 1) * Math.PI * 2 / n;
            Vector3f p0 = at(t, (float) Math.cos(a0) * r0, y0, (float) Math.sin(a0) * r0), p1 = at(t, (float) Math.cos(a1) * r0, y0, (float) Math.sin(a1) * r0);
            Vector3f q0 = at(t, (float) Math.cos(a0) * r1, y1, (float) Math.sin(a0) * r1), q1 = at(t, (float) Math.cos(a1) * r1, y1, (float) Math.sin(a1) * r1);
            quad(m, p0, p1, q1, q0, g);
            tri(m, c0, p1, p0, g);
            if (r1 > 0) tri(m, c1, q0, q1, g);
            if (edges) { m.edges.add(new Edge(p0, q0)); m.edges.add(new Edge(p0, p1)); if (r1 > 0) m.edges.add(new Edge(q0, q1)); }
        }
    }

    /** A cut gem: an n-sided bipyramid along local Y with a girdle of radius r and points at +-h. */
    private static void gem(Mesh m, Matrix4f t, int n, float r, float h, float g) {
        Vector3f top = at(t, 0, h, 0), bot = at(t, 0, -h, 0);
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            Vector3f p0 = at(t, (float) Math.cos(a0) * r, 0, (float) Math.sin(a0) * r), p1 = at(t, (float) Math.cos(a1) * r, 0, (float) Math.sin(a1) * r);
            tri(m, top, p0, p1, g); tri(m, bot, p1, p0, g);
            m.edges.add(new Edge(p0, p1)); m.edges.add(new Edge(top, p0)); m.edges.add(new Edge(bot, p0));
        }
    }

    private static Matrix4f tr(float x, float y, float z) { return new Matrix4f().translation(x, y, z); }

    /** A blade along +Y from y0 to y1: half widths left/right by k, a tip that starts at tipK, slight backward curve. */
    private static void blade(Mesh m, Matrix4f t, float y0, float y1, java.util.function.Function<Float, float[]> prof, int seg, float g) {
        float[][] L = new float[seg + 1][], S = new float[seg + 1][], R = new float[seg + 1][];
        float[] th = new float[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float k = i / (float) seg, y = y0 + (y1 - y0) * k;
            float[] p = prof.apply(k); // {left half width, right half width, spine x, thickness}
            L[i] = new float[]{p[2] - p[0], y}; R[i] = new float[]{p[2] + p[1], y}; S[i] = new float[]{p[2] + (p[1] - p[0]) * 0.15f, y}; th[i] = p[3];
        }
        plate(m, t, L, S, R, th, g, true, true);
    }

    /** A plate whose spine is a curve: pts along the spine, left/right offsets along the curve normal (in plane). */
    private static void curvedPlate(Mesh m, Matrix4f t, float[][] spine, float[] left, float[] right, float[] th, float ridgeBias, float g) {
        int n = spine.length;
        float[][] L = new float[n][], S = new float[n][], R = new float[n][];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float dx = spine[b][0] - spine[a][0], dy = spine[b][1] - spine[a][1];
            float len = (float) Math.sqrt(dx * dx + dy * dy); float nx = -dy / len, ny = dx / len;
            L[i] = new float[]{spine[i][0] + nx * left[i], spine[i][1] + ny * left[i]};
            R[i] = new float[]{spine[i][0] - nx * right[i], spine[i][1] - ny * right[i]};
            float sb = (left[i] - right[i]) * 0.5f + (left[i] + right[i]) * ridgeBias;
            S[i] = new float[]{spine[i][0] + nx * sb, spine[i][1] + ny * sb};
        }
        plate(m, t, L, S, R, th, g, true, true);
    }

    private static float bez(float a, float b, float c, float k) { return (1 - k) * (1 - k) * a + 2 * (1 - k) * k * b + k * k * c; }

    /** A grip: a slim octagonal handle with wrapped bands, and a gem pommel. */
    private static void grip(Mesh m, float y0, float y1, float r, int bands) {
        prism(m, ID, 8, y0, y1, r, r, 0, DARK, false);
        for (int i = 0; i <= bands; i++) {
            float y = y0 + (y1 - y0) * i / bands;
            prism(m, ID, 8, y - 0.012f, y + 0.012f, r * 1.35f, r * 1.35f, 0, DARK, false);
        }
        gem(m, tr(0, y0 - r * 1.9f, 0), 6, r * 1.6f, r * 1.9f, GALAXY);
    }

    private static final float GALAXY = 1f, DARK = 0.3f;

    static Mesh mesh(Weapon w) {
        return MESHES.computeIfAbsent(w, k -> {
            Mesh m = new Mesh();
            switch (k) {
                case SWORD -> {
                    grip(m, -0.24f, 0.0f, 0.027f, 3);
                    // guard: wings sweeping up toward the blade, a gem at the heart
                    int n = 9; float[][] gs = new float[n][]; float[] gl = new float[n], gr = new float[n], gt = new float[n];
                    for (int i = 0; i < n; i++) { float x = -0.2f + 0.4f * i / (n - 1), q = Math.abs(x) / 0.2f; gs[i] = new float[]{x, 0.025f + 0.06f * q * q}; gl[i] = 0.026f * (1 - 0.6f * q); gr[i] = gl[i]; gt[i] = 0.034f * (1 - 0.6f * q); }
                    curvedPlate(m, ID, gs, gl, gr, gt, 0, DARK);
                    gem(m, new Matrix4f().translation(0, 0.03f, 0).rotateX((float) Math.PI / 2), 6, 0.032f, 0.045f, GALAXY);
                    // a slender, slightly curved blade with a clipped (kissaki) point
                    blade(m, ID, 0.05f, 1.3f, kk -> {
                        float wl = 0.072f - 0.012f * kk, wr = wl;
                        if (kk > 0.84f) { float q = (kk - 0.84f) / 0.16f; wl *= 1 - q; wr *= (float) Math.sqrt(Math.max(0, 1 - q * q)) * (1 - q * 0.15f); }
                        return new float[]{wl, wr, 0.035f * kk * kk, 0.03f * (1 - 0.6f * kk)};
                    }, 14, GALAXY);
                }
                case GREATSWORD -> {
                    grip(m, -0.46f, 0.0f, 0.038f, 4);
                    int n = 11; float[][] gs = new float[n][]; float[] gl = new float[n], gr = new float[n], gt = new float[n];
                    for (int i = 0; i < n; i++) { float x = -0.38f + 0.76f * i / (n - 1), q = Math.abs(x) / 0.38f; gs[i] = new float[]{x, 0.04f + 0.12f * (float) Math.pow(q, 2.2)}; gl[i] = 0.05f * (1 - 0.85f * q * q); gr[i] = gl[i]; gt[i] = 0.06f * (1 - 0.5f * q); }
                    curvedPlate(m, ID, gs, gl, gr, gt, 0, DARK);
                    // broad blade: a narrow ricasso, shoulders, a long straight edge, and a chisel point
                    blade(m, ID, 0.08f, 2.05f, kk -> {
                        float wdt = kk < 0.07f ? 0.1f : kk < 0.11f ? 0.1f + 0.075f * (kk - 0.07f) / 0.04f : 0.175f - 0.02f * kk;
                        float wl = wdt, wr = wdt;
                        if (kk > 0.8f) { float q = (kk - 0.8f) / 0.2f; wl = wdt * (1 - q); wr = wdt * (1 - q * q); }
                        return new float[]{wl, wr, 0, 0.055f * (1 - 0.5f * kk)};
                    }, 16, GALAXY);
                    // a dark spine bar up the middle of the blade, with a gem set in it
                    blade(m, ID, 0.1f, 1.45f, kk -> new float[]{0.035f * (1 - kk * 0.7f), 0.035f * (1 - kk * 0.7f), 0, 0.072f * (1 - 0.6f * kk)}, 6, DARK);
                    gem(m, new Matrix4f().translation(0, 0.3f, 0).rotateX((float) Math.PI / 2), 4, 0.055f, 0.085f, GALAXY);
                }
                case HAMMER -> {
                    prism(m, ID, 8, -0.36f, 1.02f, 0.034f, 0.034f, 0, DARK, false);
                    for (float y : new float[]{-0.36f, -0.05f, 0.25f, 0.55f, 1.0f}) prism(m, ID, 8, y - 0.015f, y + 0.015f, 0.047f, 0.047f, 0, DARK, false);
                    gem(m, tr(0, -0.42f, 0), 6, 0.05f, 0.07f, GALAXY);
                    // the head: an octagonal drum on its side, bevelled faces, banded in dark crystal
                    Matrix4f head = new Matrix4f().translation(0, 1.2f, 0).rotateZ((float) -Math.PI / 2);
                    prism(m, head, 8, -0.36f, -0.3f, 0.11f, 0.17f, (float) Math.PI / 8, GALAXY, true);
                    prism(m, head, 8, -0.3f, 0.3f, 0.17f, 0.17f, (float) Math.PI / 8, GALAXY, true);
                    prism(m, head, 8, 0.3f, 0.36f, 0.17f, 0.11f, (float) Math.PI / 8, GALAXY, true);
                    for (float y : new float[]{-0.18f, 0.18f}) prism(m, head, 8, y - 0.035f, y + 0.035f, 0.185f, 0.185f, (float) Math.PI / 8, DARK, true);
                    prism(m, head, 4, 0.36f, 0.44f, 0.08f, 0.0f, 0, DARK, true);
                    prism(m, head, 4, -0.36f, -0.44f, 0.08f, 0.0f, 0, DARK, true);
                    prism(m, ID, 6, 0.95f, 1.05f, 0.05f, 0.07f, 0, DARK, true);
                    prism(m, ID, 4, 1.34f, 1.52f, 0.07f, 0.0f, (float) Math.PI / 4, DARK, true);
                }
                case SCYTHE -> {
                    prism(m, ID, 8, -0.62f, 1.4f, 0.028f, 0.028f, 0, DARK, false);
                    for (float y : new float[]{-0.62f, -0.2f, 0.45f, 0.65f, 1.38f}) prism(m, ID, 8, y - 0.015f, y + 0.015f, 0.04f, 0.04f, 0, DARK, false);
                    gem(m, tr(0, -0.69f, 0), 6, 0.04f, 0.07f, GALAXY);
                    prism(m, ID, 6, 1.36f, 1.5f, 0.05f, 0.04f, 0, DARK, true);
                    // the crescent: a thick back that thins to a long inner edge, sweeping out and down to a point
                    int n = 18; float[][] sp = new float[n][]; float[] lo = new float[n], li = new float[n], th = new float[n];
                    for (int i = 0; i < n; i++) {
                        float q = i / (float) (n - 1);
                        sp[i] = new float[]{bez(0.02f, -0.5f, -1.05f, q), bez(1.46f, 1.78f, 1.08f, q)};
                        lo[i] = 0.025f * (1 - q);
                        li[i] = 0.2f * (float) Math.sin(Math.PI * Math.min(1, 0.25f + q * 0.8f)) * (1 - q * 0.2f);
                        th[i] = 0.03f * (1 - q * 0.8f);
                    }
                    curvedPlate(m, ID, sp, lo, li, th, -0.35f, GALAXY);
                    // a back spike balancing the blade
                    curvedPlate(m, ID, new float[][]{{0.02f, 1.45f}, {0.13f, 1.5f}, {0.25f, 1.6f}}, new float[]{0.025f, 0.015f, 0}, new float[]{0.025f, 0.015f, 0}, new float[]{0.02f, 0.012f, 0.002f}, 0, DARK);
                }
                case SPEAR -> {
                    prism(m, ID, 8, -0.78f, 1.42f, 0.025f, 0.025f, 0, DARK, false);
                    for (float y : new float[]{-0.7f, -0.1f, 0.35f, 1.0f}) prism(m, ID, 8, y - 0.014f, y + 0.014f, 0.036f, 0.036f, 0, DARK, false);
                    prism(m, ID, 4, -0.78f, -0.95f, 0.035f, 0.0f, (float) Math.PI / 4, GALAXY, true);
                    prism(m, ID, 6, 1.4f, 1.53f, 0.035f, 0.05f, 0, DARK, true);
                    // a leaf head with a raised midrib
                    blade(m, ID, 1.52f, 2.12f, kk -> {
                        float wdt = kk < 0.3f ? 0.11f * (float) Math.sin(kk / 0.3f * Math.PI / 2) : 0.11f * (1 - (kk - 0.3f) / 0.7f);
                        return new float[]{Math.max(0.012f, wdt) * (kk >= 0.999f ? 0 : 1), Math.max(0.012f, wdt) * (kk >= 0.999f ? 0 : 1), 0, 0.035f * (1 - 0.7f * kk)};
                    }, 12, GALAXY);
                    // two hooked wings below the head
                    for (int s = -1; s <= 1; s += 2) {
                        float[][] sp = new float[6][]; float[] wl = new float[6], wr = new float[6], th = new float[6];
                        for (int i = 0; i < 6; i++) { float q = i / 5f; sp[i] = new float[]{s * bez(0.04f, 0.17f, 0.15f, q), bez(1.5f, 1.52f, 1.7f, q)}; wl[i] = 0.02f * (1 - q); wr[i] = wl[i]; th[i] = 0.018f * (1 - q * 0.8f); }
                        curvedPlate(m, ID, sp, wl, wr, th, 0, DARK);
                    }
                }
                case BULWARK -> {
                    grip(m, -0.2f, 0.0f, 0.026f, 2);
                    curvedPlate(m, ID, new float[][]{{-0.13f, 0.03f}, {0, 0.03f}, {0.13f, 0.03f}}, new float[]{0.02f, 0.03f, 0.02f}, new float[]{0.02f, 0.03f, 0.02f}, new float[]{0.025f, 0.035f, 0.025f}, 0, DARK);
                    // a broad, straight shard with a long angular point
                    blade(m, ID, 0.05f, 0.92f, kk -> {
                        float wdt = 0.085f - 0.01f * kk;
                        if (kk > 0.68f) wdt *= 1 - (kk - 0.68f) / 0.32f;
                        return new float[]{wdt, wdt, 0, 0.032f * (1 - 0.5f * kk)};
                    }, 10, GALAXY);
                }
                case GAUNTLETS -> {
                    // cuff, an armoured back of the hand, a gem, and four knuckle spikes
                    prism(m, ID, 8, -0.24f, -0.05f, 0.1f, 0.115f, (float) Math.PI / 8, DARK, true);
                    prism(m, ID, 8, -0.06f, -0.03f, 0.13f, 0.13f, (float) Math.PI / 8, DARK, true);
                    prism(m, ID, 4, -0.03f, 0.14f, 0.135f, 0.12f, (float) Math.PI / 4, GALAXY, true);
                    prism(m, ID, 4, 0.14f, 0.18f, 0.12f, 0.1f, (float) Math.PI / 4, DARK, true);
                    gem(m, new Matrix4f().translation(0, 0.05f, 0.1f).rotateX((float) Math.PI / 2), 6, 0.045f, 0.04f, GALAXY);
                    for (int i = 0; i < 4; i++) {
                        float x = -0.072f + 0.048f * i;
                        prism(m, new Matrix4f().translation(x, 0.17f, 0.035f).rotateX(0.25f), 4, 0, 0.11f, 0.026f, 0, (float) Math.PI / 4, GALAXY, true);
                    }
                }
            }
            return m;
        });
    }

    /** The hex shield: a bevelled dark rim, a galaxy panel, and a raised gem boss (faces +Z). */
    private static void hexShield(Mesh m, float r, float t) {
        float ri = r * 0.78f;
        Vector3f boss = v(0, 0, t * 1.9f);
        for (int i = 0; i < 6; i++) {
            double a0 = i * Math.PI / 3, a1 = (i + 1) * Math.PI / 3;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            Vector3f of0 = v(c0 * r, s0 * r, t * 0.5f), of1 = v(c1 * r, s1 * r, t * 0.5f), ob0 = v(c0 * r, s0 * r, -t * 0.5f), ob1 = v(c1 * r, s1 * r, -t * 0.5f);
            Vector3f if0 = v(c0 * ri, s0 * ri, t), if1 = v(c1 * ri, s1 * ri, t), ip0 = v(c0 * ri * 0.97f, s0 * ri * 0.97f, t * 0.55f), ip1 = v(c1 * ri * 0.97f, s1 * ri * 0.97f, t * 0.55f);
            quad(m, of0, of1, if1, if0, DARK);                     // rim, front bevel
            quad(m, ob0, ob1, of1, of0, DARK);                     // outer wall
            quad(m, if0, if1, ip1, ip0, DARK);                     // rim, inner wall down to the panel
            tri(m, ip0, ip1, boss, GALAXY);                         // the panel, rising to the boss
            tri(m, ob0, v(0, 0, -t * 0.8f), ob1, DARK);            // the back
            m.edges.add(new Edge(of0, of1)); m.edges.add(new Edge(if0, if1)); m.edges.add(new Edge(of0, if0)); m.edges.add(new Edge(ip0, boss));
        }
        gem(m, new Matrix4f().translation(0, 0, t * 1.9f).rotateX((float) Math.PI / 2), 6, 0.07f, 0.06f, GALAXY);
    }

    static Mesh shield() { if (SHIELD == null) { SHIELD = new Mesh(); hexShield(SHIELD, 0.42f, 0.06f); } return SHIELD; }

    /** Draw a weapon with the pose stack's current transform. alpha fades it in and out (forging). */
    public static void render(PoseStack ps, MultiBufferSource buf, Weapon w, float alpha, float glow) { render(ps, buf, mesh(w), alpha, glow); }
    public static void renderShield(PoseStack ps, MultiBufferSource buf, float alpha, float glow) { render(ps, buf, shield(), alpha, glow); }

    /**
     * A construct made of light (the giant summons, like sword shadows): no dark body, a faint violet fill and bright
     * thick edges, additive, so it never reads as a flat panel.
     */
    public static void renderEthereal(PoseStack ps, MultiBufferSource buf, Weapon w, float alpha) {
        Mesh m = w == null ? shield() : mesh(w);
        if (alpha <= 0.01f) return;
        Matrix4f mat = ps.last().pose();
        VertexConsumer fill = buf.getBuffer(Rt.addTri(Rt.WHITE));
        for (Tri t : m.tris) for (Vector3f p : new Vector3f[]{t.a(), t.b(), t.c()})
            fill.vertex(mat, p.x, p.y, p.z).color(0.35f, 0.2f, 0.75f, 0.12f * alpha).uv(0.5f, 0.5f).endVertex();
        // keep edge lines thin in world space when the construct is scaled up to giant size
        float sc = Math.max(1f, mat.getScale(new Vector3f()).x * 0.5f);
        edges(mat, buf.getBuffer(Rt.add(Rt.WHITE)), m, alpha, 0.035f / sc, 0.85f, 0.72f, 1f);
        edges(mat, buf.getBuffer(Rt.add(Rt.GLOW)), m, alpha * 0.6f, 0.12f / sc, 0.5f, 0.3f, 0.95f);
    }

    private static void edges(Matrix4f mat, VertexConsumer g, Mesh m, float a, float wdt, float r, float gg, float b) {
        for (Edge e : m.edges) {
            Vector3f d = new Vector3f(e.b()).sub(e.a());
            for (Vector3f ax : new Vector3f[]{new Vector3f(0, 0, 1), new Vector3f(1, 0, 0)}) {
                Vector3f s = new Vector3f(d).cross(ax);
                if (s.lengthSquared() < 1e-6f) continue;
                s.normalize(wdt);
                g.vertex(mat, e.a().x - s.x, e.a().y - s.y, e.a().z - s.z).color(r, gg, b, a).uv(0, 0.5f).endVertex();
                g.vertex(mat, e.a().x + s.x, e.a().y + s.y, e.a().z + s.z).color(r, gg, b, a).uv(1, 0.5f).endVertex();
                g.vertex(mat, e.b().x + s.x, e.b().y + s.y, e.b().z + s.z).color(r, gg, b, a).uv(1, 0.5f).endVertex();
                g.vertex(mat, e.b().x - s.x, e.b().y - s.y, e.b().z - s.z).color(r, gg, b, a).uv(0, 0.5f).endVertex();
            }
        }
    }

    private static void render(PoseStack ps, MultiBufferSource buf, Mesh m, float alpha, float glow) {
        if (AbyssShaders.VOID == null || alpha <= 0.01f) return;
        Matrix4f mat = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(Rt.voidSolid(Rt.BODY_CORE));
        for (Tri t : m.tris) {
            Vector3f n = new Vector3f(t.b()).sub(t.a()).cross(new Vector3f(t.c()).sub(t.a()));
            if (n.lengthSquared() < 1e-12f) continue;
            n.normalize();
            // faceted light from above-front (two-sided): every crystal face gets its own value, so the shape reads
            float lit = Math.abs(n.x * 0.35f + n.y * 0.75f + n.z * 0.56f);
            float shade = 0.55f + 0.65f * lit;
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
            side.normalize(0.007f);
            Vector3f side2 = new Vector3f(d).cross(1, 0, 0);
            if (side2.lengthSquared() < 1e-6f) side2.set(0, 0, 1);
            side2.normalize(0.007f);
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
