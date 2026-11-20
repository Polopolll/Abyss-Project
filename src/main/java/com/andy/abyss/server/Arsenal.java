package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.combat.Move;
import com.andy.abyss.combat.Moves;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.C2SInput;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CHit;
import com.andy.abyss.net.S2CMove;
import com.andy.abyss.net.S2CVisual;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * The Abyssal Arsenal, server side. Moves come from {@link Moves}: the press starts a timeline, hits land at their ticks
 * in the caster's frame (current position, facing at the press), and every client plays the same timeline. Presses
 * during a move are buffered (one), so chains flow. Nothing here moves the caster except the dodge.
 */
public final class Arsenal {
    public static final int BASIC = 0, HEAVY = 1, PLUNGE = 2, COUNTER = 3, SKILL = 4, LIB = 5;

    public static Weapon weapon(AbyssData d) { return Weapon.byIndex(d.weapon); }

    public static void input(ServerPlayer p, int button, int phase, int flags, int arg) {
        AbyssData d = AbyssData.get(p);
        long now = p.level().getGameTime();
        switch (button) {
            case C2SInput.LMB -> {
                if (phase == C2SInput.PRESS) {
                    if ((flags & C2SInput.AIR) != 0 && !p.onGround()) request(p, PLUNGE);
                    else if (now <= d.counterUntil) { d.counterUntil = 0; request(p, COUNTER); }
                } else if (phase == C2SInput.RELEASE) {
                    if (d.guardSince >= 0) { guard(p, false); request(p, HEAVY); return; }
                    if ((flags & C2SInput.AIR) != 0 && !p.onGround()) return; // the plunge already went on the press
                    request(p, arg >= 8 ? HEAVY : BASIC);
                }
            }
            case C2SInput.RMB -> { if (phase == C2SInput.PRESS) dodge(p, arg); }
            case C2SInput.M4 -> { if (phase == C2SInput.PRESS) request(p, SKILL); }
            case C2SInput.M5 -> { if (phase == C2SInput.PRESS) request(p, LIB); }
            case C2SInput.SCROLL -> switchWeapon(p, arg);
        }
    }

    public static void switchWeapon(ServerPlayer p, int dir) {
        AbyssData d = AbyssData.get(p);
        d.weapon = Math.floorMod(d.weapon + (dir >= 0 ? 1 : -1), Weapon.values().length);
        d.comboStep = 0; d.pending = -1; d.busyUntil = 0; d.dirty = true;
        Weapon w = weapon(d);
        Util.fx(p.serverLevel(), S2CFx.FORGE, p.position().add(0, 1, 0), d.weapon, 0, 0, p.getId(), -1);
        Util.sound(p.serverLevel(), p.position(), ModSounds.SUMMON.get(), 0.9f, 1.3f);
        p.displayClientMessage(Component.literal("§5Forged: §f" + w.title), true);
        Net.around(p, new S2CVisual(p.getId(), true, d.form, d.weapon));
    }

    private static void request(ServerPlayer p, int kind) {
        AbyssData d = AbyssData.get(p);
        long now = p.level().getGameTime();
        if (now < d.busyUntil) { d.pending = kind; return; }
        play(p, kind);
    }

