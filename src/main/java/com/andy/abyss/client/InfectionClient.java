package com.andy.abyss.client;

import com.andy.abyss.client.render.Draw;
import com.andy.abyss.client.render.Rt;
import com.andy.abyss.net.S2CInfection;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Infected blocks keep their own texture; the abyss is drawn over each exposed face: violet-edged galaxy veins that
 * spread through eight stages. Each face picks one of four patterns and one of four rotations from its position, so
 * neighbouring blocks never repeat.
 */
public final class InfectionClient {
    public static final Long2ByteOpenHashMap STAGE = new Long2ByteOpenHashMap();
    /** When each block last changed stage: a fresh change flares briefly. */
    private static final Long2ByteOpenHashMap FLARE = new Long2ByteOpenHashMap();

    public static void apply(S2CInfection p) {
        if (p.full()) STAGE.clear();
        for (int i = 0; i < p.pos().length; i++) {
            if (p.stage()[i] <= 0) STAGE.remove(p.pos()[i]);
            else { STAGE.put(p.pos()[i], p.stage()[i]); if (!p.full()) FLARE.put(p.pos()[i], (byte) 8); }
        }
    }

    public static void tick() {
        if (FLARE.isEmpty()) return;
        var it = FLARE.long2ByteEntrySet().fastIterator();
        while (it.hasNext()) { var e = it.next(); byte v = (byte) (e.getByteValue() - 1); if (v <= 0) it.remove(); else e.setValue(v); }
    }

    public static void clear() { STAGE.clear(); FLARE.clear(); }

    public static void render(Draw d) {
        var mc = Minecraft.getInstance();
        if (STAGE.isEmpty() || mc.level == null) return;
        VertexConsumer vc = d.buf.getBuffer(Rt.infection());
        Vec3 cam = d.cam;
        double maxD = 72 * 72;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(), np = new BlockPos.MutableBlockPos();
        for (Long2ByteMap.Entry e : STAGE.long2ByteEntrySet()) {
            long key = e.getLongKey();
            int stage = e.getByteValue();
            mp.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
            double dx = mp.getX() + 0.5 - cam.x, dy = mp.getY() + 0.5 - cam.y, dz = mp.getZ() + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > maxD) continue;
            BlockState s = mc.level.getBlockState(mp);
            if (s.isAir()) continue;
            var shape = s.getShape(mc.level, mp);
            if (shape.isEmpty()) continue;
            var box = shape.bounds();
            float flare = FLARE.get(key) / 8f;
            float alpha = 0.9f + 0.1f * flare;
            for (Direction dir : Direction.values()) {
                np.setWithOffset(mp, dir);
                BlockState n = mc.level.getBlockState(np);
                if (n.canOcclude() && n.isSolidRender(mc.level, np) && box.min(dir.getAxis()) <= 0.0 && box.max(dir.getAxis()) >= 1.0) continue;
                // only faces the camera can see
                double fx = mp.getX() + 0.5 + dir.getStepX() * 0.5 - cam.x, fy = mp.getY() + 0.5 + dir.getStepY() * 0.5 - cam.y, fz = mp.getZ() + 0.5 + dir.getStepZ() * 0.5 - cam.z;
                if (fx * dir.getStepX() + fy * dir.getStepY() + fz * dir.getStepZ() > 0) continue;
                face(d, vc, mp, box, dir, stage, key, alpha);
            }
        }
    }

    private static void face(Draw d, VertexConsumer vc, BlockPos p, net.minecraft.world.phys.AABB b, Direction dir, int stage, long key, float alpha) {
        long h = key * 31 + dir.ordinal() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        int variant = (int) (h & 3), rot = (int) ((h >>> 2) & 3);
        float u0 = variant * 0.25f, v0 = (Math.min(stage, 8) - 1) * 0.125f, u1 = u0 + 0.25f, v1 = v0 + 0.125f;
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        double x0 = p.getX() + b.minX, y0 = p.getY() + b.minY, z0 = p.getZ() + b.minZ, x1 = p.getX() + b.maxX, y1 = p.getY() + b.maxY, z1 = p.getZ() + b.maxZ;
        double[][] q = switch (dir) {
            case UP -> new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
            case DOWN -> new double[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case NORTH -> new double[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
            case SOUTH -> new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case WEST -> new double[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            case EAST -> new double[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
        };
        for (int i = 0; i < 4; i++) {
            float[] t = uv[(i + rot) & 3];
            vc.vertex(d.m, (float) (q[i][0] - d.cam.x), (float) (q[i][1] - d.cam.y), (float) (q[i][2] - d.cam.z))
                    .color(1f, 1f, 1f, alpha).uv(t[0], t[1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                    .normal(dir.getStepX(), dir.getStepY(), dir.getStepZ()).endVertex();
        }
    }
    private InfectionClient() {}
}
