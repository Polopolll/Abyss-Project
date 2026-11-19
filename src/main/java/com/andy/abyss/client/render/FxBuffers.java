package com.andy.abyss.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.*;

/** One builder per render type; void first, then alpha, then additive glow on top. */
public final class FxBuffers implements MultiBufferSource {
    private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        BufferBuilder b = builders.computeIfAbsent(type, t -> new BufferBuilder(1 << 14));
        if (!b.building()) b.begin(type.mode(), type.format());
        return b;
    }

    public void endAll() {
        List<Map.Entry<RenderType, BufferBuilder>> v = new ArrayList<>(), a = new ArrayList<>(), g = new ArrayList<>();
        for (var e : builders.entrySet()) (Rt.isVoid(e.getKey()) ? v : Rt.isAdditive(e.getKey()) ? g : a).add(e);
        for (var e : v) end(e);
        for (var e : a) end(e);
        for (var e : g) end(e);
    }

    private static void end(Map.Entry<RenderType, BufferBuilder> e) {
        if (e.getValue().building()) e.getKey().end(e.getValue(), RenderSystem.getVertexSorting());
    }
}
