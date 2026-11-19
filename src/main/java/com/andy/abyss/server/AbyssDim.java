package com.andy.abyss.server;

import com.andy.abyss.Abyss;
import com.andy.abyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The Abyss dimension: an endless dark with galaxies in it. Thrones (main bodies), prisons and closed-domain arenas float in it. */
public final class AbyssDim {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, Abyss.id("the_abyss"));
    public static final int THRONE_Y = 100;

    public static ServerLevel level(MinecraftServer s) { return s.getLevel(KEY); }
    public static boolean is(Level l) { return l.dimension() == KEY; }

    /** Each user's throne: a floating crystal disc. Spread out by a hash of the player's id. */
    public static Vec3 throne(ServerLevel abyss, java.util.UUID owner) {
        int slot = Math.floorMod(owner.hashCode(), 64);
        BlockPos c = new BlockPos(slot * 512, THRONE_Y, 0);
        disc(abyss, c, 6);
        return Vec3.atBottomCenterOf(c.above());
    }

    /** Prison platform for transported things. */
    public static Vec3 prison(ServerLevel abyss, int index) {
        BlockPos c = new BlockPos(-20000 - Math.floorMod(index, 32) * 96, THRONE_Y, 20000);
        disc(abyss, c, 9);
        return Vec3.atBottomCenterOf(c.above());
    }

    public static void disc(ServerLevel l, BlockPos c, int r) {
        BlockState s = ModBlocks.ABYSSAL_CRYSTAL.get().defaultBlockState();
        if (l.getBlockState(c).is(s.getBlock())) return;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > r + 0.3) continue;
            l.setBlock(c.offset(x, 0, z), s, 2);
            if (d < r - 2.5) l.setBlock(c.offset(x, -1, z), s, 2);
            if (d < r - 4.5) l.setBlock(c.offset(x, -2, z), s, 2);
        }
    }
    private AbyssDim() {}
}
