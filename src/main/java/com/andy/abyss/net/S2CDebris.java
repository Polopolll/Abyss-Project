package com.andy.abyss.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Erased blocks to throw as debris: where the blast came from, how hard, and which blocks (positions + states). */
public record S2CDebris(double x, double y, double z, float launch, List<BlockPos> pos, List<Integer> states) {
    public void write(FriendlyByteBuf b) {
        b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeFloat(launch);
        b.writeVarInt(pos.size());
        for (int i = 0; i < pos.size(); i++) { b.writeBlockPos(pos.get(i)); b.writeVarInt(states.get(i)); }
    }
    public static S2CDebris read(FriendlyByteBuf b) {
        double x = b.readDouble(), y = b.readDouble(), z = b.readDouble(); float l = b.readFloat();
        int n = Math.min(64, b.readVarInt());
        List<BlockPos> p = new ArrayList<>(); List<Integer> s = new ArrayList<>();
        for (int i = 0; i < n; i++) { p.add(b.readBlockPos()); s.add(b.readVarInt()); }
        return new S2CDebris(x, y, z, l, p, s);
    }
    public static void handle(S2CDebris m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.andy.abyss.client.render.Debris.receive(m)));
        ctx.get().setPacketHandled(true);
    }
}
