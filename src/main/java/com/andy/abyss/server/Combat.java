package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The abyssal combo (combat mode). Left click chains five strikes, each a different use of the abyss:
 * <ol>
 *   <li><b>Rift Jab</b> — a tear opens beside the target and a void arm punches out of it.</li>
 *   <li><b>Fold Slash</b> — space folds into a crescent of crystal that cuts everything in an arc.</li>
 *   <li><b>Rift Blink</b> — you step through a rift and strike from behind.</li>
 *   <li><b>Void Lances</b> — crystal spears erupt under the target and throw it up.</li>
 *   <li><b>Event Horizon</b> — a singularity crushes the target, then bursts.</li>
 * </ol>
 * Sprint + click = <b>Rift Lunge</b> (dash through a rift to a target up to 14 blocks away). Click while falling =
 * <b>Gravity Fold</b> (space folds you down into a crater that drags everything in). Hold and release =
 * <b>Abyssal Beam</b>. Every hit corrodes; inside your domain every hit is a sure hit.
 */
public final class Combat {
    public static final String[] NAMES = {"", "Rift Jab", "Fold Slash", "Rift Blink", "Void Lances", "Event Horizon"};
    public static final int AIR = 1, SPRINT = 2;

    private static float base(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        return (float) (Math.max(5, p.getAttributeValue(Attributes.ATTACK_DAMAGE)) * (1 + d.mastery / 200f) * Config.comboDamage.get());
    }

