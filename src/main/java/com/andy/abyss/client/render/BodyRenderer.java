package com.andy.abyss.client.render;

import com.andy.abyss.Config;
import com.andy.abyss.entity.AbyssBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** Clones and the main body: a player model made of the void (or the owner's own skin, if crystalSkin is off). */
public class BodyRenderer extends HumanoidMobRenderer<AbyssBody, PlayerModel<AbyssBody>> {
    public BodyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.4f);
        addLayer(new CorrosionLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssBody b) {
        if (Config.crystalSkin.get()) return Rt.BODY;
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null && b.ownerId() != null) {
            PlayerInfo info = conn.getPlayerInfo(b.ownerId());
            if (info != null) return info.getSkinLocation();
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    protected RenderType getRenderType(AbyssBody b, boolean visible, boolean translucent, boolean glowing) {
        if (Config.crystalSkin.get() && AbyssShaders.VOID != null) return Rt.voidEntity(Rt.BODY);
        return super.getRenderType(b, visible, translucent, glowing);
    }
}
