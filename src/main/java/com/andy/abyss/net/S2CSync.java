package com.andy.abyss.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The player's own Abyss state: data + live body roster (tag "bodies") + derived numbers. */
public record S2CSync(CompoundTag tag) {
    public void write(FriendlyByteBuf b) { b.writeNbt(tag); }
    public static S2CSync read(FriendlyByteBuf b) { return new S2CSync(b.readNbt()); }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.ClientState.sync(tag)));
        ctx.get().setPacketHandled(true);
    }
}
