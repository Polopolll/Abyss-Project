package com.andy.abyss.client.render;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Annihilation on screen. Beams come from a rift above the caster's right shoulder (seen along their side in first
 * person), orbs are slow dense knots of darkness that drink in ash as they fly, the pressure is the ground buckling,
 * and the nuke is the slow part: light drains toward a black core in front of you, it launches, the world goes
 * silent for a breath, and then the abyss opens where it landed.
 */
public final class AnniFx {
    static final class E { int type; Vec3 pos; float a, b, c; int e1, e2; long born; int life; long seed; Vec3 vel; boolean dead; }
    private static final List<E> ALL = new ArrayList<>();
    /** Per caster: the end point of their sustained beam and when it was last refreshed. */
    private static final Map<Integer, Object[]> BEAMS = new HashMap<>();
    /** Per caster: when they started charging a nuke. */
    private static final Map<Integer, Long> CHARGING = new HashMap<>();
    private static final Random R = new Random();

    public static boolean handles(int type) { return type >= S2CFx.BEAM_SHOT && type <= S2CFx.NUKE_BLAST; }

    public static void receive(S2CFx p) {
        var mc = Minecraft.getInstance();
        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        switch (p.type()) {
            case S2CFx.BEAM_SUSTAIN -> { BEAMS.put(p.e1(), new Object[]{pos, (long) ClientState.ticks}); return; }
            case S2CFx.NUKE_CHARGE -> {
                if (p.a() > 0) CHARGING.put(p.e1(), (long) ClientState.ticks); else CHARGING.remove(p.e1());
                return;
            }
            case S2CFx.ORB_BURST -> { for (E e : ALL) if (e.type == S2CFx.ORB && e.e2 == p.e2()) e.dead = true; }
            case S2CFx.NUKE_LAUNCH -> CHARGING.remove(p.e1());
            default -> {}
        }
        E e = new E();
        e.type = p.type(); e.pos = pos; e.a = p.a(); e.b = p.b(); e.c = p.c(); e.e1 = p.e1(); e.e2 = p.e2();
        e.born = ClientState.ticks; e.seed = R.nextLong();
        e.life = switch (p.type()) {
            case S2CFx.BEAM_SHOT -> 12; case S2CFx.ORB -> 90; case S2CFx.ORB_BURST -> 26; case S2CFx.PRESSURE -> 24;
            case S2CFx.NUKE_LAUNCH -> (int) p.a() + 4; case S2CFx.NUKE_BLAST -> 140; default -> 20;
        };
        if (p.type() == S2CFx.ORB) { e.vel = new Vec3(p.a(), p.b(), p.c()); }
        if (p.type() == S2CFx.NUKE_LAUNCH) {
            Entity c = mc.level == null ? null : mc.level.getEntity(p.e1());
            e.vel = c != null ? coreAt(c, 1) : pos.add(0, 5, 0); // where the core was when it launched
        }
        ALL.add(e);
        onSpawn(e);
    }

    /** The rift above a caster's right shoulder. */
    static Vec3 mount(Entity c, float pt) {
        Vec3 look = c.getViewVector(pt), right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        return c.getEyePosition(pt).add(right.scale(1.1)).add(0, 0.55, 0).add(look.scale(2.2));
    }

    /** Where the nuke core forms while charging: high in front of you, above the crosshair. */
    static Vec3 coreAt(Entity c, float pt) { return c.getEyePosition(pt).add(c.getViewVector(pt).scale(7)).add(0, 2.4, 0); }

