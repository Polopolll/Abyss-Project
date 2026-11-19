package com.andy.abyss.client.render;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.combat.Move;
import com.andy.abyss.combat.Moves;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.Ability;
import com.andy.abyss.net.S2CHit;
import com.andy.abyss.net.S2CMove;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * The Arsenal on screen. Every move arrives as one packet; this plays its timeline: the pose, the construct's swing,
 * and every visual primitive at its tick. A rend is a crescent cut through the air that shows the galaxy behind it
 * (the void shader is world-anchored, so the cut is a window, not a sticker); it sweeps in with a white leading edge,
 * hangs in the air trembling, then severs: flares wide and snaps shut. Hits freeze the attacker's timeline for a few
 * frames (hit-stop), punch the camera, and leave a number.
 */
public final class MoveFx {
    // =====================================================================================================  state
    static final class Act {
        S2CMove p; Move m; Weapon w; Entity caster;
        Vec3 o, fwd, right, up = new Vec3(0, 1, 0), aim;
        float clock; int stop; int fired; int life; long seed;
        /** The thickest cut of this move: cuts close to it are its main cuts, thinner ones are echoes. */
        float maxThick;
        Vec3 target(float pt, float fwdDist) {
            Entity t = p.target() >= 0 ? Minecraft.getInstance().level.getEntity(p.target()) : null;
            if (t != null && t.isAlive()) return t.getPosition(pt).add(0, t.getBbHeight() * 0.55, 0);
            return o.add(fwd.scale(fwdDist));
        }
        Vec3 groundAt(Vec3 v) { return new Vec3(v.x, p.groundY() + 0.04, v.z); }
    }
    static final class Prim {
        Act a; Move.Fx fx;
        /** The attacker's view frame when this fired: eyes (a touch lower) and the full look, pitch included. */
        Vec3 o, fwd, right, up;
        float lastAge = -1;
        Prim(Act a, Move.Fx fx) {
            this.a = a; this.fx = fx;
            Entity c = a.caster;
            var mc = Minecraft.getInstance();
            if (c != null && c.isAlive()) {
                o = c.getEyePosition(1).add(0, -0.28, 0);
                fwd = c.getViewVector(1).normalize();
            } else { o = a.o; fwd = a.aim; }
            Vec3 r = fwd.cross(new Vec3(0, 1, 0));
            right = r.lengthSqr() < 1e-4 ? a.right : r.normalize();
            up = right.cross(fwd).normalize();
        }
        float age(float pt) { return a.clock + (a.stop > 0 ? 0 : pt) - fx.tick(); }
    }
    record Num(Vec3 pos, String text, int color, long born, float scale, double dx, double dz) {}

    private static final List<Act> ACTS = new ArrayList<>();
    private static final List<Prim> PRIMS = new ArrayList<>();
    private static final List<Num> NUMS = new ArrayList<>();
    /** Per entity: the move it is swinging (for the construct in its hand). */
    private static final Map<Integer, Act> SWING = new HashMap<>();
    /** Per entity: when its current construct was forged, and which weapon. */
    private static final Map<Integer, long[]> FORGED = new HashMap<>();
    private static float fov, fovTarget;
    private static final Random R = new Random();
    private static final java.util.Set<Move.Kind> WARNED = java.util.EnumSet.noneOf(Move.Kind.class);

    // =====================================================================================================  packets
    public static void start(S2CMove p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Act a = new Act();
        a.p = p; a.w = Weapon.byIndex(p.weapon()); a.m = Moves.byIndex(a.w, p.move());
        if (a.m == null) return;
        a.caster = mc.level.getEntity(p.caster());
        a.o = new Vec3(p.x(), p.y(), p.z());
        a.fwd = Vec3.directionFromRotation(0, p.yaw());
        a.right = new Vec3(-a.fwd.z, 0, a.fwd.x);
        a.aim = Vec3.directionFromRotation(p.pitch(), p.yaw());
        a.seed = R.nextLong();
        for (Move.Fx f : a.m.fx()) if (f.kind() == Move.Kind.REND) a.maxThick = Math.max(a.maxThick, f.e());
        int life = a.m.duration();
        for (Move.Fx f : a.m.fx()) life = Math.max(life, f.tick() + primLife(a, f));
        a.life = life + 2;
        ACTS.add(a);
        Act prev = SWING.put(p.caster(), a);
        ClientState.pose(p.caster(), a.m.pose(), a.m.duration() + a.m.recover());
        if (prev == null) forge(p.caster(), a.w);
        // tick-0 primitives start at once
        fire(a);
    }

