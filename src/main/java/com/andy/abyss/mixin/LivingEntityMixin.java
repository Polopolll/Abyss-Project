package com.andy.abyss.mixin;

import com.andy.abyss.server.Sovereignty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sovereignty is enforced here, under every mod: health can't be forced to 0 and die() can't be called on a living vessel. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void abyss$noZero(float hp, CallbackInfo ci) {
        Object self = this;
        if (hp <= 0 && self instanceof ServerPlayer p && p.getHealth() > 0 && Sovereignty.intercept(p)) ci.cancel();
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void abyss$noDie(DamageSource src, CallbackInfo ci) {
        Object self = this;
        if (self instanceof ServerPlayer p && p.getHealth() > 0 && Sovereignty.active(p)) ci.cancel();
    }

    /** A body unmade by the abyss leaves ash and darkness, not the white death poof. */
    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void abyss$noPoof(CallbackInfo ci) {
        if (com.andy.abyss.data.Dissolving.IDS.contains(((LivingEntity) (Object) this).getId())) ci.cancel();
    }
}
