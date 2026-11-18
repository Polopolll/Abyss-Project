package com.andy.abyss.spore;

import com.andy.abyss.Config;
import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CSpores;
import com.andy.abyss.registry.ModSounds;
import com.andy.abyss.server.Corrosion;
import com.andy.abyss.server.Domains;
import com.andy.abyss.server.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Every spore in every dimension. Spores are light server-side data, not entities, so hundreds can exist. Free spores fly
 * outward fast, slow to a drift and pass through blocks; on meeting a living thing each one rolls to latch (weaker
 * things are easier to infect) or pass through. Latched spores corrode the host, multiply inside it, and jump to
 * anything touching it.
 */
public final class SporeManager {
    private static final Map<ServerLevel, List<Spore>> BY_LEVEL = new HashMap<>();
    private static final Map<Integer, Spore> BY_ID = new HashMap<>();
    private static final Map<Integer, ServerLevel> LEVEL_OF = new HashMap<>();
    private static int nextId = 1;
    private static final RandomSource R = RandomSource.create();
    static final double DRAG = 0.72;

    public static List<Spore> in(ServerLevel l) { return BY_LEVEL.computeIfAbsent(l, k -> new ArrayList<>()); }
    public static Spore byId(int id) { return BY_ID.get(id); }
    public static ServerLevel levelOf(Spore s) { return LEVEL_OF.get(s.id); }

    public static Spore add(ServerLevel l, UUID owner, int ownerEntity, Vec3 p, Vec3 v) {
        Spore s = new Spore(nextId++, owner, ownerEntity, p, v, Config.sporeLifeSeconds.get() * 20);
        in(l).add(s);
        BY_ID.put(s.id, s);
        LEVEL_OF.put(s.id, l);
        enforceCap(owner);
        return s;
    }

    public static void remove(Spore s) {
        ServerLevel l = LEVEL_OF.remove(s.id);
        BY_ID.remove(s.id);
        if (l != null) in(l).remove(s);
    }

    public static void clear(UUID owner) {
        for (Spore s : new ArrayList<>(BY_ID.values())) if (owner == null || owner.equals(s.owner)) remove(s);
    }

    private static void enforceCap(UUID owner) {
        int max = Config.maxSporesPerUser.get();
        List<Spore> mine = new ArrayList<>();
        for (Spore s : BY_ID.values()) if (s.owner.equals(owner)) mine.add(s);
        if (mine.size() <= max) return;
        // the oldest free spores fade first; latched ones are the last to go
        mine.sort(Comparator.comparing((Spore s) -> s.latched()).thenComparingInt(s -> -s.age));
        for (int i = 0; i < mine.size() - max; i++) remove(mine.get(i));
    }

    public static int count(UUID owner) { int n = 0; for (Spore s : BY_ID.values()) if (s.owner.equals(owner)) n++; return n; }
    public static int latched(UUID owner) { int n = 0; for (Spore s : BY_ID.values()) if (s.owner.equals(owner) && s.latched()) n++; return n; }

    public static List<Spore> on(LivingEntity host, UUID owner) {
        List<Spore> out = new ArrayList<>();
        for (Spore s : in((ServerLevel) host.level())) if (s.host == host && (owner == null || owner.equals(s.owner))) out.add(s);
        return out;
    }