    /** Called every player tick: run a buffered press once the current move allows it. */
    public static void tick(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (d.pending >= 0 && p.level().getGameTime() >= d.busyUntil) { int k = d.pending; d.pending = -1; play(p, k); }
        if (d.guardSince >= 0 && p.level().getGameTime() % 5 == 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 1, false, false, false));
    }

    private static void play(ServerPlayer p, int kind) {
        AbyssData d = AbyssData.get(p);
        Weapon w = weapon(d);
        Moves.Set set = Moves.of(w);
        long now = p.level().getGameTime();
        Move m;
        switch (kind) {
            case HEAVY -> m = set.heavy();
            case PLUNGE -> m = set.plunge();
            case COUNTER -> m = set.counter();
            case SKILL -> m = set.skill();
            case LIB -> {
                if (d.gauge < 100 && !d.isInfinite()) { p.displayClientMessage(Component.literal("§5Resonance " + (int) d.gauge + "/100"), true); return; }
                m = set.liberation();
            }
            default -> {
                if (now > d.chainUntil) d.comboStep = 0;
                m = set.basic().get(d.comboStep % set.basic().size());
                d.comboStep = (d.comboStep + 1) % set.basic().size();
            }
        }
        run(p, m, kind);
    }

    /** Play one specific move (the /abyss demo command): opens the Arsenal on that weapon first. */
    public static void demo(ServerPlayer p, Weapon w, int index) {
        AbyssData d = AbyssData.get(p);
        if (d.form != com.andy.abyss.data.Ability.ARSENAL.ordinal()) Forms.toggle(p, com.andy.abyss.data.Ability.ARSENAL);
        if (d.weapon != w.ordinal()) { d.weapon = w.ordinal(); Net.around(p, new S2CVisual(p.getId(), true, d.form, d.weapon)); }
        Moves.Set set = Moves.of(w);
        int nb = set.basic().size();
        int kind = index < nb ? BASIC : index == nb ? HEAVY : index == nb + 1 ? PLUNGE : index == nb + 2 ? COUNTER : index == nb + 3 ? SKILL : LIB;
        Move m = Moves.byIndex(w, index);
        if (m == null) return;
        d.busyUntil = 0;
        run(p, m, kind);
    }

    private static void run(ServerPlayer p, Move m, int kind) {
        AbyssData d = AbyssData.get(p);
        Weapon w = weapon(d);
        long now = p.level().getGameTime();
        if (m.cost() > 0 && !d.spend(m.cost())) { Actions.noOutput(p); return; }
        if (kind == LIB && !d.isInfinite()) d.gauge = 0;
        if (kind != BASIC) d.comboStep = 0;
        float yaw = p.getYRot(), pitch = p.getXRot();
        Vec3 origin = p.position().add(0, 1.1, 0);
        LivingEntity target = lock(p, w.reach * 2.4f + 4);
        double groundY = ground(p);
        int idx = Moves.indexOf(w, m);
        Net.around(p, new S2CMove(p.getId(), w.ordinal(), idx, yaw, pitch, origin.x, origin.y, origin.z, target != null ? target.getId() : -1, groundY, d.guardStored));
        boolean[] gave = {false};
        float stored = d.guardStored;
        d.guardStored = 0;
        for (Move.Hit h : m.hits()) {
            Scheduler.later(h.tick(), () -> {
                if (!p.isAlive() || p.level() != p.serverLevel()) return;
                int landed = apply(p, m, h, yaw, pitch, target, groundY, kind, stored);
                if (landed > 0 && !gave[0]) { gave[0] = true; AbyssData.get(p).gauge = Math.min(100, AbyssData.get(p).gauge + m.gauge()); AbyssData.get(p).dirty = true; }
            });
        }
        // the physical part of the move: craters, trenches, the time-stop (same ticks the clients draw them)
        for (Move.Fx f : m.fx()) {
            if (f.kind() != Move.Kind.CRATER && f.kind() != Move.Kind.TRENCH && f.kind() != Move.Kind.LUNGE) continue;
            Scheduler.later(f.tick(), () -> { if (p.isAlive() && p.level() == p.serverLevel()) physical(p, f, yaw, target, groundY); });
        }
        d.busyUntil = now + Math.max(4, m.duration() - m.recover());
        d.chainUntil = now + m.duration() + 18;
        d.dirty = true;
        Actions.mastery(p, 0.01f);
    }

    private static void physical(ServerPlayer p, Move.Fx f, float yaw, LivingEntity target, double groundY) {
        ServerLevel l = p.serverLevel();
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 o = p.position().add(0, 1.1, 0);
        switch (f.kind()) {
            case CRATER -> {
                Vec3 c;
                if (f.d() == 1 && target != null && target.isAlive()) c = target.position();
                else if (f.d() == 2) c = new Vec3(p.getX(), groundY, p.getZ());
                else c = new Vec3(o.x + fwd.x * f.c(), groundY, o.z + fwd.z * f.c());
                // the ground where it lands: drop from the point to the first solid block
                var hit = l.clip(new ClipContext(c.add(0, 2, 0), c.subtract(0, 8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (hit.getType() != HitResult.Type.MISS) c = hit.getLocation();
                Vec3 at = c.subtract(0, 0.5, 0);
                keepFooting(p, () -> Destruction.crater(l, at, f.a(), f.b(), f.e() <= 0 ? 6 : f.e(), f.f() <= 0 ? 0.5f : f.f()));
            }
            case TRENCH -> {
                Vec3 start = new Vec3(o.x + fwd.x * f.d(), groundY - 0.5, o.z + fwd.z * f.d());
                keepFooting(p, () -> Destruction.trench(l, start, fwd, f.a(), f.b(), f.c(), f.e() <= 0 ? 6 : f.e(), f.f() <= 0 ? 0.4f : f.f()));
            }
            case LUNGE -> lunge(p, f, yaw, target);
            default -> {}
        }
    }

    /**
     * Close the distance: carried toward the target (or straight ahead) over a few ticks, stopping short of it. The
     * push is spread over the ticks so the camera glides instead of jolting.
     */
    private static void lunge(ServerPlayer p, Move.Fx f, float yaw, LivingEntity target) {
        Vec3 dir;
        double dist;
        if (target != null && target.isAlive()) {
            Vec3 to = target.position().subtract(p.position()).multiply(1, 0, 1);
            dist = Math.min(f.a(), to.length() - f.b());
            dir = to.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, yaw) : to.normalize();
        } else { dir = Vec3.directionFromRotation(0, yaw); dist = f.a() * 0.5; }
        if (dist < 0.3) return;
        int n = Math.max(1, (int) f.c());
        // blocks per tick for n ticks, with ground friction undone (~0.546 kept per tick on the ground)
        double per = dist / n;
        for (int i = 0; i < n; i++) {
            int k = i;
            Scheduler.later(k, () -> {
                if (!p.isAlive()) return;
                double ease = 1.15 - 0.3 * k / Math.max(1, n - 1); // a touch faster at the start
                Vec3 v = dir.scale(per * ease / (p.onGround() ? 0.91 : 0.98));
                p.setDeltaMovement(v.x, Math.max(p.getDeltaMovement().y, p.onGround() ? 0.02 : p.getDeltaMovement().y), v.z);
                p.hurtMarked = true;
            });
        }
    }

    /** Never dig the vessel into its own hole: the column it stands on survives. */
    private static void keepFooting(ServerPlayer p, Runnable blast) {
        ServerLevel l = p.serverLevel();
        net.minecraft.core.BlockPos feet = p.blockPosition().below();
        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> keep = new java.util.HashMap<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy >= -2; dy--) {
            var bp = feet.offset(dx, dy, dz);
            keep.put(bp, l.getBlockState(bp));
        }
        blast.run();
        keep.forEach((bp, st) -> { if (!st.isAir() && l.getBlockState(bp).isAir()) l.setBlock(bp, st, 2); });
    }

    /** Aim target within range (aim, then a forward cone, then — in your domain — anything inside). */
    static LivingEntity lock(ServerPlayer p, float range) {
        LivingEntity t = Util.aim(p, range).entity();
        if (t != null) return t;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), x -> x != p && x.isAlive() && !x.isSpectator())) {
            if (Util.friend(p.getUUID(), e)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double dd = to.length();
            if (dd > range || to.normalize().dot(look) < 0.7) continue;
            if (dd < bd) { bd = dd; best = e; }
        }
        return best;
    }

    private static double ground(ServerPlayer p) {
        var hit = p.level().clip(new ClipContext(p.position(), p.position().subtract(0, 64, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.MISS ? p.getY() - 64 : hit.getLocation().y;
    }

    private static float base(ServerPlayer p, Weapon w) {
        AbyssData d = AbyssData.get(p);
        return (float) (Math.max(6, p.getAttributeValue(Attributes.ATTACK_DAMAGE)) * (1 + d.mastery / 200f) * Config.comboDamage.get() * (0.8f + w.weight * 0.25f));
    }

    private static int apply(ServerPlayer p, Move m, Move.Hit h, float yaw, float pitch, LivingEntity target, double groundY, int kind, float stored) {
        ServerLevel l = p.serverLevel();
        Weapon w = weapon(AbyssData.get(p));
        Vec3 o = p.position().add(0, 1.1, 0);
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 aim = Vec3.directionFromRotation(pitch, yaw);
        Set<LivingEntity> victims = new HashSet<>();
        switch (h.shape()) {
            case ARC -> {
                double half = Math.toRadians(h.b());
                Vec3 c = rotY(fwd, Math.toRadians(h.c()));
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(h.a() + 1.5), x -> x != p && x.isAlive())) {
                    Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(o);
                    double flat = Math.sqrt(to.x * to.x + to.z * to.z);
                    if (flat - e.getBbWidth() / 2 > h.a() || Math.abs(to.y) > 2.6 + e.getBbHeight() / 2) continue;
                    if (h.b() < 180 && flat > 0.6 && Math.acos(Math.max(-1, Math.min(1, (to.x * c.x + to.z * c.z) / flat))) > half) continue;
                    victims.add(e);
                }
            }
            case LINE -> {
                Vec3 dir = h.a() > 12 ? aim : fwd;
                Vec3 a = o.add(dir.scale(h.c())), b = o.add(dir.scale(h.a()));
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(h.b()), x -> x != p && x.isAlive()))
                    if (e.getBoundingBox().inflate(h.b() / 2).clip(a, b).isPresent()) victims.add(e);
            }
            case CIRCLE -> {
                Vec3 c;
                if (h.c() == 1 && target != null && target.isAlive()) c = target.position().add(0, target.getBbHeight() / 2, 0);
                else if (h.c() == 2) c = new Vec3(p.getX(), groundY + 0.5, p.getZ());
                else c = o.add(fwd.scale(h.b()));
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(h.a() + 1), x -> x != p && x.isAlive()))
                    if (e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(c) <= h.a() + e.getBbWidth() / 2) victims.add(e);
            }
            case TARGET -> { if (target != null && target.isAlive() && target.distanceTo(p) <= h.a() + 2) victims.add(target); }
        }
        int landed = 0;
        float dmg = base(p, w) * h.dmg() * (kind == HEAVY && stored > 0 ? 1 + Math.min(2, stored / 20f) : 1);
        int hitKind = kind == LIB || kind == COUNTER ? 2 : kind == HEAVY || kind == SKILL || h.dmg() >= 2 ? 1 : 0;
        for (LivingEntity e : victims) {
            if (Util.friend(p.getUUID(), e)) continue;
            e.invulnerableTime = 0;
            if (!Util.hurt(p.getUUID(), p, e, dmg, "abyss")) continue;
            landed++;
            Corrosion.add(e, 1, 160);
            knock(p, e, h, o, fwd);
            Net.around(e, new S2CHit(p.getId(), e.getId(), dmg, hitKind));
        }
        return landed;
    }

    private static void knock(ServerPlayer p, LivingEntity e, Move.Hit h, Vec3 o, Vec3 fwd) {
        double k = h.kb() * (1 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.7);
        if (k <= 0) return;
        Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? fwd : away.normalize();
        Vec3 v = switch (h.knock()) {
            case PUSH -> away.scale(k * 0.9).add(0, 0.15 * k, 0);
            case LAUNCH -> new Vec3(0, 0.75 * k + 0.2, 0).add(away.scale(0.1));
            case PULL -> p.position().subtract(e.position()).normalize().scale(0.55 * k).add(0, 0.1, 0);
            case DOWN -> new Vec3(0, -1.1 * k, 0);
            case SPIN -> rotY(away, Math.PI / 2).scale(0.6 * k).add(0, 0.1, 0);
            default -> Vec3.ZERO;
        };
        e.setDeltaMovement(e.getDeltaMovement().scale(0.3).add(v));
        e.hurtMarked = true;
        if (h.knock() == Move.Knock.DOWN) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, false, false));
    }

    static Vec3 rotY(Vec3 v, double a) { double c = Math.cos(a), s = Math.sin(a); return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c); }

    // ------------------------------------------------------------------ dodge, perfect dodge, guard

    /** RMB: dash with your movement input (back if none). For a moment you can't be touched; get hit then and it's perfect. */
    private static void dodge(ServerPlayer p, int input) {
        AbyssData d = AbyssData.get(p);
        long now = p.level().getGameTime();
        if (now < d.nextDodge) return;
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot()), r = new Vec3(-f.z, 0, f.x);
        Vec3 dir = Vec3.ZERO;
        if ((input & 1) != 0) dir = dir.add(f);
        if ((input & 2) != 0) dir = dir.subtract(f);
        if ((input & 4) != 0) dir = dir.subtract(r);
        if ((input & 8) != 0) dir = dir.add(r);
        if (dir.lengthSqr() < 1e-4) dir = f.scale(-1);
        dir = dir.normalize();
        p.setDeltaMovement(dir.scale(p.onGround() ? 1.25 : 0.8).add(0, p.onGround() ? 0.18 : 0.05, 0));
        p.hurtMarked = true;
        d.nextDodge = now + 12;
        d.dodgeUntil = now + 7;
        Util.fx(p.serverLevel(), S2CFx.DODGE, p.position().add(0, 1, 0), (float) dir.x, (float) dir.z, 0, p.getId(), -1);
        Util.sound(p.serverLevel(), p.position(), ModSounds.DODGE.get(), 1f, 1f);
    }

    /** Called when the vessel would be hit during the dodge window. Returns true if the hit is avoided. */
    public static boolean dodged(ServerPlayer p, boolean byAttacker) {
        AbyssData d = AbyssData.get(p);
        long now = p.level().getGameTime();
        if (now > d.dodgeUntil) return false;
        if (byAttacker && d.counterUntil < now) {
            d.counterUntil = now + 50;
            d.dodgeUntil = now + 12;
            Util.fx(p.serverLevel(), S2CFx.PERFECT_DODGE, p.position().add(0, 1, 0), 0, 0, 0, p.getId(), -1);
            Util.sound(p.serverLevel(), p.position(), ModSounds.PERFECT.get(), 1.5f, 1f);
            d.gauge = Math.min(100, d.gauge + 10);
            d.dirty = true;
        }
        return true;
    }

    private static void guard(ServerPlayer p, boolean on) {
        AbyssData d = AbyssData.get(p);
        d.guardSince = on ? p.level().getGameTime() : -1;
        Util.fx(p.serverLevel(), S2CFx.GUARD, p.position().add(0, 1, 0), on ? 1 : 0, 0, 0, p.getId(), -1);
        if (on) d.guardStored = 0;
    }

    /** Bulwark guard: hits from the front are absorbed (and stored for the Retribution bash). */
    public static boolean guarded(ServerPlayer p, net.minecraft.world.damagesource.DamageSource src, float amount) {
        AbyssData d = AbyssData.get(p);
        if (d.guardSince < 0 || d.form != com.andy.abyss.data.Ability.ARSENAL.ordinal()) return false;
        Vec3 from = src.getSourcePosition();
        if (from == null) return false;
        Vec3 to = from.subtract(p.position()).multiply(1, 0, 1).normalize();
        if (to.dot(Vec3.directionFromRotation(0, p.getYRot())) < 0.2) return false;
        d.guardStored += amount;
        Util.fx(p.serverLevel(), S2CFx.GUARD, p.position().add(0, 1, 0), 2, 0, 0, p.getId(), -1);
        Util.sound(p.serverLevel(), p.position(), ModSounds.HIT.get(), 1f, 0.7f);
        return true;
    }
    private Arsenal() {}
}
