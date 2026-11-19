package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A snapshot of the spores near one viewer. Latched spores carry their host id and offset instead of a world position. */
public record S2CSpores(int[] ids, int[] owners, int[] hosts, float[] xyz, byte[] flags) {
    public static final byte MINE = 1, PLANTED = 2;

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(ids.length);
        for (int i = 0; i < ids.length; i++) {
            b.writeVarInt(ids[i]); b.writeVarInt(owners[i]); b.writeVarInt(hosts[i] + 1);
            b.writeFloat(xyz[i * 3]); b.writeFloat(xyz[i * 3 + 1]); b.writeFloat(xyz[i * 3 + 2]);
            b.writeByte(flags[i]);
        }
    }
    public static S2CSpores read(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 8192);
        int[] ids = new int[n], ow = new int[n], hs = new int[n]; float[] xyz = new float[n * 3]; byte[] fl = new byte[n];
        for (int i = 0; i < n; i++) {
            ids[i] = b.readVarInt(); ow[i] = b.readVarInt(); hs[i] = b.readVarInt() - 1;
            xyz[i * 3] = b.readFloat(); xyz[i * 3 + 1] = b.readFloat(); xyz[i * 3 + 2] = b.readFloat();
            fl[i] = b.readByte();
        }
        return new S2CSpores(ids, ow, hs, xyz, fl);
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.SporeClient.snapshot(this)));
        ctx.get().setPacketHandled(true);
    }
}
