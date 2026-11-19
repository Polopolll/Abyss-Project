package com.andy.abyss.net;

import com.andy.abyss.server.Forms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A mouse button inside an open form. button: 0 LMB, 1 RMB, 2 mouse 4, 3 mouse 5, 4 scroll (arg = ±1).
 * phase: 0 press, 1 release (arg = ticks held), 2 hold-threshold reached. flags: 1 shift, 2 sprint, 4 airborne, 8 falling.
 * For the dodge, arg carries the movement input (bit 0 forward, 1 back, 2 left, 3 right).
 */
public record C2SInput(int button, int phase, int flags, int arg) {
    public static final int LMB = 0, RMB = 1, M4 = 2, M5 = 3, SCROLL = 4;
    public static final int PRESS = 0, RELEASE = 1, HOLD = 2;
    public static final int SHIFT = 1, SPRINT = 2, AIR = 4, FALLING = 8;

    public void write(FriendlyByteBuf b) { b.writeByte(button); b.writeByte(phase); b.writeByte(flags); b.writeVarInt(arg); }
    public static C2SInput read(FriendlyByteBuf b) { return new C2SInput(b.readByte(), b.readByte(), b.readByte(), b.readVarInt()); }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> { if (p != null) Forms.input(p, button, phase, flags, arg); });
        ctx.get().setPacketHandled(true);
    }
}
