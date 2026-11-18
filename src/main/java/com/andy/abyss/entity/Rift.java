package com.andy.abyss.entity;

import com.andy.abyss.server.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * A tear into the abyss. Anything that walks into it (people, mobs, arrows, items) comes out at the other end with its
 * momentum. Rifts reach any dimension the server has.
 */
public class Rift extends Entity {
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MAX = SynchedEntityData.defineId(Rift.class, EntityDataSerializers.INT);
    public ResourceKey<Level> destDim;
    public Vec3 dest = Vec3.ZERO;
    public float destYaw;
    public UUID owner;

    public Rift(EntityType<? extends Rift> t, Level l) { super(t, l); noPhysics = true; }

    @Override protected void defineSynchedData() { entityData.define(LIFE, 600); entityData.define(MAX, 600); }

    public void setLife(int ticks) { entityData.set(LIFE, ticks); entityData.set(MAX, ticks); }
    public int life() { return entityData.get(LIFE); }
    public int maxLife() { return Math.max(1, entityData.get(MAX)); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        int l = life() - 1;
        entityData.set(LIFE, l);
        if (l <= 0 || destDim == null) { discard(); return; }
        if (tickCount < 6) return;
        ServerLevel to = ((ServerLevel) level()).getServer().getLevel(destDim);
        if (to == null) { discard(); return; }
        long now = level().getGameTime();
        for (Entity e : level().getEntities(this, getBoundingBox().deflate(0.25, 0.1, 0.25), x -> !(x instanceof Rift) && !x.isPassenger() && !x.isSpectator())) {
            long cd = e.getPersistentData().getLong("abyss_rift_cd");
            if (now < cd) continue;
            Vec3 off = e.position().subtract(position());
            Vec3 out = dest.add(off.x * 0.3, Math.max(0, off.y), off.z * 0.3);
            Entity moved = Util.teleport(e, to, out, e.getYRot(), e.getXRot(), true);
            moved.getPersistentData().putLong("abyss_rift_cd", to.getGameTime() + 30);
            Util.fx(to, com.andy.abyss.net.S2CFx.STEP, out.add(0, 1, 0), 1, 0, 0);
        }
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.contains("dim")) destDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("dim")));
        dest = new Vec3(t.getDouble("dx"), t.getDouble("dy"), t.getDouble("dz"));
        setLife(t.getInt("life"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        if (destDim != null) t.putString("dim", destDim.location().toString());
        t.putDouble("dx", dest.x); t.putDouble("dy", dest.y); t.putDouble("dz", dest.z);
        t.putInt("life", life());
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
