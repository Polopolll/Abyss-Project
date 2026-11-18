package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** What others need to draw a vessel: the void body, the open form and the forged weapon. */
public record S2CVisual(int entity, boolean awakened, int form, int weapon) {
    public S2CVisual(int entity, boolean awakened) { this(entity, awakened, -1, 0); }
    public void write(FriendlyByteBuf b) { b.writeVarInt(entity); b.writeBoolean(awakened); b.writeByte(form + 1); b.writeByte(weapon); }
    public static S2CVisual read(FriendlyByteBuf b) { return new S2CVisual(b.readVarInt(), b.readBoolean(), b.readByte() - 1, b.readByte()); }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.ClientState.visual(this)));
        ctx.get().setPacketHandled(true);
    }
}
