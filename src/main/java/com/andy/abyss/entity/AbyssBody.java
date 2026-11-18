package com.andy.abyss.entity;

import com.andy.abyss.Config;
import com.andy.abyss.server.Bodies;
import com.andy.abyss.server.Corrosion;
import com.andy.abyss.server.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * A body of an Abyss user that the player is not currently standing in: a clone out in the world, or the main body
 * resting on its throne in the Abyss. Clones obey orders and use the user's powers on their own.
 */
public class AbyssBody extends PathfinderMob {
    public static final int HOLD = 0, FOLLOW = 1, ATTACK = 2, MIRROR = 3, PATROL = 4;
    public static final String[] ORDER_NAMES = {"Hold", "Follow", "Attack", "Mirror", "Patrol"};
    public static final TicketType<ChunkPos> TICKET = TicketType.create("abyss_body", Comparator.comparingLong(ChunkPos::toLong), 300);

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AbyssBody.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> MAIN = SynchedEntityData.defineId(AbyssBody.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(AbyssBody.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORDER = SynchedEntityData.defineId(AbyssBody.class, EntityDataSerializers.INT);

    public Vec3 home;
    public long nextSpread, nextNuke;
    public float cpMult = 0.85f;

    public AbyssBody(EntityType<? extends AbyssBody> t, Level l) {
        super(t, l);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 40).add(Attributes.ARMOR, 0).add(Attributes.KNOCKBACK_RESISTANCE, 0.4).add(Attributes.ATTACK_KNOCKBACK, 0.5);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
        entityData.define(MAIN, false);
        entityData.define(SLOT, 1);
        entityData.define(ORDER, FOLLOW);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12));
    }

    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public void setOwner(UUID u) { entityData.set(OWNER, Optional.ofNullable(u)); }
    public boolean isMain() { return entityData.get(MAIN); }
    public void setMain(boolean m) { entityData.set(MAIN, m); }
    public int slot() { return entityData.get(SLOT); }
    public void setSlot(int s) { entityData.set(SLOT, s); }
    public int order() { return entityData.get(ORDER); }
    public void setOrder(int o) { entityData.set(ORDER, Math.floorMod(o, ORDER_NAMES.length)); if (o != PATROL) home = position(); }
    public double cpMultiplier() { return cpMult; }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel l = (ServerLevel) level();
        if (tickCount % 100 == 1) l.getChunkSource().addRegionTicket(TICKET, chunkPosition(), 2, chunkPosition());
        if (tickCount % 20 == 0) Bodies.remember(this);
        UUID owner = ownerId();
        if (owner == null) { if (tickCount > 40) discard(); return; }
        Bodies.think(this);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        swing(InteractionHand.MAIN_HAND);
        if (!(target instanceof LivingEntity le)) return false;
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean hit = Util.hurt(ownerId(), this, le, dmg, "abyss");
        if (hit) {
            Corrosion.add(le, 1, 160);
            le.knockback(0.5, getX() - le.getX(), getZ() - le.getZ());
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        Entity a = src.getEntity();
        UUID o = ownerId();
        if (a != null && o != null && Util.friend(o, a)) return false;
        return super.hurt(src, amount);
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (!level().isClientSide) Bodies.onBodyDeath(this);
    }

    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canChangeDimensions() { return true; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean canBeLeashed(Player p) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        if (ownerId() != null) t.putUUID("owner", ownerId());
        t.putBoolean("main", isMain()); t.putInt("slot", slot()); t.putInt("order", order()); t.putFloat("cpMult", cpMult);
        if (home != null) { t.putDouble("hx", home.x); t.putDouble("hy", home.y); t.putDouble("hz", home.z); }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if (t.hasUUID("owner")) setOwner(t.getUUID("owner"));
        setMain(t.getBoolean("main")); setSlot(t.getInt("slot")); entityData.set(ORDER, t.getInt("order")); cpMult = t.contains("cpMult") ? t.getFloat("cpMult") : Config.cloneStatScale.get().floatValue();
        if (t.contains("hx")) home = new Vec3(t.getDouble("hx"), t.getDouble("hy"), t.getDouble("hz"));
    }
}
