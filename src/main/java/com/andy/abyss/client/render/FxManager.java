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
import net.minecraft.world.entity.Entity;
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
            case S2CFx.NUKE -> 50; case S2CFx.IMPLODE -> 14; case S2CFx.DEVOUR -> 26; case S2CFx.TRANSPORT -> 22; case S2CFx.DISINTEGRATE -> 30;
            case S2CFx.RIFT_OPEN -> 16; case S2CFx.SWAP -> 18; case S2CFx.NEGATE -> 10; case S2CFx.CLONE_SPAWN -> 22; case S2CFx.CLONE_DEATH -> 26;
            case S2CFx.TRIANGLE_END -> 24; case S2CFx.STEP -> 14; case S2CFx.CHARGE -> 10; case S2CFx.VOLLEY -> 6; case S2CFx.AWAKEN -> 70;
            case S2CFx.DOMAIN_CAST -> 30; case S2CFx.SPREAD -> 14; case S2CFx.DOMAIN_PULSE -> 30; case S2CFx.COMBO -> p.a() == 5 ? 22 : p.a() == 4 ? 16 : 12;
            case S2CFx.BEAM -> 16; case S2CFx.PLUNGE -> 24; case S2CFx.LUNGE -> 12; case S2CFx.REFORM -> 30; default -> 20;
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
            default -> {}
        }
    }

    public static void tick() {
        ACTIVE.removeIf(f -> ++f.age > f.life);
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

    public static void render(PoseStack ps, Camera cam, float pt) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FxBuffers buf = new FxBuffers();
        double time = (mc.level.getGameTime() + pt) / 20.0;
        Draw d = new Draw(ps, cam, pt, buf, time);
        try {
            for (S2CWorld.Dom dom : ClientState.domains) domain(d, dom);
            for (S2CWorld.Clash c : ClientState.clashes) clash(d, c);
            SporeClient.render(d);
            for (Mote m : MOTES) {
                float a = 1 - m.age / (float) m.life;
                d.billboard(Rt.CORE, false, m.p, 0.05f, 0x07040C, 0.8f * a, 0);
                d.billboard(Rt.GLOW, true, m.p, 0.12f, 0x7A45E0, 0.35f * a, 0);
            }
            for (Fx f : ACTIVE) draw(d, f, pt);
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
                float rad = p.a() * (1 - t);
                d.glowShell(c, Math.max(0.2, rad), 0xC0A0FF, 0.9f);
                for (int i = 0; i < 40; i++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    Vec3 from = c.add(dir.scale(p.a() * 1.5 * (1 - t)));
                    d.beam(Rt.STREAK, true, from, from.add(dir.scale(-1.2)), 0.08f, 0x9C6BFF, 0.8f);
                }
                d.voidSphere(c, 0.3 + t * 0.9, 1f, 0.1f, 0xFFFFFF);
            }
            case S2CFx.NUKE -> {
                boolean inside = p.b() == 1;
                float R0 = p.a();
                float grow = Math.min(1f, t / 0.15f), shrink = t < 0.45f ? 1 : 1 - (t - 0.45f) / 0.55f;
                Vec3 at = inside && e1 != null ? e1.getPosition(pt).add(0, e1.getBbHeight() * 0.5, 0) : c;
                d.voidSphere(at, R0 * grow * shrink * (inside ? 0.7 : 1), 1f, 0.15f, 0xFFFFFF);
                d.glowShell(at, R0 * grow * 1.05, 0xD8C8FF, 0.9f * shrink);
                float ring = R0 * (0.5f + t * 2.5f);
                d.flat(Rt.RING, true, at.subtract(0, Math.min(R0 * 0.5, 2), 0), new Vec3(0, 1, 0), ring, 0xF0E8FF, 0.9f * (1 - t), 0);
                d.flat(Rt.RING, true, at, d.fwdVec(), ring * 0.8f, 0xB080FF, 0.6f * (1 - t), 0);
                if (t < 0.12f) d.billboard(Rt.GLOW, true, at, R0 * 3, 0xFFFFFF, (1 - t / 0.12f) * d.near(at, R0), 0);
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
                float rad = 1 + t * Math.min(p.a(), 60);
                d.glowShell(c, rad, 0xE0D0FF, 0.9f * (1 - t));
                d.flat(Rt.RING, true, c.subtract(0, 0.9, 0), new Vec3(0, 1, 0), rad * 1.1f, 0xFFFFFF, 0.8f * (1 - t), 0);
                if (t < 0.2f) d.billboard(Rt.GLOW, true, c, 8, 0x2A1048, (1 - t / 0.2f) * d.near(c, 4), 0);
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

    public static void clear() { ACTIVE.clear(); MOTES.clear(); }
    private FxManager() {}
}
