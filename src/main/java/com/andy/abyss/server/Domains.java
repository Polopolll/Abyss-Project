package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CWorld;
import com.andy.abyss.registry.ModBlocks;
import com.andy.abyss.registry.ModEffects;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Domain Expansion. OPEN pours the abyss into the world: a sphere that spreads at the expansion rate until the output
 * invested runs out. CLOSED does the inverse: the sphere of world and everything in it is copied into an arena in the
 * Abyss and its occupants are taken there; when it ends they come back. Inside either: the weight of an infinite
 * dimension crushes, corrodes and pins, and every ability of the owner is a sure hit.
 */
public final class Domains {
    public static final class Domain {
        public final int id;
        public final UUID owner;
        public ServerLevel level;
        public Vec3 center;
        public double radius, max;
        public final boolean closed, small;
        public int age, duration;
        // closed
        ServerLevel origin; Vec3 originCenter; BlockPos arena; int phase; Iterator<BlockPos> copyIt;
        final List<UUID> moved = new ArrayList<>(); final Map<UUID, Vec3> returnTo = new HashMap<>(); final Map<UUID, ServerLevel> returnLevel = new HashMap<>();
        final List<BlockPos> placed = new ArrayList<>();
        /** What the domain's rot replaced, so the world can heal afterwards. */
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet infected = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        Domain(int id, UUID owner, ServerLevel l, Vec3 c, double max, boolean closed, boolean small, int duration) {
            this.id = id; this.owner = owner; this.level = l; this.center = c; this.max = max; this.closed = closed; this.small = small; this.duration = duration;
        }
        public boolean contains(Entity e) { return e.level() == level && e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(center) <= radius; }
    }

    public static final List<Domain> ALL = new ArrayList<>();
    private static int nextId = 1, arenaSlot;

    public static boolean sureHit(UUID owner, Entity target) {
        if (owner == null) return false;
        for (Domain d : ALL) if (owner.equals(d.owner) && d.contains(target) && d.phase >= 2 - (d.closed ? 0 : 2)) return true;
        return false;
    }

    /** Is this block inside a live domain that infected it (so it should not recede yet). */
    public static boolean holds(ServerLevel l, long pos) {
        for (Domain d : ALL) if (d.level == l && d.infected.contains(pos)) return true;
        return false;
    }

    public static Domain ownedBy(UUID owner) {
        for (Domain d : ALL) if (d.owner.equals(owner) && !d.small) return d;
        return null;
    }

    public static boolean inOwnDomain(Entity e, UUID owner) {
        for (Domain d : ALL) if (d.owner.equals(owner) && d.contains(e)) return true;
        return false;
    }

    public static void open(ServerPlayer p, float invested) {
        Domain old = ownedBy(p.getUUID());
        if (old != null) end(old);
        double max = Math.min(Config.domainMaxRadius.get(), 12 + invested / 2.0);
        Domain d = new Domain(nextId++, p.getUUID(), p.serverLevel(), p.position().add(0, 1, 0), max, false, false, Config.domainSeconds.get() * 20);
        d.radius = 1;
        ALL.add(d);
        Util.fx(d.level, S2CFx.DOMAIN_CAST, d.center, (float) max, 0, 0, p.getId(), -1);
        Util.sound(d.level, d.center, ModSounds.DOMAIN.get(), 3f, 0.6f);
    }

    public static void small(UUID owner, ServerLevel l, Vec3 c) {
        Domain d = new Domain(nextId++, owner, l, c, 10, false, true, 8 * 20);
        d.radius = 1;
        ALL.add(d);
        Util.fx(l, S2CFx.DOMAIN_CAST, c, 10, 1, 0);
        Util.sound(l, c, ModSounds.DOMAIN.get(), 1.5f, 1.1f);
    }

