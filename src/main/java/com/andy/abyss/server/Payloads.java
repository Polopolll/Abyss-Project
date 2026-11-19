package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.data.Payload;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModSounds;
import com.andy.abyss.spore.Spore;
import com.andy.abyss.spore.SporeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** What spores become. Each payload consumes the spore it grows from (a nuke in a host consumes all of them). */
public final class Payloads {
    public static void request(ServerPlayer p, int payloadIdx, int[] sporeIds, int[] hostIds) {
        AbyssData d = AbyssData.get(p);
        Payload pl = Payload.byIndex(payloadIdx);
        if (!d.awakened || pl == null) return;
        UUID me = p.getUUID();
        int done = 0;
        // hosts first: every latched spore of mine inside them
        Set<Integer> usedSpores = new HashSet<>();
        for (int hid : hostIds) {
            Entity he = null;
            for (ServerLevel l : p.server.getAllLevels()) { he = l.getEntity(hid); if (he != null) break; }
            if (!(he instanceof LivingEntity host) || !host.isAlive()) continue;
            List<Spore> in = SporeManager.on(host, me);
            if (in.isEmpty()) continue;
            if (!d.spend(pl.cost)) { noOutput(p, done); return; }
            for (Spore s : in) usedSpores.add(s.id);
            applyToHost(p, pl, host, in);
            done++;
        }
        for (int sid : sporeIds) {
            if (usedSpores.contains(sid)) continue;
            Spore s = SporeManager.byId(sid);
            if (s == null || !s.owner.equals(me)) continue;
            ServerLevel l = SporeManager.levelOf(s);
            if (l == null) continue;
            if (s.latched()) {
                if (!d.spend(pl.cost)) { noOutput(p, done); return; }
                LivingEntity host = s.host;
                List<Spore> in = SporeManager.on(host, me);
                for (Spore x : in) usedSpores.add(x.id);
                applyToHost(p, pl, host, pl == Payload.NUKE ? in : List.of(s));
                done++;
                continue;
            }
            if (!d.spend(pl.cost)) { noOutput(p, done); return; }
            SporeManager.remove(s);
            applyAt(p, pl, l, s.pos());
            done++;
            if (pl == Payload.STEP || pl == Payload.RIFT) break;
        }
        if (done > 0) {
            Actions.mastery(p, 0.04f * Math.min(10, done));
            if (pl == Payload.NUKE) Bodies.mirror(p, "nuke");
        }
    }

    private static void noOutput(ServerPlayer p, int done) {
        p.displayClientMessage(Component.literal("§5Output spent" + (done > 0 ? " after " + done : "") + "."), true);
    }

    static void applyToHost(ServerPlayer p, Payload pl, LivingEntity host, List<Spore> spores) {
        ServerLevel l = (ServerLevel) host.level();
        Vec3 c = host.position().add(0, host.getBbHeight() * 0.5, 0);
        switch (pl) {
            case NUKE -> { for (Spore s : spores) SporeManager.remove(s); insideOutN(p, p, host, spores.size()); }
            case DOMAIN -> { consume(spores); Domains.small(p.getUUID(), l, c); }
            case BEELZEBUB -> { consume(spores); Beelzebub.devour(p, p, host); }
            case TRANSPORT -> { consume(spores); Beelzebub.transport(p, host); }
            case DISINTEGRATE -> { consume(spores); disintegrate(p, host, Config.disintegrateDamage.get().floatValue() * Util.masteryScale(AbyssData.get(p))); }
            case RIFT -> { consume(spores); Rifts.toPoint(p, l, c.add(host.getLookAngle().scale(-2))); }
            case CLONE -> { consume(spores); Bodies.sendClone(p, l, host.position().add(host.getLookAngle().scale(-1.5))); }
            case STEP -> { consume(spores); Rifts.step(p, l, host.position().add(host.getLookAngle().scale(-1.5))); }
        }
    }

    private static void consume(List<Spore> sp) { if (!sp.isEmpty()) SporeManager.remove(sp.get(0)); }

