package com.andy.abyss.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The punches, copied from the "First Person combat fists UE4" reference video frame by frame: where each fist is on
 * screen (x, y from -1 to 1), how far from the eye (d, blocks) and how far out the arm is (e, 0 rest … 1 thrown),
 * keyed in the video's own 24 fps frames relative to the frame the punch connects. Written for the right hand; the
 * left mirrors x. The off hand has its own track (it drops away as the body turns, and after the big swings both
 * hands sink out of view and come back up).
 *
 * The positions were tracked off the video (the fist is the far end of the arm from its corner of the screen); the
 * frame each punch connects is frame 0.
 */
final class FistTracks {
    /** One key: frame, x, y, depth, extension. */
    record K(float f, float x, float y, float d, float e) {}

    /** Rest, from the idle: left (-0.43, -0.70), right (0.49, -0.87). */
    static final float REST_X = 0.5f, REST_Y_R = -0.92f, REST_Y_L = -0.8f, REST_D = 0.62f;

    // ---------------------------------------------------------------------------------------- jab / straight
    // video frames 152-178: out to the right edge, one frame to the crosshair, hold, back down below rest, settle
    static final K[] JAB = {
            k(-6, 9, 9, 0, 0), k(-4, 0.62f, -0.75f, 0.6f, 0.05f), k(-2, 0.93f, -0.64f, 0.55f, 0.15f),
            k(0, 0.02f, -0.07f, 1.0f, 1), k(2, 0.0f, -0.1f, 1.0f, 1), k(4, -0.02f, -0.18f, 0.97f, 0.95f),
            k(6, 0.11f, -0.3f, 0.85f, 0.7f), k(8, 0.41f, -0.62f, 0.7f, 0.35f), k(10, 0.59f, -0.91f, 0.6f, 0.1f),
            k(14, 0.5f, -0.75f, 0.62f, 0), k(20, 9, 9, 0, 0)};
    static final K[] JAB_OFF = {
            k(-6, 9, 9, 0, 0), k(-2, -0.56f, -0.77f, 0.62f, 0), k(0, -0.62f, -0.82f, 0.62f, 0), k(2, -0.88f, -0.9f, 0.6f, 0),
            k(8, -0.8f, -1.1f, 0.6f, 0), k(12, -0.55f, -0.84f, 0.62f, 0), k(14, -0.4f, -0.72f, 0.62f, 0), k(18, 9, 9, 0, 0)};

    // ---------------------------------------------------------------------------------------- hook
    // video frames 256-296: out past the right edge at eye height, the arm swings across flat, then both hands sink
    // out of view and rise back up (overshooting the rest a little)
    static final K[] HOOK = {
            k(-8, 9, 9, 0, 0), k(-6, 0.88f, -0.48f, 0.6f, 0.2f), k(-3, 0.95f, -0.06f, 0.7f, 0.5f),
            k(-1, 0.39f, -0.05f, 0.85f, 0.85f), k(0, 0.05f, -0.07f, 0.9f, 1), k(1, -0.45f, -0.1f, 0.85f, 1),
            k(3, -0.75f, -0.18f, 0.75f, 0.8f), k(5, -0.3f, -1.35f, 0.65f, 0.3f), k(9, 0.45f, -1.25f, 0.62f, 0),
            k(13, 0.53f, -0.66f, 0.62f, 0), k(18, 0.55f, -0.89f, 0.62f, 0), k(24, 9, 9, 0, 0)};
    static final K[] HOOK_OFF = {
            k(-8, 9, 9, 0, 0), k(-6, -0.33f, -0.73f, 0.62f, 0), k(-4, -0.14f, -0.85f, 0.62f, 0), k(-2, -0.9f, -0.95f, 0.6f, 0),
            k(2, -0.7f, -1.35f, 0.6f, 0), k(9, -0.5f, -1.2f, 0.6f, 0), k(13, -0.41f, -0.52f, 0.62f, 0), k(18, -0.42f, -0.75f, 0.62f, 0),
            k(24, 9, 9, 0, 0)};