    public static void closed(ServerPlayer p, float invested) {
        Domain old = ownedBy(p.getUUID());
        if (old != null) end(old);
        ServerLevel abyss = AbyssDim.level(p.server);
        if (abyss == null) { p.displayClientMessage(net.minecraft.network.chat.Component.literal("§5The Abyss dimension is missing."), true); return; }
        double r = Math.min(Config.closedMaxRadius.get(), 8 + invested / 8.0);
        Domain d = new Domain(nextId++, p.getUUID(), abyss, null, r, true, false, Config.closedSeconds.get() * 20);
        d.origin = p.serverLevel();
        d.originCenter = Vec3.atBottomCenterOf(p.blockPosition()).add(0, 1, 0);
        d.arena = new BlockPos(300000 + (arenaSlot++ % 64) * 1000, 120, 0);
        d.center = Vec3.atBottomCenterOf(d.arena).add(0, 1, 0);
        d.radius = r;
        List<BlockPos> pts = new ArrayList<>();
        int ri = (int) Math.ceil(r);
        BlockPos oc = BlockPos.containing(d.originCenter);
        for (int y = -ri; y <= ri; y++) for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++)
            if (x * x + y * y + z * z <= r * r) pts.add(oc.offset(x, y, z));
        d.copyIt = pts.iterator();
        d.phase = 0;
        ALL.add(d);
        Util.fx(d.origin, S2CFx.DOMAIN_CAST, d.originCenter, (float) r, 2, 0, p.getId(), -1);
        Util.sound(d.origin, d.originCenter, ModSounds.DOMAIN.get(), 3f, 0.45f);
    }

    public static void tick(MinecraftServer server) {
        for (Domain d : new ArrayList<>(ALL)) {
            try { tickOne(server, d); } catch (Exception ex) { com.andy.abyss.Abyss.LOG.error("domain tick", ex); ALL.remove(d); }
        }
        Infection.tick(server);
        if (server.getTickCount() % 4 == 0) syncAll(server);
    }

    private static void tickOne(MinecraftServer server, Domain d) {
        ServerPlayer owner = Util.player(d.owner);
        if (d.closed) {
            if (d.phase == 0) { copy(d); return; }
            if (d.phase == 1) { move(d); d.phase = 2; return; }
            if (d.phase == 3) { clearArena(d); return; }
        }
        d.age++;
        if (!d.closed) d.radius = Math.min(d.max, d.radius + Config.domainExpansionRate.get() * (d.small ? 0.5 : 1));
        boolean ownerGone = owner == null || !owner.isAlive();
        if (d.age >= d.duration || (ownerGone && !d.small)) { end(d); return; }
        if (d.age % 10 == 0) weigh(d, owner);
        if (!d.small) decay(d);
        if (d.age % 50 == 25 && !d.small) pulse(d, owner);
        if (d.closed) {
            // nothing leaves a closed domain
            for (LivingEntity e : d.level.getEntitiesOfClass(LivingEntity.class, new AABB(d.center, d.center).inflate(d.radius + 8))) {
                Vec3 off = e.position().subtract(d.center);
                if (off.length() > d.radius - 0.5 || e.getY() < d.center.y - d.radius - 2) {
                    Vec3 back = d.center.add(off.normalize().scale(Math.max(0, d.radius - 2)));
                    if (e.getY() < d.center.y - d.radius - 2) back = d.center.add(0, 2, 0);
                    e.teleportTo(back.x, back.y, back.z);
                    e.setDeltaMovement(Vec3.ZERO);
                }
            }
        }
    }

    /** The weight of an infinite dimension: pins, drags down, crushes, corrodes. Scales with the Combat Power gap. */
    private static void weigh(Domain d, ServerPlayer owner) {
        int ocp = owner != null ? CombatPower.of(owner) : 500;
        AABB box = new AABB(d.center, d.center).inflate(d.radius);
        for (LivingEntity e : d.level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !x.isSpectator())) {
            if (!d.contains(e) || Util.friend(d.owner, e)) continue;
            if (e instanceof Player pl && pl.isCreative()) continue;
            double ratio = Math.max(0.5, Math.min(6, ocp / (double) Math.max(1, CombatPower.of(e))));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, (int) Math.min(5, 1 + ratio), false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 15, 2, false, false, false));
            if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));
            if (!e.onGround()) e.setDeltaMovement(e.getDeltaMovement().add(0, -0.25 * Math.min(3, ratio), 0));
            if (e instanceof ServerPlayer sp && sp.isFallFlying()) sp.stopFallFlying();
            e.hurtMarked = true;
            if (d.age % 20 == 0) {
                Util.hurt(d.owner, owner, e, (float) (Config.domainCrush.get() * ratio * (d.small ? 0.6 : 1)), "abyss");
                if (d.age % 40 == 0) Corrosion.add(e, 1, 200);
            }
        }
        if (owner != null && d.contains(owner)) {
            var data = com.andy.abyss.data.AbyssData.get(owner);
            data.output = Math.min(data.outputCap(), data.output + data.outputCap() * 0.1f);
        }
    }

    private static void copy(Domain d) {
        int budget = Math.max(500, Config.blockBudgetPerTick.get());
        BlockPos oc = BlockPos.containing(d.originCenter);
        while (budget-- > 0 && d.copyIt.hasNext()) {
            BlockPos src = d.copyIt.next();
            BlockState s = d.origin.getBlockState(src);
            if (s.isAir() || s.hasBlockEntity()) continue;
            BlockPos dst = d.arena.offset(src.getX() - oc.getX(), src.getY() - oc.getY(), src.getZ() - oc.getZ());
            d.level.setBlock(dst, s, 2 | 16);
            d.placed.add(dst);
        }
        if (!d.copyIt.hasNext()) {
            // a floor under the copy so nobody falls out through a cave
            AbyssDim.disc(d.level, d.arena.below((int) Math.ceil(d.radius) + 1), (int) Math.ceil(d.radius));
            d.phase = 1;
        }
    }

    private static void move(Domain d) {
        AABB box = new AABB(d.originCenter, d.originCenter).inflate(d.radius);
        for (Entity e : d.origin.getEntitiesOfClass(Entity.class, box, x -> x.isAlive() && !x.isPassenger() && x.position().distanceTo(d.originCenter) <= d.radius + 0.5)) {
            if (e instanceof Player pl && pl.isSpectator()) continue;
            if (!(e instanceof LivingEntity) && !(e instanceof net.minecraft.world.entity.item.ItemEntity)) continue;
            Vec3 off = e.position().subtract(d.originCenter);
            d.returnTo.put(e.getUUID(), e.position());
            d.returnLevel.put(e.getUUID(), d.origin);
            Entity moved = Util.teleport(e, d.level, d.center.add(off).add(0, 0.05, 0), e.getYRot(), e.getXRot(), false);
            d.moved.add(moved.getUUID());
            if (moved != e) { d.returnTo.put(moved.getUUID(), d.returnTo.get(e.getUUID())); d.returnLevel.put(moved.getUUID(), d.origin); }
        }
        Util.fx(d.level, S2CFx.DOMAIN_CAST, d.center, (float) d.radius, 3, 0);
    }

    public static void end(Domain d) {
        if (d.closed && d.phase == 2) {
            for (UUID id : d.moved) {
                Entity e = d.level.getEntity(id);
                ServerLevel back = d.returnLevel.get(id);
                Vec3 to = d.returnTo.get(id);
                if (e != null && e.isAlive() && back != null && to != null) Util.teleport(e, back, to, e.getYRot(), e.getXRot(), false);
            }
            d.phase = 3;
            Util.fx(d.origin, S2CFx.IMPLODE, d.originCenter, (float) d.radius, 1, 0);
            return;
        }
        if (d.closed && d.phase < 2) { d.phase = 3; return; }
        ALL.remove(d);
        Util.fx(d.level, S2CFx.IMPLODE, d.center, (float) d.radius, 0, 0);
        Infection.release(d.level, d.infected);
    }

    /**
     * The abyss eats the world inside the domain: plants, leaves, snow and liquids vanish; ground, stone and wood turn
     * to abyssal rot (galaxy). Surface columns first, so the ground visibly blackens outward with the expansion, plus
     * random points through the volume. Dropped items dissolve.
     */
    private static void decay(Domain d) {
        if (!Config.domainDecay.get() || !Util.griefing(d.level)) return;
        int budget = Config.domainDecayPerTick.get();
        var rand = d.level.getRandom();
        for (int i = 0; i < budget; i++) {
            BlockPos p;
            double a = rand.nextDouble() * Math.PI * 2, rr = Math.sqrt(rand.nextDouble()) * d.radius;
            int x = (int) Math.floor(d.center.x + Math.cos(a) * rr), z = (int) Math.floor(d.center.z + Math.sin(a) * rr);
            if (i % 3 != 0) {
                int top = d.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - 1 - (rand.nextInt(4) == 0 ? 1 : 0);
                if (Math.abs(top - d.center.y) > d.radius) continue;
                p = new BlockPos(x, top, z);
            } else {
                p = new BlockPos(x, (int) Math.floor(d.center.y + (rand.nextDouble() * 2 - 1) * d.radius), z);
                if (p.distSqr(BlockPos.containing(d.center)) > d.radius * d.radius) continue;
            }
            if (!d.level.isLoaded(p)) continue;
            if (Infection.infect(d.level, p, d.infected) && rand.nextInt(3) == 0) {
                // veins creep into a neighbour
                BlockPos n = p.relative(net.minecraft.core.Direction.getRandom(rand));
                if (n.distSqr(BlockPos.containing(d.center)) <= d.radius * d.radius) Infection.infect(d.level, n, d.infected);
            }
        }
        if (Config.domainDissolveItems.get() && d.age % 10 == 5) {
            for (net.minecraft.world.entity.item.ItemEntity it : d.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(d.center, d.center).inflate(d.radius))) {
                if (!d.contains(it) || d.owner.equals(it.getOwner() == null ? null : it.getOwner().getUUID())) continue;
                Util.fx(d.level, S2CFx.DISINTEGRATE, it.position(), 0.6f, 0, 0);
                it.discard();
            }
        }
    }

    /** Every few seconds the domain breathes: a pressure wave rolls out, slamming everything down and inward. */
    private static void pulse(Domain d, ServerPlayer owner) {
        Util.fx(d.level, S2CFx.DOMAIN_PULSE, d.center, (float) d.radius, 0, 0);
        Util.sound(d.level, d.center, ModSounds.HEARTBEAT.get(), 3f, 0.45f);
        Util.sound(d.level, d.center, ModSounds.DOMAIN.get(), 2f, 0.35f);
        for (LivingEntity e : d.level.getEntitiesOfClass(LivingEntity.class, new AABB(d.center, d.center).inflate(d.radius), x -> x.isAlive() && d.contains(x))) {
            if (Util.friend(d.owner, e)) continue;
            Vec3 in = d.center.subtract(e.position()).normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(in.x * 0.4, -0.8, in.z * 0.4));
            e.hurtMarked = true;
            Util.hurt(d.owner, owner, e, (float) Config.domainCrush.get().doubleValue(), "abyss");
        }
    }

    private static void clearArena(Domain d) {
        int budget = Math.max(1000, Config.blockBudgetPerTick.get());
        while (budget-- > 0 && !d.placed.isEmpty()) d.level.setBlock(d.placed.remove(d.placed.size() - 1), Blocks.AIR.defaultBlockState(), 2 | 16);
        if (d.placed.isEmpty()) ALL.remove(d);
    }

    private static void syncAll(MinecraftServer server) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.players().isEmpty()) continue;
            for (ServerPlayer p : l.players()) {
                List<S2CWorld.Dom> doms = new ArrayList<>();
                for (Domain d : ALL) {
                    if (d.level != l || d.phase == 3 || (d.closed && d.phase < 2)) continue;
                    ServerPlayer o = Util.player(d.owner);
                    doms.add(new S2CWorld.Dom(d.id, o != null ? o.getId() : -1, d.center.x, d.center.y, d.center.z, (float) d.radius, (float) d.max, d.closed, d.duration - d.age, d.owner.equals(p.getUUID())));
                }
                List<S2CWorld.Clash> cl = Triangle.snapshot(l, p);
                int[] cor = corroded(l, p);
                if (doms.isEmpty() && cl.isEmpty() && cor.length == 0 && server.getTickCount() % 40 != 0) continue;
                Net.to(p, new S2CWorld(doms, cl, cor));
            }
        }
    }

    private static int[] corroded(ServerLevel l, ServerPlayer p) {
        List<Integer> out = new ArrayList<>();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64), x -> x.hasEffect(ModEffects.CORROSION.get()))) {
            out.add(e.getId()); out.add(Corrosion.stacks(e));
            if (out.size() > 400) break;
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    public static void clearAll() {
        for (Domain d : new ArrayList<>(ALL)) end(d);
        Infection.healAll(Util.server());
    }

    private Domains() {}
}
