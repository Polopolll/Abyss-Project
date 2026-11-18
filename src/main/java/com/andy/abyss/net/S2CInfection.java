package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Abyss infection on blocks: full snapshot or changes. Stage 0 = cleansed. */
public record S2CInfection(boolean full, long[] pos, byte[] stage) {
    public void write(FriendlyByteBuf b) {
        b.writeBoolean(full);
        b.writeVarInt(pos.length);
        for (int i = 0; i < pos.length; i++) { b.writeLong(pos[i]); b.writeByte(stage[i]); }
    }
    public static S2CInfection read(FriendlyByteBuf b) {
        boolean full = b.readBoolean();
        int n = Math.min(b.readVarInt(), 200000);
        long[] p = new long[n]; byte[] s = new byte[n];
        for (int i = 0; i < n; i++) { p[i] = b.readLong(); s[i] = b.readByte(); }
        return new S2CInfection(full, p, s);
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.InfectionClient.apply(this)));
        ctx.get().setPacketHandled(true);
    }
}