    private static void shake(Vec3 at, float amount, float range) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float fall = (float) Math.max(0, 1 - mc.player.position().distanceTo(at) / range);
        FxManager.shake = Math.max(FxManager.shake, Config.screenShake.get().floatValue() * amount * fall);
    }

    private static void onSpawn(E e) {
        switch (e.type) {
            case S2CFx.BEAM_SHOT -> { Dust.burst(e.pos, 22, 0.2, 0.07f, true); shake(e.pos, 0.3f, 30); }
            case S2CFx.ORB_BURST -> {
                Dust.burst(e.pos, (int) (16 + e.a * 10), 0.12 + e.a * 0.05, 0.08f, true);
                Dust.smoke(e.pos, (int) (4 + e.a * 2), e.a * 0.4, 0.9f + e.a * 0.2f, 26);
                shake(e.pos, 0.25f + e.a * 0.1f, 32);
            }
            case S2CFx.PRESSURE -> {
                if (e.b > 0) { Dust.smoke(e.pos.add(0, 0.2, 0), 10, e.a * 0.4, 1.0f, 30); shake(e.pos, 0.6f, 24); }
                for (int i = 0; i < 18; i++) {
                    double ang = R.nextDouble() * Math.PI * 2, r = R.nextDouble() * e.a;
                    Dust.add(Dust.Kind.FLECK, e.pos.add(Math.cos(ang) * r, 0.1, Math.sin(ang) * r), new Vec3(0, 0.06 + R.nextDouble() * 0.05, 0), 14, 0.07f, Dust.BLACK).gravity(0.012f).spin(0.3f);
                }
            }
            case S2CFx.NUKE_BLAST -> {
                FxManager.flash = 0.55f; FxManager.flashColor = 0x140C22;
                shake(e.pos, 2.4f, 90);
                Dust.burst(e.pos, 160, 0.9, 0.12f, true);
            }
            default -> {}
        }
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        for (E e : ALL) {
            long age = ClientState.ticks - e.born;
            if (e.type == S2CFx.ORB && !e.dead) {
                e.pos = e.pos.add(e.vel);
                Dust.add(Dust.Kind.ASH, e.pos.add(Dust.rnd().scale(1.4)), Vec3.ZERO, 14, 0.06f, Dust.BLACK).pull(e.pos, 0.02f).drag(0.85f);
            }
            if (e.type == S2CFx.NUKE_BLAST) {
                float R0 = e.a;
                // the mushroom of darkness: a column, then a cap spreading high above
                if (age < 50 && age % 2 == 0) {
                    Dust.smoke(e.pos.add(0, Math.min(age * 0.35, R0 * 1.8), 0), 2, R0 * 0.25, 1.4f + R0 * 0.12f, 70);
                    if (age > 14) Dust.smoke(e.pos.add(0, R0 * 1.8 + 1, 0), 3, R0 * 0.7, 1.8f + R0 * 0.15f, 80);
                }
                // and for a long while after, ash falls over everything
                if (age > 10 && age < 130) for (int i = 0; i < 4; i++) {
                    Vec3 at = e.pos.add((R.nextDouble() - 0.5) * R0 * 4, R0 * 1.6 + R.nextDouble() * 6, (R.nextDouble() - 0.5) * R0 * 4);
                    Dust.add(Dust.Kind.ASH, at, new Vec3(0, -0.03, 0), 80, 0.08f + R.nextFloat() * 0.06f, R.nextBoolean() ? Dust.BLACK : Dust.CHAR).drag(0.99f).spin(0.08f);
                }
            }
            if (e.type == S2CFx.NUKE_LAUNCH && age == (long) e.a) { // it lands: three ticks of silence (nothing but a pinprick)
                Dust.burst(e.pos, 12, 0.05, 0.05f, false);
            }
        }
        ALL.removeIf(e -> ClientState.ticks - e.born > e.life || (e.dead && e.type == S2CFx.ORB));
        // sustained beams: shed sparks at the impact, keep them alive while refreshed
        BEAMS.entrySet().removeIf(en -> ClientState.ticks - (long) en.getValue()[1] > 5);
        for (Object[] b : BEAMS.values()) {
            Vec3 end = (Vec3) b[0];
            Dust.burst(end, 3, 0.18, 0.06f, true);
            if (ClientState.ticks % 3 == 0) Dust.smoke(end, 1, 0.4, 0.8f, 18);
        }
        // the charging core drinks in the world around it
        for (var en : CHARGING.entrySet()) {
            Entity c = mc.level.getEntity(en.getKey());
            if (c == null) continue;
            float k = Math.min(1, (ClientState.ticks - en.getValue()) / 60f);
            Vec3 core = coreAt(c, 1);
            for (int i = 0; i < 2 + k * 6; i++) {
                Vec3 from = core.add(Dust.rnd().scale(5 + R.nextDouble() * 5));
                Dust.add(R.nextFloat() < 0.6f ? Dust.Kind.FLECK : Dust.Kind.EMBER, from, Vec3.ZERO, 40, 0.05f + R.nextFloat() * 0.04f, R.nextFloat() < 0.7f ? Dust.BLACK : Dust.VIOLET)
                        .pull(core, 0.012f + k * 0.02f).drag(0.9f);
            }
            if (c == mc.player) { FxManager.flash = Math.max(FxManager.flash, 0.22f * k); FxManager.flashColor = 0x000000; shake(core, 0.08f + 0.25f * k, 10); }
        }
    }

    public static void render(Draw d) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = d.pt;
        for (E e : ALL) {
            float age = ClientState.ticks - e.born + pt;
            switch (e.type) {
                case S2CFx.BEAM_SHOT -> shot(d, e, age);
                case S2CFx.ORB -> { if (!e.dead) orb(d, e, age); }
                case S2CFx.ORB_BURST -> orbBurst(d, e, age);
                case S2CFx.PRESSURE -> pressure(d, e, age);
                case S2CFx.NUKE_LAUNCH -> launch(d, e, age);
                case S2CFx.NUKE_BLAST -> blast(d, e, age);
                default -> {}
            }
        }
        for (var en : BEAMS.entrySet()) {
            Entity c = mc.level.getEntity(en.getKey());
            if (c != null) sustained(d, mount(c, pt), (Vec3) en.getValue()[0]);
        }
        for (var en : CHARGING.entrySet()) {
            Entity c = mc.level.getEntity(en.getKey());
            if (c != null) charge(d, c, Math.min(1, (ClientState.ticks - en.getValue() + pt) / 60f), pt);
        }
    }

    // ------------------------------------------------------------------------------------------------ beam
    private static void beamBody(Draw d, Vec3 a, Vec3 b, float w, float alpha, double time) {
        // a black core (the beam is darkness), a cold rim of light, and a white hairline down the middle
        d.beam(Abyssal.SEAM, false, a, b, w, 0xFFFFFF, alpha);
        d.beam(Rt.STREAK, true, a, b, w * 1.35f, Abyssal.COLD, alpha * 0.22f);
        d.beam(Rt.STREAK, true, a, b, w * 0.1f, Dust.PALE, alpha * 0.5f);
        // a tendril of darkness spiralling along it
        Abyssal.tendril(d, a, b, w * 0.35f, w * 0.5f, (float) time * 0.4f, 1, alpha * 0.5f);
    }

    private static void shot(Draw d, E e, float age) {
        var mc = Minecraft.getInstance();
        Entity c = mc.level.getEntity(e.e1);
        Vec3 a = c != null ? mount(c, d.pt) : e.pos.add(0, 2, 0);
        float k = age / e.life, out = 1 - k;
        Vec3 b = a.add(e.pos.subtract(a).scale(Math.min(1, age / 1.5f)));
        beamBody(d, a, b, 0.26f * (1 - k * 0.7f), out, d.time);
        Abyssal.eclipse(d, a, 0.14f, out, (float) d.time);
        if (age > 1.5f) BrawlFx.punchMark(d, e.pos, 1.6f, (age - 1.5f) * 1.2f, e.seed);
    }

    private static void sustained(Draw d, Vec3 a, Vec3 b) {
        double t = d.time;
        float w = 0.85f + 0.08f * (float) Math.sin(t * 1.7);
        beamBody(d, a, b, w, 1, t);
        Abyssal.eclipse(d, a, 0.16f, 1, (float) t);
        d.billboard(Rt.GLOW, true, b, 1.4f, Abyssal.COLD, 0.45f, 0);
        Abyssal.fracture(d, b, null, 1.6f, 1, 0.8f, (long) (t * 3));
    }

    // ------------------------------------------------------------------------------------------------ orbs
    private static void orb(Draw d, E e, float age) {
        boolean big = (e.e2 & 1) == 1;
        float r = big ? 0.8f : 0.45f;
        Vec3 p = e.pos.add(e.vel.scale(d.pt));
        float in = Math.min(1, age / 4f);
        Abyssal.orb(d, p, r * in, 1);
        d.billboard(Rt.RING, true, p, r * 1.6f * in, Abyssal.EDGE, 0.18f, (float) (d.time * 0.6));
        d.billboard(Rt.RING, false, p, r * 2.3f * in, Dust.BLACK, 0.35f, (float) (-d.time * 0.4));
    }

    private static void orbBurst(Draw d, E e, float age) {
        float R0 = e.a;
        if (age < 4) { Abyssal.eclipse(d, e.pos, R0 * 0.3f * (1 - age / 4f), 1, (float) d.time); return; } // it collapses in on itself
        float k = (age - 4) / (e.life - 4f), out = 1 - k;
        float r = R0 * (1 - (float) Math.pow(1 - Math.min(1, k * 2.2f), 3));
        Abyssal.orb(d, e.pos, r * 0.85f, out * out * 0.85f);
        d.billboard(Rt.RING, true, e.pos, r * 1.15f, Abyssal.EDGE, out * 0.3f, 0);
        Abyssal.fracture(d, e.pos, null, R0 * 1.1f, Math.min(1, k * 3), out, e.seed);
        Abyssal.groundWave(d, e.pos.subtract(0, 0.6, 0), r * 1.4f, out * 0.6f);
    }

    // ------------------------------------------------------------------------------------------------ pressure
    private static void pressure(Draw d, E e, float age) {
        float k = age / e.life, out = 1 - k;
        Vec3 c = e.pos.add(0, 0.06, 0);
        float r = e.a * (1 - (float) Math.pow(1 - Math.min(1, k * 1.6f), 3));
        Abyssal.groundWave(d, c, r, out);
        if (e.b > 0) {
            Abyssal.fracture(d, c, new Vec3(0, 1, 0), e.a * 0.9f, Math.min(1, age / 6f), out, e.seed);
            Abyssal.ink(d, c.add(0, 0.3, 0), e.a * 0.35f, k, out * 0.6f, e.seed);
        }
    }

    // ------------------------------------------------------------------------------------------------ nuke
    private static void charge(Draw d, Entity c, float k, float pt) {
        Vec3 core = coreAt(c, pt);
        float r = 0.2f + 1.0f * k;
        double t = d.time;
        Abyssal.orb(d, core, r, 1);
        Abyssal.fracture(d, core, null, r * 2.4f, Math.min(1, k * 1.4f), 0.5f + 0.5f * k, (long) (t * 2.5)); // crackling
        d.billboard(Rt.RING, true, core, r * 1.25f, Dust.PALE, 0.15f + 0.2f * k, (float) t);
        for (int i = 0; i < 3; i++) d.billboard(Rt.RING, false, core, r * (2.4f + i * 1.2f) * (1 - (float) ((t * 0.8 + i * 0.33) % 1)), Dust.BLACK, 0.3f * k, 0);
    }

    private static void launch(Draw d, E e, float age) {
        float travel = e.a;
        if (age > travel) { d.billboard(Rt.GLOW, true, e.pos, 0.12f, 0xFFFFFF, 0.9f, 0); return; } // the silent pinprick
        float k = age / travel;
        k = k * k; // it falls faster and faster
        Vec3 p = e.vel.add(e.pos.subtract(e.vel).scale(k));
        float r = 0.2f + 1.3f * 1 * (0.5f + 0.5f * e.b / 16f);
        Abyssal.orb(d, p, r, 1);
        d.billboard(Rt.RING, true, p, r * 1.3f, Dust.PALE, 0.3f, 0);
        d.beam(Abyssal.SEAM, false, e.vel.add(e.pos.subtract(e.vel).scale(Math.max(0, k - 0.25f))), p, r * 0.6f, 0xFFFFFF, 0.6f);
    }

    private static void blast(Draw d, E e, float age) {
        float R0 = e.a;
        float grow = 1 - (float) Math.pow(1 - Math.min(1, age / 10f), 3);
        float hold = age < 30 ? 1 : Math.max(0, 1 - (age - 30) / 50f);
        float r = R0 * grow;
        // the abyss opens: a dark shell with a white-violet rim, then it thins and lets go
        Abyssal.orb(d, e.pos, r, hold * 0.92f);
        d.glowShell(e.pos, r * 1.02f, 0xD8CCFF, hold * 0.35f);
        if (age < 3) d.billboard(Rt.GLOW, true, e.pos, R0 * 0.6f, 0xFFFFFF, 1 - age / 3f, 0);
        // shockwaves rolling across the ground, far past the crater
        for (int i = 0; i < 3; i++) {
            float wk = (age - i * 6) / 36f;
            if (wk < 0 || wk > 1) continue;
            Abyssal.groundWave(d, e.pos.subtract(0, R0 * 0.3, 0), R0 * (0.8f + wk * 2.4f), (1 - wk) * 0.9f);
        }
        Abyssal.fracture(d, e.pos.subtract(0, R0 * 0.32, 0), new Vec3(0, 1, 0), R0 * 1.7f, Math.min(1, age / 8f), hold, e.seed);
        // an eclipse over the crater, high up, while the darkness hangs
        Abyssal.eclipse(d, e.pos.add(0, R0 * 1.9f, 0), R0 * 0.45f * grow, hold * 0.9f, (float) d.time);
    }

    public static void clear() { ALL.clear(); BEAMS.clear(); CHARGING.clear(); }
    private AnniFx() {}
}
