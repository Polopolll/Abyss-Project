package com.andy.abyss.client;

import com.andy.abyss.Abyss;
import com.andy.abyss.client.render.MoveFx;
import com.andy.abyss.client.render.WeaponModel;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.Ability;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
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
 * First person: the construct as a view model. It rests low on the right with the blade angled up and in; during a
 * move it follows the move's swing keyframes in view space (a slash sweeps across the screen, a thrust drives toward
 * the crosshair, a slam comes down from the top), so the blade and the cut it leaves line up.
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
        float[] sw = MoveFx.swingOf(mc.player.getId(), pt, true);
        float glow = 0.95f + 0.25f * (float) Math.sin((ClientState.ticks + pt) * 0.15);
        boolean main = e.getHand() == InteractionHand.MAIN_HAND;
        if (!main) {
            if (w == Weapon.BULWARK) { e.setCanceled(true); shield(ps, e, sw[4], glow); }
            else if (w == Weapon.GAUNTLETS) { e.setCanceled(true); gauntlet(ps, e, sw, glow, true); }
            return;
        }
        e.setCanceled(true);
        if (w == Weapon.GAUNTLETS) { gauntlet(ps, e, sw, glow, false); return; }
        if (sw[3] == 1) return; // thrown
        // never past ~70° up: an overhead wind-up would swing the construct over the camera and fill the screen
        // the scythe rests diagonally across the view: shaft from your right hand up to the left, blade hanging out over the field
        if (w == Weapon.SCYTHE && sw[5] == 0) { sw[0] = -34; sw[1] = 30; }
        double yaw = Math.toRadians(sw[0]), pitch = Math.toRadians(Math.max(-80, Math.min(68, sw[1])));
        Vector3f dir = new Vector3f((float) (Math.sin(yaw) * Math.cos(pitch)), (float) Math.sin(pitch), (float) (-Math.cos(yaw) * Math.cos(pitch))).normalize();
        // the fist: low right at rest, drawn toward the centre as it swings
        float swingK = sw[5] == 0 ? 0 : 1;
        Vector3f grip = new Vector3f(0.46f - 0.16f * swingK, -0.48f + 0.06f * swingK, -0.72f).add(new Vector3f(dir).mul(0.12f + sw[2]));
        ps.pushPose();
        ps.translate(grip.x, grip.y, grip.z);
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
        // roll the flat so the edge leads (faces along the swing, i.e. across the view)
        Vector3f bx = new Vector3f(1, 0, 0).rotate(q);
        Vector3f want = new Vector3f(dir).cross(0, 0, -1);
        if (want.lengthSquared() > 1e-4f) {
            want.normalize();
            float ang = (float) Math.atan2(new Vector3f(bx).cross(want).dot(dir), bx.dot(want));
            q = new Quaternionf().rotateAxis(ang + (float) Math.PI / 2, dir).mul(q);
        }
        ps.mulPose(q);
        // turn the flat a little toward you so blades show their face, not just their edge
        // the roll leaves the weapon's width pointing at you: turn it so blades show their face (a little of the
        // edge too), and the scythe crescent, spear wings and maul head lie across the view
        if (w == Weapon.SWORD || w == Weapon.GREATSWORD || w == Weapon.BULWARK) ps.mulPose(Axis.YP.rotationDegrees(62));
        else if (w == Weapon.SPEAR || w == Weapon.HAMMER) ps.mulPose(Axis.YP.rotationDegrees(90));
        else if (w == Weapon.SCYTHE) ps.mulPose(Axis.YP.rotationDegrees(-90)); // the crescent sweeps out to the left, face on
        float sc = switch (w) { case GREATSWORD -> 0.62f; case HAMMER -> 0.8f; case SPEAR, SCYTHE -> 0.7f; case BULWARK -> 0.8f; default -> 0.75f; };
        ps.scale(sc, sc, sc);
        WeaponModel.render(ps, e.getMultiBufferSource(), w, sw[4], glow);
        ps.popPose();
        // your arms on the weapon: the right fist on the grip, and on two-handed weapons the left hand on the grip
        // or shaft (further along the weapon's own axis, so it follows every swing)
        arm(ps, e.getMultiBufferSource(), e.getPackedLight(), mc.player, false, grip, dir, sw[4]);
        float on = ArsenalPose.stance(w).leftOn();
        if (on != 0) arm(ps, e.getMultiBufferSource(), e.getPackedLight(), mc.player, true, new Vector3f(dir).mul(on * sc).add(grip), dir, sw[4]);
    }

    /** Shoulders, off the bottom corners of the screen. */
    private static final Vector3f SHOULDER_R = new Vector3f(0.62f, -1.45f, -0.2f), SHOULDER_L = new Vector3f(-0.62f, -1.45f, -0.2f);
    /** View-model arm size: a real-scale arm a hand's length from your eye fills half the screen. */
    private static final float ARM = 0.6f;

    /**
     * One of your arms, reaching from its shoulder (off screen) so the fist closes on `hand`. The weapon axis is used to
     * turn the fist so the knuckles wrap around the grip rather than point along it.
     */
    static void arm(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, boolean left, Vector3f hand, Vector3f weaponAxis, float alpha) {
        if (alpha < 0.5f) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer pr)) return;
        PlayerModel<AbstractClientPlayer> model = pr.getModel();
        ModelPart a = left ? model.leftArm : model.rightArm, sl = left ? model.leftSleeve : model.rightSleeve;
        Vector3f from = new Vector3f(left ? SHOULDER_L : SHOULDER_R);
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
        Vector3f pivot = new Vector3f(hand).sub(new Vector3f(u).mul(8.5f / 16f * ARM)); // the fist (the arm's last 3 px) closes on the grip
        ps.translate(pivot.x, pivot.y, pivot.z);
        ps.mulPose(q);
        ps.scale(ARM, ARM, ARM);
        ps.translate(-cx / 16f, 0, 0);
        boolean crystal = ClientState.awakened(p.getId()) && com.andy.abyss.Config.crystalSkin.get() && com.andy.abyss.client.render.AbyssShaders.VOID != null;
        RenderType skin = crystal ? com.andy.abyss.client.render.Rt.voidEntity(com.andy.abyss.client.render.Rt.BODY) : RenderType.entitySolid(p.getSkinTextureLocation());
        a.render(ps, buf.getBuffer(skin), light, OverlayTexture.NO_OVERLAY);
        sl.render(ps, buf.getBuffer(crystal ? skin : RenderType.entityTranslucent(p.getSkinTextureLocation())), light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        a.loadPose(pa); sl.loadPose(ps2); a.visible = va; sl.visible = vs;
    }

    private static void shield(PoseStack ps, RenderHandEvent e, float forge, float glow) {
        boolean guard = ClientState.me.getBoolean("guarding");
        Vector3f c = guard ? new Vector3f(-0.1f, -0.22f, -0.75f) : new Vector3f(-0.5f, -0.33f, -0.85f);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        ps.mulPose(Axis.YP.rotationDegrees(guard ? 0 : 25)); // the boss faces you
        ps.mulPose(Axis.ZP.rotationDegrees((ClientState.ticks + e.getPartialTick()) * 0.5f));
        float s = guard ? 0.55f : 0.38f;
        ps.scale(s, s, s);
        WeaponModel.renderShield(ps, e.getMultiBufferSource(), forge, glow);
        ps.popPose();
        // strapped to your left forearm: the fist grips the strap just behind the boss
        arm(ps, e.getMultiBufferSource(), e.getPackedLight(), Minecraft.getInstance().player, true, new Vector3f(c).add(0.02f, -0.02f, 0.05f), new Vector3f(1, 0.2f, 0).normalize(), forge);
    }

    private static void gauntlet(PoseStack ps, RenderHandEvent e, float[] sw, float glow, boolean left) {
        int pose = (int) sw[5];
        boolean punching = left ? pose == com.andy.abyss.combat.Moves.P_PUNCH_L : pose != com.andy.abyss.combat.Moves.P_PUNCH_L && pose != 0;
        float push = punching ? sw[2] : 0;
        // the fist, raised in a guard, driven at the crosshair on a punch; the gauntlet is worn on your actual hand
        Vector3f hand = new Vector3f(left ? -0.36f : 0.36f, -0.36f + push * 0.22f, -0.6f - push * 0.75f);
        Vector3f from = new Vector3f(left ? SHOULDER_L : SHOULDER_R);
        Vector3f u = new Vector3f(hand).sub(from).normalize();
        arm(ps, e.getMultiBufferSource(), e.getPackedLight(), Minecraft.getInstance().player, left, hand, new Vector3f(left ? 1 : -1, 0, 0), sw[4]);
        ps.pushPose();
        Vector3f at = new Vector3f(hand).add(new Vector3f(u).mul(-0.02f));
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), u));
        ps.mulPose(Axis.YP.rotationDegrees(left ? -90 : 90)); // the gem on the back of the hand faces up/out
        ps.scale(left ? -0.95f : 0.95f, 0.95f, 0.95f);
        WeaponModel.render(ps, e.getMultiBufferSource(), Weapon.GAUNTLETS, sw[4], glow);
        ps.popPose();
    }
    private FirstPersonWeapon() {}
}
