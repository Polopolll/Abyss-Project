package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CDebris;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Block destruction for the abyss's attacks: craters, trenches and spheres are erased from the world (no drops), and a
 * share of the erased blocks is thrown as real debris on the clients (little copies of the actual blocks tumbling out).
 * Rules: off with {@code blockDestruction=false}; never touches unbreakable blocks (hardness < 0), block entities
 * (chests, spawners...) or anything harder than {@code destructionMaxHardness}; at most {@code destructionPerCall}
 * blocks per call. Edges are noisy so craters look torn, not stamped.
 */
public final class Destruction {
    /** Is this block allowed to be erased by power `power` (≈ how hard a block it can break; obsidian ≈ 50). */
    public static boolean breakable(ServerLevel l, BlockPos p, BlockState s, float power) {
        if (s.isAir() || s.getBlock() == Blocks.BEDROCK) return false;
        float h = s.getDestroySpeed(l, p);
        if (h < 0 || h > Math.min(power, Config.destructionMaxHardness.get().floatValue())) return false;
        return l.getBlockEntity(p) == null;
    }

    public static boolean enabled(ServerLevel l) { return Config.blockDestruction.get(); }

    /**
     * A crater: a squashed half-sphere of radius r under/around c (deeper in the middle), with a torn edge.
     * power: hardest block it eats (stone 1.5, iron block 5, obsidian 50). launch: how hard debris flies (0.3 small, 1 nuke).
     * Returns the number of blocks erased.
     */
    public static int crater(ServerLevel l, Vec3 c, float r, float depth, float power, float launch) {
        if (!enabled(l) || r <= 0.4f) return 0;
        List<BlockPos> hit = new ArrayList<>();
        RandomSource rnd = l.random;
        int R = (int) Math.ceil(r + 1);
        BlockPos o = BlockPos.containing(c);
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            double d2 = (x * x + z * z) / (double) (r * r);
            if (d2 > 1.15) continue;
            double edge = 1 - d2;
            double n = 0.82 + rnd.nextDouble() * 0.36; // torn rim
            if (d2 > n) continue;
            int down = (int) Math.round(depth * Math.sqrt(Math.max(0, edge)) * (0.8 + rnd.nextDouble() * 0.4));
            for (int y = 1; y >= -down; y--) hit.add(o.offset(x, y, z));
        }
        return erase(l, hit, c, power, launch);
    }

    /** A sphere of radius r at c (a nuke core, a black star swallowing terrain). */
    public static int sphere(ServerLevel l, Vec3 c, float r, float power, float launch) {
        if (!enabled(l) || r <= 0.4f) return 0;
        List<BlockPos> hit = new ArrayList<>();
        int R = (int) Math.ceil(r);
        BlockPos o = BlockPos.containing(c);
        for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++) {
            double d = Math.sqrt(x * x + y * y + z * z) / r;
            if (d <= 0.85 + l.random.nextDouble() * 0.2) hit.add(o.offset(x, y, z));
        }
        return erase(l, hit, c, power, launch);
    }

    /** A trench along dir from start: length, half-width, depth (a fist/hammer shockwave, a cleave line). */
    public static int trench(ServerLevel l, Vec3 start, Vec3 dir, float length, float halfWidth, float depth, float power, float launch) {
        if (!enabled(l)) return 0;
        Vec3 f = new Vec3(dir.x, 0, dir.z);
        if (f.lengthSqr() < 1e-6) return 0;
        f = f.normalize();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        List<BlockPos> hit = new ArrayList<>();
        for (double t = 0; t <= length; t += 0.7) {
            double k = t / length, w = halfWidth * (1 - 0.5 * k) * (0.85 + l.random.nextDouble() * 0.3);
            int dd = (int) Math.max(1, Math.round(depth * (1 - 0.6 * k)));
            for (double s = -w; s <= w; s += 0.7) {
                Vec3 p = start.add(f.scale(t)).add(side.scale(s));
                BlockPos b = BlockPos.containing(p);
                for (int y = 0; y >= -dd; y--) hit.add(b.offset(0, y, 0));
            }
        }
        return erase(l, hit, start.add(f.scale(length * 0.3)), power, launch);
    }

    private static int erase(ServerLevel l, List<BlockPos> list, Vec3 c, float power, float launch) {
        int max = Config.destructionPerCall.get(), n = 0;
        List<BlockPos> debrisPos = new ArrayList<>();
        List<Integer> debrisState = new ArrayList<>();
        for (BlockPos p : list) {
            if (n >= max) break;
            BlockState s = l.getBlockState(p);
            if (!breakable(l, p, s, power)) continue;
            // a few of them fly as debris (the rest simply cease to exist)
            if (debrisPos.size() < 48 && l.random.nextFloat() < 0.35f && !s.getFluidState().isSource()) { debrisPos.add(p); debrisState.add(Block.getId(s)); }
            l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
            n++;
        }
        if (!debrisPos.isEmpty()) Net.near(l, c, 96, new S2CDebris(c.x, c.y, c.z, launch, debrisPos, debrisState));
        return n;
    }

    private Destruction() {}
}
