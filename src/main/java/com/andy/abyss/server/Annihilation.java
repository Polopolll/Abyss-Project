package com.andy.abyss.server;

import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.C2SInput;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Annihilation: the abyss's raw power, Ronova-style, made abyssal.
 * <ul>
 *   <li>LMB — Annihilation Beam: tap = a piercing shot; hold = a sustained black beam (128 blocks) that tunnels
 *   through the world and unmakes what it kills.</li>
 *   <li>RMB — Abyss Orb: tap = one slow, dense orb of darkness that devours what it touches and bursts; hold = a stream.</li>
 *   <li>M4 — Sovereign Pressure: the abyss's weight presses on everything around you (hold to keep pressing).</li>
 *   <li>M5 — Abyssal Nuke: hold to charge (up to 3 s) a core of compressed abyss; release to launch it.</li>
 * </ul>
 * Everything killed by these is unmade into abyssal ash (damage type inside_out).
 */
public final class Annihilation {
    static final class St { long beam = -1, orb = -1, press = -1, nuke = -1; Vec3 beamEnd; }
    static final class Orb { ServerPlayer owner; ServerLevel l; Vec3 pos, vel; float r; boolean big; int id, age; }
    private static final Map<UUID, St> STATE = new HashMap<>();
    private static final List<Orb> ORBS = new ArrayList<>();
    private static int nextOrb = 1;

