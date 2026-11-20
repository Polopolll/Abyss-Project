package com.andy.abyss.client.render;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.combat.Move;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * The physical side of the Arsenal: punches, punch rushes, phantom arms, craters, trenches and the time-stop.
 * Everything lands where you're looking (the punch point is in front of the crosshair, a little low), one main
 * thing at a time: a tight flash, a ring of pressure, the air cracking, a spray of black flecks.
 */
final class BrawlFx {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Where a punch lands: on the target, or in front of the eye along the full look (a touch below the crosshair). */
    static Vec3 aimPoint(MoveFx.Prim p, float dist, boolean onTarget, float pt) {
        if (onTarget) {
            Vec3 t = p.a.target(pt, -1);
            if (t != null && p.a.p.target() >= 0) {
                // the face of the target that looks at you, not its middle
                return t.add(p.o.subtract(t).normalize().scale(0.45));
            }
        }
        return p.o.add(p.fwd.scale(dist)).add(p.up.scale(-0.12));
    }

    static Vec3 groundPoint(MoveFx.Prim p, Move.Fx f, float pt) {
        Vec3 c;
        if (f.d() == 1 && p.a.p.target() >= 0) c = p.a.target(pt, f.c()).subtract(0, 0.6, 0);
        else if (f.d() == 2) c = p.a.o;
        else c = p.a.o.add(p.a.fwd.scale(f.c()));
        return p.a.groundAt(c);
    }

    // ------------------------------------------------------------------------------------------------ one punch
    static void spawnFist(MoveFx.Prim p, Move.Fx f) {
        Vec3 c = aimPoint(p, f.b(), f.c() == 1, 0);
        float s = Math.max(0.6f, f.a());
        Dust.burst(c, (int) (8 + 9 * s), 0.08 + 0.06 * s, 0.05f + 0.02f * s, true);
        if (s >= 2.5f) Dust.smoke(c, 6, 0.6, 0.9f, 22);
        MoveFx.sfx(c, s >= 2 ? ModSounds.PUNCH_HEAVY : ModSounds.PUNCH, 1f, s >= 2 ? 0.85f : 1.05f + Dust.R.nextFloat() * 0.15f);
        if (s >= 2.5f) MoveFx.sfx(c, ModSounds.CRATER, 0.8f, 1.1f);
    }

    static void fist(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        Vec3 c = aimPoint(p, f.b(), f.c() == 1, d.pt).add(p.right.scale(f.d() * 0.12));
        punchMark(d, c, Math.max(0.6f, f.a()), age, p.a.seed ^ (long) f.tick() * 31);
    }

    /** One punch's mark in the air: a tight white-violet flash, a dark ring of pressure, the air cracking. */
    static void punchMark(Draw d, Vec3 c, float s, float age, long seed) {
        float k = age / 10f;
        if (k >= 1) return;
        float out = 1 - k;
        if (age < 3) d.billboard(Rt.GLOW, true, c, (0.18f + 0.16f * s) * (1 - age / 3f) + 0.08f, Dust.PALE, 0.9f * (1 - age / 3f), 0);
        float ring = 0.2f + (0.45f + 0.35f * s) * (1 - (float) Math.pow(1 - Math.min(1, age / 6f), 3));
        d.billboard(Rt.RING, false, c, ring, Dust.BLACK, 0.55f * out * out, 0);
        d.billboard(Rt.RING, true, c, ring * 1.04f, Abyssal.EDGE, 0.22f * out * out, 0);
        Abyssal.fracture(d, c, null, 0.35f + 0.3f * s, Math.min(1, age / 2.5f), out * 0.95f, seed);
    }

    // ------------------------------------------------------------------------------------------------ the rush
    static float barrageTime(Move.Fx f, int i) { return i * Math.max(1, f.d()) / Math.max(1, f.a()); }

    static Vec3 barrageOffset(MoveFx.Prim p, Move.Fx f, int i) {
        Random r = new Random(p.a.seed * 977 + i * 131L);
        double ang = r.nextDouble() * Math.PI * 2, rad = Math.sqrt(r.nextDouble()) * f.b();
        return p.right.scale(Math.cos(ang) * rad).add(p.up.scale(Math.sin(ang) * rad * 0.7));
    }

