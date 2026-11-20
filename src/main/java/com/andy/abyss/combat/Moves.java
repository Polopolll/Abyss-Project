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
 * Weapon identities (Andy: "they all feel the same"):
 * <ul>
 *   <li>Abyssal Edge (sword) — Cartethyia: nimble, the fastest strokes (7-9 ticks), thin crisp single cuts, tiny camera.</li>
 *   <li>Event Cleaver (greatsword) — Augusta: slow enormous cleaves (18-22 tick wind-ups), trenches split the ground;
 *   heavies build resonance fast; liberation Ruler's Realm stops time, then delivers the verdict.</li>
 *   <li>Singularity Maul (hammer) — speed and destruction: 11-16 tick strikes, every head-strike craters the ground.</li>
 *   <li>Horizon Reaper (scythe) — reaping: wide arcs that drag enemies in; every hit harvests a little life back.</li>
 *   <li>Rift Lance (spear) — reach: fast narrow thrusts on the crosshair, a drilling beam, a piercing liberation.</li>
 *   <li>Aegis & Shard (bulwark) — sword and board: bashes, guard-counter, slams that crater.</li>
 *   <li>Hands of the Abyss (gauntlets) — Asura: a brawler, all fist. Jabs, a punch rush, uppercuts, grab-and-slam,
 *   craters on every heavy blow; liberation: extra arms and the Mantra rush.</li>
 * </ul>
 * Black holes are kept for two liberations only (the Maul's Collapse and the Reaper's Grim Eclipse).
 *
 * Every weapon's move set (Wuthering Waves style): a basic chain on click, a charged heavy on hold, a plunge in the
 * air, a counter after a perfect dodge, a skill on mouse 4 and a liberation on mouse 5 once the resonance gauge is full.
 * Nothing here moves the caster: every reach is rifts, rends and constructs.
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
        return new Move(id, name, pose, dur, rec, cost, gauge, hits, fx);
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
                L(fx(2, GHOSTS, 2), fx(4, REND_LINE, 7, 0.4f, 2, 0, 0.35f, 12), fx(5, CAMERA, 0.2f, 0.015f))));
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

        // ================================================================ GREATSWORD — Augusta: slow, enormous, the ground splits
        // Long wind-ups (18-22 ticks), one massive cleave per stroke, trenches along overhead cuts, heavy camera.
        // Heavies build resonance fast (Augusta's stacks); the liberation is Ruler's Realm: time stops, then the verdict.
        List<Move> gs = new ArrayList<>();
        gs.add(mv("gs_1", "Gladiator's Sweep", P_SLASH_RL, 18, 6, 0, 6,
                L(arc(10, 5.0f, 95, 0, 1.6f, Knock.PUSH, 0.7f)),
                L(fx(8, REND, 4.6f, 175, 0, 6, 0.95f, 20), fx(10, CAMERA, 0.45f, 0.025f))));
        gs.add(mv("gs_2", "Verdict", P_OVERHEAD, 20, 6, 0, 7,
                L(line(11, 8, 2.4f, 2.2f, Knock.DOWN, 0.6f)),
                L(fx(9, REND_LINE, 5, 0.5f, 90, 0, 0.9f, 20), fx(11, TRENCH, 8, 0.9f, 2, 1.5f, 4, 0.5f), fx(11, CAMERA, 0.7f, 0.04f))));
        gs.add(mv("gs_3", "Colosseum Spin", P_SPIN, 20, 5, 0, 7,
                L(arc(7, 4.8f, 180, 0, 1.3f, Knock.SPIN, 0.5f), arc(13, 4.8f, 180, 0, 1.0f, Knock.PUSH, 0.6f)),
                L(fx(6, REND, 4.6f, 360, 0, 4, 0.75f, 18), fx(13, SHOCK, 4.6f, 0.4f), fx(13, CAMERA, 0.4f, 0.02f))));
        gs.add(mv("gs_4", "Sovereign Rise", P_RISING, 22, 6, 0, 9,
                L(circle(11, 3.4f, 3.2f, 2.2f, Knock.LAUNCH, 1.0f)),
                L(fx(9, REND, 4.2f, 160, 0, 80, 0.9f, 20), fx(11, CRATER, 2.5f, 1.5f, 3.2f, 0, 5, 0.7f), fx(11, CAMERA, 0.7f, 0.04f))));
        Move gsHeavy = mv("gs_heavy", "Majesty Cleave", P_OVERHEAD, 34, 8, 0, 16,
                L(line(20, 14, 3, 3.2f, Knock.DOWN, 0.8f)),
                L(fx(0, CAMERA, 0.1f, -0.04f), fx(6, GHOSTS, 2), fx(18, REND_LINE, 7, 0.5f, 90, 0, 1.4f, 26), fx(20, TRENCH, 14, 1.8f, 3, 1.5f, 6, 0.7f), fx(20, CAMERA, 1.1f, 0.07f)));
        Move gsPlunge = mv("gs_plunge", "Meteor Cleave", P_PLUNGE, 20, 6, 0, 8,
                L(ground(5, 5.0f, 2.6f, Knock.DOWN, 0.9f)),
                L(fx(1, REND_LINE, 7, 0, 90, 0, 1.0f, 22), fx(5, CRATER, 4, 2.5f, 0, 2, 6, 0.8f), fx(5, CAMERA, 1.0f, 0.06f)));
        Move gsCounter = mv("gs_counter", "Riposte", P_SLASH_LR, 18, 5, 0, 10,
                L(arc(5, 5.2f, 110, 0, 3.0f, Knock.PUSH, 1.0f)),
                L(fx(1, GHOSTS, 4), fx(4, REND, 5.2f, -220, 0, -10, 1.2f, 24), fx(5, CAMERA, 0.8f, 0.05f)));
        Move gsSkill = mv("gs_skill", "Fault Line", P_OVERHEAD, 30, 8, 25, 10,
                L(line(10, 16, 3.2f, 2.2f, Knock.LAUNCH, 0.8f)),
                L(fx(8, REND_LINE, 4, 0.5f, 90, 0, 0.9f, 18), fx(10, TRENCH, 16, 1.6f, 3, 1.5f, 6, 0.8f), fx(11, SPIKES, 2.2f, 14, 2.6f, 8, 0), fx(10, CAMERA, 0.8f, 0.05f)));
        Move gsLib = mv("gs_lib", "Ruler's Realm", P_RAISE, 64, 8, 40, 0,
                L(arc(16, 6, 120, 0, 1.0f, Knock.NONE, 0), arc(24, 6, 120, 0, 1.0f, Knock.NONE, 0), arc(32, 6, 120, 0, 1.0f, Knock.NONE, 0),
                        line(44, 30, 6, 6.0f, Knock.DOWN, 1.0f)),
                L(fx(0, CAMERA, 0.3f, -0.05f), fx(4, FREEZE, 18, 50), fx(10, GHOSTS, 4), fx(14, GIANT, 9, 7, 1, 0),
                        fx(15, REND, 5.2f, 170, 0, 20, 0.7f, 16), fx(23, REND, 5.2f, -170, 0, -20, 0.7f, 16), fx(31, REND, 5.6f, 200, 0, 70, 0.8f, 16),
                        fx(42, REND_LINE, 9, 0.5f, 90, 0, 2.2f, 30), fx(44, TRENCH, 30, 2.6f, 4, 1, 12, 1.0f), fx(44, CAMERA, 1.8f, 0.1f)));
        ALL.put(Weapon.GREATSWORD, new Set(gs, gsHeavy, gsPlunge, gsCounter, gsSkill, gsLib));

        // ================================================================ HAMMER — speed and destruction
        // Quicker than the greatsword (11-16 ticks) but every head-strike craters the ground; spins and slams.
        List<Move> hm = new ArrayList<>();
        hm.add(mv("hm_1", "Crush", P_OVERHEAD, 12, 4, 0, 6,
                L(circle(6, 2.8f, 2.4f, 1.4f, Knock.DOWN, 0.5f)),
                L(fx(6, CRATER, 1.6f, 1, 2.4f, 0, 3, 0.45f), fx(6, CAMERA, 0.45f, 0.025f))));
        hm.add(mv("hm_2", "Backhand", P_SLASH_LR, 11, 4, 0, 6,
                L(arc(5, 3.8f, 80, 0, 1.2f, Knock.PUSH, 0.9f)),
                L(fx(4, REND, 3.4f, -140, 0, -10, 0.55f, 12), fx(5, CAMERA, 0.3f, 0.02f))));
        hm.add(mv("hm_3", "Twin Quake", P_SLAM, 16, 5, 0, 7,
                L(circle(5, 3.4f, 1.8f, 1.0f, Knock.PUSH, 0.4f), circle(11, 4.4f, 2.2f, 1.4f, Knock.LAUNCH, 0.6f)),
                L(fx(5, CRATER, 1.6f, 1, 1.8f, 0, 3, 0.4f), fx(11, CRATER, 2.4f, 1.5f, 2.4f, 0, 4, 0.6f), fx(11, CAMERA, 0.6f, 0.035f))));
        hm.add(mv("hm_4", "Orbit Smash", P_SPIN, 16, 5, 0, 8,
                L(arc(6, 4.2f, 180, 0, 1.0f, Knock.SPIN, 0.5f), arc(12, 4.4f, 180, 0, 1.3f, Knock.PUSH, 0.8f)),
                L(fx(5, REND, 4.0f, 360, 0, 4, 0.6f, 14), fx(12, SHOCK, 4.4f, 0.6f), fx(12, CAMERA, 0.5f, 0.03f))));
        hm.add(mv("hm_5", "Ground Zero", P_SLAM, 20, 6, 0, 10,
                L(circle(10, 5.2f, 1, 2.4f, Knock.LAUNCH, 1.0f)),
                L(fx(10, CRATER, 3.5f, 2, 1.2f, 0, 6, 0.8f), fx(10, SPIKES, 4.5f, 12, 2.4f, 1), fx(10, CAMERA, 0.9f, 0.05f))));
        Move hmHeavy = mv("hm_heavy", "Collapse Strike", P_OVERHEAD, 30, 8, 0, 12,
                L(circle(18, 4.5f, 3, 2.8f, Knock.DOWN, 1.0f)),
                L(fx(0, CAMERA, 0.1f, -0.03f), fx(18, CRATER, 4, 2.5f, 3, 0, 8, 0.9f), fx(18, TRENCH, 10, 1.2f, 1, 3, 5, 0.6f), fx(18, CAMERA, 1.1f, 0.07f)));
        Move hmPlunge = mv("hm_plunge", "Starfall", P_PLUNGE, 18, 5, 0, 8,
                L(ground(5, 5.0f, 2.8f, Knock.DOWN, 1.0f)),
                L(fx(5, CRATER, 4.5f, 3, 0, 2, 8, 1.0f), fx(5, CAMERA, 1.1f, 0.06f)));
        Move hmCounter = mv("hm_counter", "Rebound", P_RISING, 18, 5, 0, 10,
                L(target(4, 5.5f, 3.0f, Knock.LAUNCH, 1.2f)),
                L(fx(1, GHOSTS, 3), fx(4, CRATER, 2, 1.5f, 0, 1, 5, 0.6f), fx(4, CAMERA, 0.8f, 0.05f)));
        Move hmSkill = mv("hm_skill", "Gravity Well", P_SLAM, 58, 6, 30, 6,
                L(circle(8, 8, 6, 0.5f, Knock.PULL, 0.5f), circle(18, 8, 6, 0.5f, Knock.PULL, 0.5f), circle(28, 8, 6, 0.5f, Knock.PULL, 0.5f), circle(38, 8, 6, 0.5f, Knock.PULL, 0.5f), circle(52, 8, 6, 1.8f, Knock.PUSH, 1.0f)),
                L(fx(6, VORTEX, 8, 6, 46), fx(52, CRATER, 4, 2, 6, 0, 6, 0.8f), fx(52, SHOCK, 8.5f, 1.0f), fx(52, CAMERA, 0.8f, 0.04f)));
        Move hmLib = mv("hm_lib", "Collapse", P_RAISE, 56, 8, 40, 0,
                L(onTarget(24, 8, 5.5f, Knock.DOWN, 1.0f), onTarget(46, 10, 3.0f, Knock.PUSH, 1.6f)),
                L(fx(2, GIANT, 8, 6, 1, 1), fx(24, CRATER, 8, 5, 6, 1, 12, 1.0f), fx(24, STAR, 3.2f, 6, 1, 24), fx(24, CAMERA, 1.6f, 0.08f), fx(46, SHOCK, 12, 1.2f), fx(46, CAMERA, 1.4f, 0.09f)));
        ALL.put(Weapon.HAMMER, new Set(hm, hmHeavy, hmPlunge, hmCounter, hmSkill, hmLib));

        // ================================================================ SCYTHE — reaps that drag inward
        List<Move> sc = new ArrayList<>();
        sc.add(mv("sc_1", "Reap", P_SLASH_RL, 12, 4, 0, 5,
                L(arc(4, 4.4f, 80, 0, 1.1f, Knock.PULL, 0.35f)),
                L(fx(2, REND, 4.2f, 170, 0, 14, 0.5f, 18))));
        sc.add(mv("sc_2", "Wane Reap", P_SLASH_LR, 12, 4, 0, 5,
                L(arc(4, 4.4f, 80, 0, 1.1f, Knock.PULL, 0.35f)),
                L(fx(2, REND, 4.2f, -170, 0, -14, 0.5f, 18))));
        sc.add(mv("sc_3", "Hook", P_THROW, 14, 4, 0, 6,
                L(target(6, 7, 1.0f, Knock.PULL, 1.1f)),
                L(fx(3, THROW, 1.6f, 7, 1, 0), fx(6, REND, 2.2f, 90, 0, 10, 0.4f, 12))));
        sc.add(mv("sc_4", "Death Whirl", P_SPIN, 20, 5, 0, 8,
                L(arc(4, 4.2f, 180, 0, 0.8f, Knock.SPIN, 0.3f), arc(9, 4.2f, 180, 0, 0.8f, Knock.SPIN, 0.3f), arc(14, 4.4f, 180, 0, 0.9f, Knock.PULL, 0.4f)),
                L(fx(3, REND, 4.0f, 360, 0, 6, 0.45f, 14), fx(8, REND, 4.0f, -360, 0, -6, 0.45f, 14), fx(13, REND, 4.4f, 360, 0, 12, 0.6f, 16), fx(14, VORTEX, 4.5f, 0, 12))));
        sc.add(mv("sc_5", "Eclipse Reap", P_DRAW, 24, 6, 0, 10,
                L(arc(8, 6.2f, 120, 0, 2.2f, Knock.PULL, 0.6f)),
                L(fx(3, GHOSTS, 3), fx(7, REND, 6.0f, 240, 0, 8, 1.0f, 26), fx(8, REND, 5.2f, 200, 15, 16, 0.3f, 18), fx(8, CAMERA, 0.7f, 0.04f))));
        Move scHeavy = mv("sc_heavy", "Harvest Moon", P_THROW, 30, 6, 0, 10,
                L(line(7, 14, 2.2f, 1.2f, Knock.PULL, 0.4f), line(20, 14, 2.2f, 1.2f, Knock.PULL, 0.6f)),
                L(fx(4, THROW, 1.3f, 14, 1, 0)));
        Move scPlunge = mv("sc_plunge", "Grave Drop", P_PLUNGE, 18, 5, 0, 7,
                L(ground(5, 3.8f, 2.0f, Knock.DOWN, 0.6f)),
                L(fx(1, REND_LINE, 6, 0, 90, 0, 0.6f, 18), fx(5, REND, 3.8f, 360, 0, 0, 0.5f, 16), fx(5, SHOCK, 4, 0.6f), fx(5, CAMERA, 0.6f, 0.03f)));
        Move scCounter = mv("sc_counter", "Mirror Reap", P_SPIN, 18, 5, 0, 10,
                L(arc(5, 5.2f, 180, 0, 2.8f, Knock.PULL, 0.6f)),
                L(fx(1, GHOSTS, 5), fx(4, REND, 5.2f, 360, 0, 4, 1.0f, 22), fx(5, CAMERA, 0.7f, 0.05f)));
        Move scSkill = mv("sc_skill", "Soul Harvest", P_RAISE, 36, 8, 30, 6,
                L(circle(5, 7.2f, 0, 0.6f, Knock.PULL, 0.5f), circle(11, 7.2f, 0, 0.6f, Knock.PULL, 0.5f), circle(17, 7.2f, 0, 0.6f, Knock.PULL, 0.5f), circle(23, 7.2f, 0, 0.6f, Knock.PULL, 0.5f), circle(29, 7.2f, 0, 1.6f, Knock.PUSH, 0.8f)),
                L(fx(3, VORTEX, 7.4f, 0, 30), fx(3, REND_STORM, 8, 6.5f, 0), fx(29, REND, 7.2f, 360, 0, 2, 1.0f, 18), fx(29, CAMERA, 0.7f, 0.04f)));
        Move scLib = mv("sc_lib", "Grim Eclipse", P_RAISE, 52, 8, 40, 0,
                L(circle(12, 10.5f, 0, 2.0f, Knock.PULL, 0.8f), circle(36, 11, 0, 4.0f, Knock.LAUNCH, 1.2f)),
                L(fx(2, STAR, 2.4f, 0, 0, 40), fx(6, REND_STORM, 18, 10, 0), fx(12, VORTEX, 11, 0, 26), fx(36, REND, 11, 360, 0, 0, 1.6f, 28), fx(36, CAMERA, 1.5f, 0.09f)));
        ALL.put(Weapon.SCYTHE, new Set(sc, scHeavy, scPlunge, scCounter, scSkill, scLib));

        // ================================================================ SPEAR — reach and piercing lines
        List<Move> sp = new ArrayList<>();
        sp.add(mv("sp_1", "Thrust", P_THRUST, 10, 3, 0, 4,
                L(line(4, 5.8f, 1.3f, 1.0f, Knock.PUSH, 0.3f)),
                L(fx(3, REND_LINE, 5.8f, 0.6f, 0, 0, 0.3f, 14))));
        sp.add(mv("sp_2", "Twin Thrust", P_THRUST, 13, 3, 0, 5,
                L(line(3, 5.8f, 1.3f, 0.8f, Knock.NONE, 0), line(7, 6.2f, 1.3f, 0.8f, Knock.PUSH, 0.3f)),
                L(fx(2, REND_LINE, 5.8f, 0.6f, 8, -6, 0.25f, 12), fx(6, REND_LINE, 6.2f, 0.6f, -8, 6, 0.3f, 12))));
        sp.add(mv("sp_3", "Crescent Sweep", P_SLASH_RL, 13, 4, 0, 5,
                L(arc(5, 5.6f, 70, 0, 1.0f, Knock.PUSH, 0.5f)),
                L(fx(3, REND, 5.2f, 150, 0, 8, 0.45f, 16))));
        sp.add(mv("sp_4", "Piercing Line", P_THRUST, 18, 5, 0, 8,
                L(line(7, 10, 1.8f, 1.6f, Knock.PUSH, 0.7f)),
                L(fx(5, REND_LINE, 10, 0.5f, 0, 0, 0.55f, 20), fx(6, BEAM, 10, 0.4f), fx(7, CAMERA, 0.4f, 0.03f))));
        Move spHeavy = mv("sp_heavy", "Abyss Drill", P_THRUST, 30, 6, 0, 12,
                L(line(8, 16, 2.2f, 0.6f, Knock.NONE, 0), line(11, 16, 2.2f, 0.6f, Knock.NONE, 0), line(14, 16, 2.2f, 0.6f, Knock.NONE, 0), line(17, 16, 2.2f, 0.6f, Knock.NONE, 0), line(20, 16, 2.4f, 1.4f, Knock.PUSH, 0.9f)),
                L(fx(6, BEAM, 16, 1.2f), fx(20, CRATER, 2, 1.5f, 16, 0, 5, 0.6f), fx(20, CAMERA, 0.7f, 0.04f)));
        Move spPlunge = mv("sp_plunge", "Impale", P_PLUNGE, 18, 5, 0, 7,
                L(ground(5, 3.0f, 2.2f, Knock.DOWN, 0.5f)),
                L(fx(1, REND_LINE, 6, 0, 90, 0, 0.4f, 16), fx(5, SPIKES, 2.8f, 8, 2.8f, 0, 0), fx(5, SHOCK, 3.2f, 0.5f), fx(5, CAMERA, 0.6f, 0.03f)));
        Move spCounter = mv("sp_counter", "Counterpoint", P_THRUST, 16, 4, 0, 10,
                L(target(4, 8, 3.0f, Knock.PUSH, 0.8f)),
                L(fx(1, GHOSTS, 4), fx(3, REND_LINE, 8, 0.4f, 0, 0, 0.7f, 18), fx(4, CAMERA, 0.6f, 0.04f)));
        Move spSkill = mv("sp_skill", "Hundred Lances", P_CAST, 30, 6, 25, 8,
                L(target(8, 18, 0.7f, Knock.NONE, 0), target(11, 18, 0.7f, Knock.NONE, 0), target(14, 18, 0.7f, Knock.NONE, 0), target(17, 18, 0.7f, Knock.NONE, 0), target(20, 18, 1.2f, Knock.PUSH, 0.6f)),
                L(fx(2, THROW, 2.2f, 18, 0, 1), fx(5, THROW, 2.2f, 18, 0, 1), fx(8, THROW, 2.2f, 18, 0, 1), fx(11, THROW, 2.2f, 18, 0, 1), fx(14, THROW, 2.2f, 18, 0, 1)));
        Move spLib = mv("sp_lib", "Spear of the Abyss", P_THROW, 46, 8, 40, 0,
                L(line(16, 32, 3.2f, 4.0f, Knock.PUSH, 1.0f), circle(24, 6.5f, 30, 3.0f, Knock.LAUNCH, 1.2f)),
                L(fx(2, GIANT, 6, 0, 0, 2), fx(14, BEAM, 32, 1.8f), fx(16, CAMERA, 0.8f, 0.05f), fx(24, CRATER, 5, 3, 30, 0, 8, 0.9f), fx(24, SHOCK, 7, 1.0f)));
        ALL.put(Weapon.SPEAR, new Set(sp, spHeavy, spPlunge, spCounter, spSkill, spLib));

        // ================================================================ BULWARK — shard blade + hex shield
        List<Move> bw = new ArrayList<>();
        bw.add(mv("bw_1", "Shield Bash", P_BASH, 12, 4, 0, 5,
                L(arc(4, 3.2f, 50, 0, 1.0f, Knock.PUSH, 0.9f)),
                L(fx(3, BARRIER, 1.6f, 8), fx(4, SHOCK, 1.8f, 0.4f))));
        bw.add(mv("bw_2", "Shard Slash", P_SLASH_RL, 11, 4, 0, 5,
                L(arc(4, 3.4f, 70, 0, 1.0f, Knock.PUSH, 0.25f)),
                L(fx(2, REND, 3.0f, 140, 0, 10, 0.4f, 14))));
        bw.add(mv("bw_3", "Shard Return", P_SLASH_LR, 11, 4, 0, 5,
                L(arc(4, 3.4f, 70, 0, 1.0f, Knock.PUSH, 0.25f)),
                L(fx(2, REND, 3.0f, -140, 0, -12, 0.4f, 14))));
        bw.add(mv("bw_4", "Aegis Slam", P_SLAM, 18, 6, 0, 8,
                L(circle(8, 3.8f, 2, 1.8f, Knock.DOWN, 0.6f)),
                L(fx(6, BARRIER, 2.4f, 10), fx(8, CRATER, 2.5f, 1.5f, 2, 0, 4, 0.6f), fx(8, SPIKES, 3, 8, 1.6f, 2), fx(8, CAMERA, 0.6f, 0.03f))));
        Move bwHeavy = mv("bw_heavy", "Retribution", P_BASH, 18, 5, 0, 10,
                L(arc(4, 4.2f, 70, 0, 1.5f, Knock.PUSH, 1.2f)),
                L(fx(3, BARRIER, 2.4f, 10), fx(4, SHOCK, 3.6f, 0.8f), fx(4, CAMERA, 0.6f, 0.04f)));
        Move bwPlunge = mv("bw_plunge", "Aegis Drop", P_PLUNGE, 18, 5, 0, 7,
                L(ground(5, 4.6f, 2.2f, Knock.DOWN, 0.8f)),
                L(fx(4, BARRIER, 3, 10), fx(5, CRATER, 3, 2, 0, 2, 5, 0.7f), fx(5, CAMERA, 0.8f, 0.05f)));
        Move bwCounter = mv("bw_counter", "Perfect Parry", P_BASH, 16, 4, 0, 10,
                L(target(3, 5, 3.2f, Knock.PUSH, 1.2f)),
                L(fx(1, BARRIER, 2.6f, 12), fx(2, REND, 3.2f, 160, 0, 30, 0.6f, 16), fx(3, CAMERA, 0.7f, 0.05f)));
        Move bwSkill = mv("bw_skill", "Event Barrier", P_RAISE, 20, 6, 35, 6,
                L(circle(10, 6, 0, 0.3f, Knock.PUSH, 0.8f), circle(30, 6, 0, 0.3f, Knock.PUSH, 0.8f), circle(50, 6, 0, 0.3f, Knock.PUSH, 0.8f), circle(70, 6, 0, 0.3f, Knock.PUSH, 0.8f)),
                L(fx(4, BARRIER, 6, 80)));
        Move bwLib = mv("bw_lib", "Aegis of the Abyss", P_BASH, 44, 8, 40, 0,
                L(line(14, 15, 9, 4.0f, Knock.PUSH, 2.0f)),
                L(fx(2, GIANT, 7, 6, 0, 3), fx(14, TRENCH, 15, 3, 2, 3, 8, 0.8f), fx(14, SHOCK, 8, 1.2f), fx(14, CAMERA, 1.3f, 0.07f)));
        ALL.put(Weapon.BULWARK, new Set(bw, bwHeavy, bwPlunge, bwCounter, bwSkill, bwLib));

        // ================================================================ GAUNTLETS — Asura: a brawler, all fist
        // No rifts, no lances: real punches. 6-tick jabs, a punch rush at one hit a tick, uppercuts, a grab-and-slam,
        // and every heavy blow cratering the ground. The liberation is Asura's rage: extra arms and the Mantra rush.
        List<Move> ga = new ArrayList<>();
        ga.add(mv("ga_1", "Jab", P_PUNCH_L, 6, 2, 0, 3,
                L(target(3, 4.5f, 0.7f, Knock.PUSH, 0.15f)),
                L(fx(3, FIST, 1, 2.8f, 1, -1), fx(3, CAMERA, 0.12f, 0.008f))));
        ga.add(mv("ga_2", "Straight", P_PUNCH_R, 6, 2, 0, 3,
                L(target(3, 4.5f, 0.85f, Knock.PUSH, 0.2f)),
                L(fx(3, FIST, 1.2f, 2.8f, 1, 1), fx(3, CAMERA, 0.14f, 0.01f))));
        ga.add(mv("ga_3", "Liver Hook", P_PUNCH_L, 8, 3, 0, 4,
                L(target(4, 4.5f, 1.1f, Knock.SPIN, 0.4f)),
                L(fx(4, FIST, 1.5f, 2.6f, 1, -1), fx(4, CAMERA, 0.2f, 0.012f))));
        ga.add(mv("ga_4", "Mantra Rush", P_FLURRY, 18, 4, 0, 7, rush(2, 15, 9, 0.35f, 4.5f, 1.0f),
                L(fx(2, BARRAGE, 9, 0.55f, 2.8f, 14, 1), fx(16, FIST, 2, 2.8f, 1, 1), fx(16, CAMERA, 0.45f, 0.03f))));
        ga.add(mv("ga_5", "Rising Fist", P_RISING, 12, 4, 0, 6,
                L(target(5, 4.5f, 1.6f, Knock.LAUNCH, 0.9f)),
                L(fx(5, FIST, 2, 2.6f, 1, 1), fx(5, CAMERA, 0.4f, 0.03f))));
        Move gaHeavy = mv("ga_heavy", "Wrath Straight", P_PUNCH_R, 22, 6, 0, 12,
                L(line(12, 9, 2.6f, 2.6f, Knock.PUSH, 1.4f)),
                L(fx(0, CAMERA, 0.1f, -0.03f), fx(12, FIST, 3, 2.6f, 1, 1), fx(12, TRENCH, 9, 1.4f, 2, 1.5f, 3, 0.5f), fx(12, CAMERA, 1.0f, 0.07f)));
        Move gaPlunge = mv("ga_plunge", "Meteor Fist", P_SLAM, 18, 5, 0, 8,
                L(ground(4, 4.5f, 2.4f, Knock.DOWN, 1.0f)),
                L(fx(4, FIST, 2.5f, 1.5f, 0, 1), fx(4, CRATER, 4.5f, 2.5f, 0, 2, 6, 0.8f), fx(4, CAMERA, 1.1f, 0.06f)));
        Move gaCounter = mv("ga_counter", "Thousand Fists", P_FLURRY, 22, 5, 0, 12, withFinal(rush(2, 16, 12, 0.35f, 5, 0), target(18, 5, 2.4f, Knock.PUSH, 1.0f)),
                L(fx(0, PHANTOM, 4, 18), fx(2, BARRAGE, 14, 0.7f, 2.8f, 16, 1), fx(18, FIST, 2.5f, 2.8f, 1, 1), fx(18, CRATER, 2.5f, 1.5f, 0, 1, 4, 0.5f), fx(18, CAMERA, 0.8f, 0.05f)));
        Move gaSkill = mv("ga_skill", "Grab & Slam", P_SLAM, 22, 6, 20, 10,
                L(target(3, 6, 0.4f, Knock.PULL, 1.4f), target(8, 6, 0.4f, Knock.LAUNCH, 0.45f), target(14, 6, 3.4f, Knock.DOWN, 1.4f)),
                L(fx(3, FIST, 1, 2.4f, 1, 1), fx(14, FIST, 3, 2.4f, 1, -1), fx(14, CRATER, 3.5f, 2.5f, 0, 1, 6, 0.8f), fx(14, CAMERA, 1.0f, 0.06f)));
        Move gaLib = mv("ga_lib", "Asura's Mantra", P_FLURRY, 56, 8, 40, 0, withFinal(rush(4, 36, 17, 0.35f, 6, 0), onTarget(46, 6, 5.0f, Knock.LAUNCH, 1.4f)),
                L(fx(0, CAMERA, 0.3f, -0.04f), fx(0, PHANTOM, 6, 40), fx(4, BARRAGE, 30, 1.0f, 3.0f, 34, 1), fx(46, FIST, 3, 3.0f, 1, 1),
                        fx(46, CRATER, 7, 4, 0, 1, 12, 1.0f), fx(46, CAMERA, 1.8f, 0.1f)));
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
