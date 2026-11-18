package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.entity.AbyssBody;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModEntities;
import com.andy.abyss.registry.ModSounds;
import com.andy.abyss.spore.SporeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Main body and clones. The player always stands in exactly one body; every other body is an {@link AbyssBody}.
 * Swapping moves the player (inventory, keys, camera) into the target body's place and state and leaves a body where the
 * player was. A clone that dies under you throws you back into the main body; if the main body dies, everything collapses.
 */
public final class Bodies {
    private record Last(ResourceKey<Level> dim, Vec3 pos, float hp, float max, int slot, boolean main, int order) {}
    private static final Map<UUID, Last> LAST = new HashMap<>();

    public static void remember(AbyssBody b) {
        LAST.put(b.getUUID(), new Last(b.level().dimension(), b.position(), b.getHealth(), b.getMaxHealth(), b.slot(), b.isMain(), b.order()));
    }

    public static AbyssBody find(MinecraftServer s, UUID id) {
        if (id == null) return null;
        Last last = LAST.get(id);
        if (last != null) {
            ServerLevel l = s.getLevel(last.dim);
            if (l != null) { Entity e = l.getEntity(id); if (e instanceof AbyssBody b) return b; }
        }
        for (ServerLevel l : s.getAllLevels()) { Entity e = l.getEntity(id); if (e instanceof AbyssBody b) return b; }
        // not loaded: wake its chunk so it is there next time
        if (last != null) {
            ServerLevel l = s.getLevel(last.dim);
            if (l != null) { ChunkPos cp = new ChunkPos(BlockPos.containing(last.pos)); l.getChunkSource().addRegionTicket(AbyssBody.TICKET, cp, 2, cp); }
        }
        return null;
    }

