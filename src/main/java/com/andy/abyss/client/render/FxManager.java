package com.andy.abyss.client.render;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.SporeClient;
import com.andy.abyss.data.Ability;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Every Abyss visual drawn in the world: one-shot effects from the server, plus domains, Triangle clashes and spores. */
public final class FxManager {
    static final class Fx {
        S2CFx p; int age, life; long seed; Vec3 pos;
        float t(float pt) { return Math.min(1f, (age + pt) / life); }
    }
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final Random R = new Random();
    public static float shake, flash, pulse;
    public static int flashColor = 0xFFFFFF;

    public static void receive(S2CFx p) {
        Fx f = new Fx();
        f.p = p; f.seed = R.nextLong(); f.pos = new Vec3(p.x(), p.y(), p.z());
        f.life = switch (p.type()) {
            // build-up, impact, linger: nothing pops in and out
            case S2CFx.NUKE -> 96; case S2CFx.IMPLODE -> p.b() == 1 ? 20 : 24; case S2CFx.DEVOUR -> 42; case S2CFx.TRANSPORT -> 38; case S2CFx.DISINTEGRATE -> 46;
            case S2CFx.RIFT_OPEN -> 30; case S2CFx.SWAP -> 28; case S2CFx.NEGATE -> 16; case S2CFx.CLONE_SPAWN -> 36; case S2CFx.CLONE_DEATH -> 38;
            case S2CFx.TRIANGLE_END -> 42; case S2CFx.STEP -> 24; case S2CFx.CHARGE -> 12; case S2CFx.VOLLEY -> 10; case S2CFx.AWAKEN -> 110;
            case S2CFx.DOMAIN_CAST -> 72; case S2CFx.SPREAD -> 26; case S2CFx.DOMAIN_PULSE -> 42; case S2CFx.COMBO -> p.a() == 5 ? 22 : p.a() == 4 ? 16 : 12;
            case S2CFx.BEAM -> 24; case S2CFx.PLUNGE -> 30; case S2CFx.LUNGE -> 14; case S2CFx.REFORM -> 42;
            case S2CFx.DOMAIN_SPEARS -> 30; case S2CFx.DOMAIN_COLLAPSE -> 46; case S2CFx.INHALE -> (int) Math.max(10, p.a()) + 8; case S2CFx.DODGE -> 14;
            case S2CFx.PERFECT_DODGE -> 34; case S2CFx.FORGE -> 16; case S2CFx.REALM_ENTER, S2CFx.REALM_EXIT -> 40; case S2CFx.PEEK -> p.a() == 2 ? 20 : 30;
            case S2CFx.FORM_OPEN -> 26; case S2CFx.GUARD -> 12; default -> 20;
        };
        ACTIVE.add(f);
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.player.position().distanceTo(f.pos);
        float k = Config.screenShake.get().floatValue();
        switch (p.type()) {
            case S2CFx.NUKE -> { shake = Math.max(shake, (float) (k * Math.max(0, 1.6 - dist / (p.a() * 4)))); if (dist < p.a() * 5) { flash = Math.max(flash, 0.7f); flashColor = 0xEFE6FF; } }
            case S2CFx.AWAKEN -> { shake = Math.max(shake, k * 0.8f); if (dist < 30) { flash = 0.8f; flashColor = 0x14081F; } }
            case S2CFx.DOMAIN_CAST -> { shake = Math.max(shake, k * 0.5f); }
            case S2CFx.TRIANGLE_END -> { shake = Math.max(shake, k * (float) Math.max(0, 1 - dist / 40)); flash = Math.max(flash, 0.4f); flashColor = 0xFFFFFF; }
            case S2CFx.SWAP -> { if (p.e1() == mc.player.getId() && p.a() == 1) ClientState.swapFadeAt = ClientState.ticks; }
            case S2CFx.DOMAIN_PULSE -> { if (dist < p.a()) { pulse = 1; shake = Math.max(shake, k * 0.7f); } }
            case S2CFx.COMBO -> {
                int step = (int) p.a();
                if (!(step == 3 && p.b() == 0)) ClientState.pose(p.e1(), step);
                if (p.e1() == mc.player.getId()) { ClientState.comboStep = step; ClientState.comboAt = ClientState.ticks; shake = Math.max(shake, k * (step == 5 ? 0.6f : 0.18f)); }
            }
            case S2CFx.BEAM -> { ClientState.pose(p.e1(), 6); if (dist < 30) shake = Math.max(shake, k * 0.5f); }
            case S2CFx.PLUNGE -> { ClientState.pose(p.e1(), 7); shake = Math.max(shake, k * (float) Math.max(0, 1.2 - dist / 25)); }
            case S2CFx.LUNGE -> ClientState.pose(p.e1(), 1);
            case S2CFx.REFORM -> { if (p.e1() == mc.player.getId()) { flash = 0.8f; flashColor = 0x05020A; } }
            case S2CFx.DOMAIN_COLLAPSE -> { if (dist < p.a() * 2) shake = Math.max(shake, k * 0.6f); }
            case S2CFx.PERFECT_DODGE -> { if (p.e1() == mc.player.getId()) { ClientState.slowUntil = ClientState.ticks + 40; flash = 0.35f; flashColor = 0xE8F4FF; } }
            case S2CFx.FORGE -> MoveFx.forge(p.e1(), com.andy.abyss.combat.Weapon.byIndex((int) p.a()));
            case S2CFx.FORM_OPEN -> { if (p.b() == 1 && (int) p.a() == Ability.ARSENAL.ordinal()) MoveFx.forge(p.e1(), com.andy.abyss.combat.Weapon.byIndex((int) p.c())); }
            case S2CFx.REALM_ENTER, S2CFx.REALM_EXIT -> { if (p.e1() == mc.player.getId() && p.a() == 1) { flash = 0.9f; flashColor = p.type() == S2CFx.REALM_ENTER ? 0x05020A : 0xF0E8FF; shake = Math.max(shake, k * 0.4f); } }
            case S2CFx.GUARD -> {
                if (p.a() == 2 && p.e1() == mc.player.getId()) shake = Math.max(shake, k * 0.25f);
                if (p.a() == 1) ClientState.pose(p.e1(), com.andy.abyss.combat.Moves.P_BLOCK, 1_000_000); else if (p.a() == 0) ClientState.POSES.remove(p.e1());
            }
            default -> {}
        }
    }

    public static void tick() {
        ACTIVE.removeIf(f -> ++f.age > f.life);
        com.andy.abyss.client.InfectionClient.tick();
        shake *= 0.86f;
        pulse *= 0.9f;
        flash *= 0.82f;
        // ambient: awakened bodies shed a few motes of abyss
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (ClientState.ticks % 3 == 0) for (Entity e : mc.level.entitiesForRendering()) {
            boolean voidBody = (e instanceof Player && ClientState.awakened(e.getId())) || e instanceof com.andy.abyss.entity.AbyssBody;
            if (!voidBody || e.distanceToSqr(mc.player) > 48 * 48) continue;
            MOTES.add(new Mote(e.position().add((R.nextDouble() - 0.5) * 0.8, R.nextDouble() * 1.9, (R.nextDouble() - 0.5) * 0.8), new Vec3(0, 0.02 + R.nextDouble() * 0.02, 0), 30));
        }
        MOTES.removeIf(m -> { m.age++; m.p = m.p.add(m.v); return m.age > m.life; });
    }

    static final class Mote { Vec3 p, v; int age, life; Mote(Vec3 p, Vec3 v, int life) { this.p = p; this.v = v; this.life = life; } }
    private static final List<Mote> MOTES = new ArrayList<>();

    private static final FxBuffers BUFFERS = new FxBuffers();

