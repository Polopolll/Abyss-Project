package com.andy.abyss.client.render;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.combat.Move;
import com.andy.abyss.combat.Weapon;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * First person, driven by the move's own effects. Everything the hands do is solved from where the effect is on
 * screen at this exact frame: a sword's blade points at the head of the light it is drawing, a fist reaches the point
 * where its punch flashes, on the tick it flashes. Positions are in hand view space (x right, y up, -z ahead) and
 * corrected for the hand's own field of view, so they land on the same pixels as the world effect.
 */
public final class ViewDrive {
    /** The world's and the hand's field of view this frame (captured from ComputeFov). */
    public static float worldFov = 70, handFov = 70;
    /** The camera roll applied to the world this frame (shake and recoil), which the hands don't get. */
    public static float worldRoll;

    // ---------------------------------------------------------------------------------------------- view space
    /** A world point in hand view space, so a model drawn there covers the same pixels. */
    static Vector3f toView(Vec3 w) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 d = w.subtract(cam.getPosition());
        Vector3f v = new Vector3f((float) d.x, (float) d.y, (float) d.z);
        new Quaternionf().rotateZ((float) Math.toRadians(worldRoll))
                .rotateX((float) Math.toRadians(cam.getXRot()))
                .rotateY((float) Math.toRadians(cam.getYRot() + 180)).transform(v);
        float k = (float) (Math.tan(Math.toRadians(handFov) / 2) / Math.tan(Math.toRadians(worldFov) / 2));
        v.x *= k; v.y *= k;
        return v;
    }

    /** Slide a view point along its own line of sight to depth `depth` (same pixel, nearer or farther). */
    static Vector3f atDepth(Vector3f v, float depth) {
        float z = Math.min(-0.05f, v.z);
        return new Vector3f(v).mul(depth / -z);
    }

    // ---------------------------------------------------------------------------------------------- frames
    /** The attacker's view frame: where a primitive fires from (eyes, a touch lower) and the full look. */
    record Frame(Vec3 o, Vec3 fwd, Vec3 right, Vec3 up) {
        static Frame of(MoveFx.Prim p) { return new Frame(p.o, p.fwd, p.right, p.up); }
        static Frame live(Entity c, float pt) {
            Vec3 o = c.getEyePosition(pt).add(0, -0.28, 0);
            Vec3 fwd = c.getViewVector(pt).normalize();
            Vec3 r = fwd.cross(new Vec3(0, 1, 0));
            Vec3 right = r.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : r.normalize();
            return new Frame(o, fwd, right, right.cross(fwd).normalize());
        }
    }

    /** The fired primitive for this effect, if it's out already (moved with its caster). */
    static MoveFx.Prim prim(MoveFx.Act a, Move.Fx f, float pt) {
        for (MoveFx.Prim p : MoveFx.PRIMS) if (p.a == a && p.fx == f) { p.follow(pt); return p; }
        return null;
    }

    static Frame frame(MoveFx.Act a, Move.Fx f, float pt) {
        MoveFx.Prim p = prim(a, f, pt);
        if (p != null) return Frame.of(p);
        return a.caster != null ? Frame.live(a.caster, pt) : new Frame(a.o, a.aim, a.right, a.up);
    }

    // ================================================================================================ the sword
    /** Light-sweep timing of a rend (MoveFx.slash) and a thrust (MoveFx.thrust). */
    static final float REND_SWEEP = 2.2f, LINE_SHOOT = 1.6f;

    /** Where the head of a cut's light is at progress k (0 its start, 1 its end; outside that, extended along it). */
    static Vec3 head(Move.Fx f, Frame fr, float k) {
        if (f.kind() == Move.Kind.REND_LINE) {
            Vec3 dir = MoveFx.rotAbout(fr.fwd(), fr.up(), -f.d());
            dir = MoveFx.rotAbout(dir, fr.right(), f.c()).normalize();
            Vec3 start = fr.o().add(dir.scale(Math.max(0.6f, f.b())));
            // a thrust: the blade drives along the line, never past its first third (it's a reach, not a throw)
            return start.add(dir.scale(Math.max(1.5f, f.a()) * Mth.clamp(k, -0.2f, 1.1f) * 0.35f + 1.2f));
        }
        Vec3 side = MoveFx.rotAbout(fr.right(), fr.fwd(), -f.d());
        Vec3 f0 = fr.fwd();
        Vec3 dirC = f0.scale(Math.cos(Math.toRadians(f.c()))).add(side.scale(Math.sin(Math.toRadians(f.c()))));
        Vec3 sideC = f0.scale(-Math.sin(Math.toRadians(f.c()))).add(side.scale(Math.cos(Math.toRadians(f.c()))));
        float span = Math.abs(f.b()) > 359 ? 360 : f.b();
        Vec3 n = sideC.cross(dirC).normalize();
        if (n.y > 0 || (Math.abs(n.y) < 0.2 && n.dot(fr.right()) < 0)) n = n.scale(-1);
        Vec3 centre = fr.o().subtract(fr.fwd().scale(0.2)).add(n.scale(0.6));
        double half = Math.toRadians(span) / 2, th = -half + 2 * half * k;
        Vec3 dir = dirC.scale(Math.cos(th)).add(sideC.scale(Math.sin(th)));
        return centre.add(dir.scale(Math.max(1.4f, f.a())));
    }

    /** The blade in your hand: {grip (view), direction (view), edge (view, the way the cut travels)}. */
    public record Blade(Vector3f grip, Vector3f dir, Vector3f edge) {}

    static final Vector3f REST_GRIP = new Vector3f(0.36f, -0.42f, -0.64f);
    static final Vector3f REST_DIR = new Vector3f(-0.16f, 0.62f, -0.77f).normalize();
    private static Blade last;

    /** A blade pose aimed at a world point: the hand drifts toward where the light is, the blade points at it. */
    static Blade aimAt(Vec3 world, Vec3 next) {
        Vector3f h = toView(world);
        Vector3f sx = atDepth(h, 1); // the head's place on screen
        Vector3f grip = new Vector3f(REST_GRIP).add(Mth.clamp(sx.x, -1.2f, 1.2f) * 0.22f - 0.06f, Mth.clamp(sx.y, -1, 1) * 0.16f + 0.05f, 0);
        Vector3f dir = new Vector3f(h).sub(grip).normalize();
        Vector3f edge = new Vector3f(toView(next)).sub(h);
        return new Blade(grip, dir, edge);
    }

    static Blade mix(Blade a, Blade b, float k) {
        k = Mth.clamp(k, 0, 1);
        Vector3f d = new Vector3f(a.dir).lerp(b.dir, k);
        if (d.lengthSquared() < 1e-4f) d.set(b.dir);
        return new Blade(new Vector3f(a.grip).lerp(b.grip, k), d.normalize(), new Vector3f(a.edge).lerp(b.edge, k));
    }

    static boolean lead(MoveFx.Act a, Move.Fx f) {
        return f.kind() == Move.Kind.REND_LINE || (f.kind() == Move.Kind.REND && f.e() >= a.maxThick * 0.7f);
    }

    static Blade pose(MoveFx.Act a, Move.Fx f, float k, float pt) {
        Frame fr = frame(a, f, pt);
        return aimAt(head(f, fr, k), head(f, fr, k + 0.05f));
    }

    /**
     * The blade this frame. Each cut: the hand winds back to just behind where the cut starts, then the blade rides
     * the light's head across (same easing, same ticks), follows through a little past the end, and flows into the
     * next cut or back to rest. Everything blends from where the blade actually was, so nothing ever snaps.
     */
    public static Blade sword(int id, float pt) {
        MoveFx.Act a = MoveFx.SWING.get(id);
        Blade rest = new Blade(new Vector3f(REST_GRIP), new Vector3f(REST_DIR), new Vector3f(-1, 0.3f, 0));
        float breathe = (float) Math.sin((ClientState.ticks + pt) * 0.09) * 0.012f;
        rest.grip.y += breathe;
        Blade out;
        if (a == null || a.w != Weapon.SWORD) out = last == null ? rest : mix(last, rest, 0.18f);
        else {
            float t = a.time(pt);
            if (a.from == null) a.from = last == null ? rest : last;
            Blade from = (Blade) a.from;
            Move.Fx prev = null;
            out = null;
            for (Move.Fx f : a.m.fx()) {
                if (!lead(a, f)) continue;
                float sweep = f.kind() == Move.Kind.REND ? REND_SWEEP : LINE_SHOOT;
                float start = f.tick();
                if (t < start) {
                    // wind into the anticipation of this cut, from the end of the last one (or where the blade was)
                    float from0 = prev == null ? 0 : prev.tick() + (prev.kind() == Move.Kind.REND ? REND_SWEEP : LINE_SHOOT) + 1.5f;
                    Blade a0 = prev == null ? from : pose(a, prev, 1.1f, pt);
                    float k = (t - from0) / Math.max(0.8f, start - from0);
                    out = mix(a0, pose(a, f, -0.16f, pt), MoveFx.smooth(k));
                    break;
                }
                if (t <= start + sweep + 1.5f) {
                    float age = t - start;
                    // ride the light's head (MoveFx.slash: head = smooth(age / sweepT)), then follow through
                    float k = age <= sweep ? -0.16f + 1.16f * MoveFx.smooth(age / sweep) : 1 + 0.1f * MoveFx.smooth((age - sweep) / 1.5f);
                    out = pose(a, f, k, pt);
                    break;
                }
                prev = f;
            }
            if (out == null) {
                // after the last cut: hold the follow-through, then return to rest over the recovery
                Blade hold = prev != null ? pose(a, prev, 1.1f, pt) : from;
                float end = a.m.duration();
                float k = (t - end) / Math.max(3, a.m.recover() + 3);
                out = t < end ? hold : mix(hold, rest, MoveFx.smooth(k));
            }
        }
        last = out;
        return out;
    }

    // ================================================================================================ the fists
    /**
     * One of your fists this frame: wrist position (view), how far out (−0.2 wound back … 1 landed), the path's roll,
     * the hand's grip, and an override of how it's held (knuckle direction and back-of-hand direction, weighted by
     * `hold`; used by the idles: cracking the knuckles, shaking the fist, flexing, looking it over).
     */
    public record Fist(Vector3f hand, float ext, float roll, WeaponModel.Grip grip, Vector3f knuckles, Vector3f back, float hold) {
        Fist(Vector3f hand, float ext, float roll) { this(hand, ext, roll, null, null, null, 0); }
    }

    static Vector3f guard(boolean left, float pt) {
        float t = (ClientState.ticks + pt);
        // the reference's rest: low in the corners (left a little higher), breathing
        float br = (float) Math.sin(t * 0.1f) * 0.025f;
        if (true) return FistTracks.toView(left ? -FistTracks.REST_X : FistTracks.REST_X, (left ? FistTracks.REST_Y_L : FistTracks.REST_Y_R) + br + (float) Math.sin(t * 0.07f + (left ? 1.2f : 0)) * 0.01f, FistTracks.REST_D);
        float bob = (float) Math.sin(t * 0.11f + (left ? 1.7f : 0)) * 0.012f;
        float sway = (float) Math.sin(t * 0.07f + (left ? 0.4f : 2.1f)) * 0.008f;
        return new Vector3f(left ? -0.38f : 0.38f, -0.4f + bob, -0.62f + sway);
    }

    /** Where a FIST lands (MoveFx/BrawlFx.fist): on the target's near face, or ahead of the eye, a touch low. */
    static Vec3 mark(MoveFx.Act a, Move.Fx f, float pt) {
        Frame fr = frame(a, f, pt);
        Vec3 c;
        Vec3 t = a.p.target() >= 0 ? a.target(pt, -1) : null;
        // a target that's been blasted away, or that you've passed, isn't what your fist goes for: straight ahead then
        if (t != null) {
            Vec3 to = t.subtract(fr.o());
            if (to.length() > f.b() + 4 || to.normalize().dot(fr.fwd()) < 0.55) t = null;
        }
        if (f.c() == 1 && t != null) c = t.add(fr.o().subtract(t).normalize().scale(0.45));
        else c = fr.o().add(fr.fwd().scale(f.b())).add(fr.up().scale(-0.12));
        return c.add(fr.right().scale(f.d() * 0.12));
    }

    /** One punch over time, peaking exactly on its tick: cock back, snap out (accelerating), hold, draw back. */
    static float punch(float dt, float wind, float size) {
        float hold = 0.6f + size * 0.5f, back = 3.5f + size * 1.2f;
        if (dt < -wind - 1 || dt > hold + back) return 0;
        if (dt < -1) return -0.2f * MoveFx.smooth((dt + wind + 1) / Math.max(0.5f, wind)); // wind-up
        if (dt < 0) { float u = dt + 1; return -0.2f + 1.2f * u * u; }                     // the snap (ease-in: fastest at contact)
        if (dt < hold) return 1;
        return 1 - MoveFx.smooth((dt - hold) / back);
    }

    /**
     * Where a fist reaches for a point: along the line of sight to it, at arm's length — kept in front of you and
     * within the middle of the screen (a target that flew off, or that you ran past, doesn't drag the arm behind you).
     */
    static Vector3f strike(Vector3f v, float depth) {
        Vector3f d = new Vector3f(v);
        if (d.z > -0.3f) d.z = -0.3f;
        Vector3f s = atDepth(d, depth);
        s.x = Mth.clamp(s.x, -0.42f * depth, 0.42f * depth);
        s.y = Mth.clamp(s.y, -0.4f * depth, 0.3f * depth);
        return s;
    }

    /** A point on a quadratic curve. */
    static Vector3f bez(Vector3f a, Vector3f c, Vector3f b, float k) {
        float u = 1 - k;
        return new Vector3f(a).mul(u * u).add(new Vector3f(c).mul(2 * u * k)).add(new Vector3f(b).mul(k * k));
    }

    /** The control point that shapes a punch's path between guard g and strike s. */
    static Vector3f control(Vector3f g, Vector3f s, boolean left, float path) {
        Vector3f mid = new Vector3f(g).lerp(s, 0.5f);
        return switch ((int) path) {
            case 1 -> mid.add(left ? -0.42f : 0.42f, 0.06f, 0.12f);   // hook: swings in from outside
            case 2 -> mid.add(0, -0.38f, 0.08f);                      // uppercut: comes up from below
            case 3 -> mid.add(0, 0.5f, 0.12f);                        // hammer: comes down from above
            default -> mid;
        };
    }

    /** {left, right} this frame: the punches, then how the hands are held, then whatever idle is playing. */
    public static Fist[] fists(int id, float pt) {
        Fist[] f = punches(id, pt);
        MoveFx.Act a = MoveFx.SWING.get(id);
        boolean busy = a != null && a.w == Weapon.GAUNTLETS;
        for (int i = 0; i < 2; i++) {
            float k = Math.max(0, f[i].ext());
            // relaxed in the guard (fingers just loose), clenched hard as the punch lands
            WeaponModel.Grip g = WeaponModel.Grip.of(0.12f * (1 - Math.min(1, k * 3)), Math.min(1, k * 1.4f), 0);
            f[i] = new Fist(f[i].hand(), f[i].ext(), f[i].roll(), g, f[i].knuckles(), f[i].back(), f[i].hold());
        }
        return FistIdle.apply(f, busy, pt);
    }

    /**
     * The body behind a punch: as one fist goes out the other comes back to the chin, and both shift the way the
     * torso turns (a right cross turns you left), so a punch has a whole body behind it, not just an arm.
     */
    static Fist[] body(Fist[] f) {
        float kl = Math.max(0, f[0].ext()), kr = Math.max(0, f[1].ext());
        Vector3f[] h = {new Vector3f(f[0].hand()), new Vector3f(f[1].hand())};
        // the other hand chambers: back toward the chin
        h[0].add(new Vector3f(0.07f, 0.05f, 0.12f).mul(kr * (1 - kl)));
        h[1].add(new Vector3f(-0.07f, 0.05f, 0.12f).mul(kl * (1 - kr)));
        // the torso turns away from the punching side and dips a little
        float turn = 0.05f * (kl - kr);
        for (Vector3f v : h) v.add(turn, -0.025f * Math.max(kl, kr), 0);
        return new Fist[]{new Fist(h[0], f[0].ext(), f[0].roll()), new Fist(h[1], f[1].ext(), f[1].roll())};
    }

    static Fist[] punches(int id, float pt) { return punchesRaw(id, pt); } // the reference's off-hand tracks carry the body

    /**
     * A fist held the way the reference holds it: at rest upright in the corner with the fingers toward you; thrown,
     * the forearm runs from where the elbow is for that punch (behind and below for a jab, out to the side for a hook,
     * low for an uppercut) to the fist, and the back of the hand faces up (jab, hook) or away from you (uppercut).
     * path -1 = a hand at rest / off hand.
     */
    static Fist held(Vector3f hand, float e, boolean left, int path) {
        float sd = left ? -1 : 1;
        Vector3f restK = new Vector3f(-sd * 0.25f, 0.9f, -0.35f).normalize();
        Vector3f restB = new Vector3f(sd * 0.3f, 0.1f, -1).normalize();
        Vector3f elbow = switch (path) {
            case 1 -> new Vector3f(sd * 0.75f, -0.15f, -0.5f);
            case 2 -> new Vector3f(sd * 0.3f, -0.85f, -0.55f);
            default -> new Vector3f(sd * 0.42f, -0.62f, -0.25f);
        };
        Vector3f thrownK = new Vector3f(hand).sub(elbow).normalize();
        Vector3f thrownB = path == 2 ? new Vector3f(0, 0.1f, -1) : new Vector3f(0, 1, 0.2f);
        Vector3f k = new Vector3f(restK).lerp(thrownK, e).normalize();
        Vector3f b = new Vector3f(restB).lerp(thrownB, e);
        if (b.lengthSquared() < 1e-4f) b.set(restB);
        b.normalize();
        // the tracked point is the front of the fist (the knuckles), the hand is drawn from the wrist: step back
        Vector3f wrist = new Vector3f(hand).sub(new Vector3f(k).mul(0.1f + 0.14f * e));
        return new Fist(wrist, e, 0, null, k, b, 1);
    }

    static Fist[] punchesRaw(int id, float pt) {
        Vector3f gl = guard(true, pt), gr = guard(false, pt);
        Fist[] out = {held(gl, 0, true, -1), held(gr, 0, false, -1)};
        MoveFx.Act a = MoveFx.SWING.get(id);
        if (a == null || a.w != Weapon.GAUNTLETS) return out;
        float t = a.time(pt);
        float[] best = {0, 0};
        for (Move.Fx f : a.m.fx()) {
            if (f.kind() == Move.Kind.FIST && f.e() != 3) {
                // jabs, hooks and uppercuts play the reference's own motion (FistTracks), frame for frame
                boolean left = f.d() < 0;
                int i = left ? 0 : 1, o = 1 - i;
                float sd = left ? -1 : 1;
                float fr = (t - f.tick()) * FistTracks.FRAMES_PER_TICK;
                FistTracks.K[] tr = FistTracks.track((int) f.e(), false), off = FistTracks.track((int) f.e(), true);
                if (fr >= FistTracks.start(tr) && fr <= FistTracks.end(tr)) {
                    float[] v = FistTracks.eval(tr, fr, sd * FistTracks.REST_X, left ? FistTracks.REST_Y_L : FistTracks.REST_Y_R);
                    if (v[3] >= Math.abs(best[i]) || best[i] == 0) {
                        best[i] = Math.max(0.001f, v[3]);
                        Vector3f hand = FistTracks.toView(left ? -v[0] : v[0], v[1], v[2]);
                        out[i] = held(hand, v[3], left, (int) f.e());
                    }
                }
                if (fr >= FistTracks.start(off) && fr <= FistTracks.end(off) && best[o] == 0) {
                    boolean oLeft = !left;
                    float[] v = FistTracks.eval(off, fr, (oLeft ? -1 : 1) * FistTracks.REST_X, oLeft ? FistTracks.REST_Y_L : FistTracks.REST_Y_R);
                    // the off hand's track is written for the left (the right hand punches): mirror it for a left punch
                    out[o] = held(FistTracks.toView(left ? -v[0] : v[0], v[1], v[2]), 0, oLeft, -1);
                }
            } else if (f.kind() == Move.Kind.FIST) {
                boolean left = f.d() < 0;
                int i = left ? 0 : 1;
                float size = Math.max(0.6f, f.a());
                float wind = Math.min(f.tick(), 1.5f + size);
                float k = punch(t - f.tick(), wind, size);
                if (k == 0 || Math.abs(k) < Math.abs(best[i])) continue;
                best[i] = k;
                Vector3f g = left ? gl : gr;
                float sd = left ? -1 : 1, depth = 0.86f + 0.05f * size;
                Vector3f s = new Vector3f(sd * 0.06f, -0.3f, -depth + 0.08f);   // hammer: finishes low
                Vector3f hand;
                if (k >= 0) hand = bez(g, control(g, s, left, f.e()), s, k);
                else hand = new Vector3f(g).add(0, 0.02f * k, -0.35f * k).add(sd * 0.04f * -k, 0, 0);
                out[i] = new Fist(hand, k, 0);
            } else if (f.kind() == Move.Kind.BARRAGE) {
                float tau = t - f.tick(), dur = Math.max(1, f.d());
                if (tau < -2 || tau > dur + 5) continue;
                int n = Math.max(1, (int) f.a());
                float rate = dur / n;
                // a floor the fists hover at between blows: they ramp up into the rush and settle out of it
                float base = 0.3f * MoveFx.smooth((tau + 2) / 2f) * (1 - MoveFx.smooth((tau - dur) / 5f));
                Frame fr = frame(a, f, pt);
                Vec3 c0;
                if (f.e() == 1 && a.p.target() >= 0) { Vec3 tt = a.target(pt, f.c()); c0 = tt.add(fr.o().subtract(tt).normalize().scale(0.45)); }
                else c0 = fr.o().add(fr.fwd().scale(f.c())).add(fr.up().scale(-0.12));
                for (int side = 0; side < 2; side++) {
                    boolean left = side == 0;
                    Vector3f g = left ? gl : gr;
                    Vector3f sAvg = strike(toView(c0), 1.0f);
                    Vector3f restP = bez(g, control(g, sAvg, left, 0), sAvg, base);
                    float kk = base;
                    Vector3f hand = restP;
                    for (int i = side; i < n; i += 2) {
                        float dt = tau - BrawlFx.barrageTime(f, i);
                        if (dt < -rate * 0.9f || dt > rate * 1.2f) continue;
                        float u = dt < 0 ? 1 - (-dt / (rate * 0.9f)) : dt < rate * 0.25f ? 1 : 1 - (dt - rate * 0.25f) / (rate * 0.95f);
                        u = Mth.clamp(u, 0, 1);
                        u = dt < 0 ? u * u : MoveFx.smooth(u);
                        Vec3 mk = c0.add(barrageOffset(a, f, i, fr));
                        Vector3f s = strike(toView(mk), 1.0f);
                        hand = new Vector3f(restP).lerp(s, u);
                        kk = base + (1 - base) * u;
                        break;
                    }
                    if (Math.abs(kk) >= Math.abs(best[side])) { best[side] = kk; out[side] = new Fist(hand, kk, 0); }
                }
            }
        }
        return out;
    }

    /** Same scatter as BrawlFx.barrageOffset, from a frame (so it works before the rush has fired). */
    static Vec3 barrageOffset(MoveFx.Act a, Move.Fx f, int i, Frame fr) {
        Random r = new Random(a.seed * 977 + i * 131L);
        double ang = r.nextDouble() * Math.PI * 2, rad = Math.sqrt(r.nextDouble()) * f.b();
        return fr.right().scale(Math.cos(ang) * rad).add(fr.up().scale(Math.sin(ang) * rad * 0.7));
    }

    private ViewDrive() {}
}
