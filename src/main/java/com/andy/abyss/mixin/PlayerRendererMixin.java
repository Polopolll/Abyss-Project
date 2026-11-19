package com.andy.abyss.mixin;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.render.AbyssShaders;
import com.andy.abyss.client.render.Rt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person arms of an Abyss user are void crystal too. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private PlayerRendererMixin(EntityRendererProvider.Context c, PlayerModel<AbstractClientPlayer> m, float s) { super(c, m, s); }

    @Shadow private void setModelProperties(AbstractClientPlayer p) {}

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void abyss$voidHand(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (!ClientState.awakened(p.getId()) || !Config.crystalSkin.get() || AbyssShaders.VOID == null) return;
        PlayerModel<AbstractClientPlayer> model = getModel();
        setModelProperties(p);
        model.attackTime = 0;
        model.crouching = false;
        model.swimAmount = 0;
        model.setupAnim(p, 0, 0, 0, 0, 0);
        arm.xRot = 0;
        arm.render(ps, buf.getBuffer(Rt.voidEntity(Rt.BODY)), light, OverlayTexture.NO_OVERLAY);
        sleeve.xRot = 0;
        sleeve.render(ps, buf.getBuffer(Rt.voidEntity(Rt.BODY)), light, OverlayTexture.NO_OVERLAY);
        ci.cancel();
    }
}
