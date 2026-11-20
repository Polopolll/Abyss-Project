package com.andy.abyss.client.render;

import com.andy.abyss.Abyss;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Random;

/**
 * The visual vocabulary of the abyss, so effects stop repeating galaxies and black holes: reality fractures (jagged
 * black seams with a thin pale edge), dark tendrils, pressure waves rolling outward, ink spreading, cold starlight
 * glints and eclipses (a black disc with a thin white-violet corona). Everything is dark first; light is a thin edge.
 * Black holes ({@link Draw#blackHole}) are kept for the true singularities only: domain cast, collapse, implosion.
 */
public final class Abyssal {
    public static final ResourceLocation CORONA = Abyss.id("textures/fx/corona.png"), SEAM = Abyss.id("textures/fx/seam.png"),
            TENDRIL = Abyss.id("textures/fx/tendril.png"), WAVE = Abyss.id("textures/fx/wave.png"), SMUDGE = Abyss.id("textures/fx/smudge.png");

    /** The light colours of the abyss: cold starlight, a pale white-violet edge, a dim cold blue; and a deep indigo. */
    public static final int STAR = 0xDCE4FF, EDGE = 0xC9C4EC, COLD = 0x7D88B8, DEEP = 0x262A48;

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Mixes a (saturated) ability colour toward cold grey and darkens it: tints stay recognisable but quiet. */
    public static int mute(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int l = (int) (r * 0.3 + g * 0.5 + b * 0.2);
        r = (int) ((r * (1 - k) + l * 0.85f * k) * (1 - k * 0.3f));
        g = (int) ((g * (1 - k) + l * 0.9f * k) * (1 - k * 0.3f));
        b = (int) ((b * (1 - k) + l * 1.1f * k) * (1 - k * 0.3f));
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    // ------------------------------------------------------------------------------------------------ eclipse
    /** A solid black disc facing the camera (radius r). */
    public static void disc(Draw d, Vec3 c, float r, float alpha) {
        float near = d.near(c, Math.max(0.6f, r * 1.2f));
        alpha *= near * near;
        if (alpha < 0.01f || r < 0.005f) return;
        VertexConsumer vc = d.buf.getBuffer(Rt.alpha(Rt.WHITE));
        int seg = r > 2 ? 40 : 24;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            d.sv(vc, c.x, c.y, c.z, 0x000000, alpha, .5f, .5f);
            d.sv(vc, c.x + d.left.x * x0 + d.up.x * y0, c.y + d.left.y * x0 + d.up.y * y0, c.z + d.left.z * x0 + d.up.z * y0, 0x000000, alpha, .5f, .5f);
            d.sv(vc, c.x + d.left.x * x1 + d.up.x * y1, c.y + d.left.y * x1 + d.up.y * y1, c.z + d.left.z * x1 + d.up.z * y1, 0x000000, alpha, .5f, .5f);
            d.sv(vc, c.x + d.left.x * x1 + d.up.x * y1, c.y + d.left.y * x1 + d.up.y * y1, c.z + d.left.z * x1 + d.up.z * y1, 0x000000, alpha, .5f, .5f);
        }
    }

    /** An eclipse: a black disc with a thin white-violet corona (not a black hole: no disk, no swirl, no lensing). */
    public static void eclipse(Draw d, Vec3 c, float r, float alpha, float spin) {
        if (alpha < 0.01f || r < 0.005f) return;
        disc(d, c, r, alpha);
        // the corona texture's ring sits at 0.62 of its half-size; keep it thin and dim (the bloom adds the halo)
        float near = d.near(c, Math.max(0.6f, r * 1.2f));
        d.billboard(CORONA, true, c, r / 0.62f, EDGE, 0.55f * alpha * near, spin);
    }

    /** A small dark orb with only a faint cold rim: what a small void sphere is now (was a tiny black hole). */
    public static void orb(Draw d, Vec3 c, float r, float alpha) {
        if (alpha < 0.01f || r < 0.005f) return;
        disc(d, c, r, alpha * 0.95f);
        float near = d.near(c, Math.max(0.6f, r * 1.2f));
        d.billboard(CORONA, true, c, r / 0.62f * 1.02f, COLD, 0.3f * alpha * near, (float) d.time * 0.2f);
    }