    static void barrageBeat(MoveFx.Prim p, Move.Fx f, float prev, float age) {
        int n = (int) f.a();
        for (int i = 0; i < n; i++) {
            float t = barrageTime(f, i);
            if (prev < t && age >= t) {
                Vec3 c = aimPoint(p, f.c(), f.e() == 1, 0).add(barrageOffset(p, f, i));
                Dust.burst(c, 5, 0.09, 0.05f, i % 3 == 0);
                if (i % 2 == 0) MoveFx.sfx(c, ModSounds.PUNCH, 0.75f, 0.95f + (i % 5) * 0.06f);
            }
        }
    }

    static void barrage(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        int n = (int) f.a();
        Vec3 c0 = aimPoint(p, f.c(), f.e() == 1, d.pt);
        for (int i = 0; i < n; i++) {
            float t = age - barrageTime(f, i);
            if (t < 0 || t > 7) continue;
            punchMark(d, c0.add(barrageOffset(p, f, i)), 0.75f, t * 1.4f, p.a.seed + i);
        }
        // the rush blurs into streaks of fists driving in from both sides
        if (age < f.d()) {
            for (int s = -1; s <= 1; s += 2) {
                float ph = (age * 0.9f + (s > 0 ? 0.5f : 0)) % 1f;
                Vec3 from = p.o.add(p.right.scale(0.55 * s)).add(p.up.scale(-0.45)).add(p.fwd.scale(0.9));
                Vec3 to = c0.add(barrageOffset(p, f, (int) (age * 2 + (s > 0 ? 1 : 0)) % Math.max(1, n)));
                Vec3 tip = from.add(to.subtract(from).scale(0.35 + 0.65 * ph));
                d.beam(Rt.STREAK, true, from.add(to.subtract(from).scale(0.25 * ph)), tip, 0.06f, Abyssal.COLD, 0.18f * (1 - ph));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ phantom arms
    static void phantom(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        float dur = Math.max(4, f.b());
        if (age > dur + 6) return;
        float in = Math.min(1, age / 4f), out = age > dur ? 1 - (age - dur) / 6f : 1;
        float a = in * out;
        int n = Math.max(2, (int) f.a());
        Vec3 aim = aimPoint(p, 3.2f, p.a.p.target() >= 0, d.pt);
        for (int i = 0; i < n; i++) {
            int side = i % 2 == 0 ? 1 : -1;
            int row = i / 2;
            // shoulders fanned out behind and beside you, like a second and third pair of arms
            Vec3 sh = p.o.add(p.right.scale(side * (0.95 + row * 0.35))).add(p.up.scale(-0.15 + row * 0.42)).add(p.fwd.scale(0.35 - row * 0.15));
            float period = 3.2f + (i % 3) * 0.4f;
            float ph = ((age + i * 1.37f) % period) / period;
            float ext = ph < 0.35f ? ph / 0.35f : 1 - (ph - 0.35f) / 0.65f; // snap out, draw back
            ext = ext * ext * (3 - 2 * ext);
            Vec3 hit = aim.add(p.right.scale(side * 0.25 * (1 + row * 0.4))).add(p.up.scale((row - 0.5) * 0.3));
            Vec3 fist = sh.add(hit.subtract(sh).scale(0.35 + 0.6 * ext));
            Abyssal.tendril(d, sh, fist, 0.26f, 0.05f, age * 0.5f + i, 1, a * 0.85f);
            d.billboard(Rt.CORE, false, fist, 0.2f, Dust.BLACK, a * 0.95f, 0);
            d.billboard(Rt.GLOW, true, fist, 0.16f, Abyssal.COLD, a * 0.35f * ext, 0);
            if (ph < 0.36f && ph > 0.3f) punchMark(d, hit, 0.6f, 2, p.a.seed + i * 7 + (int) (age / period));
        }
    }

    // ------------------------------------------------------------------------------------------------ craters & trenches
    static void spawnCrater(MoveFx.Prim p, Move.Fx f) {
        Vec3 c = groundPoint(p, f, 0).add(0, 0.2, 0);
        float r = f.a();
        Dust.burst(c.add(0, 0.4, 0), (int) (20 + r * 10), 0.12 + r * 0.05, 0.07f, true);
        for (int i = 0; i < 10 + r * 2; i++) {
            double ang = i * Math.PI * 2 / (10 + r * 2);
            Vec3 e = c.add(Math.cos(ang) * r * 0.8, 0.2, Math.sin(ang) * r * 0.8);
            Dust.smoke(e, 1, 0.3, 0.8f + r * 0.12f, 26);
        }
        MoveFx.sfx(c, ModSounds.CRATER, 1.3f, r > 4 ? 0.7f : 0.95f);
        MoveFx.sfx(c, ModSounds.QUAKE, 1.0f, 0.9f);
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            float fall = (float) Math.max(0, 1 - mc.player.position().distanceTo(c) / (20 + r * 4));
            FxManager.shake = Math.max(FxManager.shake, com.andy.abyss.Config.screenShake.get().floatValue() * (0.4f + r * 0.12f) * fall);
        }
    }

    static void crater(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        Vec3 c = groundPoint(p, f, d.pt).add(0, 0.05, 0);
        float r = f.a(), k = age / 40f;
        if (k >= 1) return;
        float out = k < 0.6f ? 1 : 1 - (k - 0.6f) / 0.4f;
        Abyssal.groundWave(d, c, 0.5f + r * 1.6f * (1 - (float) Math.pow(1 - Math.min(1, age / 10f), 3)), (1 - Math.min(1, age / 14f)) * 0.9f);
        Abyssal.fracture(d, c, UP, r * 1.5f, Math.min(1, age / 5f), out * 0.95f, p.a.seed ^ 0xC4A7);
        Abyssal.disc(d, c.add(0, 0.02, 0), r * 0.75f, out * 0.55f);
        if (age < 3) d.billboard(Rt.GLOW, true, c.add(0, 0.5, 0), 0.6f + r * 0.25f, Dust.PALE, 0.6f * (1 - age / 3f), 0);
    }

    static void spawnTrench(MoveFx.Prim p, Move.Fx f) {
        Vec3 dir = p.a.fwd;
        for (int i = 0; i < 8; i++) {
            float t = f.d() + f.a() * i / 8f;
            Vec3 c = p.a.groundAt(p.a.o.add(dir.scale(t))).add(0, 0.3, 0);
            Dust.burst(c, 8, 0.1, 0.07f, i % 2 == 0);
            if (i % 2 == 0) Dust.smoke(c, 1, 0.2, 0.7f + f.b() * 0.3f, 22);
        }
        MoveFx.sfx(p.a.groundAt(p.a.o.add(dir.scale(f.d() + 1))), ModSounds.QUAKE, 1.2f, 0.8f);
    }

    static void trench(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        float k = age / 34f;
        if (k >= 1) return;
        float out = k < 0.6f ? 1 : 1 - (k - 0.6f) / 0.4f;
        float run = Math.min(1, age / 5f);
        Vec3 a = p.a.groundAt(p.a.o.add(p.a.fwd.scale(f.d()))).add(0, 0.06, 0);
        Vec3 b = p.a.groundAt(p.a.o.add(p.a.fwd.scale(f.d() + f.a() * run))).add(0, 0.06, 0);
        Random r = new Random(p.a.seed ^ 0x7E4C);
        // the split itself: a jagged seam along the cut, with side cracks
        Vec3 side = new Vec3(-p.a.fwd.z, 0, p.a.fwd.x);
        int segs = (int) Math.max(4, f.a() / 1.2f);
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            Vec3 q = a.add(b.subtract(a).scale(i / (float) segs)).add(side.scale((r.nextFloat() - 0.5f) * f.b() * 0.5f));
            Abyssal.seam(d, prev, q, 0.2f + f.b() * 0.25f, out);
            if (r.nextFloat() < 0.4f) Abyssal.fracture(d, q, UP, f.b() * 1.2f, Math.min(1, (age - i * 0.4f) / 4f), out * 0.8f, p.a.seed + i);
            prev = q;
        }
    }

    // ------------------------------------------------------------------------------------------------ time stop
    static void spawnFreeze(MoveFx.Prim p, Move.Fx f) {
        ClientState.freezeUntil = ClientState.ticks + (int) f.b();
        MoveFx.sfx(p.a.o, ModSounds.FREEZE, 1.4f, 0.6f);
    }

    static void freeze(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        float dur = f.b();
        if (age > dur + 8) return;
        float a = Math.min(1, age / 3f) * (age > dur ? 1 - (age - dur) / 8f : 1);
        // the moment hangs: a still ring on the ground at the edge of the stopped world, and motes frozen in place
        Vec3 c = p.a.groundAt(p.a.o).add(0, 0.05, 0);
        d.flat(Rt.RING, true, c, UP, f.a(), Abyssal.EDGE, a * 0.25f, 0);
        Abyssal.pressure(d, c, UP, f.a() * 0.98f, a * 0.6f);
        Abyssal.glints(d, p.a.o.add(p.a.fwd.scale(4)), f.a() * 0.6f, 24, a * 0.8f, p.a.seed);
    }

    private BrawlFx() {}
}
