package com.andy.abyss.client.render;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.Ability;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The construct in the hand, on the arm bone itself: it follows the arm exactly through every pose. The blade comes
 * out of the fist pointing forward with its edge down; the Aegis rides the left forearm; the gauntlets sheath both
 * fists.
 */
public class WeaponLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public WeaponLayer(RenderLayerParent<T, M> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limb, float limbAmt, float pt, float age, float headYaw, float headPitch) {
        if (ClientState.formOf(e.getId()) != Ability.ARSENAL.ordinal() || e.isInvisible()) return;
        Weapon w = Weapon.byIndex(ClientState.weaponOf(e.getId()));
        float[] sw = MoveFx.swingOf(e.getId(), pt, false);
        float forge = sw[4];
        float glow = 0.9f + 0.3f * (float) Math.sin((ClientState.ticks + pt) * 0.15 + e.getId());
        M m = getParentModel();
        if (w == Weapon.GAUNTLETS) {
            fist(ps, buf, m.rightArm, w, forge, glow, false);
            fist(ps, buf, m.leftArm, w, forge, glow, true);
            return;
        }
        if (sw[3] == 0) {
            ps.pushPose();
            m.rightArm.translateAndRotate(ps);
            ps.translate(-1 / 16f, 10 / 16f, 0);           // the fist
            // the weapon's angle in the fist: raised at the ready, straight out through a swing
            ps.mulPose(Axis.XP.rotationDegrees(com.andy.abyss.client.ArsenalPose.fistAngle(e, w)));
            ps.mulPose(Axis.YP.rotationDegrees(90));         // edge leads the swing
            float sc = com.andy.abyss.client.ArsenalPose.layerScale(w);
            ps.scale(sc, sc, sc);
            WeaponModel.render(ps, buf, w, forge, glow);
            ps.popPose();
        }
    }

    private static void fist(PoseStack ps, MultiBufferSource buf, net.minecraft.client.model.geom.ModelPart arm, Weapon w, float forge, float glow, boolean left) {
        ps.pushPose();
        arm.translateAndRotate(ps);
        ps.translate((left ? 1 : -1) / 16f, 8 / 16f, 0);
        ps.scale(left ? -1.05f : 1.05f, 1.05f, 1.05f);
        WeaponModel.render(ps, buf, w, forge, glow);
        ps.popPose();
    }
}
