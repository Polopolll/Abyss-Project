package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CInfection;
import com.andy.abyss.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * The abyss infecting the world block by block. A block keeps what it is; an overlay of galaxy veins grows over it in
 * five stages (thin veins → cracks → patches → spreading → nearly swallowed). Plants wither at stage 2, water and
 * lava boil off at stage 2, leaves crumble at stage 4. When the domain ends the infection recedes stage by stage and
 * anything that withered grows back (unless domainScarsPermanent, which turns fully infected blocks into Abyssal Rot).
 */
public final class Infection {
    public static final int MAX = 5;
    private static final Map<ServerLevel, Long2ByteOpenHashMap> STAGE = new HashMap<>();
    private static final Map<ServerLevel, Long2ByteOpenHashMap> CHANGED = new HashMap<>();
    /** What withered away (plants, liquids, leaves), to grow back. */
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<BlockState>> WITHERED = new HashMap<>();
    /** Positions that are receding (no domain holds them any more). */
    private static final Map<ServerLevel, LongArrayList> RECEDING = new HashMap<>();
    private static final Map<UUID, ServerLevel> SYNCED = new HashMap<>();

    static Long2ByteOpenHashMap map(ServerLevel l) { return STAGE.computeIfAbsent(l, k -> new Long2ByteOpenHashMap()); }
    public static int stage(ServerLevel l, BlockPos p) { var m = STAGE.get(l); return m == null ? 0 : m.get(p.asLong()); }
    public static int count(ServerLevel l) { var m = STAGE.get(l); return m == null ? 0 : m.size(); }

    private static void set(ServerLevel l, long p, int s) {
        if (s <= 0) map(l).remove(p); else map(l).put(p, (byte) s);
        CHANGED.computeIfAbsent(l, k -> new Long2ByteOpenHashMap()).put(p, (byte) Math.max(0, s));
    }

    /** Push one block one stage deeper. Returns true if something changed. */
    public static boolean infect(ServerLevel l, BlockPos p, LongSet held) {
        BlockState s = l.getBlockState(p);
        if (s.isAir() || s.hasBlockEntity() || s.is(ModBlocks.ABYSSAL_CRYSTAL.get()) || s.is(ModBlocks.ABYSSAL_ROT.get())) return false;
        if (s.getDestroySpeed(l, p) < 0) return false;
        long key = p.asLong();
        int cur = map(l).get(key);
        if (cur >= MAX) return false;
        int next = cur + 1;
        boolean liquid = !s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
        boolean plant = !liquid && (s.canBeReplaced() || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS)
                || (!s.isCollisionShapeFullBlock(l, p) && !s.is(BlockTags.LEAVES) && s.getDestroySpeed(l, p) < 0.6f));
        boolean leaves = s.is(BlockTags.LEAVES);
        if ((plant || liquid) && next >= 2 || leaves && next >= 4) {
            WITHERED.computeIfAbsent(l, k -> new Long2ObjectOpenHashMap<>()).putIfAbsent(key, s);
            l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
            set(l, key, 0);
            held.add(key);
            return true;
        }
        set(l, key, next);
        held.add(key);
        return true;
    }

    /** A domain ended: everything it held starts to recede (or, if scars are permanent, fully infected blocks become rot). */
    public static void release(ServerLevel l, LongSet held) {
        if (held.isEmpty()) return;
        if (Config.domainScarsPermanent.get()) {
            for (long k : held) {
                if (map(l).get(k) >= MAX) l.setBlock(BlockPos.of(k), ModBlocks.ABYSSAL_ROT.get().defaultBlockState(), 2 | 16);
                set(l, k, 0);
            }
            var w = WITHERED.get(l);
            if (w != null) for (long k : held) w.remove(k);
            return;
        }
        LongArrayList list = RECEDING.computeIfAbsent(l, k -> new LongArrayList());
        list.addAll(held);
    }

    public static void tick(MinecraftServer server) {
        // recede: a random handful step back each tick
        for (var en : RECEDING.entrySet()) {
            ServerLevel l = en.getKey();
            LongArrayList list = en.getValue();
            if (list.isEmpty()) continue;
            var rand = l.getRandom();
            int budget = Math.max(150, Config.domainDecayPerTick.get());
            for (int i = 0; i < budget && !list.isEmpty(); i++) {
                int idx = rand.nextInt(list.size());
                long k = list.getLong(idx);
                if (Domains.holds(l, k)) { list.removeLong(idx); continue; }
                int s = map(l).get(k);
                if (s > 0) set(l, k, s - 1);
                if (s <= 1) {
                    var w = WITHERED.get(l);
                    BlockState back = w == null ? null : w.remove(k);
                    if (back != null && l.getBlockState(BlockPos.of(k)).isAir()) l.setBlock(BlockPos.of(k), back, 2);
                    list.set(idx, list.getLong(list.size() - 1));
                    list.removeLong(list.size() - 1);
                }
            }
        }
        if (server.getTickCount() % 2 == 0) sync(server);
    }

    private static void sync(MinecraftServer server) {
        for (ServerLevel l : server.getAllLevels()) {
            var changed = CHANGED.remove(l);
            for (ServerPlayer p : l.players()) {
                if (SYNCED.get(p.getUUID()) != l) {
                    SYNCED.put(p.getUUID(), l);
                    var m = STAGE.get(l);
                    long[] pos = m == null ? new long[0] : m.keySet().toLongArray();
                    byte[] st = new byte[pos.length];
                    for (int i = 0; i < pos.length; i++) st[i] = m.get(pos[i]);
                    Net.to(p, new S2CInfection(true, pos, st));
                } else if (changed != null && !changed.isEmpty()) {
                    long[] pos = changed.keySet().toLongArray();
                    byte[] st = new byte[pos.length];
                    for (int i = 0; i < pos.length; i++) st[i] = changed.get(pos[i]);
                    Net.to(p, new S2CInfection(false, pos, st));
                }
            }
        }
    }

    public static void forget(ServerPlayer p) { SYNCED.remove(p.getUUID()); }

    /** Server stopping: put everything back now. */
    public static void healAll(MinecraftServer server) {
        for (var en : WITHERED.entrySet()) {
            ServerLevel l = en.getKey();
            for (var w : en.getValue().long2ObjectEntrySet()) if (l.getBlockState(BlockPos.of(w.getLongKey())).isAir()) l.setBlock(BlockPos.of(w.getLongKey()), w.getValue(), 2);
        }
        WITHERED.clear(); STAGE.clear(); CHANGED.clear(); RECEDING.clear(); SYNCED.clear();
    }
    private Infection() {}
}
