package com.andy.abyss.client.render;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.net.S2CFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * The particles of the one-shot effects, spawned once per tick (FxManager draws every frame, so it can't spawn):
 * real dust (flecks of black crystal, ash, ink smoke, a few cold sparks) instead of little galaxy shards.
 * Also the Realm's ambience: slow ash drifting through the dark and a rare far glint.
 */
final class FxEmit {
    private static final Random R = new Random();
    private static final int D_BLACK = Dust.BLACK, D_CHAR = Dust.CHAR, D_INDIGO = Dust.INDIGO;

    static void tick(FxManager.Fx f) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        S2CFx p = f.p;
        Vec3 c = f.pos;
        if (c.distanceToSqr(mc.player.position()) > 96 * 96) return;
        float dens = Config.fxDensity.get().floatValue();
        int age = f.age;
        float t = age / (float) f.life;
        Entity e1 = p.e1() >= 0 ? mc.level.getEntity(p.e1()) : null;
        Vec3 body = e1 != null ? e1.position().add(0, e1.getBbHeight() * 0.5, 0) : c;
        switch (p.type()) {
            case S2CFx.SPREAD -> { if (age == 1) Dust.burst(body, (int) (14 * dens), 0.25, 0.08f, false); }
            case S2CFx.IMPLODE -> {
                Vec3 at = p.b() == 1 ? body : c;
                float R0 = Math.max(1.5f, p.a());
                if (age % 2 == 0) for (int i = 0; i < 4 * dens; i++)
                    Dust.add(R.nextFloat() < 0.6f ? Dust.Kind.ASH : Dust.Kind.FLECK, at.add(Dust.rnd().scale(R0 * (1.4 + R.nextDouble()))), Vec3.ZERO, 26, 0.08f + R.nextFloat() * 0.06f, R.nextFloat() < 0.7f ? D_BLACK : D_CHAR)
                            .pull(at, 0.035f).drag(0.9f).spin(0.2f);
                if (age == f.life - 1) Dust.burst(at, (int) (30 * dens), 0.45, 0.1f, true);
            }
            case S2CFx.NUKE -> {
                Vec3 at = p.b() == 1 ? body : c;
                if (age == 3) { Dust.burst(at, (int) (60 * dens), 0.9, 0.16f, true); Dust.smoke(at, (int) (14 * dens), p.a() * 0.6, 1.4f, 50); }
                if (age > 40 && age < 70 && age % 4 == 0) Dust.smoke(at, (int) (3 * dens), p.a() * 0.7, 1.2f, 40);
            }
            case S2CFx.DEVOUR -> {
                float size = p.a();
                if (age < f.life * 0.6 && age % 2 == 0) for (int i = 0; i < 3 * dens; i++)
                    Dust.add(Dust.Kind.ASH, body.add(Dust.rnd().scale(size * 3.5)), Vec3.ZERO, 30, 0.08f + R.nextFloat() * 0.06f, R.nextFloat() < 0.5f ? 0x1A0610 : D_BLACK)
                            .pull(body, 0.04f).drag(0.9f).spin(0.15f);
            }
            case S2CFx.TRANSPORT -> {
                if (t < 0.5f && age % 2 == 0) Dust.smoke(body.subtract(0, 0.6, 0), (int) (3 * dens), 0.8, 0.9f, 30);
                if (age == (int) (f.life * 0.45f)) Dust.burst(c, (int) (18 * dens), 0.3, 0.09f, false);
            }
            case S2CFx.DISINTEGRATE -> {
                if (age < f.life * 0.7) for (int i = 0; i < 4 * dens; i++) {
                    Vec3 o = new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).scale(p.a() * 0.35);
                    Dust.add(R.nextFloat() < 0.8f ? Dust.Kind.ASH : Dust.Kind.FLECK, c.add(o), new Vec3(R.nextGaussian() * 0.01, 0.02 + R.nextDouble() * 0.03, R.nextGaussian() * 0.01), 40, 0.06f + R.nextFloat() * 0.06f, R.nextFloat() < 0.6f ? D_BLACK : D_CHAR)
                            .gravity(-0.0015f).drag(0.96f).spin(0.1f);
                }
            }
            case S2CFx.RIFT_OPEN, S2CFx.STEP -> {
                if (age == 1) { Dust.smoke(c, (int) ((p.type() == S2CFx.STEP ? 4 : 8) * dens), 0.7, 0.8f, 34); Dust.burst(c, (int) (10 * dens), 0.2, 0.07f, true); }
            }
            case S2CFx.SWAP -> { if (age % 3 == 0 && t < 0.6f) ash(c.add(0, -0.9, 0), 0.8, (int) (4 * dens), 0.05); }
            case S2CFx.NEGATE -> { if (age == 1) Dust.burst(body, (int) (16 * dens), 0.35, 0.07f, true); }
            case S2CFx.CLONE_SPAWN -> {
                if (t < 0.7f) for (int i = 0; i < 3 * dens; i++)
                    Dust.add(R.nextFloat() < 0.5f ? Dust.Kind.FLECK : Dust.Kind.SLIVER, c.add(Dust.rnd().scale(2.6)), Vec3.ZERO, 24, 0.09f, R.nextFloat() < 0.7f ? D_BLACK : D_INDIGO)
                            .pull(c, 0.04f).drag(0.88f).spin(0.3f);
                if (age == 2) Dust.smoke(c.subtract(0, 0.8, 0), (int) (6 * dens), 0.6, 0.9f, 30);
            }
            case S2CFx.CLONE_DEATH -> {
                if (age == 1) { Dust.burst(c, (int) (36 * dens), 0.35, 0.11f, true); Dust.smoke(c, (int) (8 * dens), 0.7, 1f, 40); }
            }
            case S2CFx.CHARGE -> {
                Vec3 at = e1 != null ? e1.position().add(0, 1, 0) : c;
                if (age % 3 == 0) Dust.add(Dust.Kind.FLECK, at.add(Dust.rnd().scale(2.5 + R.nextDouble() * 2)), Vec3.ZERO, 22, 0.07f, D_BLACK).pull(at, 0.03f).drag(0.88f).spin(0.3f);
            }
            case S2CFx.AWAKEN -> {
                Vec3 at = e1 != null ? e1.position() : c.subtract(0, 1, 0);
                if (age == 2) { Dust.burst(at.add(0, 1, 0), (int) (50 * dens), 0.6, 0.13f, true); }
                if (age < 60 && age % 2 == 0) Dust.smoke(at.add(0, 0.2, 0), (int) (3 * dens), 2.5, 1.3f, 60);
                if (age % 3 == 0) ash(at, 3.5, (int) (4 * dens), 0.06);
            }
            case S2CFx.DOMAIN_CAST -> {
                if (age == (int) (f.life * 0.22f)) { Dust.burst(c, (int) (90 * dens), 1.4, 0.18f, true); Dust.smoke(c, (int) (16 * dens), 4, 2f, 60); }
            }
            case S2CFx.DOMAIN_PULSE -> { if (age == 1) Dust.smoke(c.add(0, -Math.min(p.a(), 3) + 0.3, 0), (int) (8 * dens), 2, 1.2f, 30); }
            case S2CFx.BEAM -> {
                Vec3 dir = Vec3.directionFromRotation(p.c(), p.b());
                if (t < 0.6f) for (int i = 0; i < 3 * dens; i++) {
                    Vec3 q = c.add(dir.scale(R.nextDouble() * p.a() * Math.min(1, t * 5)));
                    Dust.add(R.nextFloat() < 0.7f ? Dust.Kind.ASH : Dust.Kind.EMBER, q.add(Dust.rnd().scale(0.5)), Dust.rnd().scale(0.03), 22, R.nextFloat() < 0.7f ? 0.08f : 0.04f, R.nextFloat() < 0.7f ? D_BLACK : 0xB4BEEA).drag(0.92f).spin(0.1f);
                }
            }
            case S2CFx.PLUNGE -> {
                if (age == (int) (f.life * 0.25f)) { Dust.burst(c.add(0, 0.3, 0), (int) (50 * dens), 0.7, 0.14f, true); Dust.smoke(c, (int) (12 * dens), p.a() * 0.5, 1.4f, 45); }
            }
            case S2CFx.LUNGE -> {
                Vec3 to = new Vec3(p.a(), p.b(), p.c());
                if (age == 1) for (int i = 0; i < 18 * dens; i++) {
                    Vec3 q = c.add(to.subtract(c).scale(R.nextDouble()));
                    Dust.add(Dust.Kind.SLIVER, q, to.subtract(c).normalize().scale(0.05).add(Dust.rnd().scale(0.02)), 14 + R.nextInt(8), 0.16f, R.nextFloat() < 0.7f ? D_BLACK : D_INDIGO).drag(0.9f).spin(0.3f);
                }
            }
            case S2CFx.REFORM -> {
                Vec3 at = e1 != null ? e1.position().add(0, 1, 0) : c;
                if (t < 0.6f) for (int i = 0; i < 4 * dens; i++)
                    Dust.add(R.nextFloat() < 0.5f ? Dust.Kind.ASH : Dust.Kind.FLECK, at.add(Dust.rnd().scale(2.4 + R.nextDouble())), Vec3.ZERO, 24, 0.09f, R.nextFloat() < 0.7f ? D_BLACK : D_CHAR)
                            .pull(at, 0.045f).drag(0.88f).spin(0.25f);
            }
            case S2CFx.DOMAIN_COLLAPSE -> {
                if (age == 33) { Dust.burst(c, (int) (80 * dens), 1.2, 0.18f, true); Dust.smoke(c, (int) (14 * dens), 3, 2f, 55); }
            }
            case S2CFx.FORGE -> {
                Vec3 at = e1 != null ? e1.position().add(0, 1.2, 0) : c;
                if (t < 0.6f) for (int i = 0; i < 2 * dens; i++)
                    Dust.add(Dust.Kind.SLIVER, at.add(Dust.rnd().scale(2.2)), Vec3.ZERO, 18, 0.12f, R.nextFloat() < 0.7f ? D_BLACK : D_INDIGO).pull(at, 0.05f).drag(0.86f).spin(0.4f);
            }
            case S2CFx.REALM_ENTER, S2CFx.REALM_EXIT -> {
                boolean inward = (p.type() == S2CFx.REALM_ENTER) != (p.a() == 1);
                Vec3 g = c.add(0, 0.6, 0);
                if (t < 0.7f && age % 2 == 0) {
                    for (int i = 0; i < 3 * dens; i++) {
                        Vec3 o = Dust.rnd().scale(inward ? 3.5 : 0.4);
                        var q = Dust.add(Dust.Kind.ASH, g.add(o), inward ? Vec3.ZERO : o.normalize().scale(0.08), 30, 0.09f, R.nextFloat() < 0.6f ? D_BLACK : D_CHAR).drag(0.92f).spin(0.15f);
                        if (inward) q.pull(g, 0.035f);
                    }
                    Dust.smoke(g.subtract(0, 1.4, 0), (int) (2 * dens), 1.0, 1f, 34);
                }
            }
            case S2CFx.FORM_OPEN -> {
                Vec3 feet = e1 != null ? e1.position() : c.subtract(0, 1, 0);
                if (p.b() == 1) { if (age % 2 == 0 && t < 0.6f) ash(feet, 1.4, (int) (3 * dens), 0.05); }
                else if (age == 1) Dust.burst(feet.add(0, 1, 0), (int) (22 * dens), 0.3, 0.08f, false);
            }
            case S2CFx.COMBO -> {
                int step = (int) p.a();
                if (step == 1 && age == 4) Dust.burst(c, (int) (12 * dens), 0.3, 0.07f, true);
                if (step == 3 && age == 1) Dust.smoke(c, (int) (5 * dens), 0.6, 0.8f, 26);
                if (step == 4 && age == 2) { Dust.burst(c.add(0, 0.3, 0), (int) (20 * dens), 0.4, 0.1f, false); Dust.smoke(c, (int) (5 * dens), 1.2, 1f, 34); }
                if (step == 5 && age == (int) (f.life * 0.38f)) { Dust.burst(c, (int) (40 * dens), 0.7, 0.12f, true); Dust.smoke(c, (int) (6 * dens), 1.2, 1.1f, 36); }
            }
            case S2CFx.DOMAIN_SPEARS -> { if (age == 7) Dust.burst(c.add(0, 0.4, 0), (int) (24 * dens), 0.45, 0.1f, false); }
            default -> {}
        }
    }

    /** Ash lifting off the ground around a point. */
    private static void ash(Vec3 c, double rad, int n, double up) {
        for (int i = 0; i < n; i++) {
            double a = R.nextDouble() * Math.PI * 2, r = Math.sqrt(R.nextDouble()) * rad;
            Dust.add(Dust.Kind.ASH, c.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), new Vec3(R.nextGaussian() * 0.01, up * (0.5 + R.nextDouble()), R.nextGaussian() * 0.01), 40 + R.nextInt(20), 0.07f + R.nextFloat() * 0.06f, R.nextFloat() < 0.6f ? D_BLACK : D_CHAR)
                    .gravity(-0.0008f).drag(0.96f).spin(R.nextFloat() * 0.1f - 0.05f);
        }
    }

    /** The Realm: slow ash drifting down through the dark, and now and then a far cold glint. */
    static void ambient() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(com.andy.abyss.Abyss.id("the_abyss"))) return;
        float dens = Config.fxDensity.get().floatValue();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (ClientState.ticks % 2 == 0) for (int i = 0; i < 2 * dens; i++) {
            Vec3 o = new Vec3(R.nextGaussian() * 12, 4 + R.nextDouble() * 8, R.nextGaussian() * 12);
            Dust.add(Dust.Kind.ASH, cam.add(o), new Vec3(0.006 + R.nextGaussian() * 0.003, -0.012 - R.nextDouble() * 0.01, R.nextGaussian() * 0.003), 160 + R.nextInt(80), 0.05f + R.nextFloat() * 0.05f, R.nextFloat() < 0.5f ? D_CHAR : 0x22202E)
                    .drag(0.995f).spin(R.nextFloat() * 0.04f - 0.02f).alpha(0.8f);
        }
        if (R.nextFloat() < 0.04f * dens) {
            Vec3 o = Dust.rnd().scale(28 + R.nextDouble() * 30);
            Dust.add(Dust.Kind.EMBER, cam.add(o.x, Math.abs(o.y) * 0.4 - 4, o.z), new Vec3(0, 0.002, 0), 70 + R.nextInt(60), 0.12f + R.nextFloat() * 0.1f, R.nextFloat() < 0.8f ? 0x8E9CD0 : 0x8C7CC8).drag(1f).alpha(0.5f);
        }
    }

    private FxEmit() {}
}