    /** A spore seed: a pinprick of black with a hairline cold rim and one glint. */
    public static void seed(Draw d, Vec3 c, float r, float alpha, float phase) {
        disc(d, c, r, alpha);
        float near = d.near(c, 1.2f);
        d.billboard(CORONA, true, c, r / 0.62f, EDGE, 0.35f * alpha * near, phase);
        float tw = 0.5f + 0.5f * (float) Math.sin(d.time * 2.3 + phase * 7);
        d.billboard(Rt.SPARK, true, c.add((d.left.x + d.up.x) * r * 0.5, (d.left.y + d.up.y) * r * 0.5, (d.left.z + d.up.z) * r * 0.5), r * 0.9f, STAR, 0.35f * tw * alpha * near, 0);
    }

    // ------------------------------------------------------------------------------------------------ fractures
    /**
     * A crack in reality: branching jagged black seams with a thin pale edge, radiating from c. n = the plane it lies
     * in (null = facing the camera). grow 0..1 = how far the cracks have run. Same seed, same crack.
     */
    public static void fracture(Draw d, Vec3 c, Vec3 n, float size, float grow, float alpha, long seed) {
        if (alpha < 0.01f || grow <= 0) return;
        Random r = new Random(seed);
        Vec3 u, v;
        if (n == null) { u = new Vec3(d.left.x, d.left.y, d.left.z); v = new Vec3(d.up.x, d.up.y, d.up.z); }
        else { Vec3[] b = Draw.basis(n); u = b[0]; v = b[1]; c = c.add(n.normalize().scale(0.03)); }
        int arms = 3 + r.nextInt(3);
        float w = Math.max(0.03f, size * 0.045f);
        for (int i = 0; i < arms; i++) {
            double a = (i + r.nextDouble() * 0.6) * Math.PI * 2 / arms;
            branch(d, c, u, v, a, size * (0.55f + r.nextFloat() * 0.45f), w, grow, alpha, r, 0);
        }
    }

    private static void branch(Draw d, Vec3 from, Vec3 u, Vec3 v, double ang, float len, float w, float grow, float alpha, Random r, int depth) {
        int segs = 5;
        Vec3 p = from;
        float seg = len / segs;
        for (int s = 0; s < segs; s++) {
            float k = (s + 1f) / segs;
            ang += (r.nextDouble() - 0.5) * 1.1;
            Vec3 q = p.add(u.scale(Math.cos(ang) * seg)).add(v.scale(Math.sin(ang) * seg));
            float reach = Math.min(1, grow * 1.25f - (s / (float) segs) * 0.9f - depth * 0.25f);
            if (reach <= 0) return;
            Vec3 end = reach < 1 ? p.add(q.subtract(p).scale(reach)) : q;
            float ww = w * (1.15f - k * 0.7f);
            seam(d, p, end, ww, alpha);
            if (depth < 1 && r.nextFloat() < 0.35f)
                branch(d, q, u, v, ang + (r.nextBoolean() ? 1 : -1) * (0.5 + r.nextDouble() * 0.6), len * 0.45f, ww * 0.8f, grow, alpha * 0.9f, r, depth + 1);
            p = q;
        }
    }

    /** One seam segment: black body, pale edge (baked), and a hairline of cold light in the bloom. */
    public static void seam(Draw d, Vec3 a, Vec3 b, float w, float alpha) {
        d.beam(SEAM, false, a, b, w, 0xFFFFFF, alpha);
        d.beam(Rt.STREAK, true, a, b, w * 0.22f, EDGE, alpha * 0.18f);
    }

