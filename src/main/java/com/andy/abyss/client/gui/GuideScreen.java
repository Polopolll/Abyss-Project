package com.andy.abyss.client.gui;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.Keys;
import com.andy.abyss.combat.Move;
import com.andy.abyss.combat.Moves;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.Payload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The manual (Ronova-style). Left: chapters (the basics, every skill, every weapon). Right: the chapter's medallion,
 * what it does, and each sub-action with its mouse button. Weapon chapters list the whole move set, and the selected
 * move plays as a looping diagram of its cuts, seen from above.
 */
public class GuideScreen extends Screen {
    private final Screen parent;
    private int chapter, move;
    private long opened;
    private float scroll;
    private final List<Object> chapters = new ArrayList<>();

    public GuideScreen(Screen parent) { super(Component.literal("Abyss — Manual")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        chapters.clear();
        chapters.add("basics");
        for (Ability a : Ability.values()) if (a != Ability.GUIDE && a.enabled()) chapters.add(a);
        for (Weapon w : Weapon.values()) chapters.add(w);
        opened = ClientState.ticks;
    }

    private int listX() { return width / 2 - 200; }
    private int bodyX() { return width / 2 - 70; }
    private int top() { return height / 2 - 110; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xD0050309);
        Matrix4f m = g.pose().last().pose();
        float t = ClientState.ticks - opened + pt;
        int lx = listX(), y0 = top();
        g.fill(lx - 6, y0 - 8, lx + 120, y0 + 228, 0x60100820);
        g.fill(bodyX() - 6, y0 - 8, width / 2 + 210, y0 + 228, 0x60100820);
        g.drawString(font, "§lTHE ABYSS", lx, y0 - 22, 0xFFE8DCFF, true);
        for (int i = 0; i < chapters.size(); i++) {
            Object c = chapters.get(i);
            int y = y0 + i * 13;
            boolean on = i == chapter, hov = mx >= lx - 4 && mx < lx + 118 && my >= y - 2 && my < y + 11;
            if (on) g.fill(lx - 4, y - 2, lx + 118, y + 11, 0x603A2470);
            String name = c instanceof Ability a ? a.title : c instanceof Weapon w ? "  " + w.title : "Basics & keys";
            if (i == 1) g.drawString(font, "", lx, y, 0);
            g.drawString(font, name, lx + 2, y, on ? 0xFFFFFFFF : hov ? 0xFFD8C8FF : (c instanceof Weapon ? 0xFF8C7AB0 : 0xFFB0A0D0), false);
        }
        Object c = chapters.get(chapter);
        int bx = bodyX(), y = y0;
        if (c instanceof Ability a) ability(g, m, a, bx, y, t);
        else if (c instanceof Weapon w) weapon(g, m, w, bx, y, t, mx, my);
        else basics(g, bx, y);
        g.drawCenteredString(font, "§8click a chapter · esc to close", width / 2, y0 + 236, 0xFFFFFFFF);
        boolean sh = mx >= width / 2 + 150 && mx < width / 2 + 210 && my >= y0 - 24 && my < y0 - 12;
        g.drawString(font, (sh ? "§f" : "§d") + "⚙ Settings", width / 2 + 150, y0 - 22, 0xFFFFFFFF, false);
        super.render(g, mx, my, pt);
    }

    private int para(GuiGraphics g, String text, int x, int y, int w, int col) {
        for (FormattedCharSequence line : font.split(Component.literal(text), w)) { g.drawString(font, line, x, y, col, false); y += 10; }
        return y;
    }

    private void basics(GuiGraphics g, int x, int y) {
        g.drawString(font, "§lBASICS", x, y, 0xFFFFFFFF, false);
        y += 14;
        String[] lines = {
                "§f" + Keys.name(Keys.WHEEL) + " tap§7 — next skill.  §fhold§7 — the wheel (drag a medallion onto another to swap their places).",
                "§f" + Keys.name(Keys.WHEEL) + " + 1-8§7 — jump to that place.  §f" + Keys.name(Keys.WHEEL) + " + scroll§7 — flip through.",
                "§f" + Keys.name(Keys.CAST) + "§7 — use the selected skill. A §fForm§7 opens with " + Keys.name(Keys.CAST) + " and closes with it again; while it is open, your mouse buttons are its four sub-abilities (hints by the crosshair).",
                "§f" + Keys.name(Keys.ROSTER) + "§7 — your bodies: switch, give orders, recall.",
                "§7Output is infinite in reserve but finite in how much you can pour out at once. The Abyssal Upgrade (creative) removes that limit.",
                "§7Everything you touch with the abyss is §dinfected§7: blocks rot in stages, creatures corrode from the inside.",
                "§7Cooldowns are off by default (config)."};
        for (String l : lines) y = para(g, l, x, y, 270, 0xFFFFFFFF) + 4;
    }

