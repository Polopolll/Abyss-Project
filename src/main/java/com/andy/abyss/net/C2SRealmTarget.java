package com.andy.abyss.net;

import com.andy.abyss.server.Realm;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Which world the Realm exits into. */
public record C2SRealmTarget(ResourceLocation dim) {
    public void write(FriendlyByteBuf b) { b.writeResourceLocation(dim); }
    public static C2SRealmTarget read(FriendlyByteBuf b) { return new C2SRealmTarget(b.readResourceLocation()); }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> { if (p != null) Realm.setTarget(p, dim); });
        ctx.get().setPacketHandled(true);
    }
}
