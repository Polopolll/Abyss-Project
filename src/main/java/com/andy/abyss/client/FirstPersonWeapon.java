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
        // the scythe's crescent and the spear's head turn their flat face to you
        if (w == Weapon.SCYTHE || w == Weapon.SPEAR) ps.mulPose(Axis.YP.rotationDegrees(90));
        float sc = switch (w) { case GREATSWORD -> 0.62f; case HAMMER -> 0.8f; case SPEAR, SCYTHE -> 0.7f; case BULWARK -> 0.8f; default -> 0.75f; };
        ps.scale(sc, sc, sc);
        WeaponModel.render(ps, e.getMultiBufferSource(), w, sw[4], glow);
        ps.popPose();
    }

    private static void shield(PoseStack ps, RenderHandEvent e, float forge, float glow) {
        boolean guard = ClientState.me.getBoolean("guarding");
        ps.pushPose();
        if (guard) ps.translate(-0.1f, -0.22f, -0.75f); else ps.translate(-0.62f, -0.5f, -0.85f);
        ps.mulPose(Axis.YP.rotationDegrees(guard ? 0 : 35));
        ps.mulPose(Axis.ZP.rotationDegrees((ClientState.ticks + e.getPartialTick()) * 0.5f));
        ps.scale(guard ? 0.55f : 0.38f, guard ? 0.55f : 0.38f, guard ? 0.55f : 0.38f);
        WeaponModel.renderShield(ps, e.getMultiBufferSource(), forge, glow);
        ps.popPose();
    }

    private static void gauntlet(PoseStack ps, RenderHandEvent e, float[] sw, float glow, boolean left) {
        int pose = (int) sw[5];
        boolean punching = left ? pose == com.andy.abyss.combat.Moves.P_PUNCH_L : pose != com.andy.abyss.combat.Moves.P_PUNCH_L && pose != 0;
        float push = punching ? sw[2] : 0;
        ps.pushPose();
        ps.translate(left ? -0.42f : 0.42f, -0.42f + push * 0.3f, -0.62f - push * 0.9f);
        ps.mulPose(Axis.XP.rotationDegrees(-80));
        ps.mulPose(Axis.ZP.rotationDegrees(left ? 12 : -12));
        ps.scale(left ? -0.9f : 0.9f, 0.9f, 0.9f);
        WeaponModel.render(ps, e.getMultiBufferSource(), Weapon.GAUNTLETS, sw[4], glow);
        ps.popPose();
    }
    private FirstPersonWeapon() {}
}
