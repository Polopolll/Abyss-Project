package com.andy.abyss.client;

import com.andy.abyss.net.S2CCp;
import com.andy.abyss.net.S2CWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** Everything the client knows about the Abyss. */
public final class ClientState {
    public static CompoundTag me = new CompoundTag();
    public static final Set<Integer> AWAKENED = new HashSet<>();
    public static final Map<Integer, S2CCp> CP = new HashMap<>();
    public static final Map<Integer, Long> CP_AT = new HashMap<>();
    public static List<S2CWorld.Dom> domains = List.of();
    public static List<S2CWorld.Clash> clashes = List.of();
    public static final Map<Integer, Integer> CORRODED = new HashMap<>();
    public static long ticks, worldAt;
    public static long swapFadeAt = -100;

    public static void sync(CompoundTag t) { me = t; }
    public static void visual(int id, boolean on) { if (on) AWAKENED.add(id); else AWAKENED.remove(id); }
    public static void cp(S2CCp c) { CP.put(c.entity(), c); CP_AT.put(c.entity(), ticks); }

    public static void world(S2CWorld w) {
        domains = w.domains();
        clashes = w.clashes();
        CORRODED.clear();
        int[] c = w.corroded();
        for (int i = 0; i + 1 < c.length; i += 2) CORRODED.put(c[i], c[i + 1]);
        worldAt = ticks;
    }

    public static boolean awakened() { return me.getBoolean("awakened"); }
    public static boolean awakened(int entityId) { return AWAKENED.contains(entityId); }
    public static float output() { return me.getFloat("output"); }
    public static float cap() { return Math.max(1, me.getFloat("cap")); }
    public static int selected() { return me.getInt("selected"); }
    public static int sporeMode() { return me.getInt("sporeMode"); }
    public static boolean negation() { return me.getBoolean("negation"); }
    public static int cd(int i) { int[] a = me.getIntArray("cd"); return i < a.length ? a[i] : 0; }
    public static ListTag bodies() { return me.getList("bodies", Tag.TAG_COMPOUND); }

    public static S2CWorld.Clash myClash() {
        for (S2CWorld.Clash c : clashes) if (c.you() >= 0) return c;
        return null;
    }

    public static void reset() {
        me = new CompoundTag(); AWAKENED.clear(); CP.clear(); CP_AT.clear(); domains = List.of(); clashes = List.of(); CORRODED.clear();
    }

    public static boolean inGame() { return Minecraft.getInstance().player != null && Minecraft.getInstance().level != null; }
    private ClientState() {}
}
