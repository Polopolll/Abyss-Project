package com.andy.abyss.client.gui;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.net.C2SRealmTarget;
import com.andy.abyss.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Realm's exit: every world the server has, as a ring of portals. Hover to see it, click (or 1-9) to make it the
 * world you step out into. Scroll flips through when there are many.
 */
public class WorldsScreen extends Screen {
    private final List<ResourceLocation> worlds = new ArrayList<>();
    private int hover = -1;
    private long opened;

    public WorldsScreen() { super(Component.literal("Exit")); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        var mc = Minecraft.getInstance();
        worlds.clear();
        if (mc.getConnection() != null) for (var k : mc.getConnection().levels()) if (!k.location().equals(RiftScreen.ABYSS)) worlds.add(k.location());
        worlds.sort(Comparator.comparing(ResourceLocation::toString));
        opened = ClientState.ticks;
    }

    private float[] pos(int i) {
        int n = Math.max(1, worlds.size());
        float a = i * Mth.TWO_PI / n;
        float r = Math.min(width, height) * 0.3f;
        return new float[]{width / 2f + Mth.sin(a) * r, height / 2f - Mth.cos(a) * r};
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0040208);
        Matrix4f m = g.pose().last().pose();
        float t = (ClientState.ticks - opened + pt);
        float open = Math.min(1, t / 8f);
        float cx = width / 2f, cy = height / 2f;
        Gfx.ring(m, cx, cy, Math.min(width, height) * 0.3f - 1, Math.min(width, height) * 0.3f + 1, Gfx.argb(0.25f * open, 0xB48CFF));
        String cur = ClientState.me.getString("realmTarget");
        hover = -1;
        for (int i = 0; i < worlds.size(); i++) {
            float[] p = pos(i);
            if (Math.hypot(mx - p[0], my - p[1]) < 24) hover = i;
        }
        for (int i = 0; i < worlds.size(); i++) {
            float[] p = pos(i);
            boolean h = i == hover, sel = worlds.get(i).toString().equals(cur);
            float r = (h ? 24 : 20) * Gfx.easeOutBack(Math.min(1, Math.max(0, (t - i * 1.5f) / 8f)));
            int col = colour(worlds.get(i));
            Gfx.ring(m, p[0], p[1], 0, r, Gfx.argb(0.9f, 0x05030A));
            Gfx.ring(m, p[0], p[1], r * 0.55f, r * 0.85f, Gfx.argb(0.35f + (h ? 0.3f : 0), col));
            float spin = t * 0.06f + i;
            Gfx.arc(m, p[0], p[1], r - 2, r, spin, spin + 2.2f, Gfx.argb(0.9f, sel ? 0xFFFFFF : col));
            Gfx.arc(m, p[0], p[1], r - 2, r, spin + Mth.PI, spin + Mth.PI + 1.4f, Gfx.argb(0.7f, col));
            if (sel) Gfx.ring(m, p[0], p[1], r + 2, r + 3.5f, Gfx.argb(1, 0xFFFFFF));
            g.drawCenteredString(font, String.valueOf(i + 1), (int) p[0], (int) p[1] - 4, sel ? 0xFFFFFFFF : 0xFFC8B8E8);
            g.pose().pushPose();
            g.pose().translate(p[0], p[1] + r + 5, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawCenteredString(font, Hud.shortWorld(worlds.get(i).toString()), 0, 0, h || sel ? 0xFFFFFFFF : 0xFF9A8CB8);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy - 16, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawCenteredString(font, "EXIT INTO", 0, 0, 0xFFE8DCFF);
        g.pose().popPose();
        String name = hover >= 0 ? worlds.get(hover).toString() : cur.isEmpty() ? "where you came from" : cur;
        g.drawCenteredString(font, "§f" + name, (int) cx, (int) cy + 2, 0xFFFFFFFF);
        g.drawCenteredString(font, "§8click or 1-9 · the Realm reflects the same place in every world", (int) cx, (int) cy + 16, 0xFFFFFFFF);
    }

    private static int colour(ResourceLocation r) {
        String s = r.getPath();
        if (s.contains("nether")) return 0xFF6A3A;
        if (s.contains("end")) return 0xE8D890;
        if (s.equals("overworld")) return 0x7AD07A;
        return 0x9C8CFF;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (hover >= 0) { pick(hover); return true; }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= 49 && key <= 57 && key - 49 < worlds.size()) { pick(key - 49); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private void pick(int i) {
        Net.toServer(new C2SRealmTarget(worlds.get(i)));
        ClientState.me.putString("realmTarget", worlds.get(i).toString());
        onClose();
    }
}