    /** A 360° burst from {@code from}: spores go out on a sphere at near-instant speed, then slow to a drift. */
    public static void burst(ServerLevel l, UUID owner, LivingEntity from, int count) {
        Vec3 c = from.position().add(0, from.getBbHeight() * 0.6, 0);
        double speed = Config.sporeRange.get() * (1 - DRAG);
        double golden = Math.PI * (3 - Math.sqrt(5));
        double rot = R.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double yy = 1 - (i + 0.5) / count * 2;
            double r = Math.sqrt(1 - yy * yy);
            double th = golden * i + rot;
            Vec3 dir = new Vec3(Math.cos(th) * r, yy * 0.8, Math.sin(th) * r).normalize();
            double s = speed * (0.55 + R.nextDouble() * 0.6);
            Spore sp = add(l, owner, from.getId(), c.add(dir.scale(0.6)), dir.scale(s));
            sp.passed.add(from.getId());
        }
        Util.sound(l, c, ModSounds.SPREAD.get(), 1.2f, 0.8f + R.nextFloat() * 0.3f);
    }

    /** Plant a single spore at a point (latched if it is on an entity). */
    public static Spore plant(ServerLevel l, UUID owner, int ownerEntity, Vec3 p, LivingEntity on) {
        Spore s = add(l, owner, ownerEntity, p, Vec3.ZERO);
        s.planted = true;
        if (on != null) latch(s, on);
        return s;
    }

    /** A seeker: shot along a line, latches onto the first non-friend living thing it meets. */
    public static Spore shoot(ServerLevel l, UUID owner, int ownerEntity, Vec3 from, Vec3 dir) {
        Spore s = add(l, owner, ownerEntity, from, dir.normalize().scale(4.0));
        s.seeker = true;
        s.passed.add(ownerEntity);
        return s;
    }

    static void latch(Spore s, LivingEntity e) {
        s.host = e;
        double w = e.getBbWidth() * 0.5, h = e.getBbHeight();
        // a random point on the host's body surface
        double a = R.nextDouble() * Math.PI * 2;
        s.ox = Math.cos(a) * w * 0.95; s.oz = Math.sin(a) * w * 0.95; s.oy = h * (0.15 + R.nextDouble() * 0.75);
        s.vx = s.vy = s.vz = 0;
        s.seeker = false; s.planted = false;
    }

    public static void tick(ServerLevel l) {
        List<Spore> list = in(l);
        if (list.isEmpty()) return;
        long gt = l.getGameTime();
        Map<LivingEntity, List<Spore>> hosts = new HashMap<>();
        Map<UUID, Integer> cpCache = new HashMap<>();
        for (Spore s : new ArrayList<>(list)) {
            if (++s.age > s.life) { remove(s); continue; }
            if (s.host != null) {
                if (!s.host.isAlive() || s.host.isRemoved() || s.host.level() != l) {
                    // the host is gone: the spores burst free
                    Vec3 p = s.host.position().add(s.ox, s.oy, s.oz);
                    s.host = null;
                    s.x = p.x; s.y = p.y; s.z = p.z;
                    s.vx = (R.nextDouble() - 0.5) * 0.6; s.vy = R.nextDouble() * 0.4; s.vz = (R.nextDouble() - 0.5) * 0.6;
                    continue;
                }
                Vec3 p = s.host.position();
                s.x = p.x + s.ox; s.y = p.y + s.oy; s.z = p.z + s.oz;
                hosts.computeIfAbsent(s.host, k -> new ArrayList<>()).add(s);
                continue;
            }
            if (s.planted) continue;
            double px = s.x, py = s.y, pz = s.z;
            s.x += s.vx; s.y += s.vy; s.z += s.vz;
            double sp = Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
            if (s.seeker) {
                if (s.age > 20) { s.seeker = false; s.vx *= 0.1; s.vy *= 0.1; s.vz *= 0.1; }
            } else if (sp > 0.06) {
                s.vx *= DRAG; s.vy *= DRAG; s.vz *= DRAG;
            } else {
                // drift: slow, wandering, slightly sinking — the abyss settles over things
                s.vx = s.vx * 0.95 + (R.nextDouble() - 0.5) * 0.006;
                s.vy = s.vy * 0.95 + (R.nextDouble() - 0.52) * 0.004;
                s.vz = s.vz * 0.95 + (R.nextDouble() - 0.5) * 0.006;
            }
            if (s.y < l.getMinBuildHeight() - 16) { remove(s); continue; }
            // contact
            if (sp > 0.02 || (s.age + s.id) % 4 == 0) {
                AABB seg = new AABB(px, py, pz, s.x, s.y, s.z).inflate(0.35);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, seg, x -> x.isAlive() && !x.isSpectator())) {
                    if (s.passed.contains(e.getId()) || Util.friend(s.owner, e)) continue;
                    double chance;
                    if (s.seeker || Domains.sureHit(s.owner, e)) chance = 1;
                    else {
                        int ocp = cpCache.computeIfAbsent(s.owner, u -> { ServerPlayer o = Util.player(u); return o == null ? 100 : CombatPower.of(o); });
                        double adv = CombatPower.advantage(ocp, CombatPower.of(e));
                        chance = Config.sporeLatchChance.get() * (0.75 + 0.5 * adv);
                    }
                    if (R.nextDouble() < chance && on(e, null).size() < Config.maxSporesPerHost.get() * 2) { latch(s, e); break; }
                    s.passed.add(e.getId());
                }
            }
        }
        // hosts: corrosion, multiplication, jumping
        int infect = Config.infectSeconds.get() * 20;
        for (var en : hosts.entrySet()) {
            LivingEntity h = en.getKey();
            List<Spore> sp = en.getValue();
            if ((gt + h.getId()) % 20 == 0) {
                Corrosion.atLeast(h, Math.min(10, (sp.size() + 1) / 2), 100);
                if (sp.size() >= 2) Util.hurt(sp.get(0).owner, null, h, 0.25f * sp.size() * Config.corrosionStrength.get().floatValue(), "corrosion");
                // jump to anything touching the host
                for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, h.getBoundingBox().inflate(0.4), x -> x != h && x.isAlive())) {
                    Spore src = sp.get(R.nextInt(sp.size()));
                    if (Util.friend(src.owner, t) || on(t, null).size() >= Config.maxSporesPerHost.get()) continue;
                    if (R.nextDouble() < Config.sporeJumpChance.get()) {
                        Spore n = add(l, src.owner, src.ownerEntity, t.position(), Vec3.ZERO);
                        latch(n, t);
                    }
                }
            }
            if ((gt + h.getId() * 7L) % infect == 0 && sp.size() < Config.maxSporesPerHost.get()) {
                Spore src = sp.get(R.nextInt(sp.size()));
                Spore n = add(l, src.owner, src.ownerEntity, h.position(), Vec3.ZERO);
                latch(n, h);
            }
        }
    }

    /** Send each player in this level the spores near them, every few ticks. */
    public static void sync(ServerLevel l) {
        List<Spore> list = in(l);
        for (ServerPlayer p : l.players()) {
            if ((l.getGameTime() + p.getId()) % 3 != 0) continue;
            List<Spore> near = new ArrayList<>();
            Vec3 c = p.position();
            for (Spore s : list) {
                double dx = s.x - c.x, dy = s.y - c.y, dz = s.z - c.z;
                if (dx * dx + dy * dy + dz * dz < 128 * 128) near.add(s);
            }
            if (near.isEmpty() && !list.isEmpty() && (l.getGameTime() % 30 != 0)) continue;
            if (near.size() > 2000) {
                near.sort(Comparator.comparingDouble(s -> (s.x - c.x) * (s.x - c.x) + (s.y - c.y) * (s.y - c.y) + (s.z - c.z) * (s.z - c.z)));
                near = near.subList(0, 2000);
            }
            int n = near.size();
            int[] ids = new int[n], ow = new int[n], hs = new int[n];
            float[] xyz = new float[n * 3];
            byte[] fl = new byte[n];
            for (int i = 0; i < n; i++) {
                Spore s = near.get(i);
                ids[i] = s.id; ow[i] = s.ownerEntity; hs[i] = s.host != null ? s.host.getId() : -1;
                if (s.host != null) { xyz[i * 3] = (float) s.ox; xyz[i * 3 + 1] = (float) s.oy; xyz[i * 3 + 2] = (float) s.oz; }
                else { xyz[i * 3] = (float) s.x; xyz[i * 3 + 1] = (float) s.y; xyz[i * 3 + 2] = (float) s.z; }
                fl[i] = (byte) ((s.owner.equals(p.getUUID()) ? S2CSpores.MINE : 0) | (s.planted ? S2CSpores.PLANTED : 0));
            }
            Net.to(p, new S2CSpores(ids, ow, hs, xyz, fl));
        }
    }

    public static void onLevelUnload(ServerLevel l) {
        for (Spore s : in(l)) { BY_ID.remove(s.id); LEVEL_OF.remove(s.id); }
        BY_LEVEL.remove(l);
    }

    /** Spores within range of a point (free or latched), owned by owner. */
    public static List<Spore> near(ServerLevel l, Vec3 p, double r, UUID owner) {
        List<Spore> out = new ArrayList<>();
        for (Spore s : in(l)) if ((owner == null || owner.equals(s.owner)) && s.pos().distanceToSqr(p) < r * r) out.add(s);
        return out;
    }

    static float clampf(float v) { return Mth.clamp(v, 0, 1); }
    private SporeManager() {}
}
