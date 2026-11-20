package com.andy.abyss.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Augusta's Ruler's Realm in the abyss's terms: for a few seconds the world around the vessel stops. Creatures hang
 * where they are (no motion, no AI, can't act), projectiles freeze in the air, and only the vessel moves. When time
 * resumes everything continues with the momentum it had (projectiles keep flying).
 */
public final class TimeStop {
    record Held(Entity e, Vec3 pos, Vec3 vel, boolean noGravity, boolean noAi) {}
    record Zone(UUID owner, ServerLevel level, Vec3 c, double r, long until, Map<Integer, Held> held) {}
    private static final List<Zone> ZONES = new ArrayList<>();

    public static void start(ServerPlayer p, float radius, int ticks) {
        ZONES.add(new Zone(p.getUUID(), p.serverLevel(), p.position(), radius, p.level().getGameTime() + ticks, new HashMap<>()));
        Util.sound(p.serverLevel(), p.position(), com.andy.abyss.registry.ModSounds.FREEZE.get(), 2f, 0.6f);
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            long now = z.level().getGameTime();
            if (now >= z.until()) { release(z); it.remove(); continue; }
            for (Entity e : z.level().getEntities((Entity) null, new AABB(z.c(), z.c()).inflate(z.r()), x -> x.isAlive() && !x.getUUID().equals(z.owner()))) {
                if (!(e instanceof LivingEntity || e instanceof Projectile)) continue;
                if (e instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())) continue;
                if (e instanceof LivingEntity le && Util.friend(z.owner(), le)) continue;
                Held h = z.held().computeIfAbsent(e.getId(), id -> new Held(e, e.position(), e.getDeltaMovement(), e.isNoGravity(), e instanceof Mob m && m.isNoAi()));
                e.setNoGravity(true);
                if (e instanceof Mob m) m.setNoAi(true);
                e.teleportTo(h.pos().x, h.pos().y, h.pos().z);
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
                if (e instanceof LivingEntity le) {
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 9, false, false, false));
                    le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 5, 9, false, false, false));
                }
            }
        }
    }

    private static void release(Zone z) {
        for (Held h : z.held().values()) {
            Entity e = h.e();
            if (!e.isAlive()) continue;
            e.setNoGravity(h.noGravity());
            if (e instanceof Mob m) m.setNoAi(h.noAi());
            if (e instanceof Projectile) { e.setDeltaMovement(h.vel()); e.hurtMarked = true; }
        }
    }

    public static boolean frozen(Entity e) {
        for (Zone z : ZONES) if (z.level() == e.level() && z.held().containsKey(e.getId())) return true;
        return false;
    }

    public static void clear() { for (Zone z : ZONES) release(z); ZONES.clear(); }
    private TimeStop() {}
}
