package com.andy.abyss.mixin;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.combat.Moves;
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

/**
 * Arsenal poses. Each pose is a pair of keyframes (wind-up → follow-through) for both arms and the torso, played
 * over the move's own duration with an eased swing, blended in over the first frames and out over the recovery.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;

    private PlayerModelMixin(ModelPart root) { super(root); }

    private static final float P = (float) Math.PI;

    /** {rArm x,y,z, lArm x,y,z, body y} at wind-up and at follow-through. */
    private static float[][] keys(int pose) {
        return switch (pose) {
            case Moves.P_SLASH_RL -> new float[][]{{-1.9f, -0.9f, 0.3f, -0.3f, 0, 0, -0.5f}, {-1.4f, 0.9f, 0, 0.3f, 0, -0.2f, 0.55f}};
            case Moves.P_SLASH_LR -> new float[][]{{-1.6f, 1.0f, 0, 0.2f, 0, 0, 0.5f}, {-1.4f, -1.0f, 0.4f, -0.3f, 0, 0, -0.55f}};
            case Moves.P_OVERHEAD -> new float[][]{{-3.0f, -0.1f, 0, -2.9f, 0.1f, 0, 0}, {-0.9f, -0.1f, 0, -0.8f, 0.1f, 0, 0}};
            case Moves.P_RISING -> new float[][]{{0.4f, 0.3f, 0.3f, 0.3f, 0, 0, -0.3f}, {-3.0f, 0, 0.2f, -0.4f, 0, -0.3f, 0.2f}};
            case Moves.P_THRUST -> new float[][]{{-1.2f, 0.3f, 0, 0.4f, 0, 0, -0.45f}, {-1.6f, -0.05f, 0, -0.6f, 0.3f, 0, 0.3f}};
            case Moves.P_SPIN -> new float[][]{{-1.5f, -0.6f, 0, -1.4f, 0.6f, 0, 0}, {-1.5f, -0.6f, 0, -1.4f, 0.6f, 0, 2 * P}};
            case Moves.P_SLAM -> new float[][]{{-3.1f, -0.15f, 0, -3.1f, 0.15f, 0, 0}, {-0.5f, -0.15f, 0, -0.5f, 0.15f, 0, 0}};
            case Moves.P_PUNCH_R -> new float[][]{{-0.6f, 0, 0, -1.0f, 0.4f, 0, -0.35f}, {-1.6f, -0.1f, 0, -0.4f, 0.2f, 0, 0.35f}};
            case Moves.P_PUNCH_L -> new float[][]{{-1.0f, -0.4f, 0, -0.6f, 0, 0, 0.35f}, {-0.4f, -0.2f, 0, -1.6f, 0.1f, 0, -0.35f}};
            case Moves.P_RAISE -> new float[][]{{-1.2f, 0, 0.2f, -1.2f, 0, -0.2f, 0}, {-3.0f, 0, 0.3f, -3.0f, 0, -0.3f, 0}};
            case Moves.P_BLOCK -> new float[][]{{-0.8f, -0.2f, 0, -1.5f, 0.6f, 0, 0.1f}, {-0.8f, -0.2f, 0, -1.5f, 0.6f, 0, 0.1f}};
            case Moves.P_THROW -> new float[][]{{-3.0f, 0.2f, 0.3f, -0.6f, 0, -0.4f, -0.5f}, {-1.3f, -0.3f, 0, -0.2f, 0, 0, 0.45f}};
            case Moves.P_PLUNGE -> new float[][]{{-3.0f, 0, 0, -3.0f, 0, 0, 0}, {-0.3f, 0, 0.3f, -0.3f, 0, -0.3f, 0}};
            case Moves.P_FLURRY -> new float[][]{{-1.5f, -0.8f, 0, -0.5f, 0.3f, 0, -0.35f}, {-1.5f, 0.8f, 0, -0.5f, 0.3f, 0, 0.35f}};
            case Moves.P_BASH -> new float[][]{{-0.5f, 0, 0, -1.2f, 0.5f, 0, -0.4f}, {-0.6f, 0, 0, -1.7f, 0.1f, 0, 0.35f}};
            case Moves.P_CAST -> new float[][]{{-1.4f, 0.1f, 0, -1.4f, -0.1f, 0, 0}, {-1.8f, -0.2f, 0, -1.8f, 0.2f, 0, 0}};
            case Moves.P_DRAW -> new float[][]{{-0.4f, 0.9f, 0, -0.5f, 0.6f, 0, 0.6f}, {-1.5f, -1.1f, 0.2f, -0.3f, 0, 0, -0.6f}};
            default -> null;
        };
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void abyss$pose(T e, float limb, float limbAmt, float age, float yaw, float pitch, CallbackInfo ci) {
        long[] p = ClientState.POSES.get(e.getId());
        if (p == null) return;
        float[][] k = keys((int) p[0]);
        if (k == null) return;
        float dur = p.length > 2 ? Math.max(4, p[2]) : 11;
        float pt = Minecraft.getInstance().getFrameTime();
        float t = (ClientState.ticks - p[1] + pt) / dur;
        if (t >= 1 && !(p.length > 2 && p[2] >= 100000)) return;
        // blend: fast in, held, eased out over the last quarter
        float w = t < 0.1f ? t / 0.1f : t > 0.75f ? 1 - (t - 0.75f) / 0.25f : 1;
        w = w * w * (3 - 2 * w);
        float s;
        if ((int) p[0] == Moves.P_FLURRY) s = 0.5f + 0.5f * (float) Math.sin(t * P * 6);
        else if ((int) p[0] == Moves.P_DRAW) s = t < 0.55f ? 0 : Math.min(1, (t - 0.55f) / 0.12f);
        else { float u = Math.min(1, t / 0.6f); s = 1 - (1 - u) * (1 - u) * (1 - u); }
        if (p.length > 2 && p[2] >= 100000) { w = Math.min(1, (ClientState.ticks - p[1] + pt) / 3f); s = 1; } // held (guard)
        float[] a = k[0], b = k[1];
        float aim = head.xRot * 0.5f;
        rightArm.xRot = lerp(rightArm.xRot, lerp(a[0], b[0], s) + aim, w);
        rightArm.yRot = lerp(rightArm.yRot, lerp(a[1], b[1], s), w);
        rightArm.zRot = lerp(rightArm.zRot, lerp(a[2], b[2], s), w);
        leftArm.xRot = lerp(leftArm.xRot, lerp(a[3], b[3], s) + aim, w);
        leftArm.yRot = lerp(leftArm.yRot, lerp(a[4], b[4], s), w);
        leftArm.zRot = lerp(leftArm.zRot, lerp(a[5], b[5], s), w);
        float by = lerp(a[6], b[6], s);
        body.yRot = lerp(body.yRot, by, w);
        // keep the shoulders attached to the turning torso
        float cy = (float) Math.cos(body.yRot), sy = (float) Math.sin(body.yRot);
        rightArm.x = -5 * cy; rightArm.z = 5 * sy;
        leftArm.x = 5 * cy; leftArm.z = -5 * sy;
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
    }

    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }
}
