package com.andy.abyss.client.render;

import com.andy.abyss.Abyss;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public final class AbyssShaders {
    public static ShaderInstance VOID;

    public static void register(RegisterShadersEvent e) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), Abyss.id("void"), DefaultVertexFormat.NEW_ENTITY), s -> VOID = s);
            e.registerShader(new ShaderInstance(e.getResourceProvider(), Abyss.id("bloom_blur"), DefaultVertexFormat.POSITION_TEX), s -> Bloom.BLUR = s);
            e.registerShader(new ShaderInstance(e.getResourceProvider(), Abyss.id("bloom_composite"), DefaultVertexFormat.POSITION_TEX), s -> Bloom.COMPOSITE = s);
        } catch (IOException ex) {
            Abyss.LOG.error("Abyss void shader failed to load", ex);
        }
    }
    private AbyssShaders() {}
}
