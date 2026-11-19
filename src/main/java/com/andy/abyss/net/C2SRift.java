package com.andy.abyss.net;

import com.andy.abyss.server.Rifts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SRift(ResourceLocation dim, double x, double y, double z, boolean step) {
    public void write(FriendlyByteBuf b) { b.writeResourceLocation(dim); b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeBoolean(step); }
    public static C2SRift read(FriendlyByteBuf b) { return new C2SRift(b.readResourceLocation(), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> { if (p != null) Rifts.request(p, dim, x, y, z, step); });
        ctx.get().setPacketHandled(true);
    }
}
