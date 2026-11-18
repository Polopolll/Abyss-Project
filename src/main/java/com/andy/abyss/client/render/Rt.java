package com.andy.abyss.client.render;

import com.andy.abyss.Abyss;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Render types: the void (galaxy shader) for bodies, spheres and rifts; additive glow and alpha sprites for the rest. */
public final class Rt extends RenderType {
    public static final ResourceLocation BODY = Abyss.id("textures/entity/void_body.png"), CRACKS = Abyss.id("textures/entity/void_cracks.png"),
            FILL = Abyss.id("textures/fx/void_fill.png"), RIFT = Abyss.id("textures/fx/rift.png"), GLOW = Abyss.id("textures/fx/glow.png"),
            CORE = Abyss.id("textures/fx/core.png"), RING = Abyss.id("textures/fx/ring.png"), SPARK = Abyss.id("textures/fx/spark.png"),
            WHITE = Abyss.id("textures/fx/white.png"), STREAK = Abyss.id("textures/fx/streak.png"), SHARD = Abyss.id("textures/fx/shard.png"),
            TOOTH = Abyss.id("textures/fx/tooth.png"), INFECTION = Abyss.id("textures/fx/infection.png");

    private static final Map<ResourceLocation, RenderType> VOID = new HashMap<>(), VOID_FX = new HashMap<>(), ADD = new HashMap<>(), ALPHA = new HashMap<>(), ADD_TRI = new HashMap<>(), VOID_TRI = new HashMap<>();
    private static final ShaderStateShard VOID_SHADER = new ShaderStateShard(() -> AbyssShaders.VOID);

    private Rt(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a, Runnable r) { super(n, f, m, b, c, s, a, r); }

    /** Void-shaded entity model (bodies, corrosion overlay). */
    public static RenderType voidEntity(ResourceLocation tex) {
        return VOID.computeIfAbsent(tex, t -> create("abyss_void_entity_" + t.getPath(), DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                CompositeState.builder().setShaderState(VOID_SHADER).setTextureState(new TextureStateShard(t, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(true)));
    }

    /** Void-shaded world effects (domain shells, rifts, black spheres): no culling, no depth write. */
    public static RenderType voidFx(ResourceLocation tex) { return voidFx(tex, VOID_FX, VertexFormat.Mode.QUADS); }
    public static RenderType voidTri(ResourceLocation tex) { return voidFx(tex, VOID_TRI, VertexFormat.Mode.TRIANGLES); }

    private static RenderType voidFx(ResourceLocation tex, Map<ResourceLocation, RenderType> m, VertexFormat.Mode mode) {
        return m.computeIfAbsent(tex, t -> create("abyss_void_fx_" + mode.name().toLowerCase() + "_" + t.getPath(), DefaultVertexFormat.NEW_ENTITY, mode, 1 << 16, false, true,
                CompositeState.builder().setShaderState(VOID_SHADER).setTextureState(new TextureStateShard(t, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));
    }

    private static RenderType sprite(String name, ResourceLocation tex, boolean additive, VertexFormat.Mode mode) {
        return create("abyss_" + name, DefaultVertexFormat.POSITION_COLOR_TEX, mode, 1 << 16, false, !additive,
                CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER).setTextureState(new TextureStateShard(tex, false, false))
                        .setTransparencyState(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setLightmapState(NO_LIGHTMAP).createCompositeState(false));
    }

    public static RenderType add(ResourceLocation t) { return ADD.computeIfAbsent(t, x -> sprite("add_" + x.getPath(), x, true, VertexFormat.Mode.QUADS)); }
    public static RenderType alpha(ResourceLocation t) { return ALPHA.computeIfAbsent(t, x -> sprite("alpha_" + x.getPath(), x, false, VertexFormat.Mode.QUADS)); }
    public static RenderType addTri(ResourceLocation t) { return ADD_TRI.computeIfAbsent(t, x -> sprite("addtri_" + x.getPath(), x, true, VertexFormat.Mode.TRIANGLES)); }

    private static RenderType INFECT;
    /** Infection overlay on blocks: galaxy veins pressed onto the block faces (polygon offset, no depth write). */
    public static RenderType infection() {
        if (INFECT == null) INFECT = create("abyss_infection", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1 << 18, false, true,
                CompositeState.builder().setShaderState(VOID_SHADER).setTextureState(new TextureStateShard(INFECTION, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setLayeringState(POLYGON_OFFSET_LAYERING).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        return INFECT;
    }

    public static boolean isAdditive(RenderType t) { return ADD.containsValue(t) || ADD_TRI.containsValue(t); }
    public static boolean isVoid(RenderType t) { return t == INFECT || VOID_FX.containsValue(t) || VOID_TRI.containsValue(t); }
}