    public static void render(PoseStack ps, Camera cam, float pt) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FxBuffers buf = BUFFERS; // one set, reused every frame (a new set per frame leaked native memory)
        double time = (mc.level.getGameTime() + pt) / 20.0;
        Draw d = new Draw(ps, cam, pt, buf, time);
        try {
            com.andy.abyss.client.InfectionClient.render(d);
            for (S2CWorld.Dom dom : ClientState.domains) domain(d, dom);
            for (S2CWorld.Clash c : ClientState.clashes) clash(d, c);
            SporeClient.render(d);
            for (Mote m : MOTES) {
                float a = (1 - m.age / (float) m.life) * d.near(m.p, 1.5f); // motes drifting past the eye would fill the screen
                if (a < 0.02f) continue;
                d.billboard(Rt.CORE, false, m.p, 0.05f, 0x07040C, 0.8f * a, 0);
                d.billboard(Rt.GLOW, true, m.p, 0.12f, 0x7A45E0, 0.35f * a, 0);
            }
            for (Fx f : ACTIVE) draw(d, f, pt);
            MoveFx.render(d);
            auras(d);
            souls(d);
        } catch (Exception ex) {
            com.andy.abyss.Abyss.LOG.debug("abyss fx", ex);
        }
        buf.endAll();
    }

    /**
     * An infinite space in a finite space. Outside: a sphere of galaxy punched into the world with a white-violet lensing
     * rim and an accretion ring. Inside: the sphere is a wall of space in every direction, the sky is gone, stars hang in
     * the air, crystal debris rises off the rotting ground, dark motes rain down, and each pulse rolls a ring outward.
     */
    private static void domain(Draw d, S2CWorld.Dom dom) {
        Vec3 c = new Vec3(dom.x(), dom.y(), dom.z());
        float r = dom.radius();
        boolean inside = d.cam.distanceTo(c) < r;
        float fade = Math.min(1f, dom.ticksLeft() / 20f);
        boolean growing = r < dom.max() - 0.5f;
        if (inside) {
            d.voidSphere(c, r, fade, 0f, 0xFFFFFF);
            d.glowShell(c, r * 0.995, 0xB48CFF, 0.9f * fade);
        } else {
            d.voidSphere(c, r, 0.97f * fade, 0.12f, 0xFFFFFF);
            d.glowShell(c, r * 1.01, 0xE8DCFF, 1f * fade);
            d.glowShell(c, r * 1.06, 0x7A40E0, 0.6f * fade);
            // accretion ring around the equator
            d.flat(Rt.RING, true, c, new Vec3(0.08, 1, 0.05), r * 1.35f, 0xC8B0FF, 0.7f * fade, (float) d.time * 0.3f);
            d.flat(Rt.RING, true, c, new Vec3(0.08, 1, 0.05), r * 1.6f, 0x6A40C0, 0.4f * fade, -(float) d.time * 0.2f);
        }
        // the expansion front: a bright shock rolling over the ground
        if (growing) {
            float y = (float) (c.y - Math.min(r, 3) + 0.1);
            d.flat(Rt.RING, true, new Vec3(c.x, y, c.z), new Vec3(0, 1, 0), r * 1.05f, 0xFFFFFF, 0.95f * fade, 0);
            d.flat(Rt.RING, true, new Vec3(c.x, y + 0.6, c.z), new Vec3(0, 1, 0), r * 1.1f, 0x9B6BFF, 0.7f * fade, 0);
            d.glowShell(c, r * 1.03, 0xFFFFFF, 0.6f * fade);
        }
        // pulse rings racing outward
        float pk = 1 - pulse;
        if (pulse > 0.05f) d.flat(Rt.RING, true, c.add(0, -Math.min(r, 3) + 0.2, 0), new Vec3(0, 1, 0), r * pk, 0xFFFFFF, pulse * fade, 0);
        if (!inside && r > 40) return;
        // inside: stars hanging in the air, nebula, rising debris, falling weight
        Random rr = new Random(dom.id() * 7919L);
        float dens = Config.fxDensity.get().floatValue();
        int stars = (int) (260 * dens);
        double cell = 34;
        Vec3 base = new Vec3(Math.floor(d.cam.x / cell) * cell, Math.floor(d.cam.y / cell) * cell, Math.floor(d.cam.z / cell) * cell);
        for (int gx = -1; gx <= 1; gx++) for (int gy = -1; gy <= 1; gy++) for (int gz = -1; gz <= 1; gz++) {
            Vec3 o = base.add(gx * cell, gy * cell, gz * cell);
            Random cr = new Random((long) (o.x * 73856093) ^ (long) (o.y * 19349663) ^ (long) (o.z * 83492791) ^ dom.id());
            for (int i = 0; i < stars / 27; i++) {
                Vec3 p = o.add(cr.nextDouble() * cell, cr.nextDouble() * cell, cr.nextDouble() * cell);
                if (p.distanceTo(c) > r) continue;
                float tw = 0.5f + 0.5f * (float) Math.sin(d.time * (1.5 + cr.nextDouble() * 3) + i);
                float sz = 0.05f + cr.nextFloat() * 0.09f;
                d.billboard(Rt.SPARK, true, p, sz * (1 + tw), cr.nextFloat() < 0.2f ? 0xD8B8FF : 0xF0F0FF, (0.5f + 0.5f * tw) * fade * d.near(p, 2), 0);
            }
        }
        for (int i = 0; i < 6 * dens; i++) {
            double a = rr.nextDouble() * Math.PI * 2, rad = Math.sqrt(rr.nextDouble()) * r * 0.8;
            Vec3 p = c.add(Math.cos(a + d.time * 0.02) * rad, (rr.nextDouble() - 0.3) * Math.min(r, 14), Math.sin(a + d.time * 0.02) * rad);
            d.voidBillboard(Rt.FILL, p, 3 + rr.nextFloat() * 4, 2 + rr.nextFloat() * 2, 0.35f * fade * d.near(p, 4), (float) (d.time * 0.05 + i));
        }
        int debris = (int) (70 * dens);
        for (int i = 0; i < debris; i++) {
            double a = rr.nextDouble() * Math.PI * 2, rad = Math.sqrt(rr.nextDouble()) * r * 0.95;
            double life = (d.time * (0.08 + rr.nextDouble() * 0.12) + rr.nextDouble()) % 1.0;
            double gx = c.x + Math.cos(a) * rad, gz = c.z + Math.sin(a) * rad;
            double gy = c.y - Math.min(r, 3);
            Vec3 p = new Vec3(gx + Math.sin(life * 6 + i) * 0.4, gy + life * 9, gz);
            if (p.distanceTo(d.cam) > 48) continue;
            float al = (float) (life < 0.15 ? life / 0.15 : 1 - (life - 0.15) / 0.85) * fade;
            d.voidShard(p, new Vec3(Math.sin(i + d.time), 1, Math.cos(i * 1.3)), 0.25f + (i % 4) * 0.1f, 0.08f, al);
        }
        int n = (int) Math.min(160, r * 3 * dens);
        for (int i = 0; i < n; i++) {
            double a = rr.nextDouble() * Math.PI * 2, rad = Math.sqrt(rr.nextDouble()) * r * 0.95;
            double fall = (d.time * (0.8 + rr.nextDouble()) + rr.nextDouble() * 10) % 1.0;
            Vec3 p = c.add(Math.cos(a) * rad, (1 - fall * 2) * Math.min(r, 12), Math.sin(a) * rad);
            d.beam(Rt.STREAK, false, p, p.add(0, -0.7, 0), 0.05f, 0x0A0612, 0.65f * fade);
        }
    }

    private static void clash(Draw d, S2CWorld.Clash c) {
        var mc = Minecraft.getInstance();
        Entity a = mc.level.getEntity(c.a()), b = mc.level.getEntity(c.b());
        if (a == null || b == null) return;
        Vec3 pa = a.getPosition(d.pt).add(0, a.getBbHeight() * 0.6, 0), pb = b.getPosition(d.pt).add(0, b.getBbHeight() * 0.6, 0);
        Vec3 apex = pa.add(pb).scale(0.5).add(0, Math.max(3, pa.distanceTo(pb) * 0.6), 0);
        float in = Math.min(1f, (c.age() + d.pt) / 10f);
        float pulse = 0.8f + 0.2f * (float) Math.sin(d.time * 12);
        int white = 0xFFFFFF, purple = 0xB080FF;
        d.beam(Rt.STREAK, true, pa, pa.add(apex.subtract(pa).scale(in)), 0.18f, white, 0.9f * pulse);
        d.beam(Rt.STREAK, true, pb, pb.add(apex.subtract(pb).scale(in)), 0.18f, white, 0.9f * pulse);
        d.beam(Rt.STREAK, true, pa, pb, 0.12f, purple, 0.7f);
        // the knot: where the meter stands along the base
        float m = (c.meter() + 100f) / 200f;
        // meter +100 = the user has pushed the knot all the way to the target
        Vec3 knot = pa.add(pb.subtract(pa).scale(m));
        float kn = d.near(knot, 3f);
        d.billboard(Rt.GLOW, true, knot, 0.7f, white, 0.8f * kn, 0);
        d.billboard(Rt.RING, true, knot, 0.6f + 0.2f * pulse, purple, 0.9f * kn, (float) d.time);
        for (Vec3 v : new Vec3[]{pa, pb, apex}) { d.billboard(Rt.GLOW, true, v, 0.6f, white, 0.6f * in * d.near(v, 3f), 0); d.billboard(Rt.RING, true, v, 0.7f, purple, 0.7f * in, -(float) d.time * 2); }
        // inner fill: faint galaxy triangle
        d.voidBillboard(Rt.FILL, apex.add(pa).add(pb).scale(1 / 3.0), 0.6f, 0.6f, 0.6f * in, (float) d.time);
    }

    private static void draw(Draw d, Fx f, float pt) {
        S2CFx p = f.p;
        float t = f.t(pt);
        Random r = new Random(f.seed);
        Vec3 c = f.pos;
        var mc = Minecraft.getInstance();
        Entity e1 = p.e1() >= 0 ? mc.level.getEntity(p.e1()) : null;
        switch (p.type()) {
            case S2CFx.SPREAD -> {
                Vec3 at = e1 != null ? e1.getPosition(pt).add(0, e1.getBbHeight() * 0.6, 0) : c;
                float rad = (float) (2 + t * 26 * Math.max(0.5, p.a()));
                d.glowShell(at, rad, 0x8C55FF, 0.6f * (1 - t));
                d.flat(Rt.RING, true, at.subtract(0, 1, 0), new Vec3(0, 1, 0), rad * 0.8f, 0xB89CFF, 0.6f * (1 - t), 0);
                if (t < 0.3f) d.voidSphere(at, 0.6 + t * 2, 0.9f * (1 - t / 0.3f), 0.2f, 0xFFFFFF);
            }
            case S2CFx.IMPLODE -> {
                // the gathering: space is drawn in, rings tighten, light bends round a growing seed
                boolean inside = p.b() == 1;
                Vec3 at = inside && e1 != null ? e1.getPosition(pt).add(0, e1.getBbHeight() * 0.5, 0) : c;
                float R0 = Math.max(1.5f, p.a());
                float k = t * t;
                d.glowShell(at, Math.max(0.2, R0 * 1.3 * (1 - k)), 0xC0A0FF, 0.5f + 0.4f * t);
                d.flat(Rt.RING, true, at, new Vec3(0.2, 1, 0.1), R0 * 1.6f * (1 - k) + 0.3f, 0xE8DCFF, 0.9f, (float) d.time * 3);
                d.flat(Rt.RING, true, at, new Vec3(-0.3, 1, 0.4), R0 * 1.2f * (1 - k) + 0.3f, 0x8C55FF, 0.7f, (float) -d.time * 4);
                for (int i = 0; i < 48; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    float ph = (float) ((t * 2 + r.nextDouble()) % 1.0);
                    Vec3 from = at.add(dir.scale(R0 * 2 * (1 - ph)));
                    d.beam(Rt.STREAK, true, from, from.add(dir.scale(-R0 * 0.25)), 0.07f, ph > 0.7f ? 0xFFFFFF : 0x9C6BFF, 0.85f * ph);
                }
                if (inside && e1 != null) {
                    // the host's body cracks with light from within
                    for (int i = 0; i < 10; i++) {
                        Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                        Vec3 q = at.add(dir.multiply(e1.getBbWidth() * 0.55, e1.getBbHeight() * 0.5, e1.getBbWidth() * 0.55));
                        d.beam(Rt.STREAK, true, at, q.add(dir.scale(0.3 * t)), 0.06f + 0.1f * t, 0xE8DCFF, 0.4f + 0.6f * t);
                    }
                    d.glowShell(at, Math.max(e1.getBbWidth(), e1.getBbHeight()) * 0.6, 0xFFFFFF, t);
                }
                d.blackHole(at, (float) (0.6 + t * R0 * 0.9), false, Math.min(1, t * 2.5f), (float) d.time * -1.4f);
                if (t > 0.85f) d.billboard(Rt.GLOW, true, at, R0 * 1.5f * (t - 0.85f) / 0.15f, 0xFFFFFF, d.near(at, 1), 0);
            }
            case S2CFx.NUKE -> {
                boolean inside = p.b() == 1;
                float R0 = p.a();
                float grow = 1 - (float) Math.pow(1 - Math.min(1f, t / 0.1f), 3), shrink = t < 0.5f ? 1 : (float) Math.pow(1 - (t - 0.5f) / 0.5f, 1.6);
                Vec3 at = inside && e1 != null ? e1.getPosition(pt).add(0, e1.getBbHeight() * 0.5, 0) : c;
                d.voidSphere(at, R0 * grow * shrink * (inside ? 0.7 : 1), 1f, 0.15f, 0xFFFFFF);
                d.glowShell(at, R0 * grow * 1.05, 0xD8C8FF, 0.9f * shrink);
                float ring = R0 * (0.5f + t * 2.5f);
                d.flat(Rt.RING, true, at.subtract(0, Math.min(R0 * 0.5, 2), 0), new Vec3(0, 1, 0), ring, 0xF0E8FF, 0.9f * (1 - t), 0);
                d.flat(Rt.RING, true, at, d.fwdVec(), ring * 0.8f, 0xB080FF, 0.6f * (1 - t), 0);
                if (t < 0.12f) d.billboard(Rt.GLOW, true, at, R0 * 3, 0xFFFFFF, (1 - t / 0.12f) * d.near(at, R0), 0);
                if (t > 0.08f && t < 0.6f) { float k2 = (t - 0.08f) / 0.52f; d.flat(Rt.RING, true, at.subtract(0, Math.min(R0 * 0.5, 2) - 0.05, 0), new Vec3(0, 1, 0), R0 * (1 + k2 * 4), 0x8C55FF, 0.7f * (1 - k2), 0); }
                // the surface churns: violet seams crawl over the sphere while it holds
                if (t > 0.1f && t < 0.5f) for (int i = 0; i < 12; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    Vec3 tan = dir.cross(new Vec3(0, 1, 0)).normalize();
                    Vec3 q = at.add(dir.scale(R0 * grow * shrink * (inside ? 0.7 : 1) * 1.01));
                    d.beam(Rt.STREAK, true, q, q.add(tan.scale(R0 * 0.3 * Math.sin(d.time * 3 + i))), 0.08f, 0xC8B0FF, 0.6f);
                }
                int shards = inside ? 16 + (int) p.c() * 2 : 28;
                for (int i = 0; i < shards; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7 + 0.3, r.nextGaussian()).normalize();
                    float k = (float) Math.min(1, t * 2.2);
                    Vec3 base = at.add(dir.scale(R0 * (inside ? 0.2 : 0.5) + k * R0 * 1.2));
                    d.voidShard(base, dir, (0.6f + r.nextFloat()) * (1 - t) * (inside ? 0.8f : 1.4f), 0.12f + r.nextFloat() * 0.1f, 1 - t);
                }
            }
            case S2CFx.DEVOUR -> devour(d, f, pt, t, r);
            case S2CFx.TRANSPORT -> {
                Vec3 at = e1 != null && t < 0.5f ? e1.getPosition(pt).add(0, e1.getBbHeight() / 2, 0) : c;
                float rad = p.a() * (t < 0.4f ? t / 0.4f : 1 - (t - 0.4f) / 0.6f) * 1.2f;
                d.voidSphere(at, Math.max(0.05, rad), 1f, 0.15f, 0xFFFFFF);
                d.glowShell(at, rad * 1.08, 0x9A60FF, 0.8f);
            }
            case S2CFx.DISINTEGRATE -> {
                for (int i = 0; i < 36; i++) {
                    Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(p.a() * 0.35);
                    Vec3 at = c.add(o).add(0, t * (1 + r.nextFloat() * 2), 0);
                    d.voidShard(at, new Vec3(r.nextGaussian(), 1, r.nextGaussian()), 0.25f * (1 - t), 0.06f, 1 - t);
                    d.billboard(Rt.GLOW, true, at, 0.2f, 0x8050E0, 0.5f * (1 - t), 0);
                }
                d.glowShell(c, p.a() * (0.6 + t), 0xC8B0FF, 0.6f * (1 - t));
            }
            case S2CFx.RIFT_OPEN, S2CFx.STEP -> {
                float rad = (p.type() == S2CFx.STEP ? 1.5f : 2.5f) * (0.3f + t);
                d.glowShell(c, rad, 0x9A70FF, 0.7f * (1 - t));
                d.billboard(Rt.GLOW, true, c, rad * 1.4f, 0x6A40C0, 0.6f * (1 - t) * d.near(c, 2), 0);
                if (t < 0.5f) d.voidSphere(c, 0.8 * (1 - t * 2), 1, 0.2f, 0xFFFFFF);
            }
            case S2CFx.SWAP -> {
                float h = 2.2f;
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI * 2 / 10 + t * 2;
                    Vec3 base = c.add(Math.cos(a) * 0.7 * (1 - t), -1 + t * 0.3, Math.sin(a) * 0.7 * (1 - t));
                    d.voidShard(base, new Vec3(0, 1, 0), h * (1 - t), 0.12f, 1 - t);
                }
                d.glowShell(c, 1.2 + t * 1.5, 0xA070FF, 0.8f * (1 - t));
            }
            case S2CFx.NEGATE -> {
                Vec3 at = e1 != null ? e1.getPosition(pt).add(0, e1.getBbHeight() * 0.55, 0) : c;
                float rad = (e1 != null ? Math.max(e1.getBbWidth(), e1.getBbHeight()) * 0.75f : 1.2f) * (1 + t * 0.15f);
                d.glowShell(at, rad, 0xE8E0FF, 0.9f * (1 - t));
                for (int i = 0; i < 10; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    d.billboard(Rt.SHARD, true, at.add(dir.scale(rad)), 0.25f, 0xD8C8FF, 0.8f * (1 - t), r.nextFloat() * 6);
                }
            }
            case S2CFx.CLONE_SPAWN -> {
                float h = Math.max(2, p.a() * 2);
                for (int i = 0; i < 22; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    Vec3 tgt = c.add(r.nextGaussian() * 0.25, r.nextGaussian() * h * 0.3, r.nextGaussian() * 0.25);
                    Vec3 at = tgt.add(dir.scale(3 * (1 - t)));
                    d.voidShard(at, tgt.subtract(at).lengthSqr() > 1e-4 ? tgt.subtract(at) : dir, 0.35f, 0.08f, Math.min(1, t * 3) * (1 - t * 0.5f));
                }
                d.voidSphere(c.subtract(0, h * 0.4, 0), 1.4 * (1 - t), 0.9f * (1 - t), 0.6f, 0xFFFFFF);
                d.glowShell(c, 0.6 + t * 2, 0x9A70FF, 0.7f * (1 - t));
            }
            case S2CFx.CLONE_DEATH -> {
                for (int i = 0; i < 26; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5 + 0.4, r.nextGaussian()).normalize();
                    Vec3 at = c.add(dir.scale(t * 2.5)).add(0, -t * t * 2, 0);
                    d.voidShard(at, dir, 0.3f * (1 - t), 0.07f, 1 - t);
                }
                d.voidSphere(c, Math.max(0.05, 0.9 * (1 - t * 1.6)), 1, 0.2f, 0xFFFFFF);
            }
            case S2CFx.TRIANGLE_END -> {
                Entity a = mc.level.getEntity(p.e1()), b = mc.level.getEntity(p.e2());
                d.glowShell(c, 1 + t * 10, p.a() == 1 ? 0xFFFFFF : 0xFF7070, 0.8f * (1 - t));
                if (b != null && p.a() == 1) d.voidSphere(b.getPosition(pt).add(0, b.getBbHeight() / 2, 0), (1 - t) * 1.6, 1, 0.3f, 0xFFFFFF);
                if (a != null && p.a() != 1) d.glowShell(a.getPosition(pt).add(0, 1, 0), 1.5, 0xFF5050, 0.7f * (1 - t));
            }
            case S2CFx.CHARGE -> {
                Vec3 at = e1 != null ? e1.getPosition(pt).add(0, 1, 0) : c;
                float k = Math.min(1, p.a() / 300f);
                Ability ab = Ability.byIndex((int) p.b());
                int col = ab != null ? ab.color : 0x9B6BFF;
                for (int i = 0; i < 14; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    Vec3 from = at.add(dir.scale((2 + k * 6) * (1 - t)));
                    d.beam(Rt.STREAK, true, from, from.add(dir.scale(-0.8)), 0.06f, col, 0.7f);
                }
                d.flat(Rt.RING, true, at.subtract(0, 0.95, 0), new Vec3(0, 1, 0), 1.2f + k * 4, col, 0.7f, (float) d.time);
            }
            case S2CFx.VOLLEY -> d.billboard(Rt.GLOW, true, c, 0.7f + p.a() * 0.05f, 0xA070FF, 0.8f * (1 - t) * d.near(c, 1), 0);
            case S2CFx.AWAKEN -> {
                Vec3 at = e1 != null ? e1.getPosition(pt).add(0, 1, 0) : c;
                float k = t < 0.3f ? t / 0.3f : 1 - (t - 0.3f) / 0.7f;
                for (int i = 0; i < 18; i++) {
                    double a = i * Math.PI * 2 / 18 + t * 3;
                    d.voidShard(at.add(Math.cos(a) * 1.4, -1, Math.sin(a) * 1.4), new Vec3(Math.cos(a) * 0.2, 1, Math.sin(a) * 0.2), 3.5f * k, 0.16f, k);
                }
                d.voidSphere(at, 1.4 * k, 0.9f, 0.6f, 0xFFFFFF);
                d.glowShell(at, 2 + t * 18, 0x9A60FF, 0.7f * (1 - t));
                d.flat(Rt.RING, true, at.subtract(0, 0.95, 0), new Vec3(0, 1, 0), 2 + t * 22, 0xC0B0E0, 0.8f * (1 - t), 0);
            }
            case S2CFx.DOMAIN_CAST -> {
                // 0-.22: a singularity forms (space pours into a point); .22: white-out; then the shell at lightspeed
                float R0 = Math.min(p.a(), 80);
                if (t < 0.22f) {
                    float k = t / 0.22f;
                    d.blackHole(c, 1f + k * 5f, true, Math.min(1, k * 2), 0);
                    d.flat(Rt.RING, true, c, new Vec3(0.15, 1, 0.1), 5 - k * 3.4f, 0xD8C8FF, 0.9f, (float) d.time * 5);
                    for (int i = 0; i < 40; i++) {
                        Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                        float ph = (float) ((k * 3 + r.nextDouble()) % 1.0);
                        Vec3 from = c.add(dir.scale(14 * (1 - ph)));
                        d.beam(Rt.STREAK, true, from, from.add(dir.scale(-2)), 0.1f, 0xB58CFF, ph);
                    }
                } else {
                    float k = (t - 0.22f) / 0.78f;
                    float rad = 1.5f + R0 * (1 - (float) Math.pow(1 - Math.min(1, k * 2.2f), 3));
                    float out = 1 - Math.max(0, (k - 0.45f) / 0.55f);
                    if (k < 0.08f) d.billboard(Rt.GLOW, true, c, R0 * 0.8f, 0xFFFFFF, (1 - k / 0.08f) * d.near(c, 4), 0);
                    d.glowShell(c, rad, 0xF0E8FF, 0.95f * out);
                    d.glowShell(c, rad * 0.96, 0x8C55FF, 0.6f * out);
                    for (int i = 0; i < 3; i++) d.flat(Rt.RING, true, c.subtract(0, 0.9, 0), new Vec3(0, 1, 0), rad * (1.05f + i * 0.08f), i == 0 ? 0xFFFFFF : 0x9C6BFF, 0.8f * out * (1 - i * 0.3f), 0);
                    d.flat(Rt.RING, true, c, d.fwdVec(), rad * 0.7f, 0xD8C8FF, 0.5f * out, 0);
                    for (int i = 0; i < 30; i++) {
                        Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                        d.voidShard(c.add(dir.scale(rad * (0.85 + r.nextDouble() * 0.15))), dir, 1.2f * out, 0.12f, out);
                    }
                }
            }
            case S2CFx.DOMAIN_PULSE -> {
                float rad = p.a() * t;
                d.flat(Rt.RING, true, c.add(0, -Math.min(p.a(), 3) + 0.15, 0), new Vec3(0, 1, 0), rad, 0xFFFFFF, (1 - t), 0);
                d.glowShell(c, Math.max(0.5, rad), 0xC8A8FF, 0.7f * (1 - t));
            }
            case S2CFx.COMBO -> combo(d, f, pt, t, r);
            case S2CFx.BEAM -> {
                float len = p.a();
                Vec3 dir = Vec3.directionFromRotation(p.c(), p.b());
                Vec3 a = c, b = c.add(dir.scale(len * Math.min(1, t * 5)));
                float w = (t < 0.2f ? t / 0.2f : 1 - (t - 0.2f) / 0.8f);
                // a tube of galaxy with a white core and violet sheath, rings travelling down it
                d.voidRibbon(a, b, 1.3f * w, w);
                d.beam(Rt.STREAK, true, a, b, 2.2f * w, 0x8C55FF, 0.8f * w);
                d.beam(Rt.STREAK, true, a, b, 0.5f * w, 0xFFFFFF, w);
                for (int i = 0; i < 6; i++) {
                    float k = (float) ((t * 3 + i / 6.0) % 1.0);
                    d.flat(Rt.RING, true, a.add(b.subtract(a).scale(k)), dir, 1.1f * w, 0xD8C8FF, 0.8f * w, 0);
                }
                d.billboard(Rt.GLOW, true, a, 1.5f * w, 0xFFFFFF, w * d.near(a, 1.5f), 0);
                d.voidSphere(b, 0.9 * w, w, 0.2f, 0xFFFFFF);
            }
            case S2CFx.PLUNGE -> {
                float R0 = p.a();
                Vec3 top = new Vec3(c.x, p.b(), c.z);
                if (t < 0.25f) {
                    // the fold: a column of space collapsing downward
                    float k = t / 0.25f;
                    d.beam(Rt.STREAK, true, top, c.add(0, 1, 0), 1.2f * (1 - k), 0xB48CFF, 0.9f);
                    for (int i = 0; i < 8; i++) d.flat(Rt.RING, true, top.add(c.subtract(top).scale(i / 8.0 * k)), new Vec3(0, 1, 0), 1.6f - i * 0.1f, 0xE0D0FF, 0.7f, 0);
                }
                d.flat(Rt.RING, true, c, new Vec3(0, 1, 0), R0 * (0.3f + t * 1.2f), 0xFFFFFF, (1 - t), 0);
                d.flat(Rt.RING, true, c.add(0, 0.05, 0), new Vec3(0, 1, 0), R0 * (0.2f + t), 0x7A40E0, 0.8f * (1 - t), 0);
                d.voidSphere(c, R0 * 0.35 * (1 - t), 1 - t, 0.3f, 0xFFFFFF);
                for (int i = 0; i < 18; i++) {
                    double a = i * Math.PI * 2 / 18;
                    Vec3 dir = new Vec3(Math.cos(a), 0.6, Math.sin(a));
                    d.voidShard(c.add(dir.scale(R0 * 0.6 * Math.min(1, t * 3))), new Vec3(-Math.cos(a) * 0.3, 1, -Math.sin(a) * 0.3), 1.2f * (1 - t), 0.14f, 1 - t);
                }
            }
            case S2CFx.LUNGE -> {
                Vec3 to = new Vec3(p.a(), p.b(), p.c());
                float w = 1 - t;
                d.voidBillboard(Rt.RIFT, c, 0.6f * w + 0.2f, 1.2f * w + 0.2f, w, 0);
                d.voidBillboard(Rt.RIFT, to, 0.6f * w + 0.2f, 1.2f * w + 0.2f, w, 0);
                d.beam(Rt.STREAK, true, c, to, 0.9f * w, 0x9B6BFF, w);
                d.beam(Rt.STREAK, true, c, to, 0.25f * w, 0xFFFFFF, w);
                for (int i = 0; i < 10; i++) {
                    Vec3 q = c.add(to.subtract(c).scale(i / 10.0));
                    d.voidShard(q, to.subtract(c), 0.5f * w, 0.06f, w * 0.8f);
                }
            }
            case S2CFx.REFORM -> {
                Vec3 at = e1 != null ? e1.getPosition(pt).add(0, 1, 0) : c;
                for (int i = 0; i < 24; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    d.voidShard(at.add(dir.scale(2.5 * (1 - t))), dir.scale(-1), 0.4f, 0.08f, Math.min(1, (1 - t) * 2));
                }
                d.voidSphere(at, 1.1 * (1 - t), 1 - t, 0.5f, 0xFFFFFF);
                d.glowShell(at, 0.5 + t * 3, 0xE8DCFF, 0.8f * (1 - t));
            }
            case S2CFx.DOMAIN_SPEARS -> spears(d, f, pt, t, r);
            case S2CFx.DOMAIN_COLLAPSE -> collapse(d, f, t, r);
            case S2CFx.INHALE -> inhale(d, f, pt, r);
            case S2CFx.DODGE -> {
                Vec3 dir = new Vec3(p.a(), 0, p.b());
                for (int i = 0; i < 4; i++) {
                    float k = Mth.clamp(t * 1.4f - i * 0.12f, 0, 1);
                    Vec3 at = c.subtract(dir.scale(0.4 + i * 0.7)).add(0, -0.05, 0);
                    d.voidBillboard(Rt.BODY_CORE, at, 0.3f, 0.92f, 0.6f * (1 - k), 0);
                }
                d.beam(Rt.STREAK, true, c.subtract(dir.scale(3)), c, 0.6f * (1 - t), 0x9C6BFF, 0.7f * (1 - t));
                d.flat(Rt.RING, true, c.subtract(0, 0.95, 0), new Vec3(0, 1, 0), 0.6f + t * 1.6f, 0xB58CFF, 0.6f * (1 - t), 0);
            }
            case S2CFx.PERFECT_DODGE -> {
                Vec3 at = e1 != null ? e1.getPosition(pt).add(0, 1, 0) : c;
                float k = Mth.clamp(t * 2.2f, 0, 1);
                d.glowShell(at, 0.8 + k * 9, 0xE8F4FF, 0.85f * (1 - t));
                d.flat(Rt.RING, true, at.subtract(0, 0.95, 0), new Vec3(0, 1, 0), 1 + k * 10, 0xFFFFFF, 0.9f * (1 - t), 0);
                // a clock face of crystal ticks: time catching its breath
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 + t * 0.6;
                    Vec3 q = at.add(Math.cos(a) * (2.2 + k), -0.4, Math.sin(a) * (2.2 + k));
                    d.voidShard(q, new Vec3(Math.cos(a), 0.2, Math.sin(a)), 0.6f * (1 - t), 0.06f, 1 - t);
                }
                d.voidBillboard(Rt.BODY_CORE, at.add(0, -0.05, 0), 0.33f, 0.95f, 0.8f * (1 - t), 0);
            }
            case S2CFx.FORGE -> {
                Vec3 at = e1 != null ? e1.getPosition(pt).add(0, 1.2, 0) : c;
                for (int i = 0; i < 20; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    Vec3 from = at.add(dir.scale(2.5 * (1 - t)));
                    d.voidShard(from, dir.scale(-1), 0.3f, 0.05f, Math.min(1, t * 3) * (1 - t));
                }
                d.glowShell(at, 0.4 + t * 1.4, 0xD8C8FF, 0.6f * (1 - t));
            }
            case S2CFx.REALM_ENTER, S2CFx.REALM_EXIT -> realmGate(d, f, pt, t, r);
            case S2CFx.PEEK -> {
                float open = p.a() == 2 ? 1 - t : t < 0.3f ? t / 0.3f : 1 - (t - 0.3f) / 0.7f;
                Vec3 n = d.cam.subtract(c).normalize();
                d.flat(Rt.RING, true, c, n, 0.9f * open + 0.2f, 0xE8DCFF, 0.8f * open, (float) d.time);
                d.flat(Rt.RING, true, c, n, 1.5f * open + 0.2f, 0x7A45E0, 0.5f * open, (float) -d.time);
                d.voidSphere(c, 0.35 * open, open, 0.15f, 0xFFFFFF);
                d.billboard(Rt.GLOW, true, c, 0.25f * open, 0xFFFFFF, open, 0);
            }
            case S2CFx.FORM_OPEN -> formOpen(d, f, pt, t, r);
            case S2CFx.GUARD -> {
                Vec3 at = e1 != null ? e1.getPosition(pt).add(0, 1, 0) : c;
                Vec3 fwd = e1 != null ? Vec3.directionFromRotation(0, e1.getYRot()) : d.fwdVec();
                Vec3 front = at.add(fwd.scale(0.8));
                if (p.a() == 2) {
                    for (int i = 0; i < 3; i++) d.flat(Rt.RING, true, front, fwd, 0.4f + t * (1.6f + i * 0.6f), 0xFFFFFF, (1 - t) * (1 - i * 0.25f), 0);
                    d.billboard(Rt.GLOW, true, front, 1.4f, 0xE8DCFF, (1 - t) * d.near(front, 1), 0);
                } else d.flat(Rt.RING, true, front, fwd, p.a() == 1 ? 0.6f + t * 0.6f : 1.2f - t * 0.8f, 0xB58CFF, 0.8f * (1 - t), 0);
            }
            default -> {}

        }
    }

    /** The five strikes of the abyssal combo. */
    private static void combo(Draw d, Fx f, float pt, float t, Random r) {
        S2CFx p = f.p;
        Vec3 c = f.pos;
        var mc = Minecraft.getInstance();
        Entity me = mc.level.getEntity(p.e1()), tgt = p.e2() >= 0 ? mc.level.getEntity(p.e2()) : null;
        float w = 1 - t;
        switch ((int) p.a()) {
            case 1 -> { // Rift Jab: a tear beside the target, a void arm through it
                Vec3 side = new Vec3(p.b(), 0, p.c());
                Vec3 tear = c.add(side);
                float open = Math.min(1, t * 4);
                d.voidBillboard(Rt.RIFT, tear, 0.35f * open * w + 0.05f, 0.8f * open * w + 0.05f, w, 0);
                Vec3 fist = tear.add(c.subtract(tear).scale(Math.min(1, t * 3)));
                d.beam(Rt.STREAK, true, tear, fist, 0.55f * w, 0x8C55FF, w);
                d.voidShard(fist.subtract(c.subtract(tear).normalize().scale(0.3)), c.subtract(tear), 0.7f * w, 0.22f, w);
                if (t > 0.3f) d.glowShell(c, 0.4 + (t - 0.3) * 2, 0xFFFFFF, 0.8f * w);
            }
            case 2 -> { // Fold Slash: a crescent of crystal
                float yaw = (float) Math.toRadians(p.b());
                Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
                Vec3 origin = c.subtract(fwd.scale(1.5));
                int n = 16;
                float sweep = Math.min(1, t * 3.5f);
                for (int i = 0; i < n * sweep; i++) {
                    double a = -1.1 + 2.2 * i / n;
                    Vec3 dir = fwd.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
                    Vec3 q = origin.add(dir.scale(3.2)).add(0, Math.sin(a) * 1.4, 0);
                    d.voidShard(q, dir, 0.9f * w, 0.1f, w);
                    if (i > 0) {
                        double a0 = -1.1 + 2.2 * (i - 1) / n;
                        Vec3 q0 = origin.add(fwd.scale(Math.cos(a0)).add(right.scale(Math.sin(a0))).scale(3.6)).add(0, Math.sin(a0) * 1.4, 0);
                        d.beam(Rt.STREAK, true, q0, origin.add(dir.scale(3.6)).add(0, Math.sin(a) * 1.4, 0), 0.35f * w, 0xE0D0FF, w);
                    }
                }
            }
            case 3 -> { // Rift Blink: a collapsing tear where you were / where you appear
                float sz = p.b() == 0 ? w : Math.min(1, t * 3) * w;
                d.voidBillboard(Rt.RIFT, c, 0.6f * sz + 0.05f, 1.3f * sz + 0.05f, w, 0);
                d.glowShell(c, 0.8 + t * 1.5, 0x9B6BFF, 0.7f * w);
                if (p.b() == 1 && tgt != null) d.beam(Rt.STREAK, true, c, tgt.getPosition(pt).add(0, tgt.getBbHeight() * 0.6, 0), 0.4f * w, 0xFFFFFF, w);
            }
            case 4 -> { // Void Lances from the ground
                for (int i = 0; i < 7; i++) {
                    double a = i * Math.PI * 2 / 7 + r.nextDouble() * 0.3;
                    double rad = i == 0 ? 0 : 0.9 + r.nextDouble() * 0.8;
                    Vec3 base = c.add(Math.cos(a) * rad, 0, Math.sin(a) * rad);
                    float rise = Math.min(1, t * 5) * (t > 0.6f ? 1 - (t - 0.6f) / 0.4f : 1);
                    d.voidShard(base, new Vec3(Math.cos(a) * -0.15, 1, Math.sin(a) * -0.15), (i == 0 ? 3.2f : 1.8f + r.nextFloat()) * rise, i == 0 ? 0.32f : 0.18f, Math.max(0.1f, rise));
                }
                d.flat(Rt.RING, true, c.add(0, 0.05, 0), new Vec3(0, 1, 0), 1 + t * 3, 0xB48CFF, w, 0);
            }
            case 5 -> { // Event Horizon: crush, then burst
                Vec3 at = tgt != null && t < 0.4f ? tgt.getPosition(pt).add(0, tgt.getBbHeight() * 0.5, 0) : c;
                if (t < 0.38f) {
                    float k = t / 0.38f;
                    d.voidSphere(at, 0.4 + k * 1.2, 1, 0.2f, 0xFFFFFF);
                    d.glowShell(at, 2.6 - k * 1.2, 0xE0D0FF, 0.9f);
                    d.flat(Rt.RING, true, at, new Vec3(0.2, 1, 0.1), 3.5f - k * 2, 0xC8B0FF, 0.9f, (float) d.time * 3);
                    for (int i = 0; i < 16; i++) {
                        Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                        Vec3 from = at.add(dir.scale(4 * (1 - k)));
                        d.beam(Rt.STREAK, true, from, from.add(dir.scale(-0.9)), 0.07f, 0xB48CFF, 0.9f);
                    }
                } else {
                    float k = (t - 0.38f) / 0.62f;
                    d.glowShell(at, 0.5 + k * 5, 0xFFFFFF, 1 - k);
                    d.flat(Rt.RING, true, at, d.fwdVec(), 1 + k * 6, 0xE8DCFF, 1 - k, 0);
                    if (k < 0.15f) d.billboard(Rt.GLOW, true, at, 4, 0xFFFFFF, (1 - k / 0.15f) * d.near(at, 2), 0);
                    for (int i = 0; i < 14; i++) {
                        Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6 + 0.2, r.nextGaussian()).normalize();
                        d.voidShard(at.add(dir.scale(0.5 + k * 4)), dir, 0.8f * (1 - k), 0.12f, 1 - k);
                    }
                }
            }
        }
    }

    /** Beelzebub: a ring of black teeth closes over the target while everything is drawn into the mouth. */
    private static void devour(Draw d, Fx f, float pt, float t, Random r) {
        S2CFx p = f.p;
        var mc = Minecraft.getInstance();
        Entity e1 = p.e1() >= 0 ? mc.level.getEntity(p.e1()) : null;
        Vec3 c = e1 != null && t < 0.6f ? e1.getPosition(pt).add(0, e1.getBbHeight() / 2, 0) : f.pos;
        float size = p.a();
        float open = t < 0.35f ? t / 0.35f : t < 0.6f ? 1 : 1 - (t - 0.6f) / 0.4f;
        float close = t < 0.4f ? 0 : Math.min(1, (t - 0.4f) / 0.2f);
        Vec3 n = d.cam.subtract(c).normalize();
        Vec3[] b = Draw.basis(n);
        int teeth = 14;
        float mouthR = size * (1.6f - close * 1.2f);
        d.voidSphere(c, Math.max(0.05, size * 0.9 * open), 1f, 0.2f, 0xFFFFFF);
        for (int i = 0; i < teeth; i++) {
            double a = i * Math.PI * 2 / teeth + d.time * 0.3;
            Vec3 dir = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            Vec3 base = c.add(dir.scale(mouthR)).add(n.scale(0.1));
            d.voidShard(base, dir.scale(-1).add(n.scale(0.4)), size * 0.7f * open, size * 0.12f, open);
        }
        for (int i = 0; i < 20; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            float k = (float) ((t * 3 + r.nextDouble()) % 1.0);
            Vec3 from = c.add(dir.scale(size * 4 * (1 - k)));
            d.beam(Rt.STREAK, true, from, from.add(dir.scale(-0.9)), 0.07f, 0xFF4A9A, 0.6f * open);
        }
        d.glowShell(c, size * 1.7, 0xFF4A9A, 0.5f * open);
    }

    // ------------------------------------------------------------------------------------------------- new effects
    /** Sure-hit spears: a sigil opens under the victim, then crystal erupts through it from below. */
    private static void spears(Draw d, Fx f, float pt, float t, Random r) {
        S2CFx p = f.p;
        var mc = Minecraft.getInstance();
        Entity v = mc.level.getEntity(p.e1());
        Vec3 base = v != null && t < 0.5f ? v.getPosition(pt) : f.pos;
        float size = Math.max(1, p.a());
        float age = t * f.life;
        float sig = Mth.clamp(age / 6, 0, 1) * (1 - Mth.clamp((age - 22) / 8, 0, 1));
        d.flat(Rt.RING, true, base.add(0, 0.05, 0), new Vec3(0, 1, 0), size * 1.2f * sig, 0xE8DCFF, 0.9f * sig, (float) d.time * 2);
        d.flat(Rt.RING, true, base.add(0, 0.06, 0), new Vec3(0, 1, 0), size * 0.8f * sig, 0x8C55FF, 0.8f * sig, (float) -d.time * 3);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + d.time;
            Vec3 q0 = base.add(Math.cos(a) * size * 1.1 * sig, 0.07, Math.sin(a) * size * 1.1 * sig), q1 = base.add(Math.cos(a + Math.PI * 2 / 3) * size * 1.1 * sig, 0.07, Math.sin(a + Math.PI * 2 / 3) * size * 1.1 * sig);
            d.beam(Rt.STREAK, true, q0, q1, 0.05f, 0xC8B0FF, 0.7f * sig);
        }
        float rise = Mth.clamp((age - 6) / 2.5f, 0, 1) * (1 - Mth.clamp((age - 20) / 9, 0, 1));
        if (rise > 0) {
            int n = p.b() == 1 ? 9 : 5;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + r.nextDouble() * 0.4;
                double rad = i == 0 ? 0 : size * 0.35 * (0.5 + r.nextDouble());
                Vec3 b = base.add(Math.cos(a) * rad, 0, Math.sin(a) * rad);
                Vec3 dir = new Vec3(-Math.cos(a) * rad * 0.25, 1, -Math.sin(a) * rad * 0.25);
                d.voidShard(b, dir, (i == 0 ? 3.2f : 1.8f + r.nextFloat()) * size * 0.6f * rise, (i == 0 ? 0.24f : 0.13f) * size * 0.6f, rise);
            }
            if (age < 10) d.billboard(Rt.GLOW, true, base.add(0, 1, 0), size * 2, 0xFFFFFF, Math.max(0, 1 - (age - 6) / 4) * d.near(base, 1), 0);
        }
    }

    /** The domain implodes: the shell falls inward, everything streams to the centre, then one white detonation. */
    private static void collapse(Draw d, Fx f, float t, Random r) {
        Vec3 c = f.pos;
        float R0 = Math.max(4, f.p.a());
        float age = t * f.life;
        if (age < 32) {
            float k = age / 32, e = k * k;
            float rad = R0 * (1 - e) + 1;
            d.glowShell(c, rad, 0xE8DCFF, 0.9f);
            d.glowShell(c, rad * 0.97, 0x7A45E0, 0.6f);
            d.flat(Rt.RING, true, c, new Vec3(0.1, 1, 0.05), rad * 1.1f, 0xFFFFFF, 0.8f, (float) d.time * (2 + k * 8));
            for (int i = 0; i < 60; i++) {
                Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                float ph = (float) ((age * 0.06 + r.nextDouble()) % 1.0);
                Vec3 from = c.add(dir.scale(rad * (1 - ph)));
                d.beam(Rt.STREAK, true, from, from.add(dir.scale(-R0 * 0.08 - 0.4)), 0.09f, ph > 0.6f ? 0xFFFFFF : 0x9C6BFF, 0.85f);
            }
            d.blackHole(c.add(0, 6, 0).add(d.fwdVec().multiply(1, 0, 1).normalize().scale(16)), 1.5f + k * 9f, true, Math.min(1, k * 3), 0);
        } else {
            float k = Mth.clamp((age - 32) / 14, 0, 1);
            if (k < 0.35f) d.blackHole(c.add(0, 6, 0).add(d.fwdVec().multiply(1, 0, 1).normalize().scale(16)), 10.5f * (1 - k / 0.35f), true, 1, 0);
            if (k < 0.2f) d.billboard(Rt.GLOW, true, c, R0 * 0.6f, 0xFFFFFF, (1 - k / 0.2f) * 0.6f * d.near(c, 6), 0);
            d.glowShell(c, 2 + k * R0 * 1.3, 0xFFFFFF, 1 - k);
            d.flat(Rt.RING, true, c, new Vec3(0, 1, 0), 2 + k * R0 * 1.6f, 0xF0E8FF, 1 - k, 0);
            d.flat(Rt.RING, true, c, d.fwdVec(), 2 + k * R0, 0x9C6BFF, 0.8f * (1 - k), 0);
            for (int i = 0; i < 40; i++) {
                Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                d.voidShard(c.add(dir.scale(2 + k * R0)), dir, 1.4f * (1 - k), 0.14f, 1 - k);
            }
        }
    }

    /** Beelzebub inhales: the maw over the shoulder gapes and the air itself pours in. */
    private static void inhale(Draw d, Fx f, float pt, Random r) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(f.p.e1());
        Vec3 maw = f.pos;
        Vec3 look = d.fwdVec();
        if (e != null) { look = e.getViewVector(pt); maw = e.getEyePosition(pt).add(look.scale(1.6)).add(0, 0.2, 0); }
        float age = f.age + pt, life = f.life - 8;
        float open = Mth.clamp(age / 6, 0, 1) * (1 - Mth.clamp((age - life) / 8, 0, 1));
        float size = 1.3f * open;
        Vec3[] b = Draw.basis(look);
        d.voidSphere(maw, size * 0.75, open, 0.2f, 0xFFFFFF);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16 + d.time * 0.5;
            Vec3 dir = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            d.voidShard(maw.add(dir.scale(size * 1.25)), dir.scale(-1).add(look.scale(-0.5)), size * 0.6f, size * 0.09f, open);
        }
        d.flat(Rt.RING, true, maw, look, size * 1.6f, 0xFF4A9A, 0.6f * open, (float) d.time * 2);
        d.glowShell(maw, size * 1.6, 0xFF4A9A, 0.5f * open);
        for (int i = 0; i < 50; i++) {
            Vec3 o = look.scale(2 + r.nextDouble() * 12).add(b[0].scale(r.nextGaussian() * 3)).add(b[1].scale(r.nextGaussian() * 3));
            float ph = (float) ((age * 0.08 + r.nextDouble()) % 1.0);
            Vec3 from = maw.add(o.scale(1 - ph));
            Vec3 to = from.add(maw.subtract(from).normalize().scale(0.8 + ph));
            d.beam(Rt.STREAK, true, from, to, 0.06f, ph > 0.7f ? 0xFFFFFF : 0xFF7AB8, 0.75f * open * ph);
        }
    }

    /** The great gate to the Realm: a rift as tall as a house opens behind you and swallows you, or spits you out. */
    private static void realmGate(Draw d, Fx f, float pt, float t, Random r) {
        boolean enter = f.p.type() == S2CFx.REALM_ENTER, arrive = f.p.a() == 1;
        Vec3 c = f.pos;
        float open = t < 0.3f ? (float) Math.pow(t / 0.3f, 0.6) : 1 - (float) Math.pow((t - 0.3f) / 0.7f, 2);
        d.voidBillboard(Rt.RIFT, c.add(0, 0.6, 0), 1.2f * open + 0.02f, 2.6f * open + 0.02f, open, 0);
        d.billboard(Rt.GLOW, true, c.add(0, 0.6, 0), 3.2f * open, enter ? 0x7A45E0 : 0xE8DCFF, 0.6f * open, 0);
        d.flat(Rt.RING, true, c.subtract(0, 0.95, 0), new Vec3(0, 1, 0), 0.6f + t * 5, enter ? 0x9C6BFF : 0xFFFFFF, 0.8f * (1 - t), 0);
        boolean inward = enter != arrive;
        for (int i = 0; i < 30; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            float ph = (float) ((t * 2.5 + r.nextDouble()) % 1.0);
            Vec3 from = c.add(dir.scale((inward ? 1 - ph : ph) * 4));
            d.beam(Rt.STREAK, true, from, from.add(dir.scale(inward ? -0.6 : 0.6)), 0.06f, 0xC8B0FF, open);
        }
        if (arrive && t < 0.2f) d.glowShell(c, 0.5 + t * 12, 0xFFFFFF, 1 - t / 0.2f);
    }

    /** A form opens: its sigil rises around you in its colour; closing, the sigil breaks into shards. */
    private static void formOpen(Draw d, Fx f, float pt, float t, Random r) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(f.p.e1());
        Vec3 feet = e != null ? e.getPosition(pt) : f.pos.subtract(0, 1, 0);
        Ability a = Ability.byIndex((int) f.p.a());
        int col = a != null ? a.color : 0x9B6BFF;
        if (f.p.b() == 1) {
            float k = (float) (1 - Math.pow(1 - Math.min(1, t * 1.6), 3));
            float out = 1 - Mth.clamp((t - 0.55f) / 0.45f, 0, 1);
            for (int i = 0; i < 3; i++) {
                float y = 0.05f + k * (0.3f + i * 0.75f);
                d.flat(Rt.RING, true, feet.add(0, y, 0), new Vec3(0, 1, 0), 1.6f - i * 0.35f, i == 1 ? 0xFFFFFF : col, 0.85f * out, (float) d.time * (i % 2 == 0 ? 2 : -2.5f));
            }
            for (int i = 0; i < 8; i++) {
                double an = i * Math.PI / 4 + t * 2;
                Vec3 q = feet.add(Math.cos(an) * 1.5, 0.1 + k * 1.8 * ((i % 3) / 2.0), Math.sin(an) * 1.5);
                d.voidShard(q, new Vec3(-Math.cos(an), 0.4, -Math.sin(an)), 0.35f * out, 0.05f, out);
            }
            d.glowShell(feet.add(0, 1, 0), 0.6 + k * 1.2, col, 0.5f * out);
        } else {
            for (int i = 0; i < 18; i++) {
                Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5 + 0.4, r.nextGaussian()).normalize();
                d.voidShard(feet.add(0, 1, 0).add(dir.scale(0.6 + t * 2.4)), dir, 0.35f * (1 - t), 0.05f, 1 - t);
            }
            d.flat(Rt.RING, true, feet.add(0, 0.05, 0), new Vec3(0, 1, 0), 1.6f + t * 2, col, 0.7f * (1 - t), 0);
        }
    }

    /** What an open form looks like on its owner, for everyone to see. */
    private static void auras(Draw d) {
        var mc = Minecraft.getInstance();
        boolean fp = mc.options.getCameraType().isFirstPerson();
        for (Entity e : mc.level.entitiesForRendering()) {
            Ability a = Ability.byIndex(ClientState.formOf(e.getId()));
            if (a == null || e.distanceToSqr(mc.player) > 64 * 64) continue;
            boolean self = e == mc.player && fp;
            Vec3 pos = e.getPosition(d.pt);
            float yaw = e instanceof LivingEntity le ? Mth.rotLerp(d.pt, le.yBodyRotO, le.yBodyRot) : e.getYRot();
            Vec3 fwd = Vec3.directionFromRotation(0, yaw), back = fwd.scale(-1), side = fwd.cross(new Vec3(0, 1, 0));
            Random r = new Random(e.getId());
            double time = d.time;
            switch (a) {
                case SPORES -> {
                    for (int i = 0; i < 14; i++) {
                        double an = time * (0.6 + r.nextDouble() * 0.6) * (i % 2 == 0 ? 1 : -1) + r.nextDouble() * 6;
                        double rad = 0.8 + r.nextDouble() * 0.6;
                        Vec3 q = pos.add(Math.cos(an) * rad, 0.3 + r.nextDouble() * 1.6 + Math.sin(time * 2 + i) * 0.15, Math.sin(an) * rad);
                        // in first person the motes orbit right past your eye: fade them before they smear over the view
                        float nf = self ? d.near(q, 1.6f) : 1f;
                        if (nf < 0.02f) continue;
                        d.billboard(Rt.CORE, false, q, 0.06f, 0x07040C, 0.9f * nf, 0);
                        d.billboard(Rt.GLOW, true, q, 0.16f, 0xD06AFF, 0.5f * nf, 0);
                    }
                }
                case AUTHORITY -> {
                    if (self) break;
                    Vec3 q = pos.add(back.scale(0.9)).add(0, 1.5, 0);
                    float br = 0.9f + 0.1f * (float) Math.sin(time * 2);
                    d.voidBillboard(Rt.RIFT, q, 0.55f * br, 1.5f * br, 0.95f, 0);
                    d.billboard(Rt.GLOW, true, q, 2.2f, 0x7FA8FF, 0.35f, 0);
                }
                case BEELZEBUB -> {
                    if (self) break;
                    Vec3 q = pos.add(back.scale(0.7)).add(side.scale(0.45)).add(0, 2.0, 0);
                    Vec3[] b = Draw.basis(fwd);
                    d.voidSphere(q, 0.32, 1, 0.2f, 0xFFFFFF);
                    for (int i = 0; i < 10; i++) {
                        double an = i * Math.PI / 5 + time * 0.4;
                        Vec3 dir = b[0].scale(Math.cos(an)).add(b[1].scale(Math.sin(an)));
                        d.voidShard(q.add(dir.scale(0.45)), dir.scale(-1).add(fwd.scale(0.3)), 0.28f, 0.04f, 1);
                    }
                    d.glowShell(q, 0.55, 0xFF4A9A, 0.5f);
                }
                case BODIES -> {
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 q = pos.add(side.scale(0.9 * s)).add(back.scale(0.5)).add(0, 0.95, 0);
                        d.voidBillboard(Rt.BODY_CORE, q, 0.3f, 0.9f, 0.25f + 0.1f * (float) Math.sin(time * 2 + s), 0);
                    }
                }
                case DOMAIN -> {
                    if (self) break;
                    Vec3 q = pos.add(0, e.getBbHeight() + 0.45, 0);
                    d.flat(Rt.RING, true, q, new Vec3(0, 1, 0), 0.45f, 0x9B6BFF, 0.9f, (float) time);
                    d.flat(Rt.RING, true, q, new Vec3(0, 1, 0), 0.3f, 0xFFFFFF, 0.7f, (float) -time * 1.5f);
                }
                default -> {}
            }
        }
    }

    /** In the Realm you see the souls of the reflected world, through walls: a white spark with a blue halo. */
    private static void souls(Draw d) {
        if (!ClientState.inRealm() || ClientState.soulXyz.length == 0) return;
        float[] xyz = ClientState.soulXyz;
        var g = d.buf.getBuffer(Rt.xray(Rt.GLOW));
        for (int i = 0; i + 2 < xyz.length; i += 3) {
            Vec3 p = new Vec3(xyz[i], xyz[i + 1] + 1.1, xyz[i + 2]);
            double dist = p.distanceTo(d.cam);
            float s = (float) (0.35 + dist * 0.012);
            float pulse = 0.75f + 0.25f * (float) Math.sin(d.time * 3 + i);
            xquad(d, g, p, s * 2.2f, 0x7FA8FF, 0.35f * pulse);
            xquad(d, g, p, s, 0xF0F4FF, 0.9f * pulse);
        }
    }

    private static void xquad(Draw d, com.mojang.blaze3d.vertex.VertexConsumer vc, Vec3 p, float size, int rgb, float a) {
        float lx = d.left.x * size, ly = d.left.y * size, lz = d.left.z * size, ux = d.up.x * size, uy = d.up.y * size, uz = d.up.z * size;
        d.sv(vc, p.x - lx - ux, p.y - ly - uy, p.z - lz - uz, rgb, a, 0, 1);
        d.sv(vc, p.x + lx - ux, p.y + ly - uy, p.z + lz - uz, rgb, a, 1, 1);
        d.sv(vc, p.x + lx + ux, p.y + ly + uy, p.z + lz + uz, rgb, a, 1, 0);
        d.sv(vc, p.x - lx + ux, p.y - ly + uy, p.z - lz + uz, rgb, a, 0, 0);
    }

    public static void clear() { ACTIVE.clear(); MOTES.clear(); com.andy.abyss.client.InfectionClient.clear(); }
    private FxManager() {}
}
