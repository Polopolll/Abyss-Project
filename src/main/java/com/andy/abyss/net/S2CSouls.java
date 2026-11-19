package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** In the Realm: where every soul of the reflected world is (same coordinates). */
public record S2CSouls(String world, String[] names, float[] xyz) {
    public void write(FriendlyByteBuf b) {
        b.writeUtf(world); b.writeVarInt(names.length);
        for (int i = 0; i < names.length; i++) { b.writeUtf(names[i], 64); b.writeFloat(xyz[i * 3]); b.writeFloat(xyz[i * 3 + 1]); b.writeFloat(xyz[i * 3 + 2]); }
    }
    public static S2CSouls read(FriendlyByteBuf b) {
        String w = b.readUtf();
        int n = Math.min(b.readVarInt(), 500);
        String[] names = new String[n]; float[] xyz = new float[n * 3];
        for (int i = 0; i < n; i++) { names[i] = b.readUtf(64); xyz[i * 3] = b.readFloat(); xyz[i * 3 + 1] = b.readFloat(); xyz[i * 3 + 2] = b.readFloat(); }
        return new S2CSouls(w, names, xyz);
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.ClientState.souls(this)));
        ctx.get().setPacketHandled(true);
    }
}
