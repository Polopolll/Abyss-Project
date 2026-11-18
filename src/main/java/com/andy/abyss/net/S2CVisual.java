package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Whether a player wears the void body (others need this to draw them). */
public record S2CVisual(int entity, boolean awakened) {
    public void write(FriendlyByteBuf b) { b.writeVarInt(entity); b.writeBoolean(awakened); }
    public static S2CVisual read(FriendlyByteBuf b) { return new S2CVisual(b.readVarInt(), b.readBoolean()); }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.ClientState.visual(entity, awakened)));
        ctx.get().setPacketHandled(true);
    }
}
