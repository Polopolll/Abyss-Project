package com.andy.abyss.combat;

import java.util.List;

/**
 * One move of an abyssal weapon, as data. The server reads the hits; every client reads the visuals; both run on the
 * same timeline (ticks from the press), so what you see is exactly when it lands. Coordinates are in the caster's frame
 * at the press: forward = facing (flat), right, up; the origin is the caster's chest.
 */
public record Move(String id, String name, int pose, int duration, int recover, float cost, float gauge, List<Hit> hits, List<Fx> fx) {

    /** Hit shapes. */
    public enum Shape { ARC, LINE, CIRCLE, TARGET }
    /** What a hit does to the body. */
    public enum Knock { NONE, PUSH, LAUNCH, PULL, DOWN, SPIN }

    /**
     * A damage instance. ARC: radius a, half-angle b (deg), centre yaw offset c (deg). LINE: length a, width b, start c.
     * CIRCLE: radius a at forward distance b (or on the locked target if c = 1). TARGET: the locked target within reach a.
     */
    public record Hit(int tick, Shape shape, float a, float b, float c, float dmg, Knock knock, float kb) {}

    /** Visual primitives (drawn by the client). */
    public enum Kind {
        /** A rend: a crescent cut in space showing the galaxy behind. a radius, b span (deg), c yaw offset (deg), d tilt (deg), e thickness, f linger ticks. */
        REND,
        /** A straight rend (thrust / sever line). a length, b start, c tilt, d yaw offset, e thickness, f linger. */
        REND_LINE,
        /** A void arm out of a rift: a right offset, b up offset, c back offset, d curve, e thickness. Homes on the target. */
        RIFT_ARM,
        /** Ground ring. a radius, b thickness. */
        SHOCK,
        /** Ground fissure forward: a length, b width. */
        FISSURE,
        /** Crystal spikes from the ground: a radius, b count, c height, d forward distance (or on target if e = 1). */
        SPIKES,
        /** A small black star: a radius, b forward distance (target if c = 1), d life. */
        STAR,
        /** A beam/drill along the aim: a length, b width. */
        BEAM,
        /** A giant construct from a rift above: a size, b forward distance (target if c = 1). Kind of construct in d (0 blade, 1 hammer, 2 spear, 3 shield). */
        GIANT,
        /** Many cuts across an area ahead: a count, b area radius, c forward distance. */
        REND_STORM,
        /** A vortex / pull field: a radius, b forward distance, c life. */
        VORTEX,
        /** A thrown construct: a speed, b range, c returns (1 = boomerang), d kind (0 scythe, 1 spear). */
        THROW,
        /** A dome / shield wall in front: a radius, b life. */
        BARRIER,
        /** Afterimages of the caster. a count. */
        GHOSTS,
        /** Camera: a shake, b fov punch. */
        CAMERA
    }

    public record Fx(int tick, Kind kind, float a, float b, float c, float d, float e, float f) {
        public static Fx of(int t, Kind k, float... p) {
            float[] q = new float[6];
            System.arraycopy(p, 0, q, 0, Math.min(6, p.length));
            return new Fx(t, k, q[0], q[1], q[2], q[3], q[4], q[5]);
        }
    }

    public int lastHitTick() { int m = 0; for (Hit h : hits) m = Math.max(m, h.tick()); return m; }
}