    private static LivingEntity target(ServerPlayer p, double range) {
        LivingEntity t = Util.aim(p, range).entity();
        if (t != null) return t;
        // aim assist: the nearest enemy in a forward cone
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), x -> x != p && x.isAlive() && !x.isSpectator())) {
            if (Util.friend(p.getUUID(), e)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > range || to.normalize().dot(look) < 0.75) continue;
            if (d < bd) { bd = d; best = e; }
        }
        if (best == null) best = Actions_domainTarget(p);
        return best;
    }

    private static LivingEntity Actions_domainTarget(ServerPlayer p) {
        Domains.Domain dom = Domains.ownedBy(p.getUUID());
        if (dom == null || !dom.contains(p)) return null;
        LivingEntity best = null;
        double bd = 24 * 24;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), x -> x.isAlive() && dom.contains(x) && !Util.friend(p.getUUID(), x))) {
            double dd = e.distanceToSqr(p);
            if (dd < bd) { bd = dd; best = e; }
        }
        return best;
    }

    private static boolean hit(ServerPlayer p, LivingEntity t, float dmg, Vec3 push) {
        t.invulnerableTime = 0;
        boolean ok = Util.hurt(p.getUUID(), p, t, dmg, "abyss");
        if (ok) {
            Corrosion.add(t, 1, 160);
            if (push != null) { t.setDeltaMovement(t.getDeltaMovement().add(push)); t.hurtMarked = true; }
        }
        return ok;
    }

    public static void m1(ServerPlayer p, int flags) {
        AbyssData d = AbyssData.get(p);
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime();
        if (now < d.nextHit) return;
        if (now - d.lastHit > 26) d.comboStep = 0;
        d.lastHit = now;
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        float b = base(p);
        if ((flags & AIR) != 0 && !p.onGround() && p.getDeltaMovement().y < 0.2) { plunge(p, b); d.nextHit = now + 12; d.comboStep = 0; return; }
        if ((flags & SPRINT) != 0 && d.comboStep == 0) { lunge(p, b); d.nextHit = now + 8; d.comboStep = 1; d.dirty = true; return; }
        int step = d.comboStep % 5 + 1;
        d.comboStep = step;
        d.dirty = true;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        switch (step) {
            case 1 -> { // Rift Jab
                LivingEntity t = target(p, 6);
                Vec3 at = t != null ? t.position().add(0, t.getBbHeight() * 0.6, 0) : p.getEyePosition().add(look.scale(3));
                Vec3 side = new Vec3(-flat.z, 0, flat.x).scale(p.getRandom().nextBoolean() ? 1.4 : -1.4);
                Util.fx(l, S2CFx.COMBO, at, 1, (float) side.x, (float) side.z, p.getId(), t != null ? t.getId() : -1);
                if (t != null) hit(p, t, b, flat.scale(0.25));
                d.nextHit = now + 5;
            }
            case 2 -> { // Fold Slash: an arc in front
                Vec3 c = p.position().add(0, 1.1, 0).add(flat.scale(1.5));
                Util.fx(l, S2CFx.COMBO, c, 2, p.getYRot(), 0, p.getId(), -1);
                for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4.5), x -> x != p && x.isAlive())) {
                    Vec3 to = t.position().subtract(p.position());
                    if (to.horizontalDistance() > 4.5 || new Vec3(to.x, 0, to.z).normalize().dot(flat) < 0.2) continue;
                    hit(p, t, b * 1.1f, flat.scale(0.5).add(0, 0.15, 0));
                }
                d.nextHit = now + 6;
            }
            case 3 -> { // Rift Blink: behind the target
                LivingEntity t = target(p, 12);
                if (t != null) {
                    Vec3 behind = t.position().add(t.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(1.6));
                    Vec3 from = p.position();
                    Util.fx(l, S2CFx.COMBO, from.add(0, 1, 0), 3, 0, 0, p.getId(), t.getId());
                    p.teleportTo(behind.x, t.getY(), behind.z);
                    Vec3 face = t.position().subtract(behind);
                    p.setYRot((float) (Math.atan2(-face.x, face.z) * 180 / Math.PI));
                    p.connection.teleport(behind.x, t.getY(), behind.z, p.getYRot(), p.getXRot());
                    Util.fx(l, S2CFx.COMBO, behind.add(0, 1, 0), 3, 1, 0, p.getId(), t.getId());
                    hit(p, t, b * 1.2f, face.normalize().scale(-0.1).add(0, 0.2, 0));
                    Util.sound(l, behind, ModSounds.SWAP.get(), 0.8f, 1.5f);
                } else {
                    Vec3 to = p.position().add(flat.scale(5));
                    Util.fx(l, S2CFx.COMBO, p.position().add(0, 1, 0), 3, 0, 0, p.getId(), -1);
                    p.teleportTo(to.x, p.getY(), to.z);
                }
                d.nextHit = now + 6;
            }
            case 4 -> { // Void Lances
                LivingEntity t = target(p, 10);
                Vec3 at = t != null ? t.position() : p.position().add(flat.scale(4));
                Util.fx(l, S2CFx.COMBO, at, 4, 0, 0, p.getId(), t != null ? t.getId() : -1);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.2, 1.5, 2.2), x -> x != p && x.isAlive()))
                    hit(p, e, b * 1.3f, new Vec3(0, 0.85, 0));
                d.nextHit = now + 7;
            }
            case 5 -> { // Event Horizon
                LivingEntity t = target(p, 8);
                Vec3 at = t != null ? t.position().add(0, t.getBbHeight() * 0.5, 0) : p.getEyePosition().add(look.scale(4));
                Util.fx(l, S2CFx.COMBO, at, 5, 0, 0, p.getId(), t != null ? t.getId() : -1);
                Util.sound(l, at, ModSounds.CHARGE.get(), 1.5f, 1.2f);
                Scheduler.later(8, () -> {
                    Util.sound(l, at, ModSounds.NUKE.get(), 2f, 1.5f);
                    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.5), x -> x != p && x.isAlive())) {
                        Vec3 out = e.position().subtract(at).normalize();
                        hit(p, e, b * 2.4f, out.scale(1.4).add(0, 0.45, 0));
                    }
                });
                // the pull before the burst
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(5), x -> x != p && x.isAlive() && !Util.friend(p.getUUID(), x))) {
                    e.setDeltaMovement(at.subtract(e.position()).scale(0.18));
                    e.hurtMarked = true;
                }
                d.nextHit = now + 14;
                d.comboStep = 0;
                Actions.mastery(p, 0.1f);
            }
        }
        Util.sound(l, p.position(), ModSounds.SPREAD.get(), 0.6f, 1.4f + step * 0.12f);
    }

    /** Rift Lunge: dash through a rift to a target (or 10 blocks forward). */
    private static void lunge(ServerPlayer p, float b) {
        ServerLevel l = p.serverLevel();
        LivingEntity t = target(p, 14);
        Vec3 look = p.getLookAngle(), flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 from = p.position();
        Vec3 to = t != null ? t.position().subtract(t.position().subtract(from).multiply(1, 0, 1).normalize().scale(1.4)) : from.add(flat.scale(10));
        if (t == null) {
            var hitRes = l.clip(new ClipContext(from.add(0, 1, 0), to.add(0, 1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hitRes.getType() != HitResult.Type.MISS) to = hitRes.getLocation().subtract(flat.scale(0.6)).subtract(0, 1, 0);
        }
        Util.fx(l, S2CFx.LUNGE, from.add(0, 1, 0), (float) to.x, (float) to.y + 1, (float) to.z, p.getId(), t != null ? t.getId() : -1);
        p.teleportTo(to.x, t != null ? t.getY() : to.y, to.z);
        p.connection.teleport(to.x, t != null ? t.getY() : to.y, to.z, p.getYRot(), p.getXRot());
        if (t != null) hit(p, t, b * 1.2f, flat.scale(0.6).add(0, 0.2, 0));
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1), x -> x != p && x != t && x.isAlive())) hit(p, e, b * 0.6f, null);
        Util.sound(l, to, ModSounds.RIFT.get(), 1f, 1.6f);
    }

    /** Gravity Fold: space folds you straight down; the landing crushes and drags everything in. */
    private static void plunge(ServerPlayer p, float b) {
        ServerLevel l = p.serverLevel();
        Vec3 from = p.position();
        var hitRes = l.clip(new ClipContext(from, from.subtract(0, 40, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        Vec3 ground = hitRes.getType() == HitResult.Type.MISS ? from : hitRes.getLocation();
        float fall = (float) Math.min(40, from.y - ground.y);
        Util.fx(l, S2CFx.PLUNGE, ground.add(0, 0.1, 0), 4 + fall * 0.15f, (float) from.y, 0, p.getId(), -1);
        p.teleportTo(ground.x, ground.y, ground.z);
        p.connection.teleport(ground.x, ground.y, ground.z, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        float r = 4 + fall * 0.15f;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(ground, ground).inflate(r), x -> x != p && x.isAlive())) {
            Vec3 in = ground.subtract(e.position());
            hit(p, e, b * (1.6f + fall * 0.04f), new Vec3(in.x * 0.25, -0.3, in.z * 0.25));
        }
        Util.sound(l, ground, ModSounds.IMPACT.get(), 2f, 0.7f);
        Actions.mastery(p, 0.05f);
    }

    /** Abyssal Beam: hold and release. A galaxy beam from the palm along your aim; pierces, carves soft blocks. */
    public static void heavy(ServerPlayer p, int chargeTicks) {
        AbyssData d = AbyssData.get(p);
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime();
        if (now < d.nextHit || !d.spend(12)) return;
        float k = Math.min(1.6f, 0.8f + chargeTicks / 30f);
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        double len = 28;
        var hitRes = l.clip(new ClipContext(eye, eye.add(look.scale(len)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double stop = Config.beamCarves.get() && Util.griefing(l) ? len : hitRes.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(stop));
        Util.fx(l, S2CFx.BEAM, eye.add(look.scale(0.6)).subtract(0, 0.25, 0), (float) stop, p.getYRot(), p.getXRot(), p.getId(), -1);
        Util.sound(l, eye, ModSounds.NUKE.get(), 1.6f, 1.8f);
        float b = base(p) * 3f * k;
        List<LivingEntity> hitList = new ArrayList<>();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.2), x -> x != p && x.isAlive())) {
            if (e.getBoundingBox().inflate(0.9).clip(eye, end).isPresent()) hitList.add(e);
        }
        for (LivingEntity e : hitList) hit(p, e, b, look.scale(1.2).add(0, 0.2, 0));
        if (Config.beamCarves.get() && Util.griefing(l)) {
            for (double s = 1.5; s < stop; s += 0.8) {
                BlockPos bp = BlockPos.containing(eye.add(look.scale(s)));
                for (BlockPos q : BlockPos.betweenClosed(bp.offset(-1, -1, -1), bp.offset(1, 1, 1))) {
                    var st = l.getBlockState(q);
                    if (Util.consumable(l, q, st) && st.getDestroySpeed(l, q) <= 3.5f) l.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        d.nextHit = now + 16;
        d.comboStep = 0;
        Actions.mastery(p, 0.1f);
    }
    private Combat() {}
}