    private void ability(GuiGraphics g, Matrix4f m, Ability a, int x, int y, float t) {
        float spin = t * 0.04f;
        Gfx.arc(m, x + 22, y + 22, 25, 26.5f, spin, spin + 2, Gfx.argb(0.8f, a.color));
        Gfx.icon(g, Icons.of(a), x + 22, y + 22, 44, 1, a.color, 0, 1);
        g.drawString(font, "§l" + a.title.toUpperCase(), x + 52, y + 6, 0xFF000000 | Gfx.mix(0xFFFFFF, a.color, 0.3f), false);
        g.drawString(font, "§8" + switch (a.type) { case FORM -> "FORM — " + Keys.name(Keys.CAST) + " opens / closes"; case CHARGE -> "CHARGED — hold " + Keys.name(Keys.CAST); case TOGGLE -> "TOGGLE"; default -> "INSTANT"; }, x + 52, y + 18, 0xFFFFFFFF, false);
        int yy = para(g, "§7" + a.desc, x, y + 52, 270, 0xFFFFFFFF) + 8;
        if (a.sub != null) for (int i = 0; i < 4; i++) {
            Gfx.icon(g, Icons.mouse(i), x + 7, yy + 9, 14, 1, 0xFFFFFF, 0, 1);
            Gfx.icon(g, Icons.sub(a, i), x + 24, yy + 9, 18, 1, a.color, 0, 1);
            g.drawString(font, "§f" + a.sub[i] + " §8(" + Ability.BUTTONS[i] + ")", x + 38, yy, 0xFFFFFFFF, false);
            yy = para(g, "§7" + a.subDesc[i], x + 38, yy + 10, 232, 0xFFFFFFFF) + 6;
        }
        if (a == Ability.SPORES) {
            g.drawString(font, "§fPayloads", x, yy, 0xFFFFFFFF, false);
            yy += 12;
            for (Payload p : Payload.values()) {
                Gfx.icon(g, Icons.of(p), x + 6, yy + 4, 10, 1, 0xB48CFF, 0.2f, 1);
                g.drawString(font, "§7" + p.title, x + 16, yy, 0xFFFFFFFF, false);
                yy += 11;
                if (yy > top() + 220) break;
            }
        }
    }

