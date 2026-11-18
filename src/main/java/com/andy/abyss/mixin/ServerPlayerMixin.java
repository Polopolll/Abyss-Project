package com.andy.abyss.mixin;

import com.andy.abyss.server.Sovereignty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void abyss$noDie(DamageSource src, CallbackInfo ci) {
        ServerPlayer p = (ServerPlayer) (Object) this;
        if (p.getHealth() > 0 && Sovereignty.active(p)) ci.cancel();
    }
}
