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

    public void endAll() { endBase(); endGlow(); }

    /** The solid part: void geometry, then alpha sprites (smoke, ash, flecks). */
    public void endBase() {
        for (var e : builders.entrySet()) if (Rt.isVoid(e.getKey())) end(e);
        for (var e : builders.entrySet()) if (!Rt.isVoid(e.getKey()) && !Rt.isAdditive(e.getKey())) end(e);
    }

    /** The light: additive glows (drawn into the bloom target when glow is on). */
    public void endGlow() {
        for (var e : builders.entrySet()) if (Rt.isAdditive(e.getKey())) end(e);
    }

    private static void end(Map.Entry<RenderType, BufferBuilder> e) {
        if (e.getValue().building()) e.getKey().end(e.getValue(), RenderSystem.getVertexSorting());
    }

    /**
     * If something throws mid-frame, builders may still be open; close them out (discarding the half-built geometry)
     * so the next frame starts clean instead of piling vertices into the same builder.
     */
    public void discardOpen() {
        for (BufferBuilder b : builders.values()) if (b.building()) { b.end().release(); }
    }
}
