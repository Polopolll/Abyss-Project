package com.andy.abyss.client.render;

import com.andy.abyss.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.Random;

/**
 * What your hands do when they aren't punching (first person). A short shake-out after a combo (wrists loose,
 * fingers open, then clenched again), and when you stand ready for a while, one of: cracking the knuckles (one
 * hand pressing over the other fist), shaking a fist, flexing the hands open and clenching them hard, or turning a
 * fist over to look at it. Every idle eases in from the guard and back out, and any punch cuts it off at once.
 */
final class FistIdle {
    enum Kind { NONE, SHAKE_OUT, CRACK, SHAKE, FLEX, INSPECT }
    private static final int[] LEN = {0, 16, 46, 32, 40, 56};
    private static final Random R = new Random();

    private static Kind kind = Kind.NONE;
    private static float start, lastBusy = -1000, nextIdle = 80;
    private static boolean wasBusy;
    private static Kind lastIdle = Kind.NONE;
    private static int beats;

    static ViewDrive.Fist[] apply(ViewDrive.Fist[] f, boolean busy, float pt) {
        float now = ClientState.ticks + pt;
        if (busy) {
            kind = Kind.NONE;
            lastBusy = now;
            wasBusy = true;
            return f;
        }
        // just finished a combo: shake the hands out
        if (wasBusy) {
            wasBusy = false;
            if (now - lastBusy < 3) begin(Kind.SHAKE_OUT, now + 6);
        }
        // standing ready a while: something to do with your hands
        if (kind == Kind.NONE && now - lastBusy > nextIdle) {
            Kind[] pool = {Kind.CRACK, Kind.SHAKE, Kind.FLEX, Kind.INSPECT};
            Kind k;
            do k = pool[R.nextInt(pool.length)]; while (k == lastIdle);
            lastIdle = k;
            begin(k, now);
            nextIdle = (now - lastBusy) + LEN[k.ordinal()] + 90 + R.nextInt(120);
        }
        if (kind == Kind.NONE) return f;
        float t = now - start;
        if (t < 0) return f;
        if (t > LEN[kind.ordinal()]) { kind = Kind.NONE; return f; }
        return switch (kind) {
            case SHAKE_OUT -> shakeOut(f, t);
            case CRACK -> crack(f, t);
            case SHAKE -> shake(f, t);
            case FLEX -> flex(f, t);
            case INSPECT -> inspect(f, t);
            default -> f;
        };
    }

    static String state() { return kind + " lastBusy=" + lastBusy + " next=" + nextIdle; }

    private static void begin(Kind k, float at) { kind = k; start = at; beats = 0; }

    /** 0→1 over the first `in` ticks, 1→0 over the last `out`. */
    private static float env(float t, float len, float in, float out) {
        return MoveFx.smooth(t / in) * (1 - MoveFx.smooth((t - (len - out)) / out));
    }

    private static ViewDrive.Fist hold(ViewDrive.Fist base, Vector3f pos, Vector3f knuckles, Vector3f back, WeaponModel.Grip grip, float w) {
        Vector3f hand = new Vector3f(base.hand()).lerp(pos, w);
        WeaponModel.Grip g = (base.grip() == null ? WeaponModel.Grip.of(0, 0, 0) : base.grip()).mix(grip, w);
        return new ViewDrive.Fist(hand, base.ext(), base.roll(), g, knuckles == null ? null : new Vector3f(knuckles).normalize(), back == null ? null : new Vector3f(back).normalize(), knuckles == null ? 0 : w);
    }

