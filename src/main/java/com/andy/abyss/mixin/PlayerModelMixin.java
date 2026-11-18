package com.andy.abyss.mixin;

import com.andy.abyss.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Combo poses: jab, slash, blink, lances, event horizon, beam, plunge. Eased in and out over ~10 ticks. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;

    private PlayerModelMixin(ModelPart root) { super(root); }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void abyss$pose(T e, float limb, float limbAmt, float age, float yaw, float pitch, CallbackInfo ci) {
        long[] p = ClientState.POSES.get(e.getId());
        if (p == null) return;
        float pt = Minecraft.getInstance().getFrameTime();
        float t = (ClientState.ticks - p[1] + pt) / 11f;
        if (t >= 1) return;
        float k = t < 0.25f ? t / 0.25f : 1 - (t - 0.25f) / 0.75f;
        k = k * k * (3 - 2 * k);
        float PI = (float) Math.PI;
        switch ((int) p[0]) {
            case 1 -> { rightArm.xRot = lerp(rightArm.xRot, -PI / 2 + head.xRot, k); rightArm.yRot = lerp(rightArm.yRot, -0.1f, k); body.yRot = lerp(body.yRot, 0.35f, k); }
            case 2 -> { float s = t * 2.2f - 1.1f; rightArm.xRot = lerp(rightArm.xRot, -PI / 2, k); rightArm.yRot = lerp(rightArm.yRot, s, k); body.yRot = lerp(body.yRot, s * 0.4f, k); }
            case 3 -> { rightArm.xRot = lerp(rightArm.xRot, 0.6f, k); leftArm.xRot = lerp(leftArm.xRot, 0.6f, k); body.xRot = lerp(body.xRot, 0.35f, k); }
            case 4 -> { rightArm.xRot = lerp(rightArm.xRot, -PI * 0.95f, k); leftArm.xRot = lerp(leftArm.xRot, -PI * 0.95f, k); rightArm.zRot = lerp(rightArm.zRot, 0.3f, k); leftArm.zRot = lerp(leftArm.zRot, -0.3f, k); }
            case 5 -> { rightArm.xRot = lerp(rightArm.xRot, -PI / 2 + head.xRot, k); leftArm.xRot = lerp(leftArm.xRot, -PI / 2 + head.xRot, k); rightArm.yRot = lerp(rightArm.yRot, -0.25f, k); leftArm.yRot = lerp(leftArm.yRot, 0.25f, k); }
            case 6 -> { rightArm.xRot = lerp(rightArm.xRot, -PI / 2 + head.xRot, k); rightArm.yRot = lerp(rightArm.yRot, head.yRot, k); leftArm.xRot = lerp(leftArm.xRot, -PI / 2.6f + head.xRot, k); leftArm.yRot = lerp(leftArm.yRot, 0.5f, k); }
            case 7 -> { rightArm.xRot = lerp(rightArm.xRot, 0.4f, k); leftArm.xRot = lerp(leftArm.xRot, 0.4f, k); rightArm.zRot = lerp(rightArm.zRot, 1.1f, k); leftArm.zRot = lerp(leftArm.zRot, -1.1f, k); }
            default -> { return; }
        }
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
    }

    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }
}
