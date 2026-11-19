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
        } catch (IOException ex) {
            Abyss.LOG.error("Abyss void shader failed to load", ex);
        }
    }
    private AbyssShaders() {}
}
