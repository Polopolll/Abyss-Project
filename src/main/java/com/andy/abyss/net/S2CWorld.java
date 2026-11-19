package com.andy.abyss.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Live domains, Triangle clashes and corroded entities (id, stacks pairs) in the viewer's dimension. */
public record S2CWorld(List<Dom> domains, List<Clash> clashes, int[] corroded) {
    public record Dom(int id, int owner, double x, double y, double z, float radius, float max, boolean closed, int ticksLeft, boolean mine) {}
    public record Clash(int a, int b, float meter, int age, int you) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(domains.size());
        for (Dom d : domains) {
            buf.writeVarInt(d.id); buf.writeVarInt(d.owner); buf.writeDouble(d.x); buf.writeDouble(d.y); buf.writeDouble(d.z);
            buf.writeFloat(d.radius); buf.writeFloat(d.max); buf.writeBoolean(d.closed); buf.writeVarInt(d.ticksLeft); buf.writeBoolean(d.mine);
        }
        buf.writeVarInt(clashes.size());
        for (Clash c : clashes) { buf.writeVarInt(c.a); buf.writeVarInt(c.b); buf.writeFloat(c.meter); buf.writeVarInt(c.age); buf.writeVarInt(c.you + 1); }
        buf.writeVarIntArray(corroded);
    }
    public static S2CWorld read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 256);
        List<Dom> d = new ArrayList<>();
        for (int i = 0; i < n; i++) d.add(new Dom(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean()));
        int m = Math.min(buf.readVarInt(), 256);
        List<Clash> c = new ArrayList<>();
        for (int i = 0; i < m; i++) c.add(new Clash(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt() - 1));
        return new S2CWorld(d, c, buf.readVarIntArray(4096));
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.andy.abyss.client.ClientState.world(this)));
        ctx.get().setPacketHandled(true);
    }
}
