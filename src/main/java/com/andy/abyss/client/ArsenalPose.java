package com.andy.abyss.client;

import com.andy.abyss.combat.Moves;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.Ability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How an Arsenal user actually holds the construct (third person): a ready stance per weapon when idle, the angle
 * the weapon sits in the fist, and for two-handed weapons the left hand solved onto the grip/shaft every frame, so
 * both hands stay on the weapon through every swing.
 */
public final class ArsenalPose {
    /** Ready stance: {rArm x, y, z, lArm x, y, z} (radians), the weapon's angle in the fist (degrees), left-hand offset along the weapon (blocks, 0 = one-handed). */
    record Stance(float[] arms, float fistDeg, float leftOn) {}

    public static Stance stance(Weapon w) {
        return switch (w) {
            // one-handed: arm a little forward, blade raised and angled ahead
            case SWORD -> new Stance(new float[]{-0.5f, -0.12f, 0.08f, 0.15f, 0.1f, -0.1f}, -125, 0);
            case BULWARK -> new Stance(new float[]{-0.45f, -0.1f, 0.08f, -1.05f, 0.45f, 0}, -120, 0);
            // two-handed: held across the body, point up and to the left; left hand lower on the grip
            case GREATSWORD -> new Stance(new float[]{-0.85f, -0.5f, 0, 0, 0, 0}, -150, -0.27f);
            case HAMMER -> new Stance(new float[]{-0.7f, -0.45f, 0, 0, 0, 0}, -150, 0.45f);
            // polearms: shaft forward and up, left hand forward on the shaft
            case SCYTHE -> new Stance(new float[]{-0.55f, -0.35f, 0, 0, 0, 0}, -125, 0.55f);
            case SPEAR -> new Stance(new float[]{-0.6f, -0.3f, 0, 0, 0, 0}, -100, 0.6f);
            // fists up in a guard
            case GAUNTLETS -> new Stance(new float[]{-1.25f, -0.25f, 0.1f, -1.35f, 0.35f, -0.1f}, -90, 0);
        };
    }

    public static boolean twoHanded(Weapon w) { return stance(w).leftOn() != 0; }

    /** The scale the third-person layer draws each weapon at (kept here so the hand solve matches the drawing). */
    public static float layerScale(Weapon w) { return switch (w) { case GREATSWORD -> 0.95f; case HAMMER, SCYTHE, SPEAR -> 1.0f; default -> 1.05f; }; }

    /** 0..1: how strongly a move pose currently drives the arms (same timing as the pose blend). */
    public static float poseWeight(int id) {
        long[] p = ClientState.POSES.get(id);
        if (p == null) return 0;
        float dur = p.length > 2 ? Math.max(4, p[2]) : 11;
        float pt = Minecraft.getInstance().getFrameTime();
        if (p.length > 2 && p[2] >= 100000) return Math.min(1, (ClientState.ticks - p[1] + pt) / 3f);
        float t = (ClientState.ticks - p[1] + pt) / dur;
        if (t >= 1 || t < 0) return 0;
        float w = t < 0.1f ? t / 0.1f : t > 0.75f ? 1 - (t - 0.75f) / 0.25f : 1;
        return w * w * (3 - 2 * w);
    }

    public static int poseOf(int id) { long[] p = ClientState.POSES.get(id); return p == null ? 0 : (int) p[0]; }

    /** The angle (degrees) the weapon sits at in the fist right now: the ready angle, blending to straight out during moves. */
    public static float fistAngle(LivingEntity e, Weapon w) {
        return lerp(stance(w).fistDeg(), -90, poseWeight(e.getId()));
    }

    public static boolean holding(LivingEntity e) { return ClientState.formOf(e.getId()) == Ability.ARSENAL.ordinal(); }

    /** Called at the end of the player model's setupAnim, after the move pose was applied. */
    public static void apply(LivingEntity e, ModelPart rightArm, ModelPart leftArm) {
        if (!holding(e)) return;
        Weapon w = Weapon.byIndex(ClientState.weaponOf(e.getId()));
        Stance s = stance(w);
        float idle = 1 - poseWeight(e.getId());
        int pose = poseOf(e.getId());
        if (idle > 0) {
            float[] a = s.arms();
            // the stance replaces the walking arm swing (you don't swing a greatsword like a free arm), with a light bob
            float bob = (float) Math.sin((ClientState.ticks + Minecraft.getInstance().getFrameTime()) * 0.08f) * 0.03f;
            rightArm.xRot = lerp(rightArm.xRot, a[0] + bob, idle); rightArm.yRot = lerp(rightArm.yRot, a[1], idle); rightArm.zRot = lerp(rightArm.zRot, a[2], idle);
            if (!twoHanded(w)) { leftArm.xRot = lerp(leftArm.xRot, a[3] + bob, idle * (w == Weapon.SWORD ? 0.4f : 1)); leftArm.yRot = lerp(leftArm.yRot, a[4], idle); leftArm.zRot = lerp(leftArm.zRot, a[5], idle); }
        }
        // two hands on the weapon: put the left hand on the grip/shaft, wherever the right hand has taken it
        if (twoHanded(w) && pose != Moves.P_THROW && pose != Moves.P_CAST) solveLeft(rightArm, leftArm, fistAngle(e, w), s.leftOn() * layerScale(w));
    }

    /** Point the left arm at a spot on the weapon (model space, pixels; Y down, forward is -Z). */
    static void solveLeft(ModelPart r, ModelPart l, float fistDeg, float along) {
        Quaternionf qr = new Quaternionf().rotationZYX(r.zRot, r.yRot, r.xRot);
        Vector3f hand = qr.transform(new Vector3f(-1, 10, 0)).add(r.x, r.y, r.z);
        Vector3f axis = qr.transform(new Quaternionf().rotationX((float) Math.toRadians(fistDeg)).transform(new Vector3f(0, 1, 0)));
        Vector3f target = new Vector3f(hand).add(axis.mul(along * 16));
        Vector3f d = target.sub(l.x + 1, l.y, l.z);
        if (d.lengthSquared() < 1e-4f) return;
        d.normalize();
        // arm direction for (x, y) with z = 0: (sin x sin y, cos x, sin x cos y); keep the arm forward (sin x < 0)
        float sx = -(float) Math.sqrt(d.x * d.x + d.z * d.z);
        l.xRot = (float) Math.atan2(sx, d.y);
        l.yRot = Math.abs(sx) < 1e-4f ? 0 : (float) Math.atan2(d.x / sx, d.z / sx);
        l.zRot = 0;
    }

    static float lerp(float a, float b, float k) { return a + (b - a) * k; }
    private ArsenalPose() {}
}
