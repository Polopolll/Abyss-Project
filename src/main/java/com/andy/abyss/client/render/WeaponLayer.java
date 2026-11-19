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
            ps.mulPose(Axis.XP.rotationDegrees(-90));        // blade out of the fist, forward
            ps.mulPose(Axis.YP.rotationDegrees(90));         // edge leads the swing
            float sc = switch (w) { case GREATSWORD -> 0.95f; case HAMMER, SCYTHE, SPEAR -> 1.0f; default -> 1.05f; };
            ps.scale(sc, sc, sc);
            WeaponModel.render(ps, buf, w, forge, glow);
            ps.popPose();
        }
        if (w == Weapon.BULWARK) {
            ps.pushPose();
            m.leftArm.translateAndRotate(ps);
            ps.translate(3 / 16f, 7 / 16f, 0);              // outside of the left forearm
            ps.mulPose(Axis.YP.rotationDegrees(90));         // facing out from the arm
            ps.mulPose(Axis.ZP.rotationDegrees((ClientState.ticks + pt) * 0.5f));
            ps.scale(0.85f, 0.85f, 0.85f);
            WeaponModel.renderShield(ps, buf, forge, glow);
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