    public static void hit(S2CHit h) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity v = mc.level.getEntity(h.victim());
        boolean mine = h.attacker() == mc.player.getId();
        int stop = switch (h.kind()) { case 1 -> 3; case 2 -> 5; case 3 -> 6; default -> 2; };
        for (Act a : ACTS) if (a.p.caster() == h.attacker()) a.stop = Math.max(a.stop, stop);
        float k = Config.screenShake.get().floatValue();
        if (mine) {
            FxManager.shake = Math.max(FxManager.shake, k * (0.12f + 0.12f * h.kind()));
            fovTarget = Math.max(fovTarget, 0.012f + 0.012f * h.kind());
        }
        if (h.kind() == 3) {
            ClientState.slowUntil = ClientState.ticks + 40;
            FxManager.flash = 0.45f; FxManager.flashColor = 0xD8C8FF;
        }
        if (v != null) {
            Vec3 at = v.position().add(0, v.getBbHeight() * 0.6, 0);
            IMPACTS.add(new Impact(at, ClientState.ticks, h.kind(), R.nextLong()));
            switch (h.kind()) {
                case 0 -> sfx(at, com.andy.abyss.registry.ModSounds.HIT, 1f, 1f);
                case 1 -> { sfx(at, com.andy.abyss.registry.ModSounds.HIT, 1.1f, 0.8f); sfx(at, com.andy.abyss.registry.ModSounds.HIT_HEAVY, 0.6f, 1.2f); }
                default -> { sfx(at, com.andy.abyss.registry.ModSounds.HIT_HEAVY, 1.2f, 1f); sfx(at, com.andy.abyss.registry.ModSounds.HIT, 0.9f, 0.7f); }
            }
            if (Config.damageNumbers.get() && h.damage() > 0) {
                int color = switch (h.kind()) { case 1 -> 0xD8B8FF; case 2 -> 0xFFFFFF; case 3 -> 0x9AF0FF; default -> 0xB58CFF; };
                String txt = h.damage() >= 1e6 ? "∞" : String.valueOf(Math.round(h.damage()));
                double a = R.nextDouble() * Math.PI * 2;
                NUMS.add(new Num(at.add(0, 0.4, 0), txt, color, ClientState.ticks, h.kind() >= 2 ? 1.6f : h.kind() == 1 ? 1.25f : 1f, Math.cos(a) * 0.02, Math.sin(a) * 0.02));
            }
        }
    }

    /** A construct was forged (form opened or weapon switched): it assembles out of the void over ~10 ticks. */
    public static void forge(int entity, Weapon w) { FORGED.put(entity, new long[]{ClientState.ticks, w.ordinal()}); }

    record Impact(Vec3 at, long born, int kind, long seed) {}
    private static final List<Impact> IMPACTS = new ArrayList<>();

    public static float fovKick() { return fov; }

    // ------------------------------------------------------------------------------------------------- sound
    static void sfx(Vec3 at, net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> ev, float vol, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || at == null) return;
        mc.level.playLocalSound(at.x, at.y, at.z, ev.get(), net.minecraft.sounds.SoundSource.PLAYERS, vol, pitch * (0.94f + R.nextFloat() * 0.12f), false);
    }

    /** What kind of move this is (basic, heavy, plunge, counter, skill, liberation), for how big it should sound. */
    static int moveKind(Act a) {
        Moves.Set set = Moves.ALL.get(a.w);
        if (set == null) return 0;
        if (a.m == set.liberation()) return 5;
        if (a.m == set.skill()) return 4;
        if (a.m == set.counter()) return 3;
        if (a.m == set.plunge()) return 2;
        if (a.m == set.heavy()) return 1;
        return 0;
    }

    /** Sounds that fire the moment a primitive appears. */
    private static void onSpawn(Prim p) {
        int k = moveKind(p.a);
        boolean big = k >= 1;
        Vec3 at = p.o;
        switch (p.fx.kind()) {
            case REND -> {
                sfx(at, big ? com.andy.abyss.registry.ModSounds.SWING_HEAVY : com.andy.abyss.registry.ModSounds.SWING, big ? 1.1f : 0.9f, 1f);
                sfx(at, com.andy.abyss.registry.ModSounds.TEAR, big ? 0.8f : 0.32f, big ? 0.9f : 1.15f);
            }
            case REND_LINE -> { sfx(at, com.andy.abyss.registry.ModSounds.SWING, 0.9f, 1.25f); sfx(at, com.andy.abyss.registry.ModSounds.TEAR, 0.3f, 1.3f); }
            case RIFT_ARM -> sfx(at, com.andy.abyss.registry.ModSounds.TEAR, 0.7f, 0.8f);
            case SHOCK -> sfx(p.a.groundAt(p.a.o), com.andy.abyss.registry.ModSounds.HIT_HEAVY, 1.3f, 0.9f);
            case FISSURE -> sfx(p.a.groundAt(p.a.o), com.andy.abyss.registry.ModSounds.HIT_HEAVY, 1.2f, 0.75f);
            case SPIKES -> sfx(at, com.andy.abyss.registry.ModSounds.SHATTER, 0.8f, 1.25f);
            case STAR, BEAM, VORTEX -> sfx(at, com.andy.abyss.registry.ModSounds.CHARGE_UP, 1f, k == 5 ? 0.8f : 1.05f);
            case GIANT -> sfx(at, com.andy.abyss.registry.ModSounds.SUMMON, 1.3f, 0.75f);
            case THROW -> sfx(at, com.andy.abyss.registry.ModSounds.SWING_HEAVY, 1f, 1.2f);
            case BARRIER -> sfx(at, com.andy.abyss.registry.ModSounds.FORM_OPEN, 1f, 0.8f);
            case GHOSTS -> sfx(at, com.andy.abyss.registry.ModSounds.DODGE, 0.9f, 0.9f);
            default -> {}
        }
    }

    /** Sounds on later beats of a primitive (a burst, a landing, each cut of a storm). */
    private static void onBeat(Prim p, float prev, float age) {
        int k = moveKind(p.a);
        Move.Fx f = p.fx;
        switch (f.kind()) {
            case REND_STORM -> {
                int n = Math.max(1, (int) f.a());
                for (int i = 0; i < n; i += 2) if (cross(prev, age, i * 0.8f)) sfx(p.o.add(p.fwd.scale(3)), com.andy.abyss.registry.ModSounds.SWING, 0.6f, 1.1f + (i % 4) * 0.05f);
                float end = n * 0.8f + 3;
                if (cross(prev, age, end)) { sfx(p.o.add(p.fwd.scale(3)), k == 5 ? com.andy.abyss.registry.ModSounds.LIBERATION : com.andy.abyss.registry.ModSounds.HIT_HEAVY, 1.4f, 1f); sfx(p.o, com.andy.abyss.registry.ModSounds.TEAR, 1f, 0.8f); }
            }
            case STAR -> { if (cross(prev, age, Math.max(6, f.d()))) sfx(p.o.add(p.fwd.scale(f.b())), k == 5 ? com.andy.abyss.registry.ModSounds.LIBERATION : com.andy.abyss.registry.ModSounds.HIT_HEAVY, 1.4f, 1f); }
            case BEAM -> { if (cross(prev, age, 4)) { sfx(p.o, k == 5 ? com.andy.abyss.registry.ModSounds.LIBERATION : com.andy.abyss.registry.ModSounds.HIT_HEAVY, 1.4f, 1.1f); sfx(p.o, com.andy.abyss.registry.ModSounds.TEAR, 0.9f, 0.7f); } }
            case GIANT -> {
                int imp = giantImpact(p.a, f);
                if (cross(prev, age, imp)) sfx(p.a.groundAt(p.o.add(p.fwd.scale(f.b()))), k == 5 ? com.andy.abyss.registry.ModSounds.LIBERATION : com.andy.abyss.registry.ModSounds.HIT_HEAVY, 1.6f, 0.95f);
                if (cross(prev, age, imp + 10)) sfx(p.o.add(p.fwd.scale(f.b())), com.andy.abyss.registry.ModSounds.SHATTER, 1.1f, 0.9f);
            }
            case RIFT_ARM -> { if (cross(prev, age, 6)) sfx(p.o.add(p.fwd.scale(3)), com.andy.abyss.registry.ModSounds.HIT, 0.9f, 0.8f); }
            case VORTEX -> { if (cross(prev, age, Math.max(6, f.c()))) sfx(p.o.add(p.fwd.scale(f.b())), com.andy.abyss.registry.ModSounds.HIT_HEAVY, 1.2f, 1.1f); }
            default -> {}
        }
    }

    private static boolean cross(float prev, float now, float at) { return prev < at && now >= at; }

    public static void clear() { ACTS.clear(); PRIMS.clear(); NUMS.clear(); SWING.clear(); FORGED.clear(); IMPACTS.clear(); fov = fovTarget = 0; }

    // =====================================================================================================  ticking
    public static void tick() {
        for (Act a : ACTS) {
            if (a.stop > 0) { a.stop--; continue; }
            a.clock++;
            fire(a);
        }
        for (Prim p : PRIMS) { float age = p.age(0); if (age > p.lastAge) { onBeat(p, p.lastAge, age); p.lastAge = age; } }
        ACTS.removeIf(a -> a.clock > a.life);
        PRIMS.removeIf(p -> !ACTS.contains(p.a) || p.age(0) > primLife(p.a, p.fx));
        SWING.values().removeIf(a -> a.clock > a.m.duration() + a.m.recover() + 4);
        NUMS.removeIf(n -> ClientState.ticks - n.born() > 26);
        IMPACTS.removeIf(i -> ClientState.ticks - i.born() > 12);
        fov += (fovTarget - fov) * 0.6f;
        fovTarget *= 0.7f;
        // keep a construct per armed vessel; detect switches
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (ClientState.formOf(e.getId()) != Ability.ARSENAL.ordinal()) continue;
            long[] f = FORGED.get(e.getId());
            int w = ClientState.weaponOf(e.getId());
            if (f == null || f[1] != w) forge(e.getId(), Weapon.byIndex(w));
        }
    }

    private static void fire(Act a) {
        while (a.fired < a.m.fx().size()) {
            Move.Fx f = a.m.fx().get(a.fired);
            if (f.tick() > a.clock) break;
            a.fired++;
            if (f.kind() == Move.Kind.CAMERA) camera(a, f);
            else { Prim p = new Prim(a, f); PRIMS.add(p); onSpawn(p); p.lastAge = 0; }
        }
    }

    private static void camera(Act a, Move.Fx f) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.player.position().distanceTo(a.o);
        float fall = (float) Math.max(0, 1 - dist / 40);
        if (a.p.caster() == mc.player.getId()) fall = 1;
        FxManager.shake = Math.max(FxManager.shake, Config.screenShake.get().floatValue() * f.a() * fall);
        fovTarget = Math.max(fovTarget, f.b() * fall);
    }

    /** How long a primitive stays on screen (its own build-up, hold and fade). */
    static int primLife(Act a, Move.Fx f) {
        return switch (f.kind()) {
            case REND -> 17;
            case REND_LINE -> 16;
            case RIFT_ARM -> 18;
            case SHOCK -> 22;
            case FISSURE -> 46;
            case SPIKES -> 30;
            case STAR -> (int) Math.max(6, f.d()) + 12;
            case BEAM -> 24;
            case GIANT -> giantImpact(a, f) + 26;
            case REND_STORM -> (int) (f.a() * 0.8f) + 3 + 17;
            case VORTEX -> (int) Math.max(6, f.c()) + 8;
            case THROW -> (int) Math.ceil(f.b() / Math.max(0.1f, f.a())) * (f.c() == 1 ? 2 : 1) + 8;
            case BARRIER -> (int) Math.max(4, f.b()) + 10;
            case GHOSTS -> 16;
            case CAMERA -> 1;
        };
    }

    /** A giant construct lands when the move's next shock/beam fires (so the slam and the shockwave are one frame). */
    static int giantImpact(Act a, Move.Fx g) {
        int best = Integer.MAX_VALUE;
        for (Move.Fx f : a.m.fx()) if (f.tick() > g.tick() && (f.kind() == Move.Kind.SHOCK || f.kind() == Move.Kind.BEAM)) best = Math.min(best, f.tick() - g.tick());
        return best == Integer.MAX_VALUE ? 12 : best;
    }

    // =====================================================================================================  drawing
    /** Called inside FxManager.render with the shared buffers. */
    public static void render(Draw d) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Prim p : PRIMS) {
            float age = p.age(d.pt);
            if (age < 0) continue;
            try { prim(d, p, age); } catch (Exception ex) { if (WARNED.add(p.fx.kind())) com.andy.abyss.Abyss.LOG.warn("Abyss move effect {} failed", p.fx.kind(), ex); }
        }
        for (Impact i : IMPACTS) impact(d, i);
    }

    private static void prim(Draw d, Prim p, float age) {
        Move.Fx f = p.fx; Act a = p.a;
        Random r = new Random(a.seed ^ (p.fx.hashCode() * 31L) ^ p.fx.tick());
        switch (f.kind()) {
            case REND -> rend(d, p, f, age, r);
            case REND_LINE -> rendLine(d, p, f, age, r);
            case RIFT_ARM -> riftArm(d, a, f, age);
            case SHOCK -> shock(d, a, f, age, r);
            case FISSURE -> fissure(d, a, f, age, r);
            case SPIKES -> spikes(d, a, f, age, r);
            case STAR -> star(d, a, f, age, r);
            case BEAM -> beam(d, a, f, age, r);
            case GIANT -> giant(d, a, f, age, r);
            case REND_STORM -> rendStorm(d, p, f, age, r);
            case VORTEX -> vortex(d, a, f, age, r);
            case THROW -> thrown(d, a, f, age);
            case BARRIER -> barrier(d, a, f, age);
            case GHOSTS -> ghosts(d, a, f, age, r);
            default -> {}
        }
    }

    static Vec3 rotY(Vec3 v, double deg) { double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a); return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c); }
    static Vec3 rotAbout(Vec3 v, Vec3 axis, double deg) {
        Vector3f t = new Vector3f((float) v.x, (float) v.y, (float) v.z).rotateAxis((float) Math.toRadians(deg), (float) axis.x, (float) axis.y, (float) axis.z);
        return new Vec3(t.x, t.y, t.z);
    }
    static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    // ------------------------------------------------------------------------------------------------- the slash
    /*
     * One hit, one clean arc (the Wuthering Waves / Chronoweave rule): it sweeps across in ~2 ticks with a white-hot
     * head, holds a beat, then dissolves from its tail to its head. Three additive layers on a camera-facing ribbon
     * (a soft violet glow, the body, a white core), so it reads from any angle and never goes edge-on. Behind the light
     * a hairline of the abyss stays open a moment longer and closes: the cut in reality.
     * The arc is laid out in the attacker's own view frame (eyes + full look, pitch included) at the moment it fires,
     * so in first person it crosses your screen around the crosshair, wherever you are looking.
     */
    private static void rend(Draw d, Prim p, Move.Fx f, float age, Random r) {
        // yaw offset c turns the arc inside the swing plane; tilt d rolls the plane about the look axis
        Vec3 side = rotAbout(p.right, p.fwd, -f.d());
        Vec3 f0 = rotAbout(p.fwd, side.cross(p.fwd).normalize(), 0);
        Vec3 dirC = f0.scale(Math.cos(Math.toRadians(f.c()))).add(side.scale(Math.sin(Math.toRadians(f.c()))));
        Vec3 sideC = f0.scale(-Math.sin(Math.toRadians(f.c()))).add(side.scale(Math.cos(Math.toRadians(f.c()))));
        float span = Math.abs(f.b()) > 359 ? 360 : f.b();
        // move the arc's plane off your eye line (an arc in a plane through the eye flattens to a line on screen):
        // shift it along the plane's normal, downward-ish, so it reads as a crescent sweeping across the view
        Vec3 n = sideC.cross(dirC).normalize();
        if (n.y > 0 || (Math.abs(n.y) < 0.2 && n.dot(p.right) < 0)) n = n.scale(-1);
        Vec3 centre = p.o.subtract(p.fwd.scale(0.2)).add(n.scale(0.6));
        // the main cut of a hit is bold; the thin echo cuts around it are faint hairlines (no pile-up)
        boolean main = f.e() >= p.a.maxThick * 0.7f;
        slash(d, centre, dirC, sideC, Math.max(1.4f, f.a()), span, main ? Math.max(0.3f, f.e() * 1.35f) : 0.07f, age, r, false, main);
    }

    /** The light arc. span in degrees (sign = sweep direction). thick = width at its widest, in blocks. */
    static void slash(Draw d, Vec3 centre, Vec3 f0, Vec3 side, float radius, float span, float thick, float age, Random r, boolean big) { slash(d, centre, f0, side, radius, span, thick, age, r, big, true); }

    static void slash(Draw d, Vec3 centre, Vec3 f0, Vec3 side, float radius, float span, float thick, float age, Random r, boolean big, boolean full) {
        float sweepT = big ? 3.0f : 2.2f, hold = big ? 2f : 1f, fadeT = big ? 7f : 5f;
        float head = smooth(age / sweepT);
        float tail = Mth.clamp((age - sweepT - hold) / fadeT, 0, 1);
        if (tail >= 0.999f) { if (full) seam(d, centre, f0, side, radius, span, thick, age - sweepT - hold - fadeT, r); return; }
        double half = Math.toRadians(span) / 2, from = -half, to = half;
        int seg = Math.max(14, (int) (Math.abs(span) / 5));
        VertexConsumer add = d.buf.getBuffer(Rt.addTri(Rt.WHITE));
        Vec3[] prevL = null;
        Vec3 headPos = null;
        for (int i = 0; i <= seg; i++) {
            float k = i / (float) seg;
            if (k < tail || k > head) { prevL = null; continue; }
            double th = from + (to - from) * k;
            Vec3 dir = f0.scale(Math.cos(th)).add(side.scale(Math.sin(th)));
            Vec3 tan = f0.scale(-Math.sin(th)).add(side.scale(Math.cos(th)));
            Vec3 mid = centre.add(dir.scale(radius));
            // across the ribbon: perpendicular to the arc and to the line of sight
            Vec3 across = tan.cross(d.cam.subtract(mid));
            if (across.lengthSqr() < 1e-8) across = dir;
            across = across.normalize();
            // crescent profile over the visible part: sharp tail, full near the head, sharp tip
            float u = (k - tail) / Math.max(1e-3f, head - tail);
            float prof = (float) (Math.pow(u, 0.55) * Math.pow(Math.max(0, 1 - u), 0.18));
            float w = thick * prof * (big ? 1.3f : 1f);
            // eye-safety: a segment right at the camera fades instead of filling the screen
            float near = Mth.clamp((float) (mid.distanceTo(d.cam) - 0.3) / 1.2f, 0, 1);
            Vec3[] L = {mid.add(across.scale(w * 1.9)), mid.add(across.scale(w * 0.5)), mid.add(across.scale(w * 0.12)), mid,
                    mid.subtract(across.scale(w * 0.12)), mid.subtract(across.scale(w * 0.5)), mid.subtract(across.scale(w * 1.9))};
            if (prevL != null) {
                float a = near * (0.35f + 0.65f * u);
                // glow (outer), body, core: colour and alpha per band
                if (full) {
                    band(d, add, prevL[0], prevL[1], L[1], L[0], 0x7A45E0, 0f, 0.32f * a);
                    band(d, add, prevL[6], prevL[5], L[5], L[6], 0x7A45E0, 0f, 0.32f * a);
                } else a *= 0.6f;
                band(d, add, prevL[1], prevL[2], L[2], L[1], 0xB58CFF, 0.55f * a, 0.85f * a);
                band(d, add, prevL[5], prevL[4], L[4], L[5], 0xB58CFF, 0.55f * a, 0.85f * a);
                band(d, add, prevL[2], prevL[3], L[3], L[2], 0xFFFFFF, a, a);
                band(d, add, prevL[4], prevL[3], L[3], L[4], 0xFFFFFF, a, a);
            }
            prevL = L;
            headPos = mid;
        }
        // the white-hot head and a few sparks while it sweeps
        if (full && head < 1 && headPos != null) {
            d.billboard(Rt.GLOW, true, headPos, thick * 4f, 0xFFFFFF, 0.8f * d.near(headPos, 1.2f), 0);
            for (int i = 0; i < 3; i++) {
                Vec3 s = headPos.add(r.nextGaussian() * 0.25, r.nextGaussian() * 0.25, r.nextGaussian() * 0.25);
                d.beam(Rt.STREAK, true, headPos, s, 0.03f, 0xE8DCFF, 0.8f);
            }
        }
        // the seam is already open under the light once the sweep has passed
        if (full && age > sweepT * 0.6f) seam(d, centre, f0, side, radius, span, thick, age - sweepT - hold - fadeT, r);
    }

    /** Two triangles of a ribbon band with alpha a0 on the inner edge (b,c) and a1 on the outer (a,d). */
    private static void band(Draw d, VertexConsumer g, Vec3 a, Vec3 b, Vec3 c, Vec3 dd, int rgb, float aOuter, float aInner) {
        d.sv(g, a.x, a.y, a.z, rgb, aOuter, .5f, .5f); d.sv(g, b.x, b.y, b.z, rgb, aInner, .5f, .5f); d.sv(g, c.x, c.y, c.z, rgb, aInner, .5f, .5f);
        d.sv(g, a.x, a.y, a.z, rgb, aOuter, .5f, .5f); d.sv(g, c.x, c.y, c.z, rgb, aInner, .5f, .5f); d.sv(g, dd.x, dd.y, dd.z, rgb, aOuter, .5f, .5f);
    }

    /** The hairline of the abyss left in the air: open while the light fades, then it closes over ~6 ticks. */
    private static void seam(Draw d, Vec3 centre, Vec3 f0, Vec3 side, float radius, float span, float thick, float after, Random r) {
        float close = Mth.clamp(after / 6f, 0, 1);
        if (close >= 1) return;
        float wk = (1 - close) * (1 - close);
        double half = Math.toRadians(span) / 2;
        int seg = Math.max(12, (int) (Math.abs(span) / 6));
        VertexConsumer vt = d.buf.getBuffer(Rt.voidTri(Rt.FILL));
        Vec3 pl = null, pr = null;
        for (int i = 0; i <= seg; i++) {
            float k = i / (float) seg;
            double th = -half + 2 * half * k;
            Vec3 dir = f0.scale(Math.cos(th)).add(side.scale(Math.sin(th)));
            Vec3 tan = f0.scale(-Math.sin(th)).add(side.scale(Math.cos(th)));
            Vec3 mid = centre.add(dir.scale(radius));
            Vec3 across = tan.cross(d.cam.subtract(mid));
            if (across.lengthSqr() < 1e-8) across = dir;
            across = across.normalize();
            float w = thick * 0.32f * wk * (float) Math.pow(Math.sin(Math.PI * k), 0.6);
            float a = Mth.clamp((float) (mid.distanceTo(d.cam) - 0.3) / 1.2f, 0, 1);
            Vec3 l = mid.add(across.scale(w)), rr = mid.subtract(across.scale(w));
            if (pl != null && a > 0.01f) for (int pass = 0; pass < 2; pass++) {
                d.vv(vt, pl.x, pl.y, pl.z, 0xFFFFFF, a, .5f, .5f); d.vv(vt, l.x, l.y, l.z, 0xFFFFFF, a, .5f, .5f); d.vv(vt, rr.x, rr.y, rr.z, 0xFFFFFF, a, .5f, .5f);
                d.vv(vt, pl.x, pl.y, pl.z, 0xFFFFFF, a, .5f, .5f); d.vv(vt, rr.x, rr.y, rr.z, 0xFFFFFF, a, .5f, .5f); d.vv(vt, pr.x, pr.y, pr.z, 0xFFFFFF, a, .5f, .5f);
            }
            pl = l; pr = rr;
        }
    }

    /** A thrust: a spear of light that shoots out along your aim in ~1.5 ticks, with a ring where it stops. */
    private static void rendLine(Draw d, Prim p, Move.Fx f, float age, Random r) {
        // yaw offset d and lift c relative to your look
        Vec3 dir = rotAbout(p.fwd, p.up, -f.d());
        dir = rotAbout(dir, p.right, f.c()).normalize();
        Vec3 start = p.o.add(dir.scale(Math.max(0.6f, f.b())));
        thrust(d, start, dir, Math.max(1.5f, f.a()), Math.max(0.14f, f.e()), age, r);
    }

    static void thrust(Draw d, Vec3 start, Vec3 dir, float len, float thick, float age, Random r) {
        float shootT = 1.6f, hold = 1f, fadeT = 5f;
        float head = smooth(age / shootT), tail = Mth.clamp((age - shootT - hold) / fadeT, 0, 1);
        if (tail < 0.999f) {
            Vec3 a = start.add(dir.scale(len * tail)), b = start.add(dir.scale(len * head));
            Vec3 mid = a.add(b).scale(0.5);
            float near = Mth.clamp((float) (mid.distanceTo(d.cam) - 0.3) / 1.2f, 0, 1);
            d.beam(Rt.GLOW, true, a, b, thick * 3.5f, 0x7A45E0, 0.3f * near);
            d.beam(Rt.STREAK, true, a, b, thick * 1.1f, 0xB58CFF, 0.85f * near);
            d.beam(Rt.STREAK, true, a, b, thick * 0.35f, 0xFFFFFF, near);
            if (head < 1) d.billboard(Rt.GLOW, true, b, thick * 5, 0xFFFFFF, 0.9f * d.near(b, 1.2f), 0);
        }
        float tipK = Mth.clamp((age - shootT) / 6f, 0, 1);
        if (age >= shootT && tipK < 1) {
            Vec3 tip = start.add(dir.scale(len));
            d.flat(Rt.RING, true, tip, dir, 0.3f + tipK * 1.4f, 0xE8DCFF, 0.9f * (1 - tipK), 0);
        }
        // the seam along the line
        float after = age - shootT - hold - fadeT;
        float close = Mth.clamp(after / 6f, 0, 1);
        if (age > shootT && close < 1) {
            Vec3 end = start.add(dir.scale(len));
            float w = thick * 0.3f * (1 - close) * (1 - close);
            Vec3 across = dir.cross(d.cam.subtract(start.add(end).scale(0.5)));
            if (across.lengthSqr() > 1e-8) {
                across = across.normalize().scale(w);
                VertexConsumer vt = d.buf.getBuffer(Rt.voidTri(Rt.FILL));
                Vec3 m = start.add(end).scale(0.5);
                for (int pass = 0; pass < 2; pass++) {
                    d.vv(vt, start.x, start.y, start.z, 0xFFFFFF, 1, .5f, .5f); d.vv(vt, m.x + across.x, m.y + across.y, m.z + across.z, 0xFFFFFF, 1, .5f, .5f); d.vv(vt, end.x, end.y, end.z, 0xFFFFFF, 1, .5f, .5f);
                    d.vv(vt, start.x, start.y, start.z, 0xFFFFFF, 1, .5f, .5f); d.vv(vt, end.x, end.y, end.z, 0xFFFFFF, 1, .5f, .5f); d.vv(vt, m.x - across.x, m.y - across.y, m.z - across.z, 0xFFFFFF, 1, .5f, .5f);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- arm out of a rift
    private static void riftArm(Draw d, Act a, Move.Fx f, float age) {
        Vec3 tear = a.o.add(a.right.scale(f.a())).add(0, f.b(), 0).subtract(a.fwd.scale(f.c()));
        Vec3 tgt = a.target(d.pt, 3.2f);
        float open = smooth(age / 3), extend = smooth((age - 2) / 4), retract = smooth((age - 10) / 6);
        float ext = extend * (1 - retract);
        float tearA = open * (1 - smooth((age - 13) / 5));
        Vec3 toward = tgt.subtract(tear);
        float rot = (float) Math.atan2(toward.y, Math.hypot(toward.x, toward.z));
        d.voidBillboard(Rt.RIFT, tear, 0.32f * tearA + 0.02f, 0.9f * tearA + 0.02f, tearA, rot);
        d.billboard(Rt.GLOW, true, tear, 1.3f, 0x7A45E0, 0.5f * tearA, 0);
        if (ext <= 0.01f) return;
        Vec3 ctrl = tear.add(tgt).scale(0.5).add(a.up.scale(f.d())).add(a.right.scale(f.d() * 0.5));
        int seg = 12;
        Vec3 prev = tear;
        for (int i = 1; i <= seg; i++) {
            float k = ext * i / seg;
            Vec3 p = bez(tear, ctrl, tgt, k);
            float w = f.e() * (0.55f + 0.45f * (float) Math.sin(Math.PI * Math.min(1, k * 1.2)));
            d.voidRibbon(prev, p, w, 1);
            d.beam(Rt.STREAK, true, prev, p, w * 0.35f, 0xB58CFF, 0.55f);
            prev = p;
        }
        Vec3 dir = bez(tear, ctrl, tgt, ext).subtract(bez(tear, ctrl, tgt, Math.max(0, ext - 0.05f)));
        d.voidShard(prev.subtract(dir.normalize().scale(0.2)), dir, f.e() * 2.2f, f.e() * 0.75f, 1);
        if (extend >= 0.95f && retract < 0.3f) d.glowShell(tgt, 0.5 + (age - 6) * 0.25, 0xFFFFFF, 0.7f * (1 - retract));
    }

    static Vec3 bez(Vec3 a, Vec3 b, Vec3 c, float t) { float u = 1 - t; return a.scale(u * u).add(b.scale(2 * u * t)).add(c.scale(t * t)); }

    // ------------------------------------------------------------------------------------------------- ground
    private static Vec3 groundCentre(Act a, float fwdDist, boolean onTarget, float pt) {
        Vec3 c = onTarget ? a.target(pt, fwdDist) : a.o.add(a.fwd.scale(fwdDist));
        if (onTarget && a.p.target() >= 0) {
            Entity t = Minecraft.getInstance().level.getEntity(a.p.target());
            if (t != null) return new Vec3(c.x, t.getY() + 0.04, c.z);
        }
        return a.groundAt(c);
    }

    private static void shock(Draw d, Act a, Move.Fx f, float age, Random r) {
        Vec3 c = a.groundAt(a.o.add(a.fwd.scale(Math.min(2.5, f.a() * 0.25))));
        float t = Mth.clamp(age / 18f, 0, 1);
        float rad = f.a() * (float) (1 - Math.pow(1 - t, 3));
        float fade = 1 - t;
        d.flat(Rt.RING, true, c, new Vec3(0, 1, 0), rad, 0xE8DCFF, 0.95f * fade, 0);
        d.flat(Rt.RING, true, c.add(0, 0.02, 0), new Vec3(0, 1, 0), rad * 0.82f, 0x7A45E0, 0.7f * fade, 0);
        // a band of void rolling outward on the ground
        VertexConsumer vt = d.buf.getBuffer(Rt.voidTri(Rt.FILL));
        int seg = 48;
        float bw = f.b() * fade;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double ro = rad, ri = Math.max(0, rad - bw);
            Vec3 o0 = c.add(Math.cos(a0) * ro, 0.03, Math.sin(a0) * ro), o1 = c.add(Math.cos(a1) * ro, 0.03, Math.sin(a1) * ro);
            Vec3 i0 = c.add(Math.cos(a0) * ri, 0.03, Math.sin(a0) * ri), i1 = c.add(Math.cos(a1) * ri, 0.03, Math.sin(a1) * ri);
            d.vv(vt, o0.x, o0.y, o0.z, 0xFFFFFF, fade, .5f, .5f); d.vv(vt, o1.x, o1.y, o1.z, 0xFFFFFF, fade, .5f, .5f); d.vv(vt, i1.x, i1.y, i1.z, 0xFFFFFF, fade, .5f, .5f);
            d.vv(vt, o0.x, o0.y, o0.z, 0xFFFFFF, fade, .5f, .5f); d.vv(vt, i1.x, i1.y, i1.z, 0xFFFFFF, fade, .5f, .5f); d.vv(vt, i0.x, i0.y, i0.z, 0xFFFFFF, fade, .5f, .5f);
        }
        // crystal debris thrown up off the rim
        for (int i = 0; i < 18; i++) {
            double an = r.nextDouble() * Math.PI * 2;
            Vec3 base = c.add(Math.cos(an) * rad * (0.7 + r.nextDouble() * 0.3), 0, Math.sin(an) * rad * (0.7 + r.nextDouble() * 0.3));
            float lift = (float) (t * (1.2 + r.nextDouble() * 1.5) - t * t * 1.6);
            d.voidShard(base.add(0, Math.max(0, lift), 0), new Vec3(r.nextGaussian(), 1.5, r.nextGaussian()), 0.35f * fade, 0.07f, fade);
        }
        if (age < 3) d.billboard(Rt.GLOW, true, c.add(0, 0.4, 0), Math.min(1.6f, f.a() * 0.25f), 0xFFFFFF, (1 - age / 3) * 0.6f * d.near(c, 4), 0);
    }

    private static void fissure(Draw d, Act a, Move.Fx f, float age, Random r) {
        float run = smooth(age / 7);
        float close = smooth((age - 32) / 12);
        float alpha = 1 - close;
        Vec3 start = a.groundAt(a.o.add(a.fwd.scale(0.8)));
        int seg = 18;
        VertexConsumer vt = d.buf.getBuffer(Rt.voidTri(Rt.FILL));
        Vec3 pl = null, pr = null, pm = null;
        for (int i = 0; i <= seg * run; i++) {
            float k = i / (float) seg;
            double wob = Math.sin(k * 21 + r.nextFloat()) * 0.35 + Math.sin(k * 7) * 0.5;
            Vec3 p = start.add(a.fwd.scale(f.a() * k)).add(a.right.scale(wob));
            float w = f.b() * (1 - close) * (0.35f + 0.65f * (float) Math.sin(Math.PI * Math.min(1, k * 1.05))) * (0.75f + 0.25f * r.nextFloat()) * 0.5f;
            Vec3 l = p.add(a.right.scale(w)), rr = p.subtract(a.right.scale(w));
            if (pl != null) {
                d.vv(vt, pl.x, pl.y, pl.z, 0xFFFFFF, alpha, .5f, .5f); d.vv(vt, l.x, l.y, l.z, 0xFFFFFF, alpha, .5f, .5f); d.vv(vt, rr.x, rr.y, rr.z, 0xFFFFFF, alpha, .5f, .5f);
                d.vv(vt, pl.x, pl.y, pl.z, 0xFFFFFF, alpha, .5f, .5f); d.vv(vt, rr.x, rr.y, rr.z, 0xFFFFFF, alpha, .5f, .5f); d.vv(vt, pr.x, pr.y, pr.z, 0xFFFFFF, alpha, .5f, .5f);
                d.beam(Rt.STREAK, true, pl, l, 0.06f, 0xB58CFF, 0.8f * alpha);
                d.beam(Rt.STREAK, true, pr, rr, 0.06f, 0xB58CFF, 0.8f * alpha);
                if (i % 3 == 0) d.voidShard(p.add(0, -0.1, 0), new Vec3(r.nextGaussian() * 0.3, 1, r.nextGaussian() * 0.3), (0.5f + r.nextFloat() * 0.7f) * alpha * Math.min(1, (age - i * 0.4f) / 3), 0.09f, alpha);
                // light bleeding up out of the crack
                d.beam(Rt.GLOW, true, p, p.add(0, 1.2 * alpha, 0), w * 3 + 0.2f, 0x5A2AB0, 0.25f * alpha);
            }
            pl = l; pr = rr; pm = p;
        }
        if (run < 1 && pm != null) { d.billboard(Rt.GLOW, true, pm.add(0, 0.3, 0), 1.4f, 0xFFFFFF, 0.8f, 0); }
    }

    private static void spikes(Draw d, Act a, Move.Fx f, float age, Random r) {
        Vec3 c = groundCentre(a, f.d(), f.e() == 1, d.pt);
        int n = Math.max(1, (int) f.b());
        for (int i = 0; i < n; i++) {
            double an = i * Math.PI * 2 / n + r.nextDouble() * 0.5;
            // centred on yourself, the spikes ring you instead of spearing up through your own eyes
            boolean onSelf = f.e() != 1 && f.d() < 1;
            double rad = onSelf ? 1.4 + r.nextDouble() * Math.max(0.5, f.a() - 1.4) : (i == 0 ? 0 : 0.25 + r.nextDouble() * 0.75) * f.a();
            Vec3 base = c.add(Math.cos(an) * rad, 0, Math.sin(an) * rad);
            float delay = (float) (rad / Math.max(0.1, f.a()) * 3);
            float rise = smooth((age - delay) / 2.5f) * (1 - smooth((age - 20 - delay) / 8));
            if (rise <= 0.01f) continue;
            float h = f.c() * (i == 0 && !onSelf ? 1.4f : 0.6f + r.nextFloat() * 0.6f);
            Vec3 dir = new Vec3(-Math.cos(an) * 0.25 * rad / f.a() * -1, 1, -Math.sin(an) * 0.25 * rad / f.a() * -1);
            d.voidShard(base, dir, h * rise, (i == 0 ? 0.28f : 0.14f) * (0.6f + 0.4f * rise), rise);
            if (age - delay < 4) d.billboard(Rt.GLOW, true, base.add(0, 0.2, 0), 0.6f, 0xD8C8FF, 0.8f, 0);
        }
        float t = Mth.clamp(age / 10, 0, 1);
        d.flat(Rt.RING, true, c.add(0, 0.03, 0), new Vec3(0, 1, 0), f.a() * (0.4f + t), 0xB58CFF, 0.8f * (1 - t), 0);
    }

    // ------------------------------------------------------------------------------------------------- stars and vortices
    /** A small black star: gathers (rings tighten, streaks fall in), holds, then bursts into crystal. */
    /**
     * A black star: a hard black core (no pale glow discs) that grows while space falls into it, a bright accretion
     * ring and a thin lensing halo; at the end it bursts in one white ring and a spray of crystal.
     */
    private static void star(Draw d, Act a, Move.Fx f, float age, Random r) {
        Vec3 c = f.c() == 1 ? a.target(d.pt, f.b()) : f.c() == 2 ? a.groundAt(a.o).add(0, f.a() * 0.4, 0) : a.o.add(a.fwd.scale(Math.max(1.5f + f.a(), f.b())));
        float life = Math.max(6, f.d());
        float grow = smooth(age / Math.min(8, life * 0.4f));
        float burst = age - life;
        float rad = f.a();
        float near = d.near(c, rad * 1.6f);
        if (burst < 0) {
            float rr = rad * grow * (1 + 0.04f * (float) Math.sin(age * 1.7));
            // the swirl: arcs of light spiralling into the shadow, turning as it feeds
            d.blackHole(c, rr * 2.6f, false, grow, (float) d.time * -0.9f);
            for (int i = 0; i < 18; i++) {
                Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                float k = (float) ((age * 0.09 + r.nextDouble()) % 1.0);
                Vec3 from = c.add(dir.scale(rad * (1.4 + 4.0 * (1 - k))));
                d.beam(Rt.STREAK, true, from, from.add(dir.scale(-0.6 * rad)), 0.04f, k > 0.7f ? 0xE8F4FF : 0x7FA8FF, 0.7f * grow * k);
            }
        } else {
            float k = Mth.clamp(burst / 12, 0, 1), out = 1 - k;
            if (burst < 3) d.blackHole(c, rad * 2.6f * (1 - burst / 3), false, 1, (float) d.time * -0.9f);
            if (burst < 3) d.billboard(Rt.GLOW, true, c, rad * 1.8f, 0xFFFFFF, (1 - burst / 3) * 0.6f * d.near(c, rad * 3f), 0);
            d.billboard(Rt.RING, true, c, rad * (1.2f + k * 6), 0xFFFFFF, out, 0);
            d.flat(Rt.RING, true, c, new Vec3(0, 1, 0), rad * (1 + k * 8), 0xB58CFF, out * 0.8f, 0);
            for (int i = 0; i < 26; i++) {
                Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                d.voidShard(c.add(dir.scale(rad * (0.5 + k * 4.5))), dir, rad * 0.6f * out, rad * 0.08f + 0.03f, out);
            }
        }
    }

    private static void vortex(Draw d, Act a, Move.Fx f, float age, Random r) {
        Vec3 c = a.o.add(a.fwd.scale(f.b()));
        float life = Math.max(6, f.c());
        float in = smooth(age / 5), out = 1 - smooth((age - life) / 8);
        float k = in * out;
        if (k <= 0.01f) return;
        float rad = f.a();
        for (int i = 0; i < 40; i++) {
            double an0 = r.nextDouble() * Math.PI * 2;
            float ph = (float) ((age * 0.05 + r.nextDouble()) % 1.0);
            double rr = rad * (1 - ph);
            double an = an0 + ph * 5;
            double y = (r.nextDouble() - 0.3) * rad * 0.5 * (1 - ph);
            Vec3 p = c.add(Math.cos(an) * rr, y, Math.sin(an) * rr);
            Vec3 q = c.add(Math.cos(an - 0.35) * (rr + rad * 0.08), y, Math.sin(an - 0.35) * (rr + rad * 0.08));
            d.beam(Rt.STREAK, true, q, p, 0.08f + 0.06f * ph, ph > 0.7f ? 0xE8DCFF : 0x8C55FF, 0.75f * k * (0.3f + ph));
        }
        float spin = (float) d.time * 3;
        d.flat(Rt.RING, true, c.add(0, -0.6, 0), new Vec3(0, 1, 0), rad, 0x6A35D0, 0.6f * k, spin);
        d.flat(Rt.RING, true, c.add(0, -0.4, 0), new Vec3(0, 1, 0), rad * 0.6f, 0xB58CFF, 0.7f * k, -spin * 1.5f);
        d.voidSphere(c, 0.25 + 0.35 * k, k, 0.2f, 0xFFFFFF);
    }

    private static void beam(Draw d, Act a, Move.Fx f, float age, Random r) {
        Vec3 dir = a.aim;
        Vec3 start = a.o.add(dir.scale(0.8));
        float charge = smooth(age / 4);
        float fire = age - 4;
        float w = f.b();
        if (fire < 0) {
            d.voidSphere(start, w * 0.6f * charge, 1, 0.2f, 0xFFFFFF);
            d.glowShell(start, w * (1.8 - charge), 0xE8DCFF, charge);
            for (int i = 0; i < 12; i++) {
                Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                Vec3 p = start.add(o.scale(w * 3 * (1 - charge) + 0.2));
                d.beam(Rt.STREAK, true, p, p.add(o.scale(-0.5)), 0.05f, 0xB58CFF, charge);
            }
            return;
        }
        float k = Mth.clamp(fire / 16, 0, 1);
        float len = f.a() * smooth(fire / 2);
        float ww = w * (fire < 2 ? 1.5f - fire * 0.25f : 1) * (1 - k * k);
        Vec3 end = start.add(dir.scale(len));
        d.voidRibbon(start, end, ww * 1.3f, 1 - k);
        d.beam(Rt.STREAK, true, start, end, ww * 0.45f, 0xFFFFFF, 1 - k);
        d.beam(Rt.GLOW, true, start, end, ww * 3.4f, 0x7A45E0, 0.45f * (1 - k));
        Vec3[] b = Draw.basis(dir);
        for (int i = 0; i < 7; i++) {
            float along = (float) (((fire * 0.15f + i / 7f)) % 1.0) * len;
            Vec3 c = start.add(dir.scale(along));
            d.flat(Rt.RING, true, c, dir, ww * (1.4f + 0.4f * (float) Math.sin(i + fire)), 0xD8C8FF, 0.7f * (1 - k), (float) d.time * 4 + i);
        }
        for (int i = 0; i < 10; i++) {
            float along = r.nextFloat() * len;
            double an = r.nextDouble() * Math.PI * 2 + fire * 0.6;
            Vec3 p = start.add(dir.scale(along)).add(b[0].scale(Math.cos(an) * ww)).add(b[1].scale(Math.sin(an) * ww));
            d.beam(Rt.STREAK, true, p, p.add(dir.scale(1.2)), 0.04f, 0xE8DCFF, 0.8f * (1 - k));
        }
        if (fire < 3) { d.billboard(Rt.GLOW, true, start, w * 4, 0xFFFFFF, (1 - fire / 3) * d.near(start, 1), 0); d.flat(Rt.RING, true, start, dir, w * (2 + fire * 2), 0xFFFFFF, 1 - fire / 3, 0); }
        d.billboard(Rt.GLOW, true, end, ww * 2.5f, 0xE8DCFF, 0.7f * (1 - k), 0);
    }

    // ------------------------------------------------------------------------------------------------- constructs
    /** A colossal construct out of a rift in the sky: the rift opens, the construct pushes through, lands, then shatters. */
    private static void giant(Draw d, Act a, Move.Fx f, float age, Random r) {
        int impact = giantImpact(a, f);
        int kind = (int) f.d();
        float size = f.a();
        Vec3 tgt = f.c() == 1 ? a.target(d.pt, f.b()) : a.o.add(a.fwd.scale(f.b()));
        Vec3 ground = a.groundAt(tgt);
        Vec3 riftPos = ground.add(0, size * 1.9 + 4, 0).subtract(a.fwd.scale(kind == 2 ? size * 0.6 : 0));
        if (kind == 2) riftPos = a.o.add(0, size * 0.5 + 2, 0).subtract(a.fwd.scale(1.5));
        float open = smooth(age / 6) * (1 - smooth((age - impact - 14) / 8));
        // the rift: a horizontal void disc with lensing rings
        // a sigil where it comes through (no flat void panels)
        for (int i = 0; i < 6; i++) {
            double an = i * Math.PI / 3 + d.time * 0.6;
            Vec3[] b = Draw.basis(kind == 2 ? a.fwd : new Vec3(0, 1, 0));
            Vec3 q0 = riftPos.add(b[0].scale(Math.cos(an) * size * 0.9 * open)).add(b[1].scale(Math.sin(an) * size * 0.9 * open));
            Vec3 q1 = riftPos.add(b[0].scale(Math.cos(an + 2.094) * size * 0.9 * open)).add(b[1].scale(Math.sin(an + 2.094) * size * 0.9 * open));
            d.beam(Rt.STREAK, true, q0, q1, 0.06f, 0xC8B0FF, 0.75f * open);
        }
        d.flat(Rt.RING, true, riftPos, kind == 2 ? a.fwd : new Vec3(0, 1, 0), size * 0.85f * open, 0xD8C8FF, 0.8f * open, (float) d.time);
        d.flat(Rt.RING, true, riftPos, kind == 2 ? a.fwd : new Vec3(0, 1, 0), size * 1.15f * open, 0x7A45E0, 0.5f * open, (float) -d.time * 1.4f);
        float emerge = smooth((age - 4) / Math.max(2, impact - 6));
        float shatter = smooth((age - impact - 10) / 10);
        float alpha = (1 - shatter);
        if (emerge <= 0 || alpha <= 0.01f) return;
        Weapon w = switch (kind) { case 1 -> Weapon.HAMMER; case 2 -> Weapon.SPEAR; case 3 -> Weapon.BULWARK; default -> Weapon.GREATSWORD; };
        PoseStack ps = new PoseStack();
        ps.last().pose().set(d.m);
        float sc = size / (kind == 1 ? 1.6f : kind == 2 ? 1.9f : kind == 3 ? 0.9f : 2f);
        Vec3 pos;
        if (kind == 2) {
            // the spear hangs over the caster's shoulder, aims, then the beam fires down its length
            float launch = smooth((age - impact) / 3);
            pos = riftPos.add(a.aim.scale(launch * f.b() + emerge * 1.5 - 1.5));
            ps.translate(pos.x - d.cam.x, pos.y - d.cam.y, pos.z - d.cam.z);
            Vector3f to = new Vector3f((float) a.aim.x, (float) a.aim.y, (float) a.aim.z);
            ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), to));
            ps.translate(0, -size * 0.8, 0);
        } else {
            // descends point-first (or head-first) from the rift onto the ground
            double fall = Math.pow(emerge, 2.2);
            pos = riftPos.add(ground.subtract(riftPos).scale(fall));
            ps.translate(pos.x - d.cam.x, pos.y - d.cam.y, pos.z - d.cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(-a.p.yaw()));
            if (kind == 1) { ps.mulPose(Axis.XP.rotationDegrees(180)); ps.translate(0, -size * 0.75, 0); }
            else if (kind == 3) { ps.mulPose(Axis.XP.rotationDegrees(-90)); ps.translate(0, 0, 0); }
            else { ps.mulPose(Axis.XP.rotationDegrees(180)); ps.translate(0, -size * 0.2, 0); }
        }
        ps.scale(sc, sc, sc);
        if (kind == 3) WeaponModel.renderEthereal(ps, d.buf, null, alpha);
        else WeaponModel.renderEthereal(ps, d.buf, w, alpha);
        if (shatter > 0) for (int i = 0; i < 30; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6 + 0.6, r.nextGaussian()).normalize();
            Vec3 p = pos.add(r.nextGaussian() * size * 0.3, r.nextDouble() * size, r.nextGaussian() * size * 0.3);
            d.voidShard(p.add(dir.scale(shatter * 3)), dir, 0.5f * (1 - shatter), 0.1f, 1 - shatter);
        }
        if (age >= impact && age < impact + 4) d.billboard(Rt.GLOW, true, ground.add(0, 1, 0), Math.min(3f, size * 0.8f), 0xFFFFFF, (1 - (age - impact) / 4) * 0.6f * d.near(ground, 6), 0);
    }

    static void voidDisc(Draw d, Vec3 c, Vec3 n, float r, float alpha) {
        if (r < 0.02f) return;
        VertexConsumer vt = d.buf.getBuffer(Rt.voidTri(Rt.FILL));
        Vec3[] b = Draw.basis(n);
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float j0 = 1 + 0.08f * (float) Math.sin(a0 * 5 + d.time * 2), j1 = 1 + 0.08f * (float) Math.sin(a1 * 5 + d.time * 2);
            Vec3 p0 = c.add(b[0].scale(Math.cos(a0) * r * j0)).add(b[1].scale(Math.sin(a0) * r * j0));
            Vec3 p1 = c.add(b[0].scale(Math.cos(a1) * r * j1)).add(b[1].scale(Math.sin(a1) * r * j1));
            d.vv(vt, c.x, c.y, c.z, 0xFFFFFF, alpha, .5f, .5f); d.vv(vt, p0.x, p0.y, p0.z, 0xFFFFFF, alpha, .5f, .5f); d.vv(vt, p1.x, p1.y, p1.z, 0xFFFFFF, alpha, .5f, .5f);
        }
    }

    /** Many cuts across an area in quick succession (the Iaido / storm moves), ending on one bright burst. */
    private static void rendStorm(Draw d, Prim p, Move.Fx f, float age, Random r) {
        int n = Math.max(1, (int) f.a());
        Vec3 c = p.o.add(p.fwd.scale(Math.max(2.5f, f.c())));
        float every = 0.8f, end = n * every + 3;
        for (int i = 0; i < n; i++) {
            float local = age - i * every;
            Vec3 f0 = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
            Vec3 side = f0.cross(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian())).normalize();
            Vec3 off = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5, r.nextGaussian()).scale(Math.max(1, f.b()) * 0.4);
            float rad = 1.1f + r.nextFloat() * 1.2f;
            float span = 70 + r.nextFloat() * 80;
            if (local < 0) continue;
            slash(d, c.add(off).subtract(f0.scale(rad * 0.8)), f0, side, rad, span, 0.14f + r.nextFloat() * 0.1f, local, r, false);
        }
        float k = (age - end) / 6;
        if (k >= 0 && k < 1) {
            d.billboard(Rt.GLOW, true, c, Math.max(2, f.b()) * 1.4f, 0xFFFFFF, (1 - k) * 0.8f * d.near(c, 2.5f), 0);
            d.flat(Rt.RING, true, c, d.cam.subtract(c).normalize(), 1 + k * Math.max(2, f.b()) * 1.6f, 0xE8DCFF, 1 - k, 0);
        }
    }

    /** A thrown construct, spinning, with a void wake; boomerangs come back to the hand. */
    private static void thrown(Draw d, Act a, Move.Fx f, float age) {
        float speed = Math.max(0.1f, f.a());
        float out = f.b() / speed;
        Vec3 dir = a.aim;
        Vec3 start = a.o.add(a.right.scale(0.3));
        Vec3 pos;
        float t;
        if (age < out) { t = age / out; pos = start.add(dir.scale(f.b() * (1 - (1 - t) * (1 - t)))); }
        else if (f.c() == 1) {
            float back = Mth.clamp((age - out) / out, 0, 1);
            Vec3 hand = a.caster != null ? a.caster.getPosition(d.pt).add(0, a.caster.getBbHeight() * 0.65, 0) : start;
            Vec3 far = start.add(dir.scale(f.b()));
            pos = far.add(hand.subtract(far).scale(back * back));
            if (back >= 1) return;
        } else { float k = (age - out) / 6; if (k > 1) return; pos = start.add(dir.scale(f.b())); d.glowShell(pos, 0.5 + k * 2, 0xFFFFFF, 1 - k); return; }
        boolean scythe = f.d() == 0;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(d.m);
        ps.translate(pos.x - d.cam.x, pos.y - d.cam.y, pos.z - d.cam.z);
        if (scythe) {
            ps.mulPose(Axis.YP.rotationDegrees(-a.p.yaw()));
            ps.mulPose(Axis.XP.rotationDegrees(90));
            ps.mulPose(Axis.ZP.rotationDegrees(age * 48));
            ps.translate(0, -0.6, 0);
            WeaponModel.render(ps, d.buf, Weapon.SCYTHE, 1, 1.3f);
            float spin = age * 48;
            d.flat(Rt.RING, true, pos, new Vec3(0, 1, 0), 1.5f, 0xB58CFF, 0.6f, (float) Math.toRadians(spin));
        } else {
            ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)));
            ps.mulPose(Axis.YP.rotationDegrees(age * 30));
            ps.translate(0, -1.4, 0);
            WeaponModel.render(ps, d.buf, Weapon.SPEAR, 1, 1.3f);
        }
        Vec3 wake = pos.subtract(dir.scale(Math.min(3.5, age * speed)));
        d.voidRibbon(wake, pos, 0.25f, 0.9f);
        d.beam(Rt.STREAK, true, wake, pos, 0.12f, 0xD8C8FF, 0.7f);
    }

    /** A wall of hexagonal void cells in front, assembling outward from the centre. */
    private static void barrier(Draw d, Act a, Move.Fx f, float age) {
        float life = Math.max(4, f.b());
        float in = smooth(age / 4), out = 1 - smooth((age - life) / 8);
        float k = in * out;
        if (k <= 0.01f) return;
        float rad = f.a();
        Vec3 c = a.o.add(a.fwd.scale(1.1 + rad * 0.15));
        Vec3 up = new Vec3(0, 1, 0), side = a.right;
        float cell = 0.42f;
        int n = (int) Math.ceil(rad / cell) + 1;
        VertexConsumer vt = d.buf.getBuffer(Rt.voidTri(Rt.FILL));
        for (int q = -n; q <= n; q++) for (int s = -n; s <= n; s++) {
            double x = cell * 1.5 * q, y = cell * Math.sqrt(3) * (s + q / 2.0);
            double dist = Math.hypot(x, y);
            if (dist > rad) continue;
            float appear = smooth((age - (float) (dist / rad) * 4) / 2) * out;
            if (appear <= 0.01f) continue;
            double bulge = Math.sqrt(Math.max(0, rad * rad * 1.6 - dist * dist)) - rad * 0.9;
            Vec3 cc = c.add(side.scale(x)).add(up.scale(y)).add(a.fwd.scale(bulge * 0.5));
            float cr = cell * 0.92f * appear;
            Vec3 prev = null;
            for (int i = 0; i <= 6; i++) {
                double an = i * Math.PI / 3;
                Vec3 p = cc.add(side.scale(Math.cos(an) * cr)).add(up.scale(Math.sin(an) * cr));
                if (prev != null) {
                    VertexConsumer fl = d.buf.getBuffer(Rt.addTri(Rt.WHITE));
                    d.sv(fl, cc.x, cc.y, cc.z, 0x5A2AB0, 0.18f * appear, .5f, .5f); d.sv(fl, prev.x, prev.y, prev.z, 0x9C6BFF, 0.3f * appear, .5f, .5f); d.sv(fl, p.x, p.y, p.z, 0x9C6BFF, 0.3f * appear, .5f, .5f);
                    d.beam(Rt.STREAK, true, prev, p, 0.035f, 0xC8B0FF, 0.8f * appear);
                }
                prev = p;
            }
        }
    }

    /** Afterimages: void silhouettes peeling off the caster, each one a beat behind. */
    private static void ghosts(Draw d, Act a, Move.Fx f, float age, Random r) {
        int n = Math.max(1, (int) f.a());
        Vec3 base = a.caster != null ? a.caster.getPosition(d.pt) : a.o.subtract(0, 1.2, 0);
        for (int i = 0; i < n; i++) {
            float local = age - i * 1.5f;
            if (local < 0) continue;
            float k = Mth.clamp(local / 12, 0, 1);
            double an = (i - (n - 1) / 2.0) * 0.6;
            Vec3 off = rotY(a.fwd, Math.toDegrees(an) + 180).scale(0.4 + k * 1.4);
            Vec3 p = base.add(off).add(0, 0.95, 0);
            float nearCam = Mth.clamp((float) (p.distanceTo(d.cam) - 1.2) / 1.5f, 0, 1);
            if (nearCam <= 0.01f) continue;
            d.voidBillboard(Rt.BODY_CORE, p, 0.32f, 0.92f, 0.75f * (1 - k) * nearCam, 0);
            d.billboard(Rt.GLOW, true, p, 1.1f, 0x7A45E0, 0.35f * (1 - k) * nearCam, 0);
        }
    }

    /** The hit: a tight white star with thin spikes and a ring snapping outward (no big pale disc). */
    private static void impact(Draw d, Impact i) {
        float age = ClientState.ticks - i.born() + d.pt;
        float life = i.kind() >= 2 ? 9f : 6f;
        float k = Mth.clamp(age / life, 0, 1), out = 1 - k;
        Random r = new Random(i.seed());
        float s = i.kind() >= 2 ? 1.7f : i.kind() == 1 ? 1.3f : 1f;
        float near = d.near(i.at(), 1.2f);
        if (age < 2.5f) d.billboard(Rt.GLOW, true, i.at(), 0.55f * s, 0xFFFFFF, (1 - age / 2.5f) * near, 0);
        d.billboard(Rt.SPARK, true, i.at(), 0.75f * s * (0.7f + 0.6f * k), 0xFFFFFF, out * near, r.nextFloat() * 6);
        int n = 5 + i.kind() * 3;
        for (int j = 0; j < n; j++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            Vec3 a = i.at().add(dir.scale(0.15 + k * 0.9 * s));
            d.beam(Rt.STREAK, true, a, a.add(dir.scale(0.45 * s * out)), 0.035f, j % 2 == 0 ? 0xFFFFFF : 0xC8B0FF, out * near);
        }
        d.billboard(Rt.RING, true, i.at(), s * (0.35f + k * 1.4f), 0xE8DCFF, out * 0.9f * near, 0);
    }

    // ------------------------------------------------------------------------------------------------- the constructs in hand
    /** Swing keyframes per pose: start/end yaw (deg, + = right) and pitch (deg, + = up), and a reach push. */
    static float[] swing(int pose, float s) {
        float ys, ye, ps0, pe, push = 0;
        switch (pose) {
            case Moves.P_SLASH_RL -> { ys = 85; ye = -85; ps0 = 5; pe = -15; }
            case Moves.P_SLASH_LR -> { ys = -85; ye = 85; ps0 = 5; pe = -15; }
            case Moves.P_OVERHEAD, Moves.P_SLAM -> { ys = 0; ye = 0; ps0 = 115; pe = -55; }
            case Moves.P_RISING -> { ys = 10; ye = 0; ps0 = -55; pe = 120; }
            case Moves.P_THRUST, Moves.P_BASH -> { ys = 0; ye = 0; ps0 = 0; pe = 0; push = (float) Math.sin(Math.PI * Math.min(1, s * 1.6)) * 0.7f; }
            case Moves.P_SPIN -> { ys = 0; ye = -360; ps0 = -5; pe = -5; }
            case Moves.P_PUNCH_R, Moves.P_PUNCH_L -> { ys = 0; ye = 0; ps0 = 0; pe = 0; push = (float) Math.sin(Math.PI * Math.min(1, s * 2)) * 0.5f; }
            case Moves.P_RAISE, Moves.P_CAST -> { ys = 0; ye = 0; ps0 = 30; pe = 95; }
            case Moves.P_BLOCK -> { ys = -10; ye = -10; ps0 = 70; pe = 70; }
            case Moves.P_THROW -> { ys = 20; ye = 0; ps0 = 120; pe = -10; }
            case Moves.P_PLUNGE -> { ys = 0; ye = 0; ps0 = 90; pe = -90; }
            case Moves.P_FLURRY -> { float w = (float) Math.sin(s * Math.PI * 5); return new float[]{w * 75, -5 + 15 * (float) Math.cos(s * Math.PI * 5), 0.15f}; }
            case Moves.P_DRAW -> { // iaido: hold at the hip, then one flash
                if (s < 0.6f) return new float[]{-150, -55, 0};
                float k = smooth((s - 0.6f) / 0.12f); return new float[]{-150 + k * 240, -55 + k * 50, 0};
            }
            default -> { return new float[]{30, -50, 0}; }
        }
        float e = 1 - (float) Math.pow(1 - Mth.clamp(s * 1.25f, 0, 1), 3);
        return new float[]{Mth.lerp(e, ys, ye), Mth.lerp(e, ps0, pe), push};
    }

    private static void constructs(Draw d) {
        var mc = Minecraft.getInstance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (ClientState.formOf(e.getId()) != Ability.ARSENAL.ordinal()) continue;
            Weapon w = Weapon.byIndex(ClientState.weaponOf(e.getId()));
            long[] fg = FORGED.get(e.getId());
            float forge = fg == null ? 1 : smooth((ClientState.ticks - fg[0] + d.pt) / 10f);
            boolean me = e == mc.player;
            float bodyYaw = Mth.rotLerp(d.pt, le.yBodyRotO, le.yBodyRot);
            float headYaw = Mth.rotLerp(d.pt, le.yHeadRotO, le.yHeadRot);
            float pitch = Mth.lerp(d.pt, le.xRotO, le.getXRot());
            Act act = SWING.get(e.getId());
            float yaw = act != null ? act.p.yaw() : headYaw;
            Vec3 fwd = Vec3.directionFromRotation(0, yaw);
            Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
            Vec3 pos = e.getPosition(d.pt);
            Vec3 shoulder = pos.add(0, le.getBbHeight() * 0.74, 0).add(right.scale(0.36));
            if (me && firstPerson) shoulder = d.cam.add(right.scale(0.45)).add(0, -0.45, 0).add(Vec3.directionFromRotation(pitch, headYaw).scale(0.25));
            float[] sw;
            if (act != null && act.clock + d.pt < act.m.duration() + act.m.recover()) {
                float s = (act.clock + (act.stop > 0 ? 0 : d.pt)) / Math.max(1, act.m.duration());
                sw = swing(act.m.pose(), Mth.clamp(s, 0, 1));
                float back = smooth((act.clock + d.pt - act.m.duration()) / Math.max(1, act.m.recover()));
                float[] rest = swing(0, 0);
                if (back > 0) for (int i = 0; i < 3; i++) sw[i] = Mth.lerp(back, sw[i], rest[i]);
                if (act.m.pose() == Moves.P_THROW && s > 0.35f && w != Weapon.GAUNTLETS) continue; // it's in the air
            } else {
                sw = swing(0, 0);
                if (me && firstPerson) sw = new float[]{25, -25 + pitch * -0.3f, 0};
                if (w == Weapon.BULWARK && ClientState.me.getBoolean("guarding") && me) sw = swing(Moves.P_BLOCK, 0);
            }
            float idle = (float) Math.sin((ClientState.ticks + d.pt) * 0.08 + e.getId()) * 3;
            Vec3 dir = rotY(fwd, sw[0]).scale(Math.cos(Math.toRadians(sw[1] + idle))).add(0, Math.sin(Math.toRadians(sw[1] + idle)), 0).normalize();
            Vec3 hand = shoulder.add(dir.scale(0.28 + sw[2]));
            renderHeld(d, w, hand, dir, right, forge, e.getId(), fwd, false);
            if (w == Weapon.BULWARK) {
                Vec3 lsh = pos.add(0, le.getBbHeight() * 0.68, 0).subtract(right.scale(0.42)).add(fwd.scale(0.25));
                boolean guarding = me && ClientState.me.getBoolean("guarding");
                if (me && firstPerson) lsh = d.cam.subtract(right.scale(0.5)).add(0, -0.4, 0).add(Vec3.directionFromRotation(pitch, headYaw).scale(0.55));
                if (guarding || (act != null && (act.m.pose() == Moves.P_BLOCK || act.m.pose() == Moves.P_BASH))) lsh = (me && firstPerson ? d.cam.add(Vec3.directionFromRotation(pitch, headYaw).scale(0.8)).add(0, -0.25, 0) : pos.add(0, le.getBbHeight() * 0.62, 0).add(fwd.scale(0.6)));
                renderShield(d, lsh, fwd, forge * (guarding ? 1 : 0.9f));
            }
            if (w == Weapon.GAUNTLETS) {
                Vec3 lsh = pos.add(0, le.getBbHeight() * 0.74, 0).subtract(right.scale(0.36));
                if (me && firstPerson) lsh = d.cam.subtract(right.scale(0.45)).add(0, -0.45, 0).add(Vec3.directionFromRotation(pitch, headYaw).scale(0.25));
                float push = act != null && act.m.pose() == Moves.P_PUNCH_L ? sw[2] : 0;
                Vec3 ldir = rotY(fwd, -25).scale(0.8).add(0, -0.4, 0).normalize();
                renderHeld(d, w, lsh.add(ldir.scale(0.28 + push)), ldir, right, forge, e.getId(), fwd, true);
            }
        }
    }

    private static void renderHeld(Draw d, Weapon w, Vec3 hand, Vec3 dir, Vec3 right, float forge, int id, Vec3 fwd, boolean mirror) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(d.m);
        ps.translate(hand.x - d.cam.x, hand.y - d.cam.y, hand.z - d.cam.z);
        Vector3f to = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), to);
        // turn the blade's flat so its edge leads the swing
        Vector3f bx = new Vector3f(1, 0, 0).rotate(q);
        Vector3f wantSide = new Vector3f((float) fwd.x, (float) fwd.y, (float) fwd.z).cross(to);
        if (wantSide.lengthSquared() > 1e-4f) {
            wantSide.normalize();
            float ang = (float) Math.atan2(new Vector3f(bx).cross(wantSide).dot(to), bx.dot(wantSide));
            q = new Quaternionf().rotateAxis(ang + (float) Math.PI / 2, to).mul(q);
        }
        ps.mulPose(q);
        ps.scale(1.3f, 1.3f, 1.3f);
        if (mirror) ps.scale(-1, 1, 1);
        float glow = 0.9f + 0.3f * (float) Math.sin(ClientState.ticks * 0.15 + id);
        WeaponModel.render(ps, d.buf, w, forge, glow);
        if (forge < 1) {
            // forging: violet motes condense onto the blade
            Random r = new Random(id * 7L + (long) (ClientState.ticks / 2));
            for (int i = 0; i < 10; i++) {
                Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(1.2 * (1 - forge));
                Vec3 along = hand.add(dir.scale(r.nextDouble() * 1.2));
                d.beam(Rt.STREAK, true, along.add(o), along.add(o.scale(0.6)), 0.04f, 0xC8B0FF, 1 - forge);
            }
            d.billboard(Rt.GLOW, true, hand.add(dir.scale(0.6)), 1.2f, 0x9C6BFF, 0.6f * (1 - forge) * d.near(hand, 1.5f), 0);
        }
    }

    private static void renderShield(Draw d, Vec3 at, Vec3 fwd, float alpha) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(d.m);
        ps.translate(at.x - d.cam.x, at.y - d.cam.y, at.z - d.cam.z);
        ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) fwd.x, 0, (float) fwd.z)));
        ps.mulPose(Axis.ZP.rotationDegrees((float) (ClientState.ticks % 360) * 0.5f));
        WeaponModel.renderShield(ps, d.buf, alpha, 1.1f);
    }

    /**
     * The construct's swing for an entity right now, in its own frame: {yaw (deg, + = right), pitch (deg, + = up),
     * push (blocks), hidden (1 while thrown), forge alpha 0..1, pose}. First person uses its own resting angle.
     */
    public static float[] swingOf(int id, float pt, boolean firstPerson) {
        long[] fg = FORGED.get(id);
        float forge = fg == null ? 1 : smooth((ClientState.ticks - fg[0] + pt) / 10f);
        Act act = SWING.get(id);
        float[] rest = firstPerson ? new float[]{-12, 38, 0} : swing(0, 0);
        if (act == null || act.clock + pt >= act.m.duration() + act.m.recover()) return new float[]{rest[0], rest[1], rest[2], 0, forge, 0};
        float s = (act.clock + (act.stop > 0 ? 0 : pt)) / Math.max(1, act.m.duration());
        float[] sw = swing(act.m.pose(), Mth.clamp(s, 0, 1));
        if (firstPerson) sw[1] += 18; // keep the blade in view: it sweeps across the screen, not into the distance
        float back = smooth((act.clock + pt - act.m.duration()) / Math.max(1, act.m.recover()));
        if (back > 0) for (int i = 0; i < 3; i++) sw[i] = Mth.lerp(back, sw[i], rest[i]);
        boolean hidden = act.m.pose() == Moves.P_THROW && s > 0.35f && act.w != Weapon.GAUNTLETS && back < 0.9f;
        return new float[]{sw[0], sw[1], sw[2], hidden ? 1 : 0, forge, act.m.pose()};
    }

    // ------------------------------------------------------------------------------------------------- numbers
    public static void renderNumbers(PoseStack ps, Camera cam, float pt) {
        if (NUMS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        Font font = mc.font;
        var buf = mc.renderBuffers().bufferSource();
        Vec3 c = cam.getPosition();
        for (Num n : NUMS) {
            float age = ClientState.ticks - n.born() + pt;
            float pop = age < 3 ? 1 + (1 - age / 3) * 0.8f : 1;
            float alpha = age > 18 ? 1 - (age - 18) / 8 : 1;
            if (alpha <= 0.02f) continue;
            Vec3 p = n.pos().add(n.dx() * age, 0.04 * age - 0.0012 * age * age, n.dz() * age);
            ps.pushPose();
            ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
            ps.mulPose(cam.rotation());
            float s = 0.028f * n.scale() * pop * (float) Math.max(1, p.distanceTo(c) / 10);
            ps.scale(-s, -s, s);
            int a = (int) (Mth.clamp(alpha, 0, 1) * 255) << 24;
            float x = -font.width(n.text()) / 2f;
            font.drawInBatch(n.text(), x + 1, 1, a | 0x1A0830, false, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            font.drawInBatch(n.text(), x, 0, a | n.color(), false, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }

    private MoveFx() {}
}