    static St st(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), u -> new St()); }

    /** Where the abyss's power leaves you: a rift above your right shoulder (seen beside the view, not out of the eye). */
    public static Vec3 mount(ServerPlayer p) {
        Vec3 look = p.getLookAngle(), right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        return p.getEyePosition().add(right.scale(1.1)).add(0, 0.55, 0).add(look.scale(2.2));
    }

    public static void input(ServerPlayer p, int button, int phase, int flags, int arg) {
        St s = st(p);
        long now = p.level().getGameTime();
        boolean press = phase == C2SInput.PRESS, release = phase == C2SInput.RELEASE;
        switch (button) {
            case C2SInput.LMB -> {
                if (press) s.beam = now;
                else if (release) { if (arg < 8) shot(p); s.beam = -1; }
            }
            case C2SInput.RMB -> {
                if (press) { s.orb = now; orb(p, true); }
                else if (release) s.orb = -1;
            }
            case C2SInput.M4 -> {
                if (press) { s.press = now; pressure(p, true); }
                else if (release) s.press = -1;
            }
            case C2SInput.M5 -> {
                if (press) {
                    if (!AbyssData.get(p).spend(15)) { Actions.noOutput(p); return; }
                    s.nuke = now;
                    Util.fx(p.serverLevel(), S2CFx.NUKE_CHARGE, p.position(), 1, 0, 0, p.getId(), -1);
                    Util.sound(p.serverLevel(), p.position(), ModSounds.CHARGE_UP.get(), 1.4f, 0.55f);
                } else if (release && s.nuke >= 0) {
                    float k = Math.min(1, (now - s.nuke) / 60f);
                    s.nuke = -1;
                    launchNuke(p, k);
                }
            }
        }
    }

    /** The form closed (or the vessel left): stop everything that's held. */
    public static void stop(ServerPlayer p) {
        St s = STATE.remove(p.getUUID());
        if (s != null && s.nuke >= 0) Util.fx(p.serverLevel(), S2CFx.NUKE_CHARGE, p.position(), 0, 0, 0, p.getId(), -1);
    }

    // ------------------------------------------------------------------------------------------------ beam
    private static Vec3 beamEnd(ServerPlayer p, double range, boolean throughBlocks) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 far = eye.add(look.scale(range));
        if (throughBlocks) return far;
        var hit = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
    }

    /** Tap: a quick piercing shot (stops at the first wall, blows a small hole in it). */
    private static void shot(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (!d.spend(8)) { Actions.noOutput(p); return; }
        ServerLevel l = p.serverLevel();
        Vec3 end = beamEnd(p, 64, false);
        hurtLine(p, mount(p), end, 1.2, 28);
        Destruction.sphere(l, end, 1.6f, 6, 0.35f);
        Util.fx(l, S2CFx.BEAM_SHOT, end, 0, 0, 0, p.getId(), -1);
        Util.sound(l, p.position(), ModSounds.HIT_HEAVY.get(), 1.4f, 1.3f);
    }

    private static void hurtLine(ServerPlayer p, Vec3 a, Vec3 b, double width, float dmg) {
        ServerLevel l = p.serverLevel();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(width), x -> x != p && x.isAlive())) {
            if (Util.friend(p.getUUID(), e)) continue;
            if (e.getBoundingBox().inflate(width * 0.5).clip(a, b).isEmpty()) continue;
            e.invulnerableTime = 0;
            Util.hurt(p.getUUID(), p, e, dmg, "inside_out");
        }
        // projectiles in the path simply stop existing
        for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(a, b).inflate(width), x -> x.getOwner() != p))
            if (pr.getBoundingBox().inflate(width).clip(a, b).isPresent()) pr.discard();
    }

    private static void beamTick(ServerPlayer p, St s, long now) {
        if (s.beam < 0 || now - s.beam < 8) return;
        AbyssData d = AbyssData.get(p);
        if (!d.spend(1.2f)) { s.beam = -1; Actions.noOutput(p); return; }
        ServerLevel l = p.serverLevel();
        Vec3 a = mount(p), end = beamEnd(p, 128, true);
        // the beam tunnels: carve where it meets the world, a few bites per tick along its length
        Vec3 dir = end.subtract(a).normalize();
        int bites = 0;
        for (double t = 2; t < 128 && bites < 5; t += 1.5) {
            Vec3 q = a.add(dir.scale(t));
            if (!l.getBlockState(BlockPos.containing(q)).isAir()) { Destruction.sphere(l, q, 1.4f, 50, 0.25f); bites++; t += 2; }
        }
        if ((now - s.beam) % 2 == 0) hurtLine(p, a, end, 1.6, 14);
        if ((now - s.beam) % 2 == 0) Util.fx(l, S2CFx.BEAM_SUSTAIN, end, 0, 0, 0, p.getId(), -1);
        if ((now - s.beam) % 10 == 0) Util.sound(l, p.position(), ModSounds.CHARGE_UP.get(), 1.2f, 0.7f);
    }

    // ------------------------------------------------------------------------------------------------ orbs
    private static void orb(ServerPlayer p, boolean big) {
        AbyssData d = AbyssData.get(p);
        if (!d.spend(big ? 12 : 5)) { Actions.noOutput(p); return; }
        Orb o = new Orb();
        o.owner = p; o.l = p.serverLevel(); o.big = big; o.r = big ? 0.8f : 0.45f; o.id = nextOrb++;
        o.pos = mount(p); o.vel = p.getLookAngle().scale(big ? 0.75 : 1.1);
        ORBS.add(o);
        Util.fx(o.l, S2CFx.ORB, o.pos, (float) o.vel.x, (float) o.vel.y, (float) o.vel.z, p.getId(), o.id * 2 + (big ? 1 : 0));
        Util.sound(o.l, p.position(), ModSounds.TEAR.get(), 1f, big ? 0.6f : 0.9f);
    }

    private static void orbsTick() {
        for (Iterator<Orb> it = ORBS.iterator(); it.hasNext(); ) {
            Orb o = it.next();
            o.age++;
            Vec3 next = o.pos.add(o.vel);
            boolean burst = o.age > 80 || !o.owner.isAlive();
            var hit = o.l.clip(new ClipContext(o.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, o.owner));
            if (hit.getType() != HitResult.Type.MISS) { next = hit.getLocation(); burst = true; }
            Vec3 at = next;
            if (!o.l.getEntitiesOfClass(LivingEntity.class, new AABB(o.pos, at).inflate(o.r), x -> x != o.owner && x.isAlive() && !Util.friend(o.owner.getUUID(), x)).isEmpty()) burst = true;
            o.pos = next;
            if (!burst) continue;
            it.remove();
            float R = o.big ? 3.4f : 1.8f;
            for (LivingEntity e : o.l.getEntitiesOfClass(LivingEntity.class, new AABB(o.pos, o.pos).inflate(R), x -> x != o.owner && x.isAlive())) {
                if (Util.friend(o.owner.getUUID(), e) || e.distanceToSqr(o.pos) > R * R * 1.4) continue;
                e.invulnerableTime = 0;
                Util.hurt(o.owner.getUUID(), o.owner, e, o.big ? 46 : 18, "inside_out");
                Vec3 in = o.pos.subtract(e.position()).normalize().scale(0.4);
                e.setDeltaMovement(e.getDeltaMovement().add(in));
                e.hurtMarked = true;
            }
            Destruction.sphere(o.l, o.pos, o.big ? 2.6f : 1.4f, 8, o.big ? 0.5f : 0.3f);
            Util.fx(o.l, S2CFx.ORB_BURST, o.pos, R, o.big ? 1 : 0, 0, o.owner.getId(), o.id * 2 + (o.big ? 1 : 0));
            Util.sound(o.l, o.pos, ModSounds.CRATER.get(), o.big ? 1.6f : 1f, o.big ? 0.9f : 1.3f);
        }
    }

    // ------------------------------------------------------------------------------------------------ pressure
    private static void pressure(ServerPlayer p, boolean first) {
        AbyssData d = AbyssData.get(p);
        if (!d.spend(first ? 10 : 3)) { Actions.noOutput(p); st(p).press = -1; return; }
        ServerLevel l = p.serverLevel();
        float R = 10;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(R), x -> x != p && x.isAlive())) {
            if (Util.friend(p.getUUID(), e) || e.distanceTo(p) > R) continue;
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 1, 0.2).add(0, -1.2, 0));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5, false, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 2, false, false, false));
            e.invulnerableTime = 0;
            Util.hurt(p.getUUID(), p, e, first ? 10 : 4, "inside_out");
        }
        if (first) {
            ringCrumble(p, R * 0.8f);
        }
        Util.fx(l, S2CFx.PRESSURE, p.position(), R, first ? 1 : 0, 0, p.getId(), -1);
        Util.sound(l, p.position(), first ? ModSounds.QUAKE.get() : ModSounds.DOMAIN_PULSE.get(), first ? 1.6f : 0.8f, first ? 0.6f : 0.9f);
    }

    /** The ground around you buckles: weak blocks in a ring crumble (never the ground you stand on). */
    private static void ringCrumble(ServerPlayer p, float r) {
        ServerLevel l = p.serverLevel();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + l.random.nextDouble() * 0.4;
            Vec3 c = p.position().add(Math.cos(a) * r * (0.55 + l.random.nextDouble() * 0.45), -0.5, Math.sin(a) * r * (0.55 + l.random.nextDouble() * 0.45));
            if (c.distanceTo(p.position()) < 2.2) continue;
            Destruction.crater(l, c, 1.2f, 1, 2.0f, 0.35f);
        }
    }

    // ------------------------------------------------------------------------------------------------ nuke
    private static void launchNuke(ServerPlayer p, float k) {
        AbyssData d = AbyssData.get(p);
        ServerLevel l = p.serverLevel();
        if (k < 0.2f || !d.spend(10 + 40 * k)) {
            Util.fx(l, S2CFx.NUKE_CHARGE, p.position(), 0, 0, 0, p.getId(), -1);
            if (k >= 0.2f) Actions.noOutput(p);
            return;
        }
        Vec3 eye = p.getEyePosition();
        Util.Aim aim = Util.aim(p, 72);
        Vec3 target = aim.entity() != null ? aim.entity().position() : aim.point() != null ? aim.point() : eye.add(p.getLookAngle().scale(40));
        float radius = 6 + 10 * k;
        Vec3 from = eye.add(p.getLookAngle().scale(7)).add(0, 2.4, 0);
        int travel = 6 + (int) (from.distanceTo(target) / 3.5);
        Util.fx(l, S2CFx.NUKE_LAUNCH, target, travel, radius, 0, p.getId(), -1);
        Util.sound(l, p.position(), ModSounds.LIBERATION.get(), 1.6f, 0.6f);
        // 3 ticks of silence after it lands, then the abyss opens
        Scheduler.later(travel + 3, () -> detonate(p, l, target, radius));
    }

    private static void detonate(ServerPlayer p, ServerLevel l, Vec3 c, float R) {
        Util.fx(l, S2CFx.NUKE_BLAST, c, R, 0, 0, p.getId(), -1);
        Util.sound(l, c, ModSounds.NUKE.get(), 4f, 0.5f);
        Util.sound(l, c, ModSounds.DOMAIN_COLLAPSE.get(), 3f, 0.6f);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(R * 1.5), x -> x != p && x.isAlive())) {
            if (Util.friend(p.getUUID(), e)) continue;
            double dist = e.position().distanceTo(c);
            if (dist > R * 1.5) continue;
            float fall = (float) Math.max(0.25, 1 - dist / (R * 1.5));
            e.invulnerableTime = 0;
            Util.hurt(p.getUUID(), p, e, (90 + 30 * R) * fall, "inside_out");
            Vec3 out = e.position().subtract(c).normalize().scale(1.6 * fall).add(0, 0.6 * fall, 0);
            e.setDeltaMovement(out);
            e.hurtMarked = true;
        }
        // the world inside is erased; never the ground under the caster
        Set<BlockPos> keep = new HashSet<>();
        BlockPos feet = p.blockPosition().below();
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> saved = new HashMap<>();
        if (p.level() == l && p.position().distanceTo(c) < R + 3)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy >= -3; dy--) saved.put(feet.offset(dx, dy, dz), l.getBlockState(feet.offset(dx, dy, dz)));
        Destruction.sphere(l, c, R * 0.7f, 50, 1.0f);
        Destruction.crater(l, c, R, R * 0.45f, 50, 1.1f);
        saved.forEach((bp, st) -> { if (!st.isAir() && l.getBlockState(bp).isAir()) l.setBlock(bp, st, 2); });
    }

    // ------------------------------------------------------------------------------------------------ ticking
    public static void tick(MinecraftServer server) {
        orbsTick();
        for (Iterator<Map.Entry<UUID, St>> it = STATE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            AbyssData d = p == null ? null : AbyssData.get(p);
            if (p == null || !p.isAlive() || d.form != com.andy.abyss.data.Ability.ANNIHILATION.ordinal()) {
                if (p != null && en.getValue().nuke >= 0) Util.fx(p.serverLevel(), S2CFx.NUKE_CHARGE, p.position(), 0, 0, 0, p.getId(), -1);
                it.remove();
                continue;
            }
            St s = en.getValue();
            long now = p.level().getGameTime();
            beamTick(p, s, now);
            if (s.orb >= 0 && now - s.orb >= 8 && (now - s.orb) % 5 == 0) orb(p, false);
            if (s.press >= 0 && now - s.press >= 8 && (now - s.press) % 6 == 0) pressure(p, false);
            if (s.nuke >= 0) {
                long held = now - s.nuke;
                if (held % 20 == 0) Util.sound(p.serverLevel(), p.position(), ModSounds.HEARTBEAT.get(), 1.4f, 0.6f + held / 100f);
            }
        }
    }

    private Annihilation() {}
}