    static void applyAt(ServerPlayer p, Payload pl, ServerLevel l, Vec3 at) {
        switch (pl) {
            case NUKE -> nuke(p, l, at, 1f);
            case DOMAIN -> Domains.small(p.getUUID(), l, at);
            case BEELZEBUB -> Beelzebub.mouth(p, l, at);
            case TRANSPORT -> {
                Util.fx(l, S2CFx.TRANSPORT, at, 3, 0, 0);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3), x -> x.isAlive() && !Util.friend(p.getUUID(), x)))
                    Beelzebub.transport(p, e);
            }
            case DISINTEGRATE -> {
                Util.fx(l, S2CFx.DISINTEGRATE, at, 2.5f, 0, 0);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5), x -> x.isAlive()))
                    disintegrate(p, e, Config.disintegrateDamage.get().floatValue() * Util.masteryScale(AbyssData.get(p)));
                consumeBlocks(l, at, 2.5, null);
            }
            case RIFT -> Rifts.toPoint(p, l, at);
            case CLONE -> Bodies.sendClone(p, l, at.subtract(0, 0.5, 0));
            case STEP -> Rifts.step(p, l, Rifts.safe(l, at.x, at.y - 1, at.z));
        }
    }

    /** Implosion, then a sphere of abyss that erases what it touches. */
    public static void nuke(ServerPlayer p, ServerLevel l, Vec3 at, float power) {
        AbyssData d = AbyssData.get(p);
        float r = (float) (Config.nukeRadius.get() * Math.sqrt(power) * (0.85 + d.mastery / 250f));
        Util.fx(l, S2CFx.IMPLODE, at, r, 0, 0);
        Util.sound(l, at, ModSounds.CHARGE.get(), 2f, 0.6f);
        UUID owner = p.getUUID();
        Scheduler.later(22, () -> {
            Util.fx(l, S2CFx.NUKE, at, r, 0, 0);
            Util.sound(l, at, ModSounds.NUKE.get(), 4f, 0.7f);
            float dmg = Config.nukeDamage.get().floatValue() * power * Util.masteryScale(d);
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), x -> x.isAlive())) {
                double dist = e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(at);
                if (dist > r || Util.friend(owner, e)) continue;
                float f = (float) Math.sqrt(1 - dist / r);
                Util.hurt(owner, Util.player(owner), e, dmg * (0.25f + 0.75f * f), "abyss");
                Corrosion.add(e, 3, 300);
                Vec3 push = e.position().subtract(at).normalize().scale(1.6 * f);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.4 * f, push.z));
                e.hurtMarked = true;
            }
            consumeBlocks(l, at, r * 0.6, null);
        });
    }

    /** Detonate every spore inside a host: the blast starts inside the body and ignores armor. */
    public static void insideOut(ServerPlayer p, Entity by, LivingEntity host) {
        List<Spore> in = SporeManager.on(host, p.getUUID());
        if (in.isEmpty()) return;
        if (!AbyssData.get(p).spend(Payload.NUKE.cost)) return;
        for (Spore s : in) SporeManager.remove(s);
        insideOutN(p, by, host, in.size());
    }

    static void insideOutN(ServerPlayer p, Entity by, LivingEntity host, int n) {
        // build-up: the host glows and cracks from within, then goes off
        ServerLevel l0 = (ServerLevel) host.level();
        Util.fx(l0, S2CFx.IMPLODE, host.position().add(0, host.getBbHeight() * 0.5, 0), Math.max(2, host.getBbWidth() * 2 + n * 0.3f), 1, 0, host.getId(), -1);
        Util.sound(l0, host.position(), ModSounds.CHARGE.get(), 1.5f, 1.3f);
        Scheduler.later(18, () -> { if (host.isAlive()) insideOutNow(p, by, host, n); });
    }

    static void insideOutNow(ServerPlayer p, Entity by, LivingEntity host, int n) {
        ServerLevel l = (ServerLevel) host.level();
        Vec3 c = host.position().add(0, host.getBbHeight() * 0.5, 0);
        Util.fx(l, S2CFx.NUKE, c, Math.max(2, host.getBbWidth() * 2 + n * 0.3f), 1, n, host.getId(), -1);
        Util.sound(l, c, ModSounds.NUKE.get(), 2.5f, 1.1f);
        float dmg = Config.nukeDamage.get().floatValue() * (1 + 0.6f * (n - 1)) * Util.masteryScale(AbyssData.get(p));
        Util.hurt(p.getUUID(), by, host, dmg, "inside_out");
        Corrosion.add(host, 2 + n / 2, 300);
        // the shell of the blast
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(3), x -> x != host && x.isAlive()))
            Util.hurt(p.getUUID(), by, e, dmg * 0.25f, "abyss");
    }

    /** Disintegration: damage that leaves nothing — killed this way, nothing drops. */
    public static void disintegrate(ServerPlayer p, LivingEntity e, float dmg) {
        if (Util.friend(p.getUUID(), e)) return;
        Util.fx((ServerLevel) e.level(), S2CFx.DISINTEGRATE, e.position().add(0, e.getBbHeight() / 2, 0), e.getBbWidth() + 1, 1, 0, e.getId(), -1);
        if (!(e instanceof Player)) e.getPersistentData().putBoolean("abyss_no_drops", true);
        Util.hurt(p.getUUID(), p, e, dmg, "inside_out");
        if (e.isAlive()) e.getPersistentData().remove("abyss_no_drops");
    }

    /** Erase a sphere of blocks (no drops), within the per-tick budget. */
    public static void consumeBlocks(ServerLevel l, Vec3 at, double r, Runnable done) {
        if (!Util.griefing(l)) { if (done != null) done.run(); return; }
        List<BlockPos> pts = new ArrayList<>();
        int ri = (int) Math.ceil(r);
        BlockPos c = BlockPos.containing(at);
        for (int x = -ri; x <= ri; x++) for (int y = -ri; y <= ri; y++) for (int z = -ri; z <= ri; z++)
            if (x * x + y * y + z * z <= r * r) pts.add(c.offset(x, y, z));
        Iterator<BlockPos> it = pts.iterator();
        int budget = Math.max(64, Config.blockBudgetPerTick.get());
        int runs = pts.size() / budget + 2;
        Scheduler.repeat(0, 1, runs, i -> {
            int b = budget;
            while (b-- > 0 && it.hasNext()) {
                BlockPos bp = it.next();
                if (Util.consumable(l, bp, l.getBlockState(bp))) l.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
            }
            if (!it.hasNext() && done != null && i == runs - 1) done.run();
        });
    }

    private Payloads() {}
}
