package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Angel Eyes: what the server knows about an entity. */
public record S2CCp(int entity, int cp, float hp, float maxHp, float armor, float damage, int spores, String traits) {
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(entity); b.writeVarInt(cp); b.writeFloat(hp); b.writeFloat(maxHp); b.writeFloat(armor); b.writeFloat(damage); b.writeVarInt(spores); b.writeUtf(traits, 512);
    }
    public static S2CCp read(FriendlyByteBuf b) {
        return new S2CCp(b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readUtf(512));
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.ClientState.cp(this)));
        ctx.get().setPacketHandled(true);
    }
}
