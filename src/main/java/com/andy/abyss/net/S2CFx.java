package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One visual effect: type, position, three numbers (size, power, direction...), and up to two entity ids. */
public record S2CFx(int type, double x, double y, double z, float a, float b, float c, int e1, int e2) {
    public static final int SPREAD = 0, NUKE = 1, DEVOUR = 2, TRANSPORT = 3, DISINTEGRATE = 4, RIFT_OPEN = 5, SWAP = 6, NEGATE = 7,
            CLONE_SPAWN = 8, CLONE_DEATH = 9, TRIANGLE_END = 10, STEP = 11, CHARGE = 12, VOLLEY = 13, AWAKEN = 14, IMPLODE = 15, DOMAIN_CAST = 16,
            DOMAIN_PULSE = 17, COMBO = 18, BEAM = 19, PLUNGE = 20, REFORM = 21, LUNGE = 22;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(type); buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(a); buf.writeFloat(b); buf.writeFloat(c); buf.writeVarInt(e1 + 1); buf.writeVarInt(e2 + 1);
    }
    public static S2CFx read(FriendlyByteBuf buf) {
        return new S2CFx(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1, buf.readVarInt() - 1);
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.render.FxManager.receive(this)));
        ctx.get().setPacketHandled(true);
    }
}
