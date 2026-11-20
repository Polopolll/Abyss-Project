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
        // the pack's punch on top, Minecraft's impact underneath (the weight and the crunch of the world)
        MoveFx.sfx(c, s >= 2 ? ModSounds.PUNCH_HEAVY : ModSounds.PUNCH, 1f, s >= 2 ? 0.95f : 1.0f + Dust.R.nextFloat() * 0.12f);
        MoveFx.sfx(c, s >= 2 ? ModSounds.PUNCH_HEAVY_LAYER : ModSounds.PUNCH_LAYER, s >= 2 ? 0.7f : 0.45f, 1f);
        if (s >= 2.5f) { MoveFx.sfx(c, ModSounds.PUNCH_BOOM, 1f, 0.9f); MoveFx.sfx(c, ModSounds.CRATER, 0.6f, 1.1f); }
    }

    static void fist(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        Vec3 c = aimPoint(p, f.b(), f.c() == 1, d.pt).add(p.right.scale(f.d() * 0.12));
        float s = Math.max(0.6f, f.a());
        long seed = p.a.seed ^ (long) f.tick() * 31;
        Vec3 dir = c.subtract(p.o).normalize();
        blast(d, c, dir, s, age, seed);
        punchMark(d, c, s, age, seed);
    }

    /**
     * What a punch does to the air (One Punch Man): wind lines rushing along the punch into the fist, then the air in
     * front of it blown out as a cone of pressure rings, and for the big ones a wake of streaks fanning out ahead.
     */
    static void blast(Draw d, Vec3 c, Vec3 dir, float s, float age, long seed) {
        Random r = new Random(seed);
        Vec3 a = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 u = dir.cross(a).normalize(), v = dir.cross(u).normalize();
        // speed lines converging on the fist as it lands (the last ~2 ticks before, the first ~3 after)
        if (age < 4) {
            float k = age / 4f, fade = 1 - k;
            int n = 8 + (int) (s * 5);
            for (int i = 0; i < n; i++) {
                double ang = r.nextDouble() * Math.PI * 2, rad = (0.12 + r.nextDouble() * 0.32) * (0.6 + 0.4 * s);
                Vec3 off = u.scale(Math.cos(ang) * rad).add(v.scale(Math.sin(ang) * rad));
                double len = (0.8 + r.nextDouble() * 1.2) * (0.7 + 0.3 * s);
                Vec3 tail = c.add(off.scale(1.6)).subtract(dir.scale(len + 0.4 + k * 0.6));
                Vec3 head = c.add(off).subtract(dir.scale(0.15 + k * 0.5));
                d.beam(Rt.STREAK, true, tail, head, 0.03f + 0.014f * s, i % 3 == 0 ? Dust.PALE : Abyssal.COLD, 0.85f * fade * d.near(head, 1.0f));
            }
        }
        // the pressure cone: rings blown forward off the knuckles, each a beat behind the last
        int rings = s >= 2 ? 4 : s >= 1.3f ? 3 : 2;
        for (int i = 0; i < rings; i++) {
            float t = age - i * 0.6f;
            if (t < 0 || t > 7) continue;
            float k = t / 7f, out = (1 - k) * (1 - k);
            float rad = (0.18f + i * 0.14f) * s * (0.4f + 1.1f * (1 - (float) Math.pow(1 - Math.min(1, t / 3f), 3)));
            Vec3 at = c.add(dir.scale((0.25 + i * 0.45) * s * (0.6 + 0.6 * Math.min(1, t / 3f))));
            d.flat(Rt.RING, false, at, dir, rad, Dust.BLACK, 0.75f * out, 0);
            d.flat(Rt.RING, true, at, dir, rad * 1.03f, Dust.PALE, 0.6f * out, 0);
        }
        // the big ones split the air ahead: streaks fanning out along the punch, black flecks riding them
        if (s >= 2) {
            float k = Math.min(1, age / 5f), fade = age < 5 ? 1 : Math.max(0, 1 - (age - 5) / 6f);
            int n = 10 + (int) (s * 4);
            for (int i = 0; i < n; i++) {
                double ang = r.nextDouble() * Math.PI * 2, spread = 0.25 + r.nextDouble() * 0.35;
                Vec3 out = dir.add(u.scale(Math.cos(ang) * spread)).add(v.scale(Math.sin(ang) * spread)).normalize();
                double far = (1.5 + r.nextDouble() * 2.5) * s * (0.3 + 0.7 * k);
                Vec3 from = c.add(out.scale(far * 0.35)), to = c.add(out.scale(far));
                d.beam(Rt.STREAK, true, from, to, 0.03f + 0.012f * s, i % 4 == 0 ? Dust.PALE : Abyssal.COLD, 0.4f * fade);
                if (i % 3 == 0) d.beam(Rt.STREAK, false, from, from.add(to.subtract(from).scale(0.5)), 0.05f + 0.02f * s, Dust.BLACK, 0.5f * fade);
            }
        }
    }

    /** One punch's mark in the air: a tight white-violet flash, a dark ring of pressure, the air cracking. */
    static void punchMark(Draw d, Vec3 c, float s, float age, long seed) { punchMark(d, c, s, age, seed, true); }

    static void punchMark(Draw d, Vec3 c, float s, float age, long seed, boolean lit) {
        float k = age / 10f;
        if (k >= 1) return;
        float out = 1 - k;
        // the hit itself: a hard white flash with a star of short spikes (one frame of pure light on contact)
        if (age < 3 && lit) {
            float fl = 1 - age / 3f;
            d.billboard(Rt.GLOW, true, c, (0.25f + 0.2f * s) * fl + 0.08f, 0xFFFFFF, 0.95f * fl, 0);
            d.billboard(Rt.SPARK, true, c, (0.35f + 0.3f * s) * (0.6f + 0.6f * age / 3f), 0xFFFFFF, 0.9f * fl, seed % 7);
        }
        float ring = 0.12f + (0.25f + 0.25f * s) * (1 - (float) Math.pow(1 - Math.min(1, age / 6f), 3));
        d.billboard(Rt.RING, false, c, ring, Dust.BLACK, 0.5f * out * out, 0);
        if (lit) d.billboard(Rt.RING, true, c, ring * 1.04f, Abyssal.EDGE, 0.09f * out * out, 0);
        Abyssal.fracture(d, c, null, 0.35f + 0.3f * s, Math.min(1, age / 2.5f), out * 0.95f, seed);
    }

    /** A ghost fist in the world (knuckles along dir), for the rush's afterimages. */
    static void afterimage(Draw d, Vec3 at, Vec3 dir, Vec3 right, boolean left, float alpha) {
        if (alpha < 0.03f || d.near(at, 0.8f) < 0.05f) return;
        com.mojang.blaze3d.vertex.PoseStack ps = new com.mojang.blaze3d.vertex.PoseStack();
        ps.last().pose().set(d.m);
        ps.translate(at.x - d.cam.x, at.y - d.cam.y, at.z - d.cam.z);
        org.joml.Vector3f y = new org.joml.Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        org.joml.Vector3f z = new org.joml.Vector3f(0, 1, 0).sub(new org.joml.Vector3f(y).mul(y.y));
        if (z.lengthSquared() < 1e-4f) z.set((float) right.x, (float) right.y, (float) right.z);
        z.normalize();
        org.joml.Vector3f x = new org.joml.Vector3f(y).cross(z).normalize();
        ps.mulPose(new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(x, y, z)));
        float sc = 0.6f;
        ps.scale(left ? -sc : sc, sc, sc);
        WeaponModel.renderEthereal(ps, d.buf, com.andy.abyss.combat.Weapon.GAUNTLETS, alpha);
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
                MoveFx.sfx(c, ModSounds.PUNCH, 0.7f, 1.0f + (i % 5) * 0.05f);
                if (i % 3 == 0) MoveFx.sfx(c, ModSounds.PUNCH_LAYER, 0.35f, 1.1f);
            }
        }
    }

    static void barrage(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        int n = (int) f.a();
        Vec3 c0 = aimPoint(p, f.c(), f.e() == 1, d.pt);
        Vec3 dir = c0.subtract(p.o).normalize();
        for (int i = 0; i < n; i++) {
            float t = age - barrageTime(f, i);
            if (t < 0 || t > 7) continue;
            Vec3 at = c0.add(barrageOffset(p, f, i));
            punchMark(d, at, 0.75f, t * 1.4f, p.a.seed + i, i % 4 == 0);
            if (i % 2 == 0) blast(d, at, dir, 0.7f, t * 1.3f, p.a.seed + i * 17L);
            // the afterimage of the fist that just landed: a ghost of it, hanging where it hit, fading fast
            if (t < 3) afterimage(d, at.subtract(dir.scale(0.35)), dir, p.right, i % 2 == 0, (1 - t / 3f) * 0.75f);
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
        var mc = Minecraft.getInstance();
        // the caster sees the phantom arms as extra arms on screen (FirstPersonWeapon); here only for everyone else
        if (mc.player != null && p.a.p.caster() == mc.player.getId() && mc.options.getCameraType().isFirstPerson()) return;
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
            // extra shoulders just ahead of you, fanned around the view: the arms reach in toward the target (seen
            // along their length, not stretched past the camera)
            Vec3 sh = p.o.add(p.fwd.scale(1.1 + row * 0.15)).add(p.right.scale(side * (0.55 + row * 0.22))).add(p.up.scale(-0.42 + row * 0.38));
            float period = 3.2f + (i % 3) * 0.4f;
            float ph = ((age + i * 1.37f) % period) / period;
            float ext = ph < 0.35f ? ph / 0.35f : 1 - (ph - 0.35f) / 0.65f; // snap out, draw back
            ext = ext * ext * (3 - 2 * ext);
            Vec3 hit = aim.add(p.right.scale(side * 0.25 * (1 + row * 0.4))).add(p.up.scale((row - 0.5) * 0.3));
            Vec3 fist = sh.add(hit.subtract(sh).scale(0.35 + 0.6 * ext));
            Abyssal.tendril(d, sh, fist, 0.2f, 0.03f, age * 0.5f + i, 1, a * 0.8f);
            d.billboard(Rt.CORE, false, fist, 0.13f, Dust.BLACK, a * 0.9f, 0);
            d.billboard(Rt.GLOW, true, fist, 0.1f, Abyssal.COLD, a * 0.25f * ext, 0);
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
        MoveFx.sfx(c, ModSounds.PUNCH_BOOM, 1.2f, r > 4 ? 0.8f : 0.95f);
        MoveFx.sfx(c, ModSounds.CRATER, 0.9f, r > 4 ? 0.7f : 0.95f);
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

    // ------------------------------------------------------------------------------------------------ the lunge
    static void spawnLunge(MoveFx.Prim p, Move.Fx f) {
        MoveFx.sfx(p.a.o, ModSounds.DODGE, 0.8f, 1.25f);
        var mc = Minecraft.getInstance();
        if (mc.player != null && p.a.p.caster() == mc.player.getId()) MoveFx.fovPunch(0.035f);
    }

    /** A low wake of dark air behind the caster while they close in (others see it; you feel the fov). */
    static void lunge(Draw d, MoveFx.Prim p, Move.Fx f, float age) {
        var mc = Minecraft.getInstance();
        if (p.a.caster == null || (mc.player != null && p.a.p.caster() == mc.player.getId() && mc.options.getCameraType().isFirstPerson())) return;
        float dur = Math.max(1, f.c());
        if (age > dur + 6) return;
        float a = age < dur ? 1 : 1 - (age - dur) / 6f;
        Vec3 at = p.a.caster.getPosition(d.pt).add(0, 0.9, 0);
        Vec3 back = p.a.fwd.scale(-1.6);
        for (int i = 0; i < 4; i++) {
            Vec3 o = at.add(p.a.right.scale((i - 1.5) * 0.18)).add(0, (i % 2) * 0.3 - 0.2, 0);
            d.beam(Rt.STREAK, true, o, o.add(back), 0.05f, Abyssal.COLD, 0.25f * a);
        }
    }

    private BrawlFx() {}
}