    /** Apply the player's stats to a body. Main bodies are the player at full strength; clones are scaled. */
    static void stats(AbyssBody b, ServerPlayer p, boolean main) {
        float k = main ? 1f : Config.cloneStatScale.get().floatValue();
        b.cpMult = k;
        b.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(4, p.getMaxHealth() * k));
        b.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Math.max(2, p.getAttributeValue(Attributes.ATTACK_DAMAGE) * k + 3));
        b.getAttribute(Attributes.ARMOR).setBaseValue(p.getAttributeValue(Attributes.ARMOR) * k);
        b.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32 * (1 + AbyssData.get(p).absorbedBonus("speed")));
    }

    static AbyssBody create(ServerLevel l, Vec3 pos, float yaw, ServerPlayer p, boolean main, int slot, float hpFrac, int order) {
        AbyssBody b = ModEntities.BODY.get().create(l);
        if (b == null) return null;
        b.setOwner(p.getUUID());
        b.setMain(main);
        b.setSlot(slot);
        stats(b, p, main);
        b.setHealth(Math.max(1, b.getMaxHealth() * hpFrac));
        b.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        b.setYHeadRot(yaw);
        b.home = pos;
        b.setOrder(order);
        b.setCustomName(Component.literal(p.getGameProfile().getName() + (main ? " · Main Body" : " · Clone " + slot)));
        l.addFreshEntity(b);
        remember(b);
        return b;
    }

    private static int freeSlot(AbyssData d, MinecraftServer s) {
        Set<Integer> used = new HashSet<>();
        if (!d.inMain) used.add(d.slot);
        for (UUID u : d.clones) { Last l = LAST.get(u); if (l != null) used.add(l.slot); }
        for (int i = 1; i <= 32; i++) if (!used.contains(i)) return i;
        return 1;
    }

    public static int cloneCount(AbyssData d) { return d.clones.size() + (d.inMain ? 0 : 1); }

    public static boolean sendClone(ServerPlayer p, ServerLevel l, Vec3 at) {
        AbyssData d = AbyssData.get(p);
        prune(p);
        if (cloneCount(d) >= Config.maxClones.get()) { p.displayClientMessage(Component.literal("§5All " + Config.maxClones.get() + " clones are out."), true); return false; }
        if (Config.cooldownsEnabled.get() && l.getGameTime() < d.cloneReadyAt) { p.displayClientMessage(Component.literal("§5The abyss is still re-forming a body (" + (d.cloneReadyAt - l.getGameTime()) / 20 + "s)."), true); return false; }
        AbyssBody b = create(l, at, p.getYRot(), p, false, freeSlot(d, p.server), 1f, AbyssBody.FOLLOW);
        if (b == null) return false;
        d.clones.add(b.getUUID());
        d.dirty = true;
        Util.fx(l, S2CFx.CLONE_SPAWN, at.add(0, 1, 0), 1, 0, 0, b.getId(), -1);
        Util.sound(l, at, ModSounds.RIFT.get(), 1f, 1.2f);
        return true;
    }

    /** Drop roster entries whose bodies are gone for good. */
    public static void prune(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        d.clones.removeIf(u -> !LAST.containsKey(u) && find(p.server, u) == null && p.server.getTickCount() > 200);
    }

    /** The bodies in slot order: slot 0 is the main body. Entries are UUIDs (null = the player). */
    public static List<UUID> roster(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        Map<Integer, UUID> bySlot = new TreeMap<>();
        bySlot.put(d.inMain ? 0 : d.slot, null);
        if (!d.inMain && d.mainBody != null) bySlot.put(0, d.mainBody);
        for (UUID u : d.clones) { Last l = LAST.get(u); bySlot.put(l != null ? l.slot : 99 + bySlot.size(), u); }
        return new ArrayList<>(bySlot.values());
    }

    public static void cycle(ServerPlayer p, int dir) {
        List<UUID> r = roster(p);
        if (r.size() < 2) { p.displayClientMessage(Component.literal("§5No other body to move into."), true); return; }
        int me = r.indexOf(null);
        UUID target = r.get(Math.floorMod(me + dir, r.size()));
        swap(p, target, true);
    }

    public static void swapToSlot(ServerPlayer p, int slot) {
        AbyssData d = AbyssData.get(p);
        if ((slot == 0 && d.inMain) || (!d.inMain && slot == d.slot)) return;
        if (slot == 0) { swap(p, d.mainBody, true); return; }
        for (UUID u : d.clones) { Last l = LAST.get(u); if (l != null && l.slot == slot) { swap(p, u, true); return; } }
    }

    /**
     * Move into body {@code id}. If {@code leaveBody}, a body is left where the player stood (normal swap); if not, the
     * current body is simply destroyed (the clone you were in died).
     */
    public static boolean swap(ServerPlayer p, UUID id, boolean leaveBody) {
        AbyssData d = AbyssData.get(p);
        AbyssBody target = find(p.server, id);
        if (target == null) {
            p.displayClientMessage(Component.literal("§5Reaching for that body..."), true);
            return false;
        }
        ServerLevel fromL = p.serverLevel();
        Vec3 from = p.position();
        float frac = p.getHealth() / Math.max(1, p.getMaxHealth());
        boolean wasMain = d.inMain;
        int wasSlot = d.inMain ? 0 : d.slot;
        int wasOrder = wasMain ? AbyssBody.HOLD : AbyssBody.FOLLOW;
        if (leaveBody) {
            AbyssBody left = create(fromL, from, p.getYRot(), p, wasMain, wasSlot, frac, wasOrder);
            if (left == null) return false;
            if (wasMain) d.mainBody = left.getUUID(); else d.clones.add(left.getUUID());
        } else {
            Util.fx(fromL, S2CFx.CLONE_DEATH, from.add(0, 1, 0), 1, 0, 0);
        }
        float tfrac = target.getHealth() / Math.max(1, target.getMaxHealth());
        boolean tMain = target.isMain();
        int tSlot = target.slot();
        ServerLevel toL = (ServerLevel) target.level();
        Vec3 to = target.position();
        float yaw = target.getYRot();
        d.clones.remove(target.getUUID());
        if (tMain) d.mainBody = null;
        LAST.remove(target.getUUID());
        target.discard();
        p.stopRiding();
        Util.teleport(p, toL, to, yaw, 0, false);
        p.setHealth(Math.max(1, tfrac * p.getMaxHealth()));
        d.inMain = tMain;
        d.slot = tMain ? 0 : tSlot;
        d.dirty = true;
        Util.fx(fromL, S2CFx.SWAP, from.add(0, 1, 0), 0, 0, 0, p.getId(), -1);
        Util.fx(toL, S2CFx.SWAP, to.add(0, 1, 0), 1, 0, 0, p.getId(), -1);
        Util.sound(toL, to, ModSounds.SWAP.get(), 1f, 1f);
        return true;
    }

    /** Main body sinks into the Abyss (the player carries on as a clone), or the player returns to it, or comes out of the Abyss. */
    public static void descend(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        ServerLevel abyss = AbyssDim.level(p.server);
        if (abyss == null) return;
        if (!d.inMain) { swap(p, d.mainBody, true); return; }
        if (!AbyssDim.is(p.level())) {
            if (cloneCount(d) >= Config.maxClones.get() + 1) { p.displayClientMessage(Component.literal("§5Too many bodies out."), true); return; }
            Vec3 throne = AbyssDim.throne(abyss, p.getUUID());
            AbyssBody main = create(abyss, throne, 0, p, true, 0, p.getHealth() / p.getMaxHealth(), AbyssBody.HOLD);
            if (main == null) return;
            d.mainBody = main.getUUID();
            d.inMain = false;
            d.slot = freeSlot(d, p.server);
            d.dirty = true;
            Util.fx(p.serverLevel(), S2CFx.SWAP, p.position().add(0, 1, 0), 1, 1, 0, p.getId(), -1);
            Util.sound(p.serverLevel(), p.position(), ModSounds.COLLAPSE.get(), 1f, 0.6f);
            p.displayClientMessage(Component.literal("§5Your main body rests in the Abyss. You walk on as Clone " + d.slot + "."), true);
        } else {
            // already home: leave the main body here and go back out as a clone
            CompoundTag out = p.getPersistentData().getCompound("abyss_outside");
            ServerLevel l = out.contains("dim") ? p.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(out.getString("dim")))) : null;
            if (l == null) l = p.server.overworld();
            Vec3 pos = out.contains("x") ? new Vec3(out.getDouble("x"), out.getDouble("y"), out.getDouble("z")) : Vec3.atBottomCenterOf(l.getSharedSpawnPos());
            AbyssBody main = create(abyss, p.position(), p.getYRot(), p, true, 0, p.getHealth() / p.getMaxHealth(), AbyssBody.HOLD);
            if (main == null) return;
            d.mainBody = main.getUUID();
            d.inMain = false;
            d.slot = freeSlot(d, p.server);
            d.dirty = true;
            Rifts.step(p, l, Rifts.safe(l, pos.x, pos.y, pos.z));
        }
    }

    public static void rememberOutside(ServerPlayer p) {
        if (AbyssDim.is(p.level())) return;
        CompoundTag t = new CompoundTag();
        t.putString("dim", p.level().dimension().location().toString());
        t.putDouble("x", p.getX()); t.putDouble("y", p.getY()); t.putDouble("z", p.getZ());
        p.getPersistentData().put("abyss_outside", t);
    }

    public static void recall(ServerPlayer p, int slot) {
        AbyssData d = AbyssData.get(p);
        for (UUID u : new ArrayList<>(d.clones)) {
            Last l = LAST.get(u);
            if (slot >= 0 && (l == null || l.slot != slot)) continue;
            AbyssBody b = find(p.server, u);
            if (b != null) { Util.fx((ServerLevel) b.level(), S2CFx.CLONE_DEATH, b.position().add(0, 1, 0), 0, 0, 0); b.discard(); }
            d.clones.remove(u);
            LAST.remove(u);
        }
        d.dirty = true;
    }

    public static void order(ServerPlayer p, int slot, int order) {
        AbyssData d = AbyssData.get(p);
        for (UUID u : d.clones) {
            Last l = LAST.get(u);
            if (slot >= 0 && (l == null || l.slot != slot)) continue;
            AbyssBody b = find(p.server, u);
            if (b != null) { b.setOrder(order); remember(b); }
        }
        d.dirty = true;
    }

    public static void onBodyDeath(AbyssBody b) {
        ServerPlayer p = Util.player(b.ownerId());
        LAST.remove(b.getUUID());
        Util.fx((ServerLevel) b.level(), S2CFx.CLONE_DEATH, b.position().add(0, 1, 0), 1, 0, 0);
        Util.sound((ServerLevel) b.level(), b.position(), ModSounds.COLLAPSE.get(), 1f, 1.2f);
        if (p == null) return;
        AbyssData d = AbyssData.get(p);
        if (b.isMain()) {
            // the main body fell: every clone collapses, and so does the one you are in
            d.mainBody = null;
            collapseAll(p);
            d.inMain = true;
            d.slot = 0;
            p.displayClientMessage(Component.literal("§4Your main body was destroyed."), false);
            Sovereignty.allowDeath = true;
            try {
                p.hurt(Util.src(p.serverLevel(), "collapse", null), Float.MAX_VALUE);
                if (p.isAlive()) p.kill();
            } finally { Sovereignty.allowDeath = false; }
        } else {
            d.clones.remove(b.getUUID());
            d.cloneReadyAt = b.level().getGameTime() + Config.cloneRespawnSeconds.get() * 20L;
        }
        d.dirty = true;
    }

    public static void collapseAll(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        for (UUID u : new ArrayList<>(d.clones)) {
            AbyssBody b = find(p.server, u);
            if (b != null) { Util.fx((ServerLevel) b.level(), S2CFx.CLONE_DEATH, b.position().add(0, 1, 0), 1, 0, 0); b.discard(); }
            LAST.remove(u);
        }
        d.clones.clear();
        d.dirty = true;
    }

    /**
     * The player is about to die. If they are in a clone, the clone breaks and they wake in the main body instead.
     * Returns true if the death was absorbed.
     */
    public static boolean onPlayerDeath(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (!d.awakened) return false;
        if (d.inMain) { collapseAll(p); return false; }
        UUID main = d.mainBody;
        p.setHealth(1);
        p.clearFire();
        p.removeAllEffects();
        d.cloneReadyAt = p.level().getGameTime() + Config.cloneRespawnSeconds.get() * 20L;
        if (main != null && swap(p, main, false)) return true;
        // the main body isn't loaded yet: rest the player on the throne (full health), it was assumed to be there
        ServerLevel abyss = AbyssDim.level(p.server);
        if (abyss == null) return false;
        Util.fx(p.serverLevel(), S2CFx.CLONE_DEATH, p.position().add(0, 1, 0), 1, 0, 0);
        Vec3 throne = AbyssDim.throne(abyss, p.getUUID());
        Util.teleport(p, abyss, throne, 0, 0, false);
        p.setHealth(p.getMaxHealth());
        if (main != null) { AbyssBody old = find(p.server, main); if (old != null) old.discard(); }
        d.mainBody = null; d.inMain = true; d.slot = 0; d.dirty = true;
        return true;
    }

    /** Clone brain, every tick. */
    public static void think(AbyssBody b) {
        ServerPlayer p = Util.player(b.ownerId());
        if (p == null) { b.getNavigation().stop(); return; }
        AbyssData d = AbyssData.get(p);
        if (!d.awakened) { b.discard(); return; }
        if (b.isMain()) { b.getNavigation().stop(); b.setTarget(null); return; }
        int order = b.order();
        LivingEntity target = b.getTarget();
        if (target != null && (!target.isAlive() || Util.friend(b.ownerId(), target) || target.distanceToSqr(b) > 40 * 40)) { b.setTarget(null); target = null; }
        boolean sameLevel = p.level() == b.level();
        if (b.tickCount % 10 == 0) {
            switch (order) {
                case AbyssBody.HOLD -> { b.getNavigation().stop(); target = nearestEnemy(b, 4); b.setTarget(target); }
                case AbyssBody.FOLLOW -> {
                    LivingEntity t = sameLevel ? firstAlive(p.getLastHurtMob(), p.getLastHurtByMob()) : null;
                    if (t == null) t = nearestEnemy(b, 8);
                    b.setTarget(t);
                    if (t == null && sameLevel && b.distanceToSqr(p) > 25) b.getNavigation().moveTo(p, 1.25);
                    if (sameLevel && b.distanceToSqr(p) > 48 * 48) b.teleportTo(p.getX() + b.getRandom().nextGaussian() * 2, p.getY(), p.getZ() + b.getRandom().nextGaussian() * 2);
                }
                case AbyssBody.ATTACK, AbyssBody.MIRROR -> {
                    LivingEntity t = sameLevel ? firstAlive(p.getLastHurtMob(), p.getLastHurtByMob()) : null;
                    if (t == null || t.distanceToSqr(b) > 32 * 32) t = nearestEnemy(b, 24);
                    b.setTarget(t);
                }
                case AbyssBody.PATROL -> {
                    LivingEntity t = nearestEnemy(b, 16);
                    b.setTarget(t);
                    if (t == null && b.getNavigation().isDone() && b.home != null) {
                        Vec3 h = b.home.add(b.getRandom().nextGaussian() * 10, 0, b.getRandom().nextGaussian() * 10);
                        b.getNavigation().moveTo(h.x, h.y, h.z, 0.9);
                    }
                }
            }
        }
        target = b.getTarget();
        long now = b.level().getGameTime();
        // clones use the abyss on their own: spread spores over a fight, detonate infested targets from inside
        if (target != null && target.distanceToSqr(b) < 20 * 20) {
            if (now >= b.nextSpread && d.spend(15)) {
                SporeManager.burst((ServerLevel) b.level(), p.getUUID(), b, Math.max(8, Config.sporesPerBurst.get() / 3));
                Util.fx((ServerLevel) b.level(), S2CFx.SPREAD, b.position().add(0, 1.2, 0), 0.6f, 0, 0, b.getId(), -1);
                b.nextSpread = now + 120;
            }
            if (now >= b.nextNuke && SporeManager.on(target, p.getUUID()).size() >= 3) {
                Payloads.insideOut(p, b, target);
                b.nextNuke = now + 100;
            }
        }
    }

    /** Mirror-ordered clones repeat what the user just did, on their own nearest target. */
    public static void mirror(ServerPlayer p, String what) {
        AbyssData d = AbyssData.get(p);
        for (UUID u : d.clones) {
            AbyssBody b = find(p.server, u);
            if (b == null || b.order() != AbyssBody.MIRROR) continue;
            ServerLevel l = (ServerLevel) b.level();
            LivingEntity t = b.getTarget() != null ? b.getTarget() : nearestEnemy(b, 24);
            switch (what) {
                case "spread" -> { SporeManager.burst(l, p.getUUID(), b, Config.sporesPerBurst.get() / 2); Util.fx(l, S2CFx.SPREAD, b.position().add(0, 1.2, 0), 0.8f, 0, 0, b.getId(), -1); }
                case "beelzebub" -> { if (t != null) Beelzebub.devour(p, b, t); }
                case "nuke" -> { if (t != null && !SporeManager.on(t, p.getUUID()).isEmpty()) Payloads.insideOut(p, b, t); }
                case "triangle" -> { }
                default -> { }
            }
        }
    }

    private static LivingEntity firstAlive(LivingEntity a, LivingEntity b) {
        if (a != null && a.isAlive()) return a;
        return b != null && b.isAlive() ? b : null;
    }

    static LivingEntity nearestEnemy(LivingEntity from, double r) {
        UUID o = Util.ownerOf(from);
        LivingEntity best = null;
        double bd = r * r;
        for (LivingEntity e : from.level().getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(r), x -> x.isAlive() && (x instanceof Enemy || (x instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null && Util.friend(o, m.getTarget()))))) {
            if (Util.friend(o, e)) continue;
            double dd = e.distanceToSqr(from);
            if (dd < bd) { bd = dd; best = e; }
        }
        return best;
    }

    /** Roster for the client: slot, entity id, hp, max, dimension, position, order, main flag, you flag. */
    public static ListTag rosterTag(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        ListTag list = new ListTag();
        CompoundTag me = new CompoundTag();
        me.putInt("slot", d.inMain ? 0 : d.slot); me.putBoolean("you", true); me.putBoolean("main", d.inMain);
        me.putFloat("hp", p.getHealth()); me.putFloat("max", p.getMaxHealth()); me.putString("dim", p.level().dimension().location().toString());
        me.putDouble("x", p.getX()); me.putDouble("y", p.getY()); me.putDouble("z", p.getZ()); me.putInt("order", -1);
        list.add(me);
        List<UUID> all = new ArrayList<>(d.clones);
        if (!d.inMain && d.mainBody != null) all.add(0, d.mainBody);
        for (UUID u : all) {
            Last l = LAST.get(u);
            if (l == null) continue;
            CompoundTag t = new CompoundTag();
            t.putInt("slot", l.slot); t.putBoolean("main", l.main); t.putFloat("hp", l.hp); t.putFloat("max", l.max);
            t.putString("dim", l.dim.location().toString()); t.putDouble("x", l.pos.x); t.putDouble("y", l.pos.y); t.putDouble("z", l.pos.z); t.putInt("order", l.order);
            list.add(t);
        }
        return list;
    }

    /** Keep the last known places of this player's bodies in their save, so they are found again after a restart. */
    public static void saveTo(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        ListTag list = new ListTag();
        List<UUID> all = new ArrayList<>(d.clones);
        if (d.mainBody != null) all.add(d.mainBody);
        for (UUID u : all) {
            Last l = LAST.get(u);
            if (l == null) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("id", u); t.putString("dim", l.dim.location().toString()); t.putDouble("x", l.pos.x); t.putDouble("y", l.pos.y); t.putDouble("z", l.pos.z);
            t.putFloat("hp", l.hp); t.putFloat("max", l.max); t.putInt("slot", l.slot); t.putBoolean("main", l.main); t.putInt("order", l.order);
            list.add(t);
        }
        p.getPersistentData().put("abyss_bodies", list);
    }

    public static void loadFrom(ServerPlayer p) {
        for (net.minecraft.nbt.Tag x : p.getPersistentData().getList("abyss_bodies", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(t.getString("dim")));
            Vec3 pos = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
            UUID id = t.getUUID("id");
            LAST.putIfAbsent(id, new Last(dim, pos, t.getFloat("hp"), t.getFloat("max"), t.getInt("slot"), t.getBoolean("main"), t.getInt("order")));
            ServerLevel l = p.server.getLevel(dim);
            if (l != null) { ChunkPos cp = new ChunkPos(BlockPos.containing(pos)); l.getChunkSource().addRegionTicket(AbyssBody.TICKET, cp, 2, cp); }
        }
    }

    public static int cp(ServerPlayer p) { return CombatPower.of(p); }
    private Bodies() {}
}
