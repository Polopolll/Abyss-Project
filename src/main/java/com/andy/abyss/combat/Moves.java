package com.andy.abyss.combat;

import com.andy.abyss.combat.Move.Fx;
import com.andy.abyss.combat.Move.Hit;
import com.andy.abyss.combat.Move.Kind;
import com.andy.abyss.combat.Move.Knock;
import com.andy.abyss.combat.Move.Shape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.andy.abyss.combat.Move.Kind.*;

/**
 * The two weapons of the Arsenal.
 * <ul>
 *   <li>Abyssal Edge (sword): Cartethyia. Nimble, the fastest strokes, thin crisp cuts; the blade in your hand drives
 *   the light of every cut.</li>
 *   <li>Hands of the Abyss (gauntlets): Asura. A brawler, all fist: jabs, crosses, hooks, a rush, uppercuts, a
 *   grab-and-slam, craters on the heavy blows; the liberation brings the extra arms.</li>
 * </ul>
 * Every move set: a basic chain on click, a charged heavy on hold, a plunge in the air, a counter after a perfect
 * dodge, a skill on mouse 4 and a liberation on mouse 5 once the resonance gauge is full.
 */
public final class Moves {
    public record Set(List<Move> basic, Move heavy, Move plunge, Move counter, Move skill, Move liberation) {}

    public static final Map<Weapon, Set> ALL = new EnumMap<>(Weapon.class);
    /** Poses (client animation ids). */
    public static final int P_SLASH_RL = 1, P_SLASH_LR = 2, P_OVERHEAD = 3, P_RISING = 4, P_THRUST = 5, P_SPIN = 6, P_SLAM = 7,
            P_PUNCH_R = 8, P_PUNCH_L = 9, P_RAISE = 10, P_BLOCK = 11, P_THROW = 12, P_PLUNGE = 13, P_FLURRY = 14, P_BASH = 15, P_CAST = 16, P_DRAW = 17;

