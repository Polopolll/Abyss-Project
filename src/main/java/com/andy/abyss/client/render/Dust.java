package com.andy.abyss.client.render;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Real particles for the abyss: small flecks of black crystal, flakes of abyssal ash, dim embers, slivers and smoke
 * of darkness. They look like particles (not little galaxies): mostly black and charcoal, deep indigo, with only rare
 * violet-white sparks. Simple physics (gravity, drag, an optional point that pulls them in), drawn as sprites.
 */
public final class Dust {
    public enum Kind { FLECK, ASH, EMBER, WISP, SLIVER }

    public static final int BLACK = 0x050307, CHAR = 0x18121F, INDIGO = 0x2A1C47, DUSK = 0x4B3577, VIOLET = 0x8C6CD8, PALE = 0xE6DEFF;

    public static final class P {
        public double x, y, z, vx, vy, vz;
        public float size, grow, rot, rotV, gravity = 0, drag = 0.96f, alpha = 1;
        public int age, life, rgb, variant;
        public Kind kind;
        public Vec3 pull; public float pullK;
        public boolean streak;
        public P gravity(float g) { gravity = g; return this; }
        public P drag(float d) { drag = d; return this; }
        public P grow(float g) { grow = g; return this; }
        public P spin(float s) { rotV = s; return this; }
        public P alpha(float a) { alpha = a; return this; }
        public P pull(Vec3 c, float k) { pull = c; pullK = k; return this; }
        public P streak() { streak = true; return this; }
    }

    private static final List<P> ALL = new ArrayList<>();
    private static final int CAP = 7000;
    public static final Random R = new Random();

    public static P add(Kind k, Vec3 p, Vec3 v, int life, float size, int rgb) {
        P q = new P();
        q.kind = k; q.x = p.x; q.y = p.y; q.z = p.z; q.vx = v.x; q.vy = v.y; q.vz = v.z;
        q.life = Math.max(2, life); q.size = size; q.rgb = rgb; q.rot = R.nextFloat() * 6.28f; q.variant = R.nextInt(4);
        if (ALL.size() < CAP) ALL.add(q);
        return q;
    }

    /** A spray of particles from a point: n of them, outward speed v, mixed kinds in the abyss palette. */
    public static void burst(Vec3 c, int n, double speed, float size, boolean embers) {
        for (int i = 0; i < n; i++) {
            Vec3 dir = rnd().scale(speed * (0.3 + R.nextDouble() * 0.9));
            float r = R.nextFloat();
            if (r < 0.45f) add(Kind.FLECK, c, dir, 14 + R.nextInt(16), size * (0.5f + R.nextFloat()), R.nextFloat() < 0.8f ? BLACK : INDIGO).gravity(-0.02f).drag(0.9f).spin(R.nextFloat() * 0.6f - 0.3f);
            else if (r < 0.8f) add(Kind.ASH, c, dir.scale(0.7), 24 + R.nextInt(24), size * (0.8f + R.nextFloat()), R.nextFloat() < 0.7f ? CHAR : BLACK).gravity(0.004f).drag(0.9f).spin(R.nextFloat() * 0.3f - 0.15f);
            else if (embers) add(Kind.EMBER, c, dir.scale(1.2), 8 + R.nextInt(10), size * 0.6f, R.nextFloat() < 0.75f ? VIOLET : PALE).drag(0.88f).streak();
            else add(Kind.SLIVER, c, dir, 12 + R.nextInt(10), size * 1.4f, INDIGO).gravity(-0.025f).drag(0.92f).spin(R.nextFloat() * 0.5f - 0.25f);
        }
    }

    /** Rolling smoke of darkness. */
    public static void smoke(Vec3 c, int n, double spread, float size, int life) {
        for (int i = 0; i < n; i++) {
            Vec3 o = rnd().scale(spread * R.nextDouble());
            add(Kind.WISP, c.add(o), o.scale(0.04).add(0, 0.01 + R.nextDouble() * 0.02, 0), life + R.nextInt(life / 2 + 1), size * (0.7f + R.nextFloat() * 0.6f), R.nextFloat() < 0.6f ? BLACK : CHAR)
                    .grow(0.012f * size).drag(0.94f).spin(R.nextFloat() * 0.04f - 0.02f).alpha(0.75f);
        }
    }

    public static Vec3 rnd() {
        double u = R.nextDouble() * 2 - 1, t = R.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(t), u, s * Math.sin(t));
    }

    public static void tick() {
        for (int i = ALL.size() - 1; i >= 0; i--) {
            P q = ALL.get(i);
            if (++q.age >= q.life) { ALL.set(i, ALL.get(ALL.size() - 1)); ALL.remove(ALL.size() - 1); continue; }
            if (q.pull != null) {
                double dx = q.pull.x - q.x, dy = q.pull.y - q.y, dz = q.pull.z - q.z, l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4;
                q.vx += dx / l * q.pullK; q.vy += dy / l * q.pullK; q.vz += dz / l * q.pullK;
                if (l < 0.25) q.age = q.life; // swallowed
            }
            q.vy -= q.gravity;
            q.vx *= q.drag; q.vy *= q.drag; q.vz *= q.drag;
            q.x += q.vx; q.y += q.vy; q.z += q.vz;
            q.rot += q.rotV; q.size += q.grow;
        }
    }

    public static void render(Draw d, float pt) {
        for (P q : ALL) {
            float t = (q.age + pt) / q.life;
            if (t >= 1) continue;
            float a = q.alpha * (t < 0.12f ? t / 0.12f : 1) * (1 - Math.max(0, (t - 0.55f) / 0.45f));
            Vec3 p = new Vec3(q.x + q.vx * pt, q.y + q.vy * pt, q.z + q.vz * pt);
            switch (q.kind) {
                case FLECK -> {
                    d.sprite(Rt.P_FLECK, false, p, q.size * 0.6f, q.size, q.rgb, a, q.rot, 0, 1);
                    d.sprite(Rt.P_EMBER, true, p, q.size * 0.5f, q.size * 0.5f, 0x3A2766, a * 0.35f, 0, 0, 1); // a faint rim of light so black reads on black
                }
                case ASH -> d.sprite(Rt.P_ASH, false, p, q.size, q.size * 0.9f, q.rgb, a, q.rot, q.variant * 0.25f, q.variant * 0.25f + 0.25f);
                case EMBER -> {
                    if (q.streak) {
                        Vec3 tail = p.subtract(q.vx * 2.5, q.vy * 2.5, q.vz * 2.5);
                        if (tail.distanceToSqr(p) > 1e-4) d.beam(Rt.P_EMBER, true, tail, p, q.size * 0.6f, q.rgb, a);
                    }
                    d.sprite(Rt.P_EMBER, true, p, q.size, q.size, q.rgb, a, 0, 0, 1);
                }
                case WISP -> d.sprite(Rt.P_WISP, false, p, q.size, q.size, q.rgb, a * (1 - t), q.rot, 0, 1);
                case SLIVER -> d.sprite(Rt.P_SLIVER, false, p, q.size * 0.25f, q.size, q.rgb, a, q.rot, 0, 1);
            }
        }
    }

    public static int count() { return ALL.size(); }
    public static void clear() { ALL.clear(); }
    private Dust() {}
}
