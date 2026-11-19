package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A hit landed: hit-stop, a flash on the victim, a damage number. kind: 0 normal, 1 heavy, 2 finisher/counter, 3 perfect dodge. */
public record S2CHit(int attacker, int victim, float damage, int kind) {
    public void write(FriendlyByteBuf b) { b.writeVarInt(attacker); b.writeVarInt(victim); b.writeFloat(damage); b.writeByte(kind); }
    public static S2CHit read(FriendlyByteBuf b) { return new S2CHit(b.readVarInt(), b.readVarInt(), b.readFloat(), b.readByte()); }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.render.MoveFx.hit(this)));
        ctx.get().setPacketHandled(true);
    }
}
