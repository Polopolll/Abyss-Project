package com.andy.abyss.mixin;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.render.AbyssShaders;
import com.andy.abyss.client.render.Rt;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Abyss users are drawn as the crystalline void body. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void abyss$voidBody(LivingEntity e, boolean visible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (visible && e instanceof AbstractClientPlayer && ClientState.awakened(e.getId()) && Config.crystalSkin.get() && AbyssShaders.VOID != null)
            cir.setReturnValue(Rt.voidEntity(Rt.BODY));
    }
}