    private void weapon(GuiGraphics g, Matrix4f m, Weapon w, int x, int y, float t, int mx, int my) {
        Gfx.icon(g, Icons.of(w), x + 20, y + 20, 40, 1, 0xB48CFF, 0, 1);
        g.drawString(font, "§l" + w.title.toUpperCase(), x + 46, y + 6, 0xFFE8DCFF, false);
        para(g, "§7" + w.desc, x + 46, y + 18, 224, 0xFFFFFFFF);
        Moves.Set set = Moves.ALL.get(w);
        List<Move> all = new ArrayList<>(set.basic());
        String[] tag = new String[set.basic().size() + 5];
        for (int i = 0; i < set.basic().size(); i++) tag[i] = "LMB " + (i + 1);
        all.add(set.heavy()); tag[set.basic().size()] = "hold LMB";
        all.add(set.plunge()); tag[set.basic().size() + 1] = "LMB in air";
        all.add(set.counter()); tag[set.basic().size() + 2] = "after perfect dodge";
        all.add(set.skill()); tag[set.basic().size() + 3] = "M4";
        all.add(set.liberation()); tag[set.basic().size() + 4] = "M5 · full gauge";
        if (move >= all.size()) move = 0;
        int ly = y + 50;
        for (int i = 0; i < all.size(); i++) {
            int yy = ly + i * 12;
            boolean on = i == move;
            if (on) g.fill(x - 2, yy - 2, x + 150, yy + 10, 0x603A2470);
            g.drawString(font, (on ? "§f" : "§7") + all.get(i).name(), x, yy, 0xFFFFFFFF, false);
            g.pose().pushPose(); g.pose().translate(x + 148, yy + 1, 0); g.pose().scale(0.7f, 0.7f, 1);
            g.drawString(font, "§8" + tag[i], -font.width(tag[i]), 0, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
        preview(g, m, all.get(move), x + 220, y + 140, t);
    }

    /** The move from above: the caster is the dot at the centre, facing up. Cuts sweep as they do in the world. */
    private void preview(GuiGraphics g, Matrix4f m, Move mv, float cx, float cy, float t) {
        float scale = 8;
        Gfx.ring(m, cx, cy, 0, 62, Gfx.argb(0.55f, 0x05030A));
        Gfx.ring(m, cx, cy, 61, 62, Gfx.argb(0.5f, 0x5A3A9A));
        Gfx.ring(m, cx, cy, 0, 2.5f, Gfx.argb(1, 0xFFFFFF));
        Gfx.arc(m, cx, cy, 4, 5, -0.25f, 0.25f, Gfx.argb(1, 0xFFFFFF));
        int loop = mv.duration() + 30;
        float time = t % loop;
        for (Move.Fx f : mv.fx()) {
            float age = time - f.tick();
            if (age < 0) continue;
            switch (f.kind()) {
                case REND -> {
                    float life = 3 + f.f() + 8;
                    if (age > life) break;
                    float reveal = Math.min(1, age / 3f);
                    float alpha = age > 3 + f.f() ? 1 - (age - 3 - f.f()) / 8 : 1;
                    float span = Math.abs(f.b()) > 359 ? Mth.TWO_PI : (float) Math.toRadians(Math.abs(f.b()));
                    float centre = (float) Math.toRadians(f.c());
                    float sgn = Math.signum(f.b() == 0 ? 1 : f.b());
                    float a0 = centre - sgn * span / 2, a1 = a0 + sgn * span * reveal;
                    float r = Math.min(58, f.a() * scale) * (float) Math.max(0.35, Math.cos(Math.toRadians(f.d())));
                    float w = Math.max(1.2f, f.e() * scale * 0.6f);
                    Gfx.arc(m, cx, cy, r - w, r + w * 0.3f, Math.min(a0, a1), Math.max(a0, a1), Gfx.argb(0.85f * alpha, 0xB48CFF));
                    Gfx.arc(m, cx, cy, r, r + 0.8f, Math.min(a0, a1), Math.max(a0, a1), Gfx.argb(alpha, 0xFFFFFF));
                }
                case REND_LINE -> {
                    float life = 3 + f.f() + 8;
                    if (age > life) break;
                    float alpha = 1 - age / life;
                    float len = Math.min(60, f.a() * scale) * Math.min(1, age / 3f);
                    float ang = (float) Math.toRadians(f.d());
                    for (int i = 0; i < 10; i++) {
                        float k = i / 10f * len;
                        Gfx.ring(m, cx + Mth.sin(ang) * k, cy - Mth.cos(ang) * k, 0, 1.5f, Gfx.argb(alpha, 0xE8DCFF));
                    }
                }
                case SHOCK, CRATER -> {
                    float life = 18;
                    if (age > life) break;
                    float k = age / life;
                    float r = Math.min(58, f.a() * scale * k);
                    float py = cy - Math.min(55, (f.kind() == Move.Kind.CRATER ? f.c() : 1.5f) * scale);
                    Gfx.ring(m, cx, py, Math.max(0, r - 2), r, Gfx.argb(0.9f * (1 - k), 0x8C90C8));
                }
                case FIST, BARRAGE -> {
                    float life = f.kind() == Move.Kind.BARRAGE ? f.d() + 8 : 8;
                    if (age > life) break;
                    float k = age / life;
                    float py = cy - Math.min(55, (f.kind() == Move.Kind.BARRAGE ? f.c() : f.b()) * scale);
                    Gfx.ring(m, cx + f.d() * 4, py, 0, 3 + 4 * k * Math.max(1, f.a()), Gfx.argb(1 - k, 0xE8E6F4));
                }
                case REND_STORM -> {
                    float life = f.a() * 0.8f + 18;
                    if (age > life) break;
                    java.util.Random r = new java.util.Random(f.tick() * 31L);
                    for (int i = 0; i < f.a() && i * 0.8f < age; i++) {
                        float px = cx + (float) r.nextGaussian() * f.b() * scale * 0.35f, py = cy - Math.min(55, f.c() * scale) + (float) r.nextGaussian() * f.b() * scale * 0.35f;
                        float a = r.nextFloat() * Mth.TWO_PI;
                        Gfx.arc(m, px, py, 6, 7.2f, a, a + 1.4f, Gfx.argb(1 - age / life, 0xE8DCFF));
                    }
                }
                case GHOSTS, TRENCH, LUNGE -> {
                    if (age > 20) break;
                    float k = age / 20f;
                    float d = f.kind() == Move.Kind.TRENCH ? f.a() * k : 3;
                    float py = cy - Math.min(55, d * scale);
                    Gfx.ring(m, cx, py, 0, 3, Gfx.argb(1 - k, 0xD8C8FF));
                }
                default -> {}
            }
        }
        g.drawCenteredString(font, "§f" + mv.name(), (int) cx, (int) cy + 68, 0xFFFFFFFF);
        String info = "§8" + mv.hits().size() + " hit" + (mv.hits().size() == 1 ? "" : "s") + (mv.cost() > 0 ? " · " + (int) mv.cost() + " output" : "") + (mv.gauge() > 0 ? " · +" + (int) mv.gauge() + " gauge" : "");
        g.drawCenteredString(font, info, (int) cx, (int) cy + 79, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int lx = listX(), y0 = top();
        if (mx >= width / 2 + 150 && mx < width / 2 + 210 && my >= y0 - 24 && my < y0 - 12) { minecraft.setScreen(new SettingsScreen(this)); return true; }
        for (int i = 0; i < chapters.size(); i++) {
            int y = y0 + i * 13;
            if (mx >= lx - 4 && mx < lx + 118 && my >= y - 2 && my < y + 11) { chapter = i; move = 0; opened = ClientState.ticks; return true; }
        }
        if (chapters.get(chapter) instanceof Weapon) {
            int x = bodyX(), ly = y0 + 50;
            if (mx >= x - 2 && mx < x + 150 && my >= ly - 2) { int i = (int) ((my - ly + 2) / 12); if (i >= 0 && i < 13) { move = i; opened = ClientState.ticks; return true; } }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        chapter = Mth.clamp(chapter - (int) Math.signum(d), 0, chapters.size() - 1);
        move = 0;
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