    // ---- tiny DSL
    private static Hit arc(int t, float r, float half, float yaw, float dmg, Knock k, float kb) { return new Hit(t, Shape.ARC, r, half, yaw, dmg, k, kb); }
    private static Hit line(int t, float len, float w, float dmg, Knock k, float kb) { return new Hit(t, Shape.LINE, len, w, 0, dmg, k, kb); }
    private static Hit circle(int t, float r, float fwd, float dmg, Knock k, float kb) { return new Hit(t, Shape.CIRCLE, r, fwd, 0, dmg, k, kb); }
    private static Hit onTarget(int t, float r, float dmg, Knock k, float kb) { return new Hit(t, Shape.CIRCLE, r, 0, 1, dmg, k, kb); }
    private static Hit ground(int t, float r, float dmg, Knock k, float kb) { return new Hit(t, Shape.CIRCLE, r, 0, 2, dmg, k, kb); }
    private static Hit target(int t, float reach, float dmg, Knock k, float kb) { return new Hit(t, Shape.TARGET, reach, 0, 0, dmg, k, kb); }
    private static Fx fx(int t, Kind k, float... p) { return Fx.of(t, k, p); }
    private static Move mv(String id, String name, int pose, int dur, int rec, float cost, float gauge, List<Hit> hits, List<Fx> fx) {
        List<Fx> sorted = new ArrayList<>(fx);
        sorted.sort(java.util.Comparator.comparingInt(Fx::tick)); // the client fires them in tick order
        return new Move(id, name, pose, dur, rec, cost, gauge, hits, List.copyOf(sorted));
    }
    @SafeVarargs private static <T> List<T> L(T... xs) { return List.of(xs); }
    /** A punch rush: n target hits spread evenly from tick t0 to t1 (the last one pushes if kb > 0). */
    private static List<Hit> rush(int t0, int t1, int n, float dmg, float reach, float kb) {
        List<Hit> h = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean last = i == n - 1 && kb > 0;
            h.add(target(t0 + Math.round((t1 - t0) * i / (float) Math.max(1, n - 1)), reach, last ? dmg * 2 : dmg, last ? Knock.PUSH : Knock.NONE, last ? 0.5f * kb : 0));
        }
        return h;
    }
    private static List<Hit> withFinal(List<Hit> h, Hit last) { List<Hit> o = new ArrayList<>(h); o.add(last); return o; }

    static {
        // ================================================================ SWORD — Cartethyia: nimble, quick, clean cuts
        // The fastest weapon: 7-9 tick strokes, thin crisp single cuts, tiny camera, no craters until the liberation.
        List<Move> sw = new ArrayList<>();
        sw.add(mv("sword_1", "Flicker Cut", P_SLASH_RL, 7, 2, 0, 4,
                L(arc(2, 3.6f, 70, 0, 0.75f, Knock.PUSH, 0.12f)),
                L(fx(1, REND, 3.0f, 130, 0, 6, 0.32f, 10), fx(2, CAMERA, 0.08f, 0.006f))));
        sw.add(mv("sword_2", "Return Flicker", P_SLASH_LR, 7, 2, 0, 4,
                L(arc(2, 3.6f, 70, 0, 0.75f, Knock.PUSH, 0.12f)),
                L(fx(1, REND, 3.0f, -130, 0, -24, 0.32f, 10), fx(2, CAMERA, 0.08f, 0.006f))));
        sw.add(mv("sword_3", "Petal Pair", P_FLURRY, 9, 2, 0, 5,
                L(arc(2, 3.4f, 60, 0, 0.55f, Knock.NONE, 0), arc(5, 3.4f, 60, 0, 0.6f, Knock.PUSH, 0.15f)),
                L(fx(1, REND, 2.8f, 110, 0, 40, 0.26f, 9), fx(4, REND, 2.8f, -110, 0, -40, 0.26f, 9))));
        sw.add(mv("sword_4", "Swallow Rise", P_RISING, 9, 3, 0, 5,
                L(arc(3, 3.6f, 45, 0, 0.9f, Knock.LAUNCH, 0.55f)),
                L(fx(2, REND, 3.2f, 150, 0, 82, 0.34f, 11), fx(3, CAMERA, 0.12f, 0.01f))));
        sw.add(mv("sword_5", "Sever Step", P_DRAW, 12, 3, 0, 7,
                L(line(5, 7, 2.2f, 1.3f, Knock.PUSH, 0.4f)),
                L(fx(1, LUNGE, 4, 1.6f, 3), fx(2, GHOSTS, 2), fx(4, REND_LINE, 7, 0.4f, 2, 0, 0.35f, 12), fx(5, CAMERA, 0.2f, 0.015f))));
        Move swHeavy = mv("sword_heavy", "Blade Choir", P_DRAW, 24, 5, 0, 10,
                L(circle(6, 3.8f, 4.5f, 0.5f, Knock.NONE, 0), circle(10, 3.8f, 4.5f, 0.5f, Knock.NONE, 0), circle(18, 4.2f, 4.5f, 1.6f, Knock.PUSH, 0.4f)),
                L(fx(2, GHOSTS, 3), fx(4, REND_STORM, 8, 3.5f, 4.5f), fx(17, REND, 4.2f, 200, 0, 4, 0.5f, 14), fx(18, CAMERA, 0.4f, 0.03f)));
        Move swPlunge = mv("sword_plunge", "Falling Petal", P_PLUNGE, 12, 3, 0, 6,
                L(ground(4, 3.0f, 1.5f, Knock.DOWN, 0.4f)),
                L(fx(1, REND_LINE, 5, 0, 90, 0, 0.35f, 12), fx(4, SHOCK, 2.5f, 0.4f), fx(4, CAMERA, 0.3f, 0.02f)));
        Move swCounter = mv("sword_counter", "Null Step", P_DRAW, 14, 3, 0, 10,
                L(target(3, 7, 1.2f, Knock.NONE, 0), target(8, 7, 1.8f, Knock.PUSH, 0.5f)),
                L(fx(1, GHOSTS, 4), fx(2, REND, 3.2f, 150, 0, 30, 0.3f, 10), fx(7, REND, 3.6f, -170, 0, -30, 0.45f, 12), fx(8, CAMERA, 0.4f, 0.03f)));
        Move swSkill = mv("sword_skill", "Rend Weave", P_SLASH_RL, 20, 5, 25, 8,
                L(circle(4, 5.5f, 6, 0.6f, Knock.PULL, 0.3f), circle(16, 5.5f, 6, 1.6f, Knock.PUSH, 0.4f)),
                L(fx(2, REND_STORM, 10, 5, 6), fx(15, REND, 5.5f, 260, 0, 0, 0.5f, 14), fx(16, CAMERA, 0.5f, 0.03f)));
        Move swLib = mv("sword_lib", "Abyss Severance", P_DRAW, 40, 8, 40, 0,
                L(line(10, 30, 8, 2.5f, Knock.NONE, 0), line(30, 30, 9, 4.5f, Knock.LAUNCH, 0.9f)),
                L(fx(0, CAMERA, 0.2f, -0.04f), fx(4, GHOSTS, 6), fx(8, REND_STORM, 16, 8, 12), fx(29, REND_LINE, 32, -2, 0, 0, 1.6f, 30),
                        fx(29, REND, 11, 220, 0, 2, 0.8f, 30), fx(30, TRENCH, 26, 1.0f, 2, 4, 6, 0.6f), fx(30, CAMERA, 1.2f, 0.08f)));
        ALL.put(Weapon.SWORD, new Set(sw, swHeavy, swPlunge, swCounter, swSkill, swLib));

        // ================================================================ GAUNTLETS — Asura: a brawler, all fist
        // Every FIST is one real punch: the fist is fully out on the exact tick its flash appears. Paths: 0 straight,
        // 1 hook (swings in from the side), 2 uppercut (comes up from below), 3 hammer (comes down from above).
        List<Move> ga = new ArrayList<>();
        ga.add(mv("ga_1", "Jab", P_PUNCH_L, 9, 3, 0, 3,
                L(target(4, 4.5f, 0.7f, Knock.PUSH, 0.15f)),
                L(fx(4, FIST, 1, 2.6f, 1, -1, 0), fx(4, CAMERA, 0.25f, 0.008f))));
        ga.add(mv("ga_2", "Cross", P_PUNCH_R, 9, 3, 0, 3,
                L(target(4, 4.5f, 0.9f, Knock.PUSH, 0.25f)),
                L(fx(4, FIST, 1.3f, 2.6f, 1, 1, 0), fx(4, CAMERA, 0.35f, 0.01f))));
        ga.add(mv("ga_3", "Hook", P_PUNCH_L, 13, 5, 0, 4,
                L(target(7, 4.5f, 1.1f, Knock.SPIN, 0.45f)),
                L(fx(7, FIST, 1.6f, 2.6f, 1, -1, 1), fx(7, CAMERA, 0.5f, 0.012f))));
        ga.add(mv("ga_4", "Rush", P_FLURRY, 16, 4, 0, 7, rush(2, 13, 8, 0.38f, 4.5f, 1.0f),
                L(fx(1, LUNGE, 2.5f, 1.8f, 3), fx(2, BARRAGE, 8, 0.45f, 2.6f, 12, 1), fx(14, FIST, 2, 2.6f, 1, 1, 0), fx(14, CAMERA, 0.6f, 0.03f))));
        ga.add(mv("ga_5", "Uppercut", P_RISING, 14, 6, 0, 6,
                L(target(5, 4.5f, 1.6f, Knock.LAUNCH, 0.9f)),
                L(fx(5, FIST, 2, 2.4f, 1, 1, 2), fx(5, CAMERA, 0.7f, 0.03f))));
        Move gaHeavy = mv("ga_heavy", "Wrath Straight", P_PUNCH_R, 20, 6, 0, 12,
                L(line(10, 9, 2.6f, 2.6f, Knock.PUSH, 1.4f)),
                L(fx(2, LUNGE, 4, 1.6f, 5), fx(10, FIST, 3, 2.6f, 1, 1, 0), fx(10, TRENCH, 9, 1.4f, 2, 1.5f, 3, 0.5f), fx(10, CAMERA, 1.2f, 0.07f)));
        Move gaPlunge = mv("ga_plunge", "Meteor Fist", P_SLAM, 16, 5, 0, 8,
                L(ground(4, 4.5f, 2.4f, Knock.DOWN, 1.0f)),
                L(fx(4, FIST, 2.5f, 1.6f, 0, 1, 3), fx(4, CRATER, 4.5f, 2.5f, 0, 2, 6, 0.8f), fx(4, CAMERA, 1.3f, 0.06f)));
        Move gaCounter = mv("ga_counter", "Thousand Fists", P_FLURRY, 22, 5, 0, 12, withFinal(rush(2, 16, 12, 0.35f, 5, 0), target(18, 5, 2.4f, Knock.PUSH, 1.0f)),
                L(fx(0, LUNGE, 4, 1.8f, 3), fx(0, PHANTOM, 4, 18), fx(2, BARRAGE, 12, 0.6f, 2.6f, 14, 1), fx(18, FIST, 2.5f, 2.6f, 1, 1, 0),
                        fx(18, CRATER, 2.5f, 1.5f, 0, 1, 4, 0.5f), fx(18, CAMERA, 0.9f, 0.05f)));
        Move gaSkill = mv("ga_skill", "Grab & Slam", P_SLAM, 22, 6, 20, 10,
                L(target(4, 6, 0.4f, Knock.PULL, 1.4f), target(9, 6, 0.4f, Knock.LAUNCH, 0.45f), target(15, 6, 3.4f, Knock.DOWN, 1.4f)),
                L(fx(4, FIST, 1, 2.4f, 1, 1, 1), fx(15, FIST, 3, 2.4f, 1, -1, 3), fx(15, CRATER, 3.5f, 2.5f, 0, 1, 6, 0.8f), fx(15, CAMERA, 1.2f, 0.06f)));
        Move gaLib = mv("ga_lib", "Asura's Mantra", P_FLURRY, 56, 8, 40, 0, withFinal(rush(4, 36, 17, 0.35f, 6, 0), onTarget(46, 6, 5.0f, Knock.LAUNCH, 1.4f)),
                L(fx(0, CAMERA, 0.4f, -0.04f), fx(0, PHANTOM, 4, 40), fx(2, LUNGE, 5, 1.8f, 4), fx(4, BARRAGE, 30, 0.9f, 2.8f, 34, 1),
                        fx(46, FIST, 3, 2.6f, 1, 1, 0), fx(46, CRATER, 7, 4, 0, 1, 12, 1.0f), fx(46, CAMERA, 2.0f, 0.1f)));
        ALL.put(Weapon.GAUNTLETS, new Set(ga, gaHeavy, gaPlunge, gaCounter, gaSkill, gaLib));
    }

    public static Set of(Weapon w) { return ALL.get(w); }

    /** All moves of a weapon, indexed: 0.. basic, then heavy, plunge, counter, skill, liberation. */
    public static Move byIndex(Weapon w, int i) {
        Set s = ALL.get(w);
        int n = s.basic().size();
        if (i < n) return s.basic().get(Math.max(0, i));
        return switch (i - n) { case 0 -> s.heavy(); case 1 -> s.plunge(); case 2 -> s.counter(); case 3 -> s.skill(); default -> s.liberation(); };
    }

    public static int indexOf(Weapon w, Move m) {
        Set s = ALL.get(w);
        int i = s.basic().indexOf(m);
        if (i >= 0) return i;
        int n = s.basic().size();
        if (m == s.heavy()) return n;
        if (m == s.plunge()) return n + 1;
        if (m == s.counter()) return n + 2;
        if (m == s.skill()) return n + 3;
        return n + 4;
    }
    private Moves() {}
}
