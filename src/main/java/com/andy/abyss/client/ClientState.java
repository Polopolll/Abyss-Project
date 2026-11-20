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
    public static int selectAnim = 99;
    /** Spore Lens: which payload right-click fires. */
    public static int payload;
    /** Domain chosen while charging: 0 Open, 1 Closed. */
    public static int domainVariant;
    /** Combo display (WuWa-style chain): last step and when. */
    public static int comboStep;
    public static long comboAt = -100;
    /** Heavy attack: ticks the attack button has been held in combat mode. */
    public static int heavyHeld;
    /** Pose per entity id: pose and the tick it started. */
    public static final Map<Integer, long[]> POSES = new HashMap<>();

    public static void sync(CompoundTag t) { me = t; }
    public static void visual(int id, boolean on) { if (on) AWAKENED.add(id); else AWAKENED.remove(id); }
    /** Open form and weapon of every vessel we can see. */
    public static final Map<Integer, int[]> FORMS = new HashMap<>();
    public static void visual(com.andy.abyss.net.S2CVisual v) {
        visual(v.entity(), v.awakened());
        if (v.awakened()) FORMS.put(v.entity(), new int[]{v.form(), v.weapon()}); else FORMS.remove(v.entity());
    }
    public static int formOf(int entity) { int[] f = FORMS.get(entity); return f == null ? -1 : f[0]; }
    public static int weaponOf(int entity) { int[] f = FORMS.get(entity); return f == null ? 0 : f[1]; }

    /** Realm: souls of the reflected world. */
    public static String soulWorld = "";
    public static String[] soulNames = new String[0];
    public static float[] soulXyz = new float[0];
    public static long soulsAt;
    public static void souls(com.andy.abyss.net.S2CSouls s) { soulWorld = s.world(); soulNames = s.names(); soulXyz = s.xyz(); soulsAt = ticks; }

    /** Spore form: selection painting. */
    public static boolean painting;
    /** Perfect dodge slow-motion until this tick. */
    public static long slowUntil;
    /** Time is stopped around a greatsword's Ruler's Realm until this tick (screen goes cold and still). */
    public static long freezeUntil;
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
    public static int form() { return me.contains("form") ? me.getInt("form") : -1; }
    public static com.andy.abyss.data.Ability openForm() { return com.andy.abyss.data.Ability.byIndex(form()); }
    public static int weapon() { return me.getInt("weapon"); }
    public static float gauge() { return me.getFloat("gauge"); }
    public static boolean inRealm() { return me.getBoolean("inRealm"); }
    public static boolean counterReady() { return me.getLong("counterLeft") > 0; }
    public static boolean infinite() { return me.getBoolean("inf"); }
    public static void pose(int entity, int pose) { pose(entity, pose, 11); }
    public static void pose(int entity, int pose, int duration) { POSES.put(entity, new long[]{pose, ticks, duration}); }
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
        me = new CompoundTag(); AWAKENED.clear(); CP.clear(); CP_AT.clear(); domains = List.of(); clashes = List.of(); CORRODED.clear(); POSES.clear(); FORMS.clear(); soulNames = new String[0]; soulXyz = new float[0];
    }

    public static boolean inGame() { return Minecraft.getInstance().player != null && Minecraft.getInstance().level != null; }
    private ClientState() {}
}
