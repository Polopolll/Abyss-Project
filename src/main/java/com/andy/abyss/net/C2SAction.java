package com.andy.abyss.net;

import com.andy.abyss.server.Actions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SAction(int action, int arg) {
    public static final int CAST_START = 0, CAST_RELEASE = 1, SELECT = 2, SPREAD_START = 3, SPREAD_STOP = 4, SPORE_MODE = 5, SHOOT_SPORE = 6,
            SWAP_NEXT = 7, SWAP_PREV = 8, SWAP_MAIN = 9, SWAP_TO = 10, ORDER = 11, ORDER_ALL = 12, RECALL_ONE = 13, CLASH_PUSH = 14,
            CP_QUERY = 15, LENS = 16;

    public void write(FriendlyByteBuf b) { b.writeVarInt(action); b.writeVarInt(arg); }
    public static C2SAction read(FriendlyByteBuf b) { return new C2SAction(b.readVarInt(), b.readVarInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> { if (p != null) Actions.handle(p, action, arg); });
        ctx.get().setPacketHandled(true);
    }
}
