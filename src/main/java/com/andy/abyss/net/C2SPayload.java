package com.andy.abyss.net;

import com.andy.abyss.server.Payloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Turn the selected spores (and the spores inside the selected hosts) into a payload. */
public record C2SPayload(int payload, int[] spores, int[] hosts) {
    public void write(FriendlyByteBuf b) { b.writeVarInt(payload); b.writeVarIntArray(spores); b.writeVarIntArray(hosts); }
    public static C2SPayload read(FriendlyByteBuf b) { return new C2SPayload(b.readVarInt(), b.readVarIntArray(4096), b.readVarIntArray(512)); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> { if (p != null) Payloads.request(p, payload, spores, hosts); });
        ctx.get().setPacketHandled(true);
    }
}
