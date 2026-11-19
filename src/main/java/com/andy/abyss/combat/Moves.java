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

    static {
        // ================================================================ SWORD — the rends
        List<Move> sw = new ArrayList<>();
        sw.add(mv("sword_1", "Horizon Cut", P_SLASH_RL, 10, 4, 0, 4,
                L(arc(3, 3.8f, 75, 0, 1.0f, Knock.PUSH, 0.25f)),
                L(fx(1, REND, 3.2f, 150, 0, 6, 0.55f, 18), fx(2, REND, 2.7f, 100, 12, -8, 0.22f, 12), fx(3, REND, 3.6f, 70, -20, 3, 0.15f, 10))));
        sw.add(mv("sword_2", "Reverse Wane", P_SLASH_LR, 10, 4, 0, 4,
                L(arc(3, 3.8f, 75, 0, 1.0f, Knock.PUSH, 0.25f)),
                L(fx(1, REND, 3.2f, -150, 0, -30, 0.55f, 18), fx(2, REND, 2.6f, -90, -10, -50, 0.2f, 12))));
        sw.add(mv("sword_3", "Crossfold", P_FLURRY, 13, 4, 0, 5,
                L(arc(3, 3.6f, 60, 0, 0.8f, Knock.NONE, 0), arc(6, 3.6f, 60, 0, 0.8f, Knock.PUSH, 0.3f)),
                L(fx(2, REND, 3.0f, 120, 0, 45, 0.45f, 22), fx(5, REND, 3.0f, -120, 0, -45, 0.45f, 22), fx(6, STAR, 0.35f, 2.6f, 0, 8))));
        sw.add(mv("sword_4", "Thousand Petals", P_FLURRY, 14, 3, 0, 6,
                L(arc(2, 3.4f, 55, 0, 0.45f, Knock.NONE, 0), arc(4, 3.4f, 55, 0, 0.45f, Knock.NONE, 0), arc(6, 3.4f, 55, 0, 0.45f, Knock.NONE, 0), arc(8, 3.4f, 55, 0, 0.6f, Knock.PUSH, 0.2f)),
                L(fx(1, REND, 2.6f, 110, 0, 70, 0.25f, 12), fx(3, REND, 2.8f, -110, 0, -20, 0.25f, 12), fx(5, REND, 2.5f, 120, 0, 30, 0.25f, 12), fx(7, REND, 3.0f, -140, 0, -75, 0.3f, 14), fx(8, GHOSTS, 2))));
        sw.add(mv("sword_5", "Ascending Sever", P_RISING, 12, 4, 0, 6,
                L(arc(4, 3.6f, 45, 0, 1.3f, Knock.LAUNCH, 0.7f)),
                L(fx(2, REND, 3.4f, 160, 0, 85, 0.6f, 20), fx(3, REND_LINE, 4.5f, 0.6f, 80, 0, 0.18f, 16))));
        sw.add(mv("sword_6", "Dimension Sever", P_DRAW, 30, 6, 0, 12,
                L(line(7, 8, 3.2f, 1.6f, Knock.NONE, 0), line(24, 8, 3.6f, 2.4f, Knock.PUSH, 0.8f)),
                L(fx(3, GHOSTS, 3), fx(6, REND_LINE, 9, 0.5f, 2, 0, 0.9f, 22), fx(6, REND, 5.5f, 200, 0, 4, 0.35f, 22), fx(7, CAMERA, 0.4f, 0.03f),
                        fx(24, STAR, 1.2f, 4.5f, 0, 14), fx(24, CAMERA, 0.9f, 0.06f))));
        Move swHeavy = mv("sword_heavy", "Iaido: Thousand Rends", P_DRAW, 30, 6, 0, 10,
                L(circle(5, 4.8f, 5, 0.55f, Knock.NONE, 0), circle(9, 4.8f, 5, 0.55f, Knock.NONE, 0), circle(22, 5.2f, 5, 2.6f, Knock.PULL, 0.4f)),
                L(fx(2, GHOSTS, 4), fx(4, REND_STORM, 11, 4.6f, 5), fx(4, CAMERA, 0.3f, 0.02f), fx(22, STAR, 1.4f, 5, 0, 16), fx(22, CAMERA, 1.0f, 0.07f)));
        Move swPlunge = mv("sword_plunge", "Falling Rend", P_PLUNGE, 16, 4, 0, 6,
                L(ground(5, 3.4f, 1.9f, Knock.DOWN, 0.6f)),
                L(fx(1, REND_LINE, 6, 0, 90, 0, 0.6f, 20), fx(5, FISSURE, 6, 1.2f), fx(5, SHOCK, 3.5f, 0.6f), fx(5, CAMERA, 0.6f, 0.03f)));
        Move swCounter = mv("sword_counter", "Null Step", P_DRAW, 18, 4, 0, 10,
                L(target(3, 7, 1.4f, Knock.NONE, 0), target(10, 7, 2.2f, Knock.PUSH, 0.6f)),
                L(fx(1, GHOSTS, 5), fx(2, REND_STORM, 6, 2.2f, -1), fx(10, STAR, 0.9f, 0, 1, 10), fx(10, CAMERA, 0.7f, 0.05f)));
        Move swSkill = mv("sword_skill", "Rend Weave", P_SLASH_RL, 26, 6, 25, 8,
                L(circle(4, 6.2f, 7, 0.7f, Knock.PULL, 0.35f), circle(19, 6.2f, 7, 1.9f, Knock.PUSH, 0.5f)),
                L(fx(2, REND_STORM, 9, 6, 7), fx(3, CAMERA, 0.3f, 0.02f), fx(19, STAR, 1.6f, 7, 0, 16), fx(19, CAMERA, 0.8f, 0.05f)));
        Move swLib = mv("sword_lib", "Abyss Severance", P_DRAW, 48, 8, 40, 0,
                L(line(12, 30, 9, 3.0f, Knock.NONE, 0), line(36, 30, 10, 5.0f, Knock.LAUNCH, 1.0f)),
                L(fx(0, CAMERA, 0.2f, -0.05f), fx(6, GHOSTS, 6), fx(11, REND_LINE, 32, -2, 0, 0, 2.6f, 38), fx(11, REND, 12, 230, 0, 2, 0.6f, 36),
                        fx(12, REND_STORM, 14, 9, 14), fx(12, CAMERA, 0.8f, 0.06f), fx(36, STAR, 3, 14, 0, 22), fx(36, CAMERA, 1.6f, 0.1f)));
        ALL.put(Weapon.SWORD, new Set(sw, swHeavy, swPlunge, swCounter, swSkill, swLib));

        // ================================================================ GREATSWORD — slow, wide, the ground opens
        List<Move> gs = new ArrayList<>();
        gs.add(mv("gs_1", "Wide Sweep", P_SLASH_RL, 16, 6, 0, 6,
                L(arc(7, 4.8f, 90, 0, 1.4f, Knock.PUSH, 0.6f)),
                L(fx(5, REND, 4.6f, 175, 0, 6, 0.95f, 22), fx(6, REND, 4.0f, 120, 8, -4, 0.3f, 14), fx(7, CAMERA, 0.35f, 0.02f))));
        gs.add(mv("gs_2", "Overhead Cleave", P_OVERHEAD, 18, 6, 0, 7,
                L(line(9, 7.5f, 2.2f, 1.8f, Knock.DOWN, 0.5f)),
                L(fx(7, REND_LINE, 5, 0.5f, 90, 0, 0.9f, 22), fx(9, FISSURE, 8, 1.6f), fx(9, SHOCK, 2.5f, 0.6f), fx(9, CAMERA, 0.6f, 0.03f))));
        gs.add(mv("gs_3", "Orbit Cleave", P_SPIN, 18, 5, 0, 7,
                L(arc(5, 4.6f, 180, 0, 1.2f, Knock.SPIN, 0.4f), arc(11, 4.6f, 180, 0, 0.9f, Knock.PUSH, 0.6f)),
                L(fx(4, REND, 4.4f, 360, 0, 4, 0.8f, 18), fx(10, REND, 4.8f, -360, 0, -6, 0.5f, 16), fx(11, SHOCK, 4.6f, 0.4f))));
        gs.add(mv("gs_4", "Upheaval", P_RISING, 20, 6, 0, 9,
                L(circle(9, 3.4f, 3.2f, 2.0f, Knock.LAUNCH, 1.0f)),
                L(fx(7, REND, 4.2f, 160, 0, 80, 0.9f, 20), fx(9, SPIKES, 3.2f, 11, 3.2f, 3.2f), fx(9, SHOCK, 3.6f, 0.5f), fx(9, CAMERA, 0.7f, 0.04f))));
        Move gsHeavy = mv("gs_heavy", "Gravity Cleave", P_SPIN, 34, 8, 0, 12,
                L(circle(10, 6.2f, 0, 2.6f, Knock.PULL, 0.6f), circle(26, 6.6f, 0, 1.6f, Knock.PUSH, 0.9f)),
                L(fx(8, REND, 5.6f, 360, 0, 2, 1.4f, 26), fx(10, SHOCK, 6.4f, 0.8f), fx(10, VORTEX, 6.4f, 0, 24), fx(26, SHOCK, 7.2f, 0.6f), fx(26, CAMERA, 0.9f, 0.05f)));
        Move gsPlunge = mv("gs_plunge", "Meteor Cleave", P_PLUNGE, 20, 6, 0, 8,
                L(ground(5, 5.0f, 2.4f, Knock.DOWN, 0.8f)),
                L(fx(1, REND_LINE, 7, 0, 90, 0, 1.0f, 22), fx(5, FISSURE, 9, 2), fx(5, SHOCK, 5.4f, 0.8f), fx(5, SPIKES, 4.5f, 10, 2, 0, 0), fx(5, CAMERA, 0.9f, 0.05f)));
        Move gsCounter = mv("gs_counter", "Riposte", P_SLASH_LR, 18, 5, 0, 10,
                L(arc(5, 5.2f, 110, 0, 3.0f, Knock.PUSH, 1.0f)),
                L(fx(1, GHOSTS, 4), fx(4, REND, 5.2f, -220, 0, -10, 1.2f, 24), fx(5, CAMERA, 0.8f, 0.05f)));
        Move gsSkill = mv("gs_skill", "Fault Line", P_OVERHEAD, 30, 8, 25, 8,
                L(line(8, 15, 3.2f, 1.7f, Knock.LAUNCH, 0.8f), line(18, 15, 3.6f, 1.2f, Knock.NONE, 0)),
                L(fx(6, REND_LINE, 4, 0.5f, 90, 0, 0.9f, 18), fx(8, FISSURE, 16, 2.8f), fx(9, SPIKES, 2.2f, 18, 2.6f, 8, 0), fx(18, REND_STORM, 6, 2.5f, 9), fx(8, CAMERA, 0.6f, 0.03f)));
        Move gsLib = mv("gs_lib", "Sword of the Pit", P_RAISE, 50, 8, 40, 0,
                L(onTarget(26, 7.5f, 5.0f, Knock.DOWN, 1.0f), onTarget(40, 9, 2.0f, Knock.PUSH, 1.2f)),
                L(fx(2, GIANT, 9, 7, 1, 0), fx(26, SHOCK, 10, 1.4f), fx(26, FISSURE, 12, 3), fx(26, CAMERA, 1.6f, 0.08f), fx(40, STAR, 2.2f, 7, 1, 18)));
        ALL.put(Weapon.GREATSWORD, new Set(gs, gsHeavy, gsPlunge, gsCounter, gsSkill, gsLib));

        // ================================================================ HAMMER — collapses
        List<Move> hm = new ArrayList<>();
        hm.add(mv("hm_1", "Smash", P_OVERHEAD, 14, 5, 0, 6,
                L(circle(7, 2.8f, 2.2f, 1.4f, Knock.DOWN, 0.4f)),
                L(fx(7, SHOCK, 3.2f, 0.7f), fx(7, STAR, 0.45f, 2.2f, 0, 8), fx(7, CAMERA, 0.45f, 0.02f))));
        hm.add(mv("hm_2", "Uppercut", P_RISING, 13, 5, 0, 6,
                L(arc(5, 3.4f, 50, 0, 1.2f, Knock.LAUNCH, 0.9f)),
                L(fx(4, REND, 3.0f, 120, 0, 80, 0.7f, 14), fx(5, STAR, 0.4f, 2.4f, 0, 8))));
        hm.add(mv("hm_3", "Twin Quake", P_SLAM, 18, 5, 0, 7,
                L(circle(5, 3.6f, 1.5f, 1.0f, Knock.PUSH, 0.4f), circle(12, 4.6f, 1.5f, 1.3f, Knock.PUSH, 0.6f)),
                L(fx(5, SHOCK, 3.8f, 0.6f), fx(12, SHOCK, 4.8f, 0.8f), fx(12, SPIKES, 3.5f, 8, 1.5f, 1.5f), fx(12, CAMERA, 0.5f, 0.03f))));
        hm.add(mv("hm_4", "Ground Zero", P_SLAM, 22, 7, 0, 10,
                L(circle(10, 5.6f, 1, 2.4f, Knock.LAUNCH, 1.0f)),
                L(fx(10, SHOCK, 6.4f, 1.0f), fx(10, SPIKES, 5.4f, 14, 2.6f, 1), fx(10, STAR, 0.9f, 1, 0, 12), fx(10, CAMERA, 1.0f, 0.06f))));
        Move hmHeavy = mv("hm_heavy", "Collapse Strike", P_OVERHEAD, 46, 8, 0, 12,
                L(circle(12, 3.2f, 3, 2.0f, Knock.PULL, 0.4f), circle(40, 5.4f, 3, 2.6f, Knock.PUSH, 1.2f)),
                L(fx(12, STAR, 2.0f, 3, 0, 30), fx(12, VORTEX, 7, 3, 30), fx(12, SHOCK, 3.4f, 0.8f), fx(12, CAMERA, 0.6f, 0.03f), fx(40, SHOCK, 7, 1.0f), fx(40, CAMERA, 1.1f, 0.07f)));
        Move hmPlunge = mv("hm_plunge", "Starfall", P_PLUNGE, 20, 6, 0, 8,
                L(ground(5, 5.2f, 2.8f, Knock.DOWN, 1.0f)),
                L(fx(5, SHOCK, 6, 1.2f), fx(5, STAR, 1.2f, 0, 2, 14), fx(5, SPIKES, 5, 12, 2.2f, 0, 0), fx(5, CAMERA, 1.1f, 0.06f)));
        Move hmCounter = mv("hm_counter", "Rebound", P_RISING, 18, 5, 0, 10,
                L(target(4, 5.5f, 3.0f, Knock.LAUNCH, 1.2f)),
                L(fx(1, GHOSTS, 3), fx(4, SHOCK, 4, 0.8f), fx(4, STAR, 0.9f, 0, 1, 12), fx(4, CAMERA, 0.8f, 0.05f)));
        Move hmSkill = mv("hm_skill", "Gravity Well", P_SLAM, 22, 6, 30, 6,
                L(circle(8, 8, 6, 0.5f, Knock.PULL, 0.5f), circle(18, 8, 6, 0.5f, Knock.PULL, 0.5f), circle(28, 8, 6, 0.5f, Knock.PULL, 0.5f), circle(38, 8, 6, 0.5f, Knock.PULL, 0.5f), circle(52, 8, 6, 1.6f, Knock.PUSH, 1.0f)),
                L(fx(6, STAR, 1.6f, 6, 0, 50), fx(6, VORTEX, 8, 6, 48), fx(52, SHOCK, 8.5f, 1.0f), fx(52, CAMERA, 0.8f, 0.04f)));
        Move hmLib = mv("hm_lib", "Collapse", P_RAISE, 56, 8, 40, 0,
                L(onTarget(24, 8, 5.5f, Knock.DOWN, 1.0f), onTarget(46, 10, 3.0f, Knock.PUSH, 1.6f)),
                L(fx(2, GIANT, 8, 6, 1, 1), fx(24, SHOCK, 10, 1.5f), fx(24, STAR, 3.2f, 6, 1, 24), fx(24, CAMERA, 1.6f, 0.08f), fx(46, SHOCK, 12, 1.2f), fx(46, CAMERA, 1.4f, 0.09f)));
        ALL.put(Weapon.HAMMER, new Set(hm, hmHeavy, hmPlunge, hmCounter, hmSkill, hmLib));

        // ================================================================ SCYTHE — reaps that drag inward
        List<Move> sc = new ArrayList<>();
        sc.add(mv("sc_1", "Reap", P_SLASH_RL, 12, 4, 0, 5,
                L(arc(4, 4.4f, 80, 0, 1.1f, Knock.PULL, 0.35f)),
                L(fx(2, REND, 4.2f, 170, 0, 14, 0.5f, 18), fx(3, REND, 3.6f, 130, 10, 22, 0.18f, 12))));
        sc.add(mv("sc_2", "Wane Reap", P_SLASH_LR, 12, 4, 0, 5,
                L(arc(4, 4.4f, 80, 0, 1.1f, Knock.PULL, 0.35f)),
                L(fx(2, REND, 4.2f, -170, 0, -14, 0.5f, 18), fx(3, REND, 3.6f, -130, -10, -22, 0.18f, 12))));
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
                L(fx(6, BEAM, 16, 1.2f), fx(20, STAR, 1.0f, 16, 0, 10), fx(20, CAMERA, 0.7f, 0.04f)));
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
                L(fx(2, GIANT, 6, 0, 0, 2), fx(14, BEAM, 32, 1.8f), fx(16, CAMERA, 0.8f, 0.05f), fx(24, STAR, 2.6f, 30, 0, 18), fx(24, SHOCK, 7, 1.0f)));
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
                L(fx(6, BARRIER, 2.4f, 10), fx(8, SHOCK, 4.2f, 0.8f), fx(8, SPIKES, 3, 8, 1.6f, 2), fx(8, CAMERA, 0.6f, 0.03f))));
        Move bwHeavy = mv("bw_heavy", "Retribution", P_BASH, 18, 5, 0, 10,
                L(arc(4, 4.2f, 70, 0, 1.5f, Knock.PUSH, 1.2f)),
                L(fx(3, BARRIER, 2.4f, 10), fx(4, SHOCK, 3.6f, 0.8f), fx(4, CAMERA, 0.6f, 0.04f)));
        Move bwPlunge = mv("bw_plunge", "Aegis Drop", P_PLUNGE, 18, 5, 0, 7,
                L(ground(5, 4.6f, 2.2f, Knock.DOWN, 0.8f)),
                L(fx(4, BARRIER, 3, 10), fx(5, SHOCK, 5, 1.0f), fx(5, CAMERA, 0.8f, 0.05f)));
        Move bwCounter = mv("bw_counter", "Perfect Parry", P_BASH, 16, 4, 0, 10,
                L(target(3, 5, 3.2f, Knock.PUSH, 1.2f)),
                L(fx(1, BARRIER, 2.6f, 12), fx(2, REND, 3.2f, 160, 0, 30, 0.6f, 16), fx(3, CAMERA, 0.7f, 0.05f)));
        Move bwSkill = mv("bw_skill", "Event Barrier", P_RAISE, 20, 6, 35, 6,
                L(circle(10, 6, 0, 0.3f, Knock.PUSH, 0.8f), circle(30, 6, 0, 0.3f, Knock.PUSH, 0.8f), circle(50, 6, 0, 0.3f, Knock.PUSH, 0.8f), circle(70, 6, 0, 0.3f, Knock.PUSH, 0.8f)),
                L(fx(4, BARRIER, 6, 80)));
        Move bwLib = mv("bw_lib", "Aegis of the Abyss", P_BASH, 44, 8, 40, 0,
                L(line(14, 15, 9, 4.0f, Knock.PUSH, 2.0f)),
                L(fx(2, GIANT, 7, 6, 0, 3), fx(14, SHOCK, 8, 1.2f), fx(14, CAMERA, 1.3f, 0.07f)));
        ALL.put(Weapon.BULWARK, new Set(bw, bwHeavy, bwPlunge, bwCounter, bwSkill, bwLib));

        // ================================================================ GAUNTLETS — arms out of rifts
        List<Move> ga = new ArrayList<>();
        ga.add(mv("ga_1", "Rift Jab", P_PUNCH_R, 8, 3, 0, 3,
                L(target(5, 7, 0.8f, Knock.PUSH, 0.2f)),
                L(fx(0, RIFT_ARM, 1.1f, 1.7f, 0.3f, 0.6f, 0.32f))));
        ga.add(mv("ga_2", "Cross Hook", P_PUNCH_L, 8, 3, 0, 3,
                L(target(5, 7, 0.8f, Knock.PUSH, 0.2f)),
                L(fx(0, RIFT_ARM, -1.2f, 1.6f, 0.3f, -0.9f, 0.32f))));
        ga.add(mv("ga_3", "Under Rift", P_RISING, 11, 3, 0, 4,
                L(onTarget(6, 2, 1.0f, Knock.LAUNCH, 0.6f)),
                L(fx(1, SPIKES, 1.2f, 5, 2.2f, 0, 1), fx(1, RIFT_ARM, 0.3f, -0.6f, -2.5f, 0.0f, 0.36f))));
        ga.add(mv("ga_4", "Barrage", P_FLURRY, 14, 3, 0, 6,
                L(target(4, 7, 0.4f, Knock.NONE, 0), target(6, 7, 0.4f, Knock.NONE, 0), target(8, 7, 0.4f, Knock.NONE, 0), target(10, 7, 0.4f, Knock.NONE, 0), target(12, 7, 0.5f, Knock.PUSH, 0.3f)),
                L(fx(0, RIFT_ARM, 1.4f, 2.1f, 0.2f, 0.7f, 0.26f), fx(2, RIFT_ARM, -1.5f, 1.9f, 0.4f, -0.7f, 0.26f), fx(4, RIFT_ARM, 0.9f, 0.6f, 0.6f, 0.5f, 0.26f),
                        fx(6, RIFT_ARM, -0.8f, 2.6f, 0.2f, -0.4f, 0.26f), fx(8, RIFT_ARM, 0, 3.0f, 0.8f, 0.2f, 0.3f))));
        ga.add(mv("ga_5", "Converge", P_RAISE, 16, 4, 0, 7,
                L(target(8, 7, 1.4f, Knock.PUSH, 0.4f)),
                L(fx(1, RIFT_ARM, 2.4f, 1.0f, -1.5f, 0.6f, 0.3f), fx(1, RIFT_ARM, -2.4f, 1.0f, -1.5f, -0.6f, 0.3f), fx(2, RIFT_ARM, 1.6f, 3.0f, 0.4f, 0.5f, 0.3f),
                        fx(2, RIFT_ARM, -1.6f, 3.0f, 0.4f, -0.5f, 0.3f), fx(3, RIFT_ARM, 0, 3.6f, 1.2f, 0, 0.3f), fx(3, RIFT_ARM, 0, 0.2f, 1.6f, 0, 0.3f))));
        ga.add(mv("ga_6", "Event Horizon", P_CAST, 26, 6, 0, 10,
                L(onTarget(8, 4.5f, 1.0f, Knock.PULL, 0.5f), onTarget(20, 4.5f, 2.4f, Knock.PUSH, 1.0f)),
                L(fx(4, STAR, 1.6f, 0, 1, 16), fx(4, VORTEX, 5, 0, 16), fx(20, SHOCK, 4.5f, 0.8f), fx(20, CAMERA, 0.9f, 0.06f))));
        Move gaHeavy = mv("ga_heavy", "Abyssal Beam", P_CAST, 30, 6, 0, 12,
                L(line(7, 28, 2.4f, 0.7f, Knock.NONE, 0), line(10, 28, 2.4f, 0.7f, Knock.NONE, 0), line(13, 28, 2.4f, 0.7f, Knock.NONE, 0), line(16, 28, 2.4f, 0.7f, Knock.NONE, 0), line(19, 28, 2.6f, 1.3f, Knock.PUSH, 1.0f)),
                L(fx(5, BEAM, 28, 1.4f), fx(6, CAMERA, 0.5f, 0.03f)));
        Move gaPlunge = mv("ga_plunge", "Gravity Fold", P_PLUNGE, 22, 6, 0, 8,
                L(ground(8, 4.8f, 2.2f, Knock.DOWN, 0.8f)),
                L(fx(1, REND_LINE, 10, 0, 90, 0, 1.0f, 20), fx(8, SHOCK, 5.4f, 1.0f), fx(8, SPIKES, 4.4f, 12, 2.4f, 0, 0), fx(8, CAMERA, 0.9f, 0.05f)));
        Move gaCounter = mv("ga_counter", "Thousand-Arm Counter", P_FLURRY, 22, 5, 0, 10,
                L(target(4, 8, 0.5f, Knock.NONE, 0), target(6, 8, 0.5f, Knock.NONE, 0), target(8, 8, 0.5f, Knock.NONE, 0), target(10, 8, 0.5f, Knock.NONE, 0), target(12, 8, 0.5f, Knock.NONE, 0), target(14, 8, 1.4f, Knock.PUSH, 0.9f)),
                L(fx(0, RIFT_ARM, 2.0f, 2.0f, -0.5f, 0.6f, 0.26f), fx(1, RIFT_ARM, -2.0f, 2.2f, -0.4f, -0.6f, 0.26f), fx(3, RIFT_ARM, 1.2f, 3.2f, 0.4f, 0.3f, 0.26f),
                        fx(5, RIFT_ARM, -1.2f, 0.3f, 0.6f, -0.3f, 0.26f), fx(7, RIFT_ARM, 2.6f, 0.8f, 0.8f, 0.8f, 0.26f), fx(9, RIFT_ARM, 0, 3.8f, 1.0f, 0, 0.4f)));
        Move gaSkill = mv("ga_skill", "Rift Lance", P_PUNCH_R, 26, 6, 25, 8,
                L(line(10, 30, 2.6f, 2.4f, Knock.PUSH, 1.0f)),
                L(fx(0, RIFT_ARM, 0.0f, 2.4f, 2.0f, 0, 0.9f), fx(9, BEAM, 30, 0.9f), fx(10, CAMERA, 0.7f, 0.04f)));
        Move gaLib = mv("ga_lib", "Thousand Hands", P_RAISE, 52, 8, 40, 0,
                L(circle(8, 16, 0, 0.45f, Knock.NONE, 0), circle(12, 16, 0, 0.45f, Knock.NONE, 0), circle(16, 16, 0, 0.45f, Knock.NONE, 0), circle(20, 16, 0, 0.45f, Knock.NONE, 0),
                        circle(24, 16, 0, 0.45f, Knock.NONE, 0), circle(28, 16, 0, 0.45f, Knock.NONE, 0), circle(32, 16, 0, 0.45f, Knock.NONE, 0), circle(40, 16, 0, 2.0f, Knock.LAUNCH, 1.0f)),
                L(fx(2, REND_STORM, 16, 14, 0), fx(4, CAMERA, 0.4f, 0.03f), fx(40, SHOCK, 16, 1.4f), fx(40, CAMERA, 1.4f, 0.08f)));
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
