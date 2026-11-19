package com.andy.abyss.client.render;

import com.andy.abyss.net.S2CDebris;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Chunks of the erased blocks flung out of a crater: real block models, tumbling, falling, crumbling to ash. */
public final class Debris {
    static final class Chunk { double x, y, z, vx, vy, vz; float rx, ry, rvx, rvy, size; int age, life; BlockState s; }
    private static final List<Chunk> ALL = new ArrayList<>();
    private static final Random R = new Random();

    public static void receive(S2CDebris m) {
        Vec3 c = new Vec3(m.x(), m.y(), m.z());
        for (int i = 0; i < m.pos().size(); i++) {
            if (ALL.size() > 260) break;
            BlockPos p = m.pos().get(i);
            Chunk k = new Chunk();
            k.s = Block.stateById(m.states().get(i));
            k.x = p.getX() + 0.5; k.y = p.getY() + 0.5; k.z = p.getZ() + 0.5;
            Vec3 out = new Vec3(k.x - c.x, 0, k.z - c.z);
            out = out.lengthSqr() < 1e-4 ? Dust.rnd().multiply(1, 0, 1) : out.normalize();
            double sp = m.launch() * (0.35 + R.nextDouble() * 0.65);
            k.vx = out.x * sp * 0.6 + (R.nextDouble() - 0.5) * 0.15; k.vz = out.z * sp * 0.6 + (R.nextDouble() - 0.5) * 0.15;
            k.vy = sp * (0.6 + R.nextDouble() * 0.8);
            k.size = 0.25f + R.nextFloat() * 0.35f; k.life = 30 + R.nextInt(30);
            k.rvx = (R.nextFloat() - 0.5f) * 30; k.rvy = (R.nextFloat() - 0.5f) * 30;
            ALL.add(k);
        }
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        ALL.removeIf(k -> {
            k.age++;
            k.vy -= 0.06; k.vx *= 0.985; k.vz *= 0.985;
            k.x += k.vx; k.y += k.vy; k.z += k.vz;
            k.rx += k.rvx; k.ry += k.rvy;
            boolean ground = mc.level != null && !mc.level.getBlockState(BlockPos.containing(k.x, k.y - k.size * 0.5, k.z)).isAir();
            if (ground && k.vy < 0) { k.vy *= -0.3; k.vx *= 0.6; k.vz *= 0.6; k.rvx *= 0.5; k.rvy *= 0.5; }
            if (k.age >= k.life) { // it doesn't land as a block: it crumbles into ash
                Dust.burst(new Vec3(k.x, k.y, k.z), 4, 0.05, 0.06f, false);
                return true;
            }
            return false;
        });
    }

    public static void render(PoseStack ps, MultiBufferSource buf, Vec3 cam, float pt) {
        if (ALL.isEmpty()) return;
        var br = Minecraft.getInstance().getBlockRenderer();
        for (Chunk k : ALL) {
            float shrink = k.age > k.life - 8 ? (k.life - k.age - pt) / 8f : 1;
            float sz = k.size * Math.max(0, shrink);
            if (sz <= 0.01f) continue;
            ps.pushPose();
            ps.translate(k.x + k.vx * pt - cam.x, k.y + k.vy * pt - cam.y, k.z + k.vz * pt - cam.z);
            ps.mulPose(Axis.XP.rotationDegrees(k.rx + k.rvx * pt));
            ps.mulPose(Axis.YP.rotationDegrees(k.ry + k.rvy * pt));
            ps.scale(sz, sz, sz);
            ps.translate(-0.5, -0.5, -0.5);
            br.renderSingleBlock(k.s, ps, buf, 0xF000F0, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    public static void clear() { ALL.clear(); }
    private Debris() {}
}
