package com.andy.abyss.client.gui;

import com.andy.abyss.net.C2SRift;
import com.andy.abyss.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pick any dimension the server has and a place in it; open a two-way rift, or step straight through. */
public class RiftScreen extends Screen {
    private final boolean step;
    private final List<ResourceKey<Level>> dims = new ArrayList<>();
    private int dimIndex;
    private EditBox bx, by, bz;
    public static final ResourceLocation ABYSS = new ResourceLocation("abyss", "the_abyss");

    public RiftScreen(boolean step) { super(Component.literal("Rift")); this.step = step; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        var mc = Minecraft.getInstance();
        dims.clear();
        if (mc.getConnection() != null) dims.addAll(mc.getConnection().levels());
        dims.sort(Comparator.comparing(k -> k.location().toString()));
        for (int i = 0; i < dims.size(); i++) if (dims.get(i) == mc.level.dimension()) dimIndex = i;
        int cx = width / 2, y = height / 2 - 40;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> dimIndex = Math.floorMod(dimIndex - 1, Math.max(1, dims.size()))).bounds(cx - 120, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> dimIndex = Math.floorMod(dimIndex + 1, Math.max(1, dims.size()))).bounds(cx + 100, y, 20, 20).build());
        y += 30;
        bx = box(cx - 120, y, (int) mc.player.getX());
        by = box(cx - 38, y, (int) mc.player.getY());
        bz = box(cx + 44, y, (int) mc.player.getZ());
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Here"), b -> { bx.setValue("" + (int) mc.player.getX()); by.setValue("" + (int) mc.player.getY()); bz.setValue("" + (int) mc.player.getZ()); }).bounds(cx - 120, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("World spawn"), b -> { bx.setValue("0"); by.setValue("320"); bz.setValue("0"); }).bounds(cx - 38, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("My throne"), b -> {
            for (int i = 0; i < dims.size(); i++) if (dims.get(i).location().equals(ABYSS)) dimIndex = i;
            by.setValue("-9999");
        }).bounds(cx + 44, y, 76, 20).build());
        y += 30;
        addRenderableWidget(Button.builder(Component.literal(step ? "§dStep through" : "§dOpen rift"), b -> go(step)).bounds(cx - 120, y, 118, 20).build());
        addRenderableWidget(Button.builder(Component.literal(step ? "Open rift instead" : "Step through instead"), b -> go(!step)).bounds(cx + 2, y, 118, 20).build());
    }

    private EditBox box(int x, int y, int v) {
        EditBox e = new EditBox(font, x, y, 76, 20, Component.literal(""));
        e.setValue(String.valueOf(v));
        e.setFilter(s -> s.matches("-?\\d*"));
        addRenderableWidget(e);
        return e;
    }

    private void go(boolean doStep) {
        if (dims.isEmpty()) return;
        try {
            double x = Double.parseDouble(bx.getValue()) + 0.5, y = Double.parseDouble(by.getValue()), z = Double.parseDouble(bz.getValue()) + 0.5;
            Net.toServer(new C2SRift(dims.get(dimIndex).location(), x, y, z, doStep));
            onClose();
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = width / 2, y = height / 2 - 40;
        g.drawCenteredString(font, "§5ABYSSAL RIFT", cx, y - 26, 0xFFFFFFFF);
        String name = dims.isEmpty() ? "?" : dims.get(dimIndex).location().toString();
        g.drawCenteredString(font, "§f" + name, cx, y + 6, 0xFFFFFFFF);
        g.drawString(font, "§7X", cx - 120, y + 20, 0xFFFFFFFF); g.drawString(font, "§7Y", cx - 38, y + 20, 0xFFFFFFFF); g.drawString(font, "§7Z", cx + 44, y + 20, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, "§8Every dimension the server has is in reach.", cx, y + 118, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) { go(step); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
