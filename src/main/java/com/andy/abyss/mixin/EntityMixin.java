package com.andy.abyss.mixin;

import com.andy.abyss.server.Sovereignty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Other mods can't remove the vessel as "killed". */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void abyss$noRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        Object self = this;
        if (reason == Entity.RemovalReason.KILLED && self instanceof ServerPlayer p && p.getHealth() > 0 && Sovereignty.active(p)) ci.cancel();
    }
}