    private static void sound(SoundEvent e, float vol, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY() - 0.4, mc.player.getZ(), e, SoundSource.PLAYERS, vol, pitch * (0.95f + R.nextFloat() * 0.1f), false);
    }

    /** Sounds on beats: fires each beat once as t passes it. */
    private static void beat(float t, float at, int index, Runnable r) {
        if (t >= at && beats <= index) { beats = index + 1; r.run(); }
    }

    // ------------------------------------------------------------------------------------------------ the idles
    /** After a combo: the wrists go loose and flick, fingers open, then the fists close again. */
    private static ViewDrive.Fist[] shakeOut(ViewDrive.Fist[] f, float t) {
        float len = LEN[Kind.SHAKE_OUT.ordinal()], w = env(t, len, 3, 6);
        beat(t, 2, 0, () -> sound(SoundEvents.ARMOR_EQUIP_CHAIN, 0.35f, 1.4f));
        ViewDrive.Fist[] o = new ViewDrive.Fist[2];
        for (int i = 0; i < 2; i++) {
            boolean left = i == 0;
            float ph = t * 2.4f + (left ? 1.3f : 0);
            float flick = (float) Math.sin(ph) * (1 - t / len);
            Vector3f g = new Vector3f(f[i].hand()).add(0, -0.06f, 0.02f).add(flick * 0.025f, (float) Math.cos(ph * 1.3f) * 0.015f, 0);
            Vector3f k = new Vector3f(left ? 0.15f : -0.15f, 1, -0.2f).add(flick * 0.5f, 0, flick * 0.3f);
            Vector3f b = new Vector3f(left ? 0.4f : -0.4f, 0.1f, 1).add(0, flick * 0.4f, 0);
            float open = 0.7f * MoveFx.smooth(t / 4) * (1 - MoveFx.smooth((t - 9) / 5));
            o[i] = hold(f[i], g, k, b, new WeaponModel.Grip(new float[]{open, open * 0.9f, open * 0.85f, open * 0.8f}, 0, open), w);
        }
        return o;
    }

    /**
     * Cracking the knuckles: the left fist comes across the middle, knuckles pointing right; the right hand opens,
     * covers it, and presses down, three cracks.
     */
    private static ViewDrive.Fist[] crack(ViewDrive.Fist[] f, float t) {
        float len = LEN[Kind.CRACK.ordinal()], w = env(t, len, 9, 10);
        float press = 0;
        for (float at : new float[]{15, 22, 29}) {
            float d = t - at;
            if (d > -2 && d < 4) press = Math.max(press, d < 0 ? 1 + d / 2 : 1 - d / 4);
        }
        beat(t, 15, 0, () -> sound(SoundEvents.BONE_BLOCK_BREAK, 0.4f, 1.9f));
        beat(t, 22, 1, () -> sound(SoundEvents.BONE_BLOCK_BREAK, 0.45f, 1.75f));
        beat(t, 29, 2, () -> sound(SoundEvents.BONE_BLOCK_BREAK, 0.5f, 1.6f));
        Vector3f lp = new Vector3f(-0.07f, -0.3f - press * 0.012f, -0.56f);
        ViewDrive.Fist l = hold(f[0], lp, new Vector3f(1, 0.12f, -0.25f), new Vector3f(0.05f, 1, 0.35f), WeaponModel.Grip.of(0, 0.6f + press * 0.4f, 0), w);
        Vector3f rp = new Vector3f(0.05f, -0.235f - press * 0.03f, -0.54f);
        float open = 0.8f - press * 0.25f;
        ViewDrive.Fist r = hold(f[1], rp, new Vector3f(-1, -0.45f, -0.15f), new Vector3f(-0.1f, 1, 0.5f), new WeaponModel.Grip(new float[]{open, open, open * 0.95f, open * 0.9f}, 0, 0.6f), w);
        return new ViewDrive.Fist[]{l, r};
    }

    /** Shaking a fist: the right comes up, clenched hard, trembling with it. */
    private static ViewDrive.Fist[] shake(ViewDrive.Fist[] f, float t) {
        float len = LEN[Kind.SHAKE.ordinal()], w = env(t, len, 7, 8);
        float tr = (float) Math.min(1, t / 8) * (1 - MoveFx.smooth((t - 20) / 8));
        beat(t, 8, 0, () -> sound(SoundEvents.BONE_BLOCK_BREAK, 0.25f, 2.0f));
        Vector3f p = new Vector3f(0.17f, -0.2f, -0.6f).add((float) Math.sin(t * 2.7f) * 0.011f * tr, (float) Math.sin(t * 3.3f + 1) * 0.009f * tr, 0);
        Vector3f k = new Vector3f(-0.12f, 1, -0.35f).add((float) Math.sin(t * 2.2f) * 0.06f * tr, 0, 0);
        ViewDrive.Fist r = hold(f[1], p, k, new Vector3f(-0.25f, 0.2f, 1), WeaponModel.Grip.of(0, 1, 0), w);
        return new ViewDrive.Fist[]{f[0], r};
    }

    /** Flexing: both hands open wide, fingers rolling, then they clench hard (a crack at the end). */
    private static ViewDrive.Fist[] flex(ViewDrive.Fist[] f, float t) {
        float len = LEN[Kind.FLEX.ordinal()], w = env(t, len, 6, 7);
        beat(t, 27, 0, () -> sound(SoundEvents.BONE_BLOCK_BREAK, 0.35f, 1.8f));
        ViewDrive.Fist[] o = new ViewDrive.Fist[2];
        for (int i = 0; i < 2; i++) {
            boolean left = i == 0;
            float open = MoveFx.smooth((t - 3) / 7) * (1 - MoveFx.smooth((t - 20) / 5));
            float[] fo = new float[4];
            for (int j = 0; j < 4; j++) fo[j] = Mth.clamp(open - 0.25f * Math.max(0, (float) Math.sin((t - 10) * 0.5f - j * 0.9f)) * (t > 10 && t < 20 ? 1 : 0), 0, 1);
            float clench = MoveFx.smooth((t - 22) / 4) * (1 - MoveFx.smooth((t - 32) / 5));
            float shiver = clench * (float) Math.sin(t * 3.1f + i) * 0.006f;
            Vector3f p = new Vector3f(left ? -0.22f : 0.22f, -0.27f + shiver, -0.6f);
            Vector3f k = new Vector3f(left ? 0.25f : -0.25f, 1, -0.45f);
            Vector3f b = new Vector3f(left ? 0.25f : -0.25f, 0.1f, 1);
            o[i] = hold(f[i], p, k, b, new WeaponModel.Grip(fo, clench, open), w);
        }
        return o;
    }

    /** Looking it over: the right fist comes to the middle and turns over, the fingers open a moment, it closes. */
    private static ViewDrive.Fist[] inspect(ViewDrive.Fist[] f, float t) {
        float len = LEN[Kind.INSPECT.ordinal()], w = env(t, len, 10, 10);
        float turn = MoveFx.smooth((t - 8) / 14) * (1 - MoveFx.smooth((t - 36) / 12));
        double ang = Math.toRadians(-115 * turn);
        Vector3f k = new Vector3f(-0.35f, 1, -0.5f);
        Vector3f b = new Vector3f(-0.2f, 0.15f, 1).rotateAxis((float) ang, k.x, k.y, k.z);
        float open = 0.55f * MoveFx.smooth((t - 22) / 5) * (1 - MoveFx.smooth((t - 32) / 5));
        beat(t, 34, 0, () -> sound(SoundEvents.ARMOR_EQUIP_CHAIN, 0.3f, 1.5f));
        Vector3f p = new Vector3f(0.06f, -0.2f, -0.5f);
        ViewDrive.Fist r = hold(f[1], p, k, b, new WeaponModel.Grip(new float[]{open, open * 1.05f, open * 1.1f, open * 1.15f}, open > 0.01f ? 0 : 0.3f, open), w);
        return new ViewDrive.Fist[]{f[0], r};
    }

    private FistIdle() {}
}
