package com.andy.abyss.client.render;

import com.andy.abyss.client.ClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/** The abyss eating a creature: galaxy cracks spreading over its body, more of them the more stacks it has. Abyss puppets wear it too. */
public class CorrosionLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public CorrosionLayer(RenderLayerParent<T, M> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limb, float limbAmt, float pt, float age, float yaw, float pitch) {
        Integer stacks = ClientState.CORRODED.get(e.getId());
        if (stacks == null || stacks <= 0 || AbyssShaders.VOID == null || e.isInvisible()) return;
        float a = stacks >= 11 ? 0.55f : Math.min(1f, 0.25f + stacks * 0.08f);
        try {
            getParentModel().renderToBuffer(ps, buf.getBuffer(Rt.voidEntity(Rt.CRACKS)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, a);
        } catch (Exception ignored) {}
    }
}