    // ------------------------------------------------------------------------------------------------ tendrils
    /**
     * A dark tendril from a to b: a wavy black ribbon tapering to its tip, a faint light on its edge. amp = how far it
     * writhes sideways, phase animates the writhing, reach 0..1 = how much of it has grown out.
     */
    public static void tendril(Draw d, Vec3 a, Vec3 b, float width, float amp, float phase, float reach, float alpha) {
        if (alpha < 0.01f || reach <= 0) return;
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1e-3) return;
        Vec3 side = dir.cross(d.cam.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1e-8) side = Draw.basis(dir)[0];
        side = side.normalize();
        Vec3 bend = dir.cross(side).normalize();
        int n = 12;
        int last = Math.max(1, (int) Math.ceil(n * reach));
        Vec3 prev = a;
        for (int i = 1; i <= last; i++) {
            float k = Math.min(i / (float) n, reach);
            double wob = Math.sin(k * 7.0 + phase) * amp * Math.sin(k * Math.PI) + Math.sin(k * 13.0 - phase * 1.7) * amp * 0.3 * k;
            double cur = Math.cos(k * 5.0 + phase * 0.6) * amp * 0.5 * k;
            Vec3 p = a.add(dir.scale(k)).add(side.scale(wob)).add(bend.scale(cur));
            float k0 = (i - 1f) / n;
            float w = width * (1 - k0 * 0.85f);
            d.beam(SEAM, false, prev, p, w, 0xFFFFFF, alpha);
            prev = p;
        }
        d.beam(Rt.STREAK, true, a, prev, width * 0.12f, COLD, alpha * 0.12f);
    }

    // ------------------------------------------------------------------------------------------------ pressure
    /** A dark ring rolling outward on a plane (normal n), its front edged by a hairline of pale light. */
    public static void pressure(Draw d, Vec3 c, Vec3 n, float r, float alpha) {
        if (alpha < 0.01f || r < 0.05f) return;
        // the wave texture's front sits at 0.88 of its half-size, like ring.png
        d.flat(WAVE, false, c, n, r, 0x000000, alpha * 0.75f, 0);
        d.flat(Rt.RING, true, c.add(n.normalize().scale(0.02)), n, r, EDGE, alpha * 0.16f, 0);
    }

    /** A pressure wave on the ground. */
    public static void groundWave(Draw d, Vec3 c, float r, float alpha) { pressure(d, c, UP, r, alpha); }

    // ------------------------------------------------------------------------------------------------ ink
    /**
     * Darkness spreading like ink: a few slow-turning black smudges around c, swelling as grow goes 0..1. Drawn per
     * frame from a seed (for real drifting smoke use {@link Dust#smoke}).
     */
    public static void ink(Draw d, Vec3 c, float r, float grow, float alpha, long seed) {
        if (alpha < 0.01f) return;
        Random rr = new Random(seed);
        int n = 5;
        for (int i = 0; i < n; i++) {
            Vec3 o = new Vec3(rr.nextGaussian() * r * 0.45, rr.nextGaussian() * r * 0.3, rr.nextGaussian() * r * 0.45);
            Vec3 p = c.add(o.scale(0.4 + grow * 0.8));
            float s = r * (0.5f + rr.nextFloat() * 0.5f) * (0.35f + grow * 0.75f);
            int var = rr.nextInt(4);
            float rot = (float) (d.time * (rr.nextFloat() - 0.5) * 0.4 + rr.nextFloat() * 6);
            d.sprite(SMUDGE, false, p, s, s, 0x020104, alpha * 0.8f * d.near(p, s), rot, var * 0.25f, var * 0.25f + 0.25f);
        }
    }

    // ------------------------------------------------------------------------------------------------ glints
    /** A few sparse points of cold starlight around c, twinkling. Tiny: the bloom makes them read. */
    public static void glints(Draw d, Vec3 c, float radius, int n, float alpha, long seed) {
        if (alpha < 0.01f) return;
        Random rr = new Random(seed);
        for (int i = 0; i < n; i++) {
            Vec3 p = c.add(rr.nextGaussian() * radius * 0.5, rr.nextGaussian() * radius * 0.5, rr.nextGaussian() * radius * 0.5);
            float tw = 0.5f + 0.5f * (float) Math.sin(d.time * (2 + rr.nextFloat() * 3) + i * 1.7);
            float s = 0.035f + rr.nextFloat() * 0.05f;
            d.billboard(Rt.SPARK, true, p, s * (0.7f + 0.6f * tw), rr.nextFloat() < 0.15f ? EDGE : STAR, alpha * tw * d.near(p, 0.8f), 0);
        }
    }

    /** One glint at p. */
    public static void glint(Draw d, Vec3 p, float size, float alpha) {
        d.billboard(Rt.SPARK, true, p, size, STAR, alpha * d.near(p, 0.8f), 0);
    }

    /** Camera-facing axis helpers for callers that build shapes in screen space. */
    public static Vec3 left(Draw d) { return new Vec3(d.left.x, d.left.y, d.left.z); }
    public static Vec3 up(Draw d) { Vector3f u = d.up; return new Vec3(u.x, u.y, u.z); }

    private Abyssal() {}
}