    // ---------------------------------------------------------------------------------------- uppercut
    // video frames 412-454: dips low to the right, rips up through the middle to above the crosshair, carries on
    // across, then both hands sink away and come back up
    static final K[] UPPER = {
            k(-6, 9, 9, 0, 0), k(-4, 0.62f, -0.95f, 0.6f, 0.05f), k(-2, 0.74f, -1.05f, 0.58f, 0.15f),
            k(-1, 0.35f, -0.45f, 0.75f, 0.6f), k(0, -0.17f, 0.38f, 0.92f, 1), k(2, -0.35f, 0.35f, 0.9f, 1),
            k(4, -0.7f, -0.6f, 0.75f, 0.6f), k(6, 0.1f, -1.35f, 0.65f, 0.2f), k(12, 0.5f, -1.15f, 0.62f, 0),
            k(16, 0.51f, -0.77f, 0.62f, 0), k(22, 9, 9, 0, 0)};
    static final K[] UPPER_OFF = {
            k(-6, 9, 9, 0, 0), k(-4, -0.15f, -0.66f, 0.62f, 0), k(-2, -0.32f, -0.56f, 0.62f, 0), k(0, -0.42f, -0.62f, 0.62f, 0),
            k(2, -0.6f, -1.3f, 0.6f, 0), k(12, -0.45f, -1.2f, 0.6f, 0), k(16, -0.38f, -0.66f, 0.62f, 0), k(22, 9, 9, 0, 0)};

    private static K k(float f, float x, float y, float d, float e) { return new K(f, x, y, d, e); }

    /** Video frames per game tick (24 fps against 20 ticks a second). */
    static final float FRAMES_PER_TICK = 24f / 20f;

    static K[] track(int path, boolean off) {
        return switch (path) {
            case 1 -> off ? HOOK_OFF : HOOK;
            case 2 -> off ? UPPER_OFF : UPPER;
            default -> off ? JAB_OFF : JAB;
        };
    }

    /** First and last frame of a track (outside them the hand is at rest). */
    static float start(K[] t) { return t[0].f(); }
    static float end(K[] t) { return t[t.length - 1].f(); }

    /**
     * The track at frame f, Catmull-Rom between keys (smooth through every key, no corners). Keys at (9, 9) mean "at
     * rest": they're filled in with the rest point (rx, ry). Returns {x, y, d, e}.
     */
    static float[] eval(K[] t, float f, float rx, float ry) {
        int n = t.length;
        if (f <= t[0].f()) return new float[]{rx, ry, REST_D, 0};
        if (f >= t[n - 1].f()) return new float[]{rx, ry, REST_D, 0};
        int i = 0;
        while (i < n - 2 && t[i + 1].f() <= f) i++;
        K a = t[Math.max(0, i - 1)], b = t[i], c = t[i + 1], d = t[Math.min(n - 1, i + 2)];
        float u = (f - b.f()) / Math.max(1e-3f, c.f() - b.f());
        float[] o = new float[4];
        float[][] v = {vals(a, rx, ry), vals(b, rx, ry), vals(c, rx, ry), vals(d, rx, ry)};
        for (int j = 0; j < 4; j++) o[j] = cr(v[0][j], v[1][j], v[2][j], v[3][j], u);
        o[3] = Mth.clamp(o[3], 0, 1);
        return o;
    }

    private static float[] vals(K k, float rx, float ry) {
        return k.x() > 5 ? new float[]{rx, ry, REST_D, 0} : new float[]{k.x(), k.y(), k.d(), k.e()};
    }

    private static float cr(float p0, float p1, float p2, float p3, float u) {
        float u2 = u * u, u3 = u2 * u;
        return 0.5f * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    /** A screen point (x, y in -1..1) at depth d, into hand view space (the hand's own projection). */
    static Vector3f toView(float x, float y, float d) {
        var w = Minecraft.getInstance().getWindow();
        float aspect = (float) w.getWidth() / Math.max(1, w.getHeight());
        float t = (float) Math.tan(Math.toRadians(ViewDrive.handFov) / 2);
        return new Vector3f(x * t * aspect * d, y * t * d, -d);
    }

    private FistTracks() {}
}
