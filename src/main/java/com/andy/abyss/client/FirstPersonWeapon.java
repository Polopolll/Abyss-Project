package com.andy.abyss.client;

import com.andy.abyss.Abyss;
import com.andy.abyss.client.render.MoveFx;
import com.andy.abyss.client.render.ViewDrive;
import com.andy.abyss.client.render.WeaponModel;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.Ability;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First person. Both weapons are driven by {@link ViewDrive}: the blade rides the head of the light it is cutting,
 * each fist lands on the point where its punch flashes, on the tick it flashes. Your own arms reach from off-screen
 * shoulders to the grip / fist.
 */
@Mod.EventBusSubscriber(modid = Abyss.MODID, value = Dist.CLIENT)
public final class FirstPersonWeapon {
    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || ClientState.formOf(mc.player.getId()) != Ability.ARSENAL.ordinal()) return;
        Weapon w = Weapon.byIndex(ClientState.weaponOf(mc.player.getId()));
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();
        float forge = MoveFx.swingOf(mc.player.getId(), pt, true)[4];
        float glow = 0.95f + 0.25f * (float) Math.sin((ClientState.ticks + pt) * 0.15);
        boolean main = e.getHand() == InteractionHand.MAIN_HAND;
        if (w == Weapon.GAUNTLETS) { e.setCanceled(true); gauntlet(ps, e, forge, glow, !main); return; }
        if (!main) return;
        e.setCanceled(true);
        sword(ps, e, forge, glow);
    }

    /** The edge's last direction, so the blade never flips over between frames (its two edges are alike). */
    private static Vector3f lastEdge;

    private static void sword(PoseStack ps, RenderHandEvent e, float forge, float glow) {
        var mc = Minecraft.getInstance();
        ViewDrive.Blade b = ViewDrive.sword(mc.player.getId(), e.getPartialTick());
        Vector3f dir = b.dir(), grip = b.grip();
        // the blade's width runs along the cut (its edge leads), never flipping sides from one frame to the next
        Vector3f edge = new Vector3f(b.edge()).sub(new Vector3f(dir).mul(b.edge().dot(dir)));
        if (edge.lengthSquared() < 1e-6f) edge = new Vector3f(dir).cross(0, 0, -1);
        if (edge.lengthSquared() < 1e-6f) edge.set(1, 0, 0);
        edge.normalize();
        if (lastEdge != null && edge.dot(lastEdge) < 0) edge.negate();
        if (lastEdge != null) { edge = new Vector3f(lastEdge).lerp(edge, 0.5f); edge.sub(new Vector3f(dir).mul(edge.dot(dir))).normalize(); }
        lastEdge = new Vector3f(edge);
        Vector3f face = new Vector3f(edge).cross(dir).normalize();
        // the model: +Y along the blade, +X across its width, +Z out of its face
        org.joml.Matrix3f basis = new org.joml.Matrix3f(edge, dir, face);
        Quaternionf q = new Quaternionf().setFromNormalized(basis);
        ps.pushPose();
        ps.translate(grip.x, grip.y, grip.z);
        ps.mulPose(q);
        ps.mulPose(Axis.YP.rotationDegrees(40)); // turn the flat toward you a little: you see the blade, not a line
        ps.scale(0.75f, 0.75f, 0.75f);
        WeaponModel.render(ps, e.getMultiBufferSource(), Weapon.SWORD, forge, glow);
        ps.popPose();
        arm(ps, e.getMultiBufferSource(), e.getPackedLight(), mc.player, false, grip, dir, forge);
    }

    /** How much the next arm may stretch to reach its shoulder (0 = never: a guard; 1 = fully: a punch thrown out). */
    private static float stretchK;

    /** Shoulders, off the bottom corners of the screen. */
    private static final Vector3f SHOULDER_R = new Vector3f(0.62f, -1.45f, -0.2f), SHOULDER_L = new Vector3f(-0.62f, -1.45f, -0.2f);
    /** View-model arm size: a real-scale arm a hand's length from your eye fills half the screen. */
    private static final float ARM = 0.6f;

    /**
     * One of your arms, reaching from its shoulder (off screen) so the fist closes on `hand`. The weapon axis is used to
     * turn the fist so the knuckles wrap around the grip rather than point along it.
     */
    static void arm(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, boolean left, Vector3f hand, Vector3f weaponAxis, float alpha) {
        arm(ps, buf, light, p, left, hand, weaponAxis, alpha, left ? SHOULDER_L : SHOULDER_R, 0);
    }

    /** ghost > 0: a phantom arm (Asura's extra arms) — dark and translucent, from its own shoulder. */
    static void arm(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, boolean left, Vector3f hand, Vector3f weaponAxis, float alpha, Vector3f shoulder, float ghost) {
        if (alpha < 0.5f && ghost <= 0) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer pr)) return;
        PlayerModel<AbstractClientPlayer> model = pr.getModel();
        ModelPart a = left ? model.leftArm : model.rightArm, sl = left ? model.leftSleeve : model.rightSleeve;
        Vector3f from = new Vector3f(shoulder);
        Vector3f u = new Vector3f(hand).sub(from).normalize();
        // the forearm: +Y of the arm model runs from the shoulder to the fist
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), u);
        // roll it so the back of the hand faces away from the weapon axis (the grip runs across the palm)
        Vector3f side = new Vector3f(left ? -1 : 1, 0, 0).rotate(q);
        Vector3f wantSide = new Vector3f(u).cross(weaponAxis);
        if (wantSide.lengthSquared() > 1e-4f) {
            wantSide.normalize();
            if (left) wantSide.negate();
            float ang = (float) Math.atan2(new Vector3f(side).cross(wantSide).dot(u), side.dot(wantSide));
            q = new Quaternionf().rotateAxis(ang, u).mul(q);
        }
        boolean slim = "slim".equals(p.getModelName());
        float cx = (left ? 1 : -1) * (slim ? 0.5f : 1f);
        PartPose pa = a.storePose(), ps2 = sl.storePose();
        boolean va = a.visible, vs = sl.visible;
        a.setPos(0, 0, 0); a.setRotation(0, 0, 0); sl.setPos(0, 0, 0); sl.setRotation(0, 0, 0); a.visible = true; sl.visible = true;
        ps.pushPose();
        // the arm runs all the way back to its shoulder: stretched along its length when the hand is far out (a
        // view-model arm is shorter than the reach of a full punch), never shorter than it is
        float reach = new Vector3f(hand).sub(from).length();
        float stretch = Math.max(1, 1 + (reach / (10.5f / 16f * ARM) - 1) * stretchK);
        Vector3f pivot = new Vector3f(hand).sub(new Vector3f(u).mul(8.5f / 16f * ARM * stretch)); // the fist (the arm's last 3 px) closes on the grip
        ps.translate(pivot.x, pivot.y, pivot.z);
        ps.mulPose(q);
        ps.scale(ARM, ARM * stretch, ARM);
        ps.translate(-cx / 16f, 0, 0);
        boolean crystal = ClientState.awakened(p.getId()) && com.andy.abyss.Config.crystalSkin.get() && com.andy.abyss.client.render.AbyssShaders.VOID != null;
        RenderType skin = crystal ? com.andy.abyss.client.render.Rt.voidEntity(com.andy.abyss.client.render.Rt.BODY) : RenderType.entitySolid(p.getSkinTextureLocation());
        if (ghost > 0) {
            // a phantom arm: the same arm, black and see-through
            RenderType gt = RenderType.entityTranslucent(p.getSkinTextureLocation());
            a.render(ps, buf.getBuffer(gt), light, OverlayTexture.NO_OVERLAY, 0.06f, 0.05f, 0.1f, ghost * 0.7f);
            sl.render(ps, buf.getBuffer(gt), light, OverlayTexture.NO_OVERLAY, 0.06f, 0.05f, 0.1f, ghost * 0.7f);
        } else {
            a.render(ps, buf.getBuffer(skin), light, OverlayTexture.NO_OVERLAY);
            sl.render(ps, buf.getBuffer(crystal ? skin : RenderType.entityTranslucent(p.getSkinTextureLocation())), light, OverlayTexture.NO_OVERLAY);
        }
        ps.popPose();
        a.loadPose(pa); sl.loadPose(ps2); a.visible = va; sl.visible = vs;
    }

    /** Asura's extra arms: pairs of phantom arms from higher and wider shoulders, hammering around the crosshair. */
    private static void phantoms(PoseStack ps, RenderHandEvent e, float glow) {
        var mc = Minecraft.getInstance();
        float[] ph = MoveFx.phantomDrive(mc.player.getId(), e.getPartialTick());
        if (ph == null) return;
        float vis = ph[0];
        int n = (int) ph[1];
        float time = ph[2];
        for (int i = 0; i < n; i++) {
            boolean left = i % 2 == 0;
            int row = i / 2;
            float sx = left ? -1 : 1;
            Vector3f sh = new Vector3f(sx * (0.7f + row * 0.25f), -1.0f + row * 0.55f, -0.15f);
            float period = 3.0f + (i % 3) * 0.35f;
            float t = ((time + i * 1.13f) % period) / period;
            float k = t < 0.3f ? t / 0.3f : 1 - (t - 0.3f) / 0.7f;
            k = k * k * (3 - 2 * k);
            Vector3f guard = new Vector3f(sx * (0.45f + row * 0.18f), -0.2f + row * 0.22f, -0.8f);
            Vector3f strike = new Vector3f(sx * (0.12f + row * 0.06f), -0.05f + (row - 0.5f) * 0.12f, -1.5f);
            Vector3f hand = new Vector3f(guard).lerp(strike, k);
            arm(ps, e.getMultiBufferSource(), e.getPackedLight(), mc.player, left, hand, new Vector3f(left ? 1 : -1, 0, 0), 1, sh, vis);
            Vector3f u = new Vector3f(hand).sub(sh).normalize();
            ps.pushPose();
            ps.translate(hand.x, hand.y, hand.z);
            ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), u));
            ps.mulPose(Axis.YP.rotationDegrees(left ? -90 : 90));
            ps.scale(left ? -0.6f : 0.6f, 0.6f, 0.6f);
            WeaponModel.renderEthereal(ps, e.getMultiBufferSource(), Weapon.GAUNTLETS, vis * 0.8f);
            ps.popPose();
        }
    }

    /**
     * One fist. At rest it's up in a guard (forearm rising into it, knuckles up, the back of the hand toward you, like
     * a boxer's); as it punches the knuckles turn to lead along the path and the back of the hand rolls up.
     */
    private static void gauntlet(PoseStack ps, RenderHandEvent e, float forge, float glow, boolean left) {
        var mc = Minecraft.getInstance();
        if (!left) phantoms(ps, e, glow);
        ViewDrive.Fist f = ViewDrive.fists(mc.player.getId(), e.getPartialTick())[left ? 0 : 1];
        Vector3f wrist = f.hand();
        float ext = Mth.clamp(f.ext(), 0, 1);
        // the shoulder: below the screen in the guard (forearms rise into the fists), drawn back behind you as the
        // arm extends (so a punch is the whole arm driving out from the bottom corner along the line of sight)
        Vector3f from = new Vector3f(left ? SHOULDER_L : SHOULDER_R).lerp(new Vector3f(left ? -0.3f : 0.3f, -0.6f, 0.2f), MoveFx.smoothPublic(ext));
        // knuckles: up and a touch forward in the guard, at the target as the punch lands
        Vector3f guardK = new Vector3f(left ? 0.08f : -0.08f, 1, -0.35f).normalize();
        // (a touch up, so you see the back of your hand and the knuckles over it, not just the cuff end-on)
        Vector3f aimK = new Vector3f(wrist).normalize().add(left ? 0.1f : -0.1f, 0.38f, 0).normalize();
        Vector3f y = new Vector3f(guardK).lerp(aimK, ext).normalize();
        // back of the hand: toward you in the guard, up when the arm is out
        Vector3f back = new Vector3f(left ? 0.3f : -0.3f, 0.15f, 1).lerp(new Vector3f(0, 1, 0.25f), ext);
        back.sub(new Vector3f(y).mul(back.dot(y)));
        if (back.lengthSquared() < 1e-5f) back.set(0, 0, 1);
        back.normalize();
        if (f.roll() != 0) back.rotateAxis((float) Math.toRadians(f.roll()), y.x, y.y, y.z);
        // an idle holding the hand its own way (knuckle cracking, shaking, looking it over): blend to that
        if (f.hold() > 0 && f.knuckles() != null) {
            y.lerp(f.knuckles(), f.hold()).normalize();
            back.lerp(f.back(), f.hold());
            back.sub(new Vector3f(y).mul(back.dot(y)));
            if (back.lengthSquared() < 1e-5f) back.set(0, 0, 1);
            back.normalize();
        }
        Vector3f x = new Vector3f(y).cross(back).normalize();
        stretchK = MoveFx.smoothPublic(ext);
        arm(ps, e.getMultiBufferSource(), e.getPackedLight(), mc.player, left, wrist, x, forge, from, 0);
        stretchK = 0;
        ps.pushPose();
        ps.translate(wrist.x, wrist.y, wrist.z);
        ps.mulPose(new Quaternionf().setFromNormalized(new org.joml.Matrix3f(x, y, back)));
        float sc = 0.52f;
        ps.scale(left ? -sc : sc, sc, sc);
        WeaponModel.renderFist(ps, e.getMultiBufferSource(), forge, glow + ext * 0.6f, f.grip() != null ? f.grip() : WeaponModel.Grip.of(0, 0, 0));
        ps.popPose();
    }
    private FirstPersonWeapon() {}
}
