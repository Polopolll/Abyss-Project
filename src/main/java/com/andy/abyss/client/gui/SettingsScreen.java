package com.andy.abyss.client.gui;

import com.andy.abyss.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-game settings. Two tabs: Visuals (this client) and World (the server rules: editable in singleplayer, read-only
 * on a server). Booleans are switches; numbers are sliders (drag) with fine steps on scroll. Saved immediately.
 */
public class SettingsScreen extends Screen {
    private final Screen parent;
    private boolean world;
    private float scroll;
    private ForgeConfigSpec.ConfigValue<?> dragging;
    private final List<Map.Entry<String, ForgeConfigSpec.ConfigValue<?>>> rows = new ArrayList<>();

    public SettingsScreen(Screen parent) { super(Component.literal("Abyss — Settings")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return true; }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        rows.clear();
        for (var e : (world ? Config.ALL : Config.CLIENT).entrySet()) if (!e.getKey().equals("wheelOrder")) rows.add(e);
        scroll = 0;
    }

    private boolean editable() { return !world || Minecraft.getInstance().hasSingleplayerServer() || Minecraft.getInstance().level == null; }
    private int left() { return width / 2 - 160; }
    private int top() { return 46; }
    private int rowY(int i) { return top() + i * 18 - (int) scroll; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xE0050309);
        g.drawCenteredString(font, "§lABYSS SETTINGS", width / 2, 10, 0xFFE8DCFF);
        tab(g, width / 2 - 82, 24, "Visuals", !world, mx, my);
        tab(g, width / 2 + 2, 24, "World rules", world, mx, my);
        g.enableScissor(0, top() - 2, width, height - 22);
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (y < top() - 18 || y > height) continue;
            var e = rows.get(i);
            boolean hov = mx >= left() && mx < left() + 320 && my >= y - 2 && my < y + 14;
            if (hov) g.fill(left() - 4, y - 3, left() + 324, y + 14, 0x40A080FF);
            g.drawString(font, nice(e.getKey()), left(), y + 1, editable() ? 0xFFD8C8FF : 0xFF7A6A98, false);
            control(g, e.getValue(), left() + 190, y);
        }
        g.disableScissor();
        g.drawCenteredString(font, editable() ? "§8click switches · drag sliders · scroll a slider for fine steps · esc to close" : "§8World rules are set by the server", width / 2, height - 14, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
    }

    private void tab(GuiGraphics g, int x, int y, String s, boolean on, int mx, int my) {
        g.fill(x, y, x + 80, y + 14, on ? 0xC03A2470 : 0x60100820);
        if (on) g.fill(x, y + 13, x + 80, y + 14, 0xFFE8DCFF);
        g.drawCenteredString(font, s, x + 40, y + 3, on ? 0xFFFFFFFF : 0xFFA090C0);
    }

    private static String nice(String key) {
        StringBuilder b = new StringBuilder();
        for (char c : key.toCharArray()) { if (Character.isUpperCase(c)) b.append(' '); b.append(b.length() == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c)); }
        return b.toString();
    }

    private void control(GuiGraphics g, ForgeConfigSpec.ConfigValue<?> v, int x, int y) {
        if (v instanceof ForgeConfigSpec.BooleanValue b) {
            boolean on = b.get();
            g.fill(x, y, x + 24, y + 11, on ? 0xFF7A45E0 : 0xFF2A2238);
            g.fill(on ? x + 13 : x + 1, y + 1, on ? x + 23 : x + 11, y + 10, 0xFFF0E8FF);
            g.drawString(font, on ? "on" : "off", x + 30, y + 2, on ? 0xFFFFFFFF : 0xFF8070A0, false);
        } else if (v instanceof ForgeConfigSpec.DoubleValue || v instanceof ForgeConfigSpec.IntValue) {
            double[] r = range(v);
            double val = ((Number) v.get()).doubleValue();
            float k = (float) Mth.clamp((val - r[0]) / (r[1] - r[0]), 0, 1);
            k = (float) Math.sqrt(k); // wide ranges: more travel for small values
            g.fill(x, y + 4, x + 90, y + 7, 0xFF2A2238);
            g.fill(x, y + 4, x + (int) (90 * k), y + 7, 0xFF9C6BFF);
            g.fill(x + (int) (90 * k) - 2, y + 1, x + (int) (90 * k) + 2, y + 10, 0xFFF0E8FF);
            String s = v instanceof ForgeConfigSpec.IntValue ? String.valueOf(v.get()) : String.format("%.2f", val);
            g.drawString(font, s, x + 96, y + 2, 0xFFFFFFFF, false);
        }
    }

    private static double[] range(ForgeConfigSpec.ConfigValue<?> v) {
        try {
            var spec = v instanceof ForgeConfigSpec.IntValue || v instanceof ForgeConfigSpec.DoubleValue ? specOf(v) : null;
            if (spec != null && spec.getRange() != null) {
                Object mn = spec.getRange().getMin(), mx = spec.getRange().getMax();
                return new double[]{((Number) mn).doubleValue(), Math.min(((Number) mx).doubleValue(), ((Number) mn).doubleValue() + 1e5)};
            }
        } catch (Exception ignored) {}
        return new double[]{0, 100};
    }

    private static ForgeConfigSpec.ValueSpec specOf(ForgeConfigSpec.ConfigValue<?> v) {
        ForgeConfigSpec s = Config.CLIENT.containsValue(v) ? Config.CLIENT_SPEC : Config.SPEC;
        return s.getRaw(v.getPath());
    }

    @SuppressWarnings("unchecked")
    private void setFrom(ForgeConfigSpec.ConfigValue<?> v, double mx, int x) {
        double[] r = range(v);
        float k = Mth.clamp((float) (mx - x) / 90f, 0, 1);
        double val = r[0] + (r[1] - r[0]) * k * k;
        if (v instanceof ForgeConfigSpec.IntValue iv) iv.set((int) Math.round(val));
        else if (v instanceof ForgeConfigSpec.DoubleValue dv) dv.set(Math.round(val * 100) / 100.0);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (my >= 24 && my < 38) {
            if (mx >= width / 2 - 82 && mx < width / 2 - 2) { world = false; rebuild(); return true; }
            if (mx >= width / 2 + 2 && mx < width / 2 + 82) { world = true; rebuild(); return true; }
        }
        if (!editable()) return super.mouseClicked(mx, my, b);
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (my < y - 2 || my >= y + 14 || my < top() - 2) continue;
            var v = rows.get(i).getValue();
            int x = left() + 190;
            if (v instanceof ForgeConfigSpec.BooleanValue bv && mx >= x && mx < x + 60) { bv.set(!bv.get()); return true; }
            if (mx >= x - 3 && mx < x + 93) { dragging = v; setFrom(v, mx, x); return true; }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (dragging != null) { setFrom(dragging, mx, left() + 190); return true; }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int b) { dragging = null; return super.mouseReleased(mx, my, b); }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (editable()) for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i), x = left() + 190;
            if (my >= y - 2 && my < y + 14 && mx >= x - 3 && mx < x + 130) {
                var v = rows.get(i).getValue();
                double[] r = range(v);
                if (v instanceof ForgeConfigSpec.IntValue iv) { iv.set((int) Mth.clamp(iv.get() + Math.signum(d), r[0], r[1])); return true; }
                if (v instanceof ForgeConfigSpec.DoubleValue dv) { double step = (r[1] - r[0]) > 50 ? 1 : 0.05; dv.set(Mth.clamp(Math.round((dv.get() + Math.signum(d) * step) * 100) / 100.0, r[0], r[1])); return true; }
            }
        }
        scroll = Mth.clamp(scroll - (float) d * 18, 0, Math.max(0, rows.size() * 18 - (height - top() - 30)));
        return true;
    }

    @Override
    public void onClose() {
        Config.CLIENT_SPEC.save();
        if (editable() && Minecraft.getInstance().hasSingleplayerServer()) Config.SPEC.save();
        minecraft.setScreen(parent);
    }
}
