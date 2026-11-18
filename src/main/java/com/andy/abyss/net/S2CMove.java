package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** An Arsenal move started: everyone nearby plays its timeline (visuals, pose, weapon swing). */
public record S2CMove(int caster, int weapon, int move, float yaw, float pitch, double x, double y, double z, int target, double groundY, float charge) {
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(caster); b.writeByte(weapon); b.writeByte(move); b.writeFloat(yaw); b.writeFloat(pitch);
        b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeVarInt(target + 1); b.writeDouble(groundY); b.writeFloat(charge);
    }
    public static S2CMove read(FriendlyByteBuf b) {
        return new S2CMove(b.readVarInt(), b.readByte(), b.readByte(), b.readFloat(), b.readFloat(), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt() - 1, b.readDouble(), b.readFloat());
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.render.MoveFx.start(this)));
        ctx.get().setPacketHandled(true);
    }
}
