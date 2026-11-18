package com.andy.abyss.server;

import com.andy.abyss.Abyss;
import com.andy.abyss.Config;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.entity.AbyssBody;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModBlocks;
import com.andy.abyss.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;
import java.util.function.Function;

public final class Util {
    /** Set while the abyss deals a sure-hit blow: the defence-override listener lets it through even if another mod cancelled it. */
    public static boolean sureHitInFlight;

    public static MinecraftServer server() { return ServerLifecycleHooks.getCurrentServer(); }

    public static ServerPlayer player(UUID id) {
        MinecraftServer s = server();
        return s == null || id == null ? null : s.getPlayerList().getPlayer(id);
    }

    public static DamageSource src(ServerLevel l, String type, Entity attacker) {
        Holder<DamageType> h = l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, Abyss.id(type)));
        return new DamageSource(h, attacker);
    }

    /** Is this entity on the Abyss user's side: themself, their bodies, their puppets, or a teammate. */
    public static boolean friend(UUID owner, Entity e) {
        if (e == null || owner == null) return false;
        if (e.getUUID().equals(owner)) return true;
        if (e instanceof AbyssBody b && owner.equals(b.ownerId())) return true;
        if (e.getPersistentData().hasUUID("abyss_puppet_owner") && owner.equals(e.getPersistentData().getUUID("abyss_puppet_owner"))) return true;
        if (Config.spareAllies.get()) {
            ServerPlayer p = player(owner);
            if (p != null && e.getTeam() != null && p.getTeam() != null && e.isAlliedTo(p)) return true;
        }
        return false;
    }

    public static UUID ownerOf(Entity e) {
        if (e instanceof AbyssBody b) return b.ownerId();
        if (e instanceof Player p && AbyssData.get(p).awakened) return p.getUUID();
        return null;
    }

    /**
     * Abyss damage. Scales by config and PvP; inside the owner's domain the blow is a sure hit: invulnerability frames are
     * cleared and the defence-override listener lets it land through other mods' cancellations.
     */
    public static boolean hurt(UUID owner, Entity attacker, LivingEntity target, float amount, String type) {
        if (target == null || !target.isAlive() || friend(owner, target)) return false;
        ServerLevel l = (ServerLevel) target.level();
        amount *= Config.damageMultiplier.get().floatValue();
        if (target instanceof Player) amount *= Config.pvpMultiplier.get().floatValue();
        boolean sure = Domains.sureHit(owner, target) || target.hasEffect(ModEffects.SHATTERED.get());
        Entity credit = attacker != null ? attacker : player(owner);
        if (sure) target.invulnerableTime = 0;
        sureHitInFlight = sure;
        try {
            return target.hurt(src(l, type, credit), amount);
        } finally { sureHitInFlight = false; }
    }

    public static void fx(ServerLevel l, int type, Vec3 p, float a, float b, float c, int e1, int e2) {
        Net.near(l, p, 160, new S2CFx(type, p.x, p.y, p.z, a, b, c, e1, e2));
    }
    public static void fx(ServerLevel l, int type, Vec3 p, float a, float b, float c) { fx(l, type, p, a, b, c, -1, -1); }

    public static void sound(ServerLevel l, Vec3 p, SoundEvent s, float vol, float pitch) {
        l.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** What the player is aiming at: entity first (within range), else block, else the far point. */
    public record Aim(Vec3 point, LivingEntity entity, BlockHitResult block) {}

    public static Aim aim(LivingEntity looker, double range) {
        Vec3 eye = looker.getEyePosition(), look = looker.getViewVector(1f), end = eye.add(look.scale(range));
        BlockHitResult bh = looker.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker));
        double maxD = bh.getType() == HitResult.Type.MISS ? range : bh.getLocation().distanceTo(eye);
        LivingEntity best = null;
        double bestD = maxD;
        AABB box = looker.getBoundingBox().expandTowards(look.scale(maxD)).inflate(1.5);
        UUID owner = ownerOf(looker);
        for (LivingEntity e : looker.level().getEntitiesOfClass(LivingEntity.class, box, x -> x != looker && x.isAlive() && !x.isSpectator())) {
            if (owner != null && friend(owner, e)) continue;
            var hit = e.getBoundingBox().inflate(0.4 + Math.min(1.5, e.distanceTo(looker) * 0.02)).clip(eye, eye.add(look.scale(maxD)));
            if (hit.isPresent()) {
                double d = hit.get().distanceTo(eye);
                if (d < bestD) { bestD = d; best = e; }
            }
        }
        if (best != null) return new Aim(best.position().add(0, best.getBbHeight() * 0.5, 0), best, null);
        return new Aim(bh.getType() == HitResult.Type.MISS ? end : bh.getLocation(), null, bh.getType() == HitResult.Type.MISS ? null : bh);
    }

    public static boolean griefing(ServerLevel l) {
        return Config.griefing.get() && l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** Can the abyss consume this block. */
    public static boolean consumable(ServerLevel l, BlockPos p, BlockState s) {
        if (s.isAir() || s.is(ModBlocks.ABYSSAL_CRYSTAL.get())) return false;
        float h = s.getDestroySpeed(l, p);
        return h >= 0 && h <= Config.maxBreakHardness.get();
    }

    /** Teleport anything, across dimensions too, keeping its momentum. */
    public static Entity teleport(Entity e, ServerLevel to, Vec3 pos, float yaw, float pitch, boolean keepVelocity) {
        Vec3 vel = keepVelocity ? e.getDeltaMovement() : Vec3.ZERO;
        if (e instanceof ServerPlayer sp) {
            if (sp.isSleeping()) sp.stopSleeping();
            sp.teleportTo(to, pos.x, pos.y, pos.z, yaw, pitch);
            sp.setDeltaMovement(vel);
            sp.hurtMarked = true;
            sp.fallDistance = 0;
            return sp;
        }
        if (e.level() == to) {
            e.teleportTo(pos.x, pos.y, pos.z);
            e.setYRot(yaw); e.setXRot(pitch);
            e.setDeltaMovement(vel);
            e.hurtMarked = true;
            e.fallDistance = 0;
            return e;
        }
        Entity moved = e.changeDimension(to, new ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel cur, ServerLevel dest, float y, Function<Boolean, Entity> reposition) {
                Entity n = reposition.apply(false);
                if (n != null) { n.moveTo(pos.x, pos.y, pos.z, yaw, pitch); n.setDeltaMovement(vel); n.fallDistance = 0; }
                return n;
            }
            @Override public boolean playTeleportSound(ServerPlayer p, ServerLevel s, ServerLevel d) { return false; }
        });
        return moved == null ? e : moved;
    }

    public static float masteryScale(AbyssData d) { return 1f + d.mastery / 100f; }

    private Util() {}
}
