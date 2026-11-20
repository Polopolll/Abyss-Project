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
    record Tri(Vector3f a, Vector3f b, Vector3f c, float galaxy, int group) { Tri(Vector3f a, Vector3f b, Vector3f c, float galaxy) { this(a, b, c, galaxy, 0); } }
    record Edge(Vector3f a, Vector3f b, int group) { Edge(Vector3f a, Vector3f b) { this(a, b, 0); } }
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

    /**
     * Load a mesh exported from Blender: "v x y z" vertices, "f a b c g" triangles (g = 1 galaxy body, 0 dark
     * crystal), "e a b" glow lines along the sharp creases. Returns false if it isn't there.
     */
    static boolean load(Mesh m, String name) {
        try (var in = WeaponModel.class.getResourceAsStream("/assets/abyss/models/mesh/" + name + ".mesh")) {
            if (in == null) return false;
            List<Vector3f> vs = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                String[] t = line.trim().split("\\s+");
                if (t.length < 3) continue;
                switch (t[0]) {
                    case "v" -> vs.add(v(Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])));
                    case "f" -> m.tris.add(new Tri(vs.get(Integer.parseInt(t[1])), vs.get(Integer.parseInt(t[2])), vs.get(Integer.parseInt(t[3])),
                            t.length > 4 && t[4].equals("1") ? GALAXY : DARK, t.length > 5 ? Integer.parseInt(t[5]) : 0));
                    case "e" -> m.edges.add(new Edge(vs.get(Integer.parseInt(t[1])), vs.get(Integer.parseInt(t[2])), t.length > 3 ? Integer.parseInt(t[3]) : 0));
                    default -> {}
                }
            }
            return !m.tris.isEmpty();
        } catch (Exception ex) {
            com.andy.abyss.Abyss.LOG.warn("Abyss: couldn't load mesh {}", name, ex);
            m.tris.clear(); m.edges.clear();
            return false;
        }
    }

    /** The hand-built fist (used if the Blender mesh is missing). */
    private static void proceduralFist(Mesh m) {
        prism(m, ID, 8, -0.2f, -0.02f, 0.16f, 0.17f, (float) Math.PI / 8, DARK, true);
        box(m, -0.15f, 0.15f, -0.01f, 0.25f, -0.08f, 0.08f, GALAXY, true);
        for (int i = 0; i < 4; i++) {
            float x0 = -0.15f + i * 0.0755f, x1 = x0 + 0.07f;
            box(m, x0, x1, 0.24f, 0.335f, -0.07f, 0.085f, GALAXY, true);
            box(m, x0 + 0.004f, x1 - 0.004f, 0.17f, 0.31f, -0.165f, -0.07f, GALAXY, true);
        }
    }

    /** An axis-aligned box (in the mesh's own frame). */
    private static void box(Mesh m, float x0, float x1, float y0, float y1, float z0, float z1, float g, boolean edges) {
        boxT(m, ID, x0, x1, y0, y1, z0, z1, g);
        if (!edges) return;
        Vector3f[] c = corners(ID, x0, x1, y0, y1, z0, z1);
        int[][] e = {{0,1},{1,3},{3,2},{2,0},{4,5},{5,7},{7,6},{6,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] q : e) m.edges.add(new Edge(c[q[0]], c[q[1]]));
    }

    private static Vector3f[] corners(Matrix4f t, float x0, float x1, float y0, float y1, float z0, float z1) {
        return new Vector3f[]{at(t, x0, y0, z0), at(t, x1, y0, z0), at(t, x0, y1, z0), at(t, x1, y1, z0),
                at(t, x0, y0, z1), at(t, x1, y0, z1), at(t, x0, y1, z1), at(t, x1, y1, z1)};
    }

    /** A box under a transform (closed, outward faces). */
    private static void boxT(Mesh m, Matrix4f t, float x0, float x1, float y0, float y1, float z0, float z1, float g) {
        Vector3f[] c = corners(t, x0, x1, y0, y1, z0, z1);
        quad(m, c[0], c[2], c[3], c[1], g); // -z
        quad(m, c[4], c[5], c[7], c[6], g); // +z
        quad(m, c[0], c[4], c[6], c[2], g); // -x
        quad(m, c[1], c[3], c[7], c[5], g); // +x
        quad(m, c[0], c[1], c[5], c[4], g); // -y
        quad(m, c[2], c[6], c[7], c[3], g); // +y
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
                case GAUNTLETS -> {
                    // modelled in Blender (assets/abyss/models/mesh/fist.mesh); the procedural fist is the fallback
                    if (!load(m, "fist")) proceduralFist(m);
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

    /**
     * How the fist's hand is held: open 0 (clenched) … 1 (flat, fingers out) per finger (index first), squeeze 0..1
     * (clenched tighter, knuckles rolled in), thumb 0 (folded across) … 1 (out).
     */
    public record Grip(float[] open, float squeeze, float thumb) {
        public static Grip of(float open, float squeeze, float thumb) { return new Grip(new float[]{open, open, open, open}, squeeze, thumb); }
        public Grip mix(Grip o, float k) {
            float[] f = new float[4];
            for (int i = 0; i < 4; i++) f[i] = open[i] + (o.open[i] - open[i]) * k;
            return new Grip(f, squeeze + (o.squeeze - squeeze) * k, thumb + (o.thumb - thumb) * k);
        }
    }

    /** The fist's joints (from the Blender rig): per finger the knuckle-row pivot and the curled-tip pivot. */
    private static final float[] FX = {-0.112f, -0.037f, 0.038f, 0.113f}, DROP = {0.01f, 0, 0, 0.028f};

    /** Draw the fist with its fingers posed. */
    public static void renderFist(PoseStack ps, MultiBufferSource buf, float alpha, float glow, Grip g) {
        Matrix4f[] xf = new Matrix4f[11];
        for (int i = 0; i < 4; i++) {
            float o = Math.max(0, Math.min(1, g.open()[i])), sq = Math.max(0, Math.min(1, g.squeeze()));
            float py = 0.24f - DROP[i], dy = 0.335f - DROP[i];
            // the knuckle row: rolls in when squeezed, lifts and fans out as the hand opens
            Matrix4f prox = new Matrix4f().translate(FX[i], py, 0)
                    .rotateZ((float) Math.toRadians((1.5f - i) * 7 * o))
                    .rotateX((float) Math.toRadians(-14 * sq + 6 * o))
                    .translate(-FX[i], -py, 0);
            // the curled tips swing out from under the palm to straighten the finger
            Matrix4f dist = new Matrix4f(prox).translate(FX[i], dy, -0.07f)
                    .rotateX((float) Math.toRadians(150 * o - 8 * sq))
                    .translate(-FX[i], -dy, 0.07f);
            xf[1 + i] = prox; xf[5 + i] = dist;
        }
        float th = Math.max(0, Math.min(1, g.thumb()));
        xf[9] = new Matrix4f().translate(-0.13f, 0.08f, -0.06f).rotateZ((float) Math.toRadians(25 * th)).translate(0.13f, -0.08f, 0.06f);
        xf[10] = new Matrix4f(xf[9]).translate(-0.13f, 0.14f, -0.13f).rotateZ((float) Math.toRadians(55 * th)).rotateX((float) Math.toRadians(-20 * th)).translate(0.13f, -0.14f, 0.13f);
        renderSolid(ps, buf, mesh(Weapon.GAUNTLETS), alpha, glow, xf);
    }

    /**
     * The fist as a solid, lit object (not a window into the galaxy): dark armour plates over violet-grey crystal
     * fingers, shaded by Minecraft's own light so every finger and knuckle reads, with the glowing creases on top.
     */
    private static void renderSolid(PoseStack ps, MultiBufferSource buf, Mesh m, float alpha, float glow, Matrix4f[] xf) {
        if (alpha <= 0.01f) return;
        Matrix4f mat = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(Rt.WHITE));
        for (Tri t0 : m.tris) {
            Tri t = xf == null || t0.group() == 0 ? t0 : new Tri(px(t0.a(), t0.group(), xf), px(t0.b(), t0.group(), xf), px(t0.c(), t0.group(), xf), t0.galaxy(), t0.group());
            Vector3f n = new Vector3f(t.b()).sub(t.a()).cross(new Vector3f(t.c()).sub(t.a()));
            if (n.lengthSquared() < 1e-12f) continue;
            n.normalize();
            boolean armour = t.galaxy() < 0.9f;
            // armour: near-black indigo; the hand under it: a cold violet-grey crystal (fingers a shade lighter than the palm)
            float r, g, b;
            if (armour) { r = 0.13f; g = 0.12f; b = 0.2f; }
            else if (t.group() >= 1) { r = 0.5f; g = 0.45f; b = 0.68f; }
            else { r = 0.4f; g = 0.36f; b = 0.56f; }
            Vector3f[] vs = {t.a(), t.b(), t.c(), t.c()};
            for (Vector3f p : vs)
                vc.vertex(mat, p.x, p.y, p.z).color(r, g, b, alpha).uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nm, n.x, n.y, n.z).endVertex();
        }
        VertexConsumer gl = buf.getBuffer(Rt.add(Rt.WHITE));
        float a = alpha * glow * 0.8f;
        for (Edge e0 : m.edges) {
            Edge e = xf == null || e0.group() == 0 ? e0 : new Edge(px(e0.a(), e0.group(), xf), px(e0.b(), e0.group(), xf), e0.group());
            Vector3f d = new Vector3f(e.b()).sub(e.a());
            for (Vector3f ax : new Vector3f[]{new Vector3f(0, 0, 1), new Vector3f(1, 0, 0)}) {
                Vector3f sd = new Vector3f(d).cross(ax);
                if (sd.lengthSquared() < 1e-6f) continue;
                sd.normalize(0.006f);
                gl.vertex(mat, e.a().x - sd.x, e.a().y - sd.y, e.a().z - sd.z).color(0.62f, 0.4f, 1f, a).uv(0.5f, 0.5f).endVertex();
                gl.vertex(mat, e.a().x + sd.x, e.a().y + sd.y, e.a().z + sd.z).color(0.62f, 0.4f, 1f, a).uv(0.5f, 0.5f).endVertex();
                gl.vertex(mat, e.b().x + sd.x, e.b().y + sd.y, e.b().z + sd.z).color(0.8f, 0.65f, 1f, a).uv(0.5f, 0.5f).endVertex();
                gl.vertex(mat, e.b().x - sd.x, e.b().y - sd.y, e.b().z - sd.z).color(0.8f, 0.65f, 1f, a).uv(0.5f, 0.5f).endVertex();
            }
        }
    }

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
            fill.vertex(mat, p.x, p.y, p.z).color(0.16f, 0.15f, 0.32f, 0.14f * alpha).uv(0.5f, 0.5f).endVertex();
        // keep edge lines thin in world space when the construct is scaled up to giant size
        float sc = Math.max(1f, mat.getScale(new Vector3f()).x * 0.5f);
        edges(mat, buf.getBuffer(Rt.add(Rt.WHITE)), m, alpha * 0.55f, 0.035f / sc, 0.8f, 0.78f, 0.95f);
        edges(mat, buf.getBuffer(Rt.add(Rt.GLOW)), m, alpha * 0.25f, 0.12f / sc, 0.45f, 0.42f, 0.7f);
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

    private static void render(PoseStack ps, MultiBufferSource buf, Mesh m, float alpha, float glow) { render(ps, buf, m, alpha, glow, null); }

    /** A vertex through its group's joint transform (null = the mesh as modelled). */
    private static Vector3f px(Vector3f p, int group, Matrix4f[] xf) {
        if (xf == null || group <= 0 || group >= xf.length || xf[group] == null) return p;
        return xf[group].transformPosition(new Vector3f(p));
    }

    private static void render(PoseStack ps, MultiBufferSource buf, Mesh m, float alpha, float glow, Matrix4f[] xf) {
        if (AbyssShaders.VOID == null || alpha <= 0.01f) return;
        Matrix4f mat = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(Rt.voidSolid(Rt.BODY_CORE));
        for (Tri t0 : m.tris) {
            Tri t = xf == null || t0.group() == 0 ? t0 : new Tri(px(t0.a(), t0.group(), xf), px(t0.b(), t0.group(), xf), px(t0.c(), t0.group(), xf), t0.galaxy(), t0.group());
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
        for (Edge e0 : m.edges) {
            Edge e = xf == null || e0.group() == 0 ? e0 : new Edge(px(e0.a(), e0.group(), xf), px(e0.b(), e0.group(), xf), e0.group());
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
