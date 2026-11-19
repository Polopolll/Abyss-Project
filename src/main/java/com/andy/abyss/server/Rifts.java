package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.entity.Rift;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModEntities;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Two-way rifts to anywhere in any dimension the server has ("outerversal"). */
public final class Rifts {
    public static void request(ServerPlayer p, ResourceLocation dim, double x, double y, double z, boolean step) {
        AbyssData d = AbyssData.get(p);
        if (!d.awakened) return;
        ServerLevel to = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (to == null) { p.displayClientMessage(Component.literal("§5No such dimension: " + dim), true); return; }
        if (Math.abs(x) > 29_000_000 || Math.abs(z) > 29_000_000) return;
        Vec3 dest = AbyssDim.is(to) && y < -9000 ? AbyssDim.throne(to, p.getUUID()) : safe(to, x, y, z);
        if (step) {
            if (!d.spend(15)) { Actions.noOutput(p); return; }
            Util.fx(p.serverLevel(), S2CFx.STEP, p.position().add(0, 1, 0), 1, 0, 0);
            Util.teleport(p, to, dest, p.getYRot(), p.getXRot(), false);
            Util.fx(to, S2CFx.STEP, dest.add(0, 1, 0), 1, 1, 0);
            Util.sound(to, dest, ModSounds.RIFT.get(), 1.2f, 1.3f);
            Actions.mastery(p, 0.05f);
            return;
        }
        if (!d.spend(25)) { Actions.noOutput(p); return; }
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 here = p.position().add(look.scale(2.6));
        pair(p.getUUID(), p.serverLevel(), here, p.getYRot() + 180, to, dest, p.getYRot());
        Actions.mastery(p, 0.05f);
    }

    /** A rift at {@code a} leading to {@code b}, and one at {@code b} leading back. */
    public static void pair(UUID owner, ServerLevel la, Vec3 a, float yawA, ServerLevel lb, Vec3 b, float yawB) {
        int life = Config.riftSeconds.get() * 20;
        Vec3 outB = b.add(Vec3.directionFromRotation(0, yawB).scale(2.2));
        Vec3 outA = a.add(Vec3.directionFromRotation(0, yawA).scale(2.2));
        Rift ra = make(la, a, yawA, lb, outB, life, owner);
        Rift rb = make(lb, b, yawB + 180, la, outA, life, owner);
        if (ra != null) Util.fx(la, S2CFx.RIFT_OPEN, a.add(0, 1.3, 0), 1, yawA, 0, ra.getId(), -1);
        if (rb != null) Util.fx(lb, S2CFx.RIFT_OPEN, b.add(0, 1.3, 0), 1, yawB, 0, rb.getId(), -1);
        Util.sound(la, a, ModSounds.RIFT.get(), 1.5f, 0.8f);
        Util.sound(lb, b, ModSounds.RIFT.get(), 1.5f, 0.8f);
    }

    private static Rift make(ServerLevel l, Vec3 p, float yaw, ServerLevel to, Vec3 dest, int life, UUID owner) {
        Rift r = ModEntities.RIFT.get().create(l);
        if (r == null) return null;
        r.moveTo(p.x, p.y, p.z, yaw, 0);
        r.destDim = to.dimension();
        r.dest = dest;
        r.owner = owner;
        r.setLife(life);
        l.addFreshEntity(r);
        return r;
    }

    /** Land on something: on the surface if y is unloaded air far above, a crystal disc if there is nothing at all (the Abyss, the void). */
    public static Vec3 safe(ServerLevel l, double x, double y, double z) {
        BlockPos bp = BlockPos.containing(x, Mth.clamp(y, l.getMinBuildHeight() + 1, l.getMaxBuildHeight() - 3), z);
        l.getChunk(bp);
        if (!l.getBlockState(bp).isAir() || !l.getBlockState(bp.above()).isAir()) {
            int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
            bp = new BlockPos(bp.getX(), Math.max(top, l.getMinBuildHeight() + 1), bp.getZ());
        }
        boolean ground = false;
        for (int i = 1; i < 40; i++) if (!l.getBlockState(bp.below(i)).isAir()) { ground = true; break; }
        if (!ground) AbyssDim.disc(l, bp.below(), 3);
        return Vec3.atBottomCenterOf(bp);
    }

    /** Spore payload: a rift between the user and a point. */
    public static void toPoint(ServerPlayer p, ServerLevel l, Vec3 point) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        pair(p.getUUID(), p.serverLevel(), p.position().add(look.scale(2.6)), p.getYRot() + 180, l, point.subtract(0, 1.2, 0), p.getYRot());
    }

    public static void step(LivingEntity e, ServerLevel l, Vec3 point) {
        Util.fx((ServerLevel) e.level(), S2CFx.STEP, e.position().add(0, 1, 0), 1, 0, 0);
        Util.teleport(e, l, point, e.getYRot(), e.getXRot(), false);
        Util.fx(l, S2CFx.STEP, point.add(0, 1, 0), 1, 1, 0);
        Util.sound(l, point, ModSounds.RIFT.get(), 1f, 1.5f);
    }
    private Rifts() {}
}
