package com.andy.abyss.client.gui;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.Keys;
import com.andy.abyss.client.WheelOrder;
import com.andy.abyss.data.Ability;
import com.andy.abyss.net.C2SAction;
import com.andy.abyss.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Hold the wheel key, point at a skill, release to select it (tap instead to keep the wheel open until you click).
 * Drag a medallion onto another to swap their places (the order is yours, saved, and is what X + 1-9 and X tap use).
 * Dark glass segments in each skill's colour around a galaxy core; orbiting motes instead of clock ticks.
 */
public class WheelScreen extends Screen {
    private static final float INNER = 38, OUTER = 98, ICON_R = 68;
    private int N = WheelOrder.get().size();
    private int age, hovered = -1;
    private boolean sticky;
    private final float[] hoverAnim = new float[N];
    private float lastAngle;
    /** Slot being dragged (-1: none) and whether the mouse actually moved. */
    private int dragFrom = -1;
    private boolean dragged;
    private double downX, downY;
    private List<Ability> order = WheelOrder.get();
    private Ability at(int slot) { return slot >= 0 && slot < order.size() ? order.get(slot) : null; }

    public WheelScreen() { super(Component.literal("Abyss")); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        age++;
        for (int i = 0; i < N; i++) hoverAnim[i] += ((i == hovered ? 1 : 0) - hoverAnim[i]) * 0.5f;
        // released = the key-up event arrived, or the raw key has read "up" for two ticks in a row
        // (key-repeat while holding X must never count as a new press)
        upTicks = Keys.down(Keys.WHEEL) ? 0 : upTicks + 1;
        if (!sticky && dragFrom < 0 && (released || upTicks >= 2)) {
            if (age < 4 && hovered < 0) sticky = true;   // a quick tap: keep it open, click to choose
            else selectAndClose();                     // hold, point, let go: choose what you pointed at
        }
    }

    private boolean released;
    private int upTicks;

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (Keys.WHEEL.matches(key, scan)) { released = true; return true; }
        return super.keyReleased(key, scan, mods);
    }

    private void selectAndClose() {
        Ability a = at(hovered);
        if (a != null) {
            Net.toServer(new C2SAction(C2SAction.SELECT, a.ordinal()));
            ClientState.me.putInt("selected", a.ordinal());
            ClientState.selectAnim = 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.0f));
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        updateHover(mx, my);
        if (b == 0 && hovered >= 0) { dragFrom = hovered; dragged = false; downX = mx; downY = my; sticky = true; return true; }
        selectAndClose();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (dragFrom >= 0 && Math.hypot(mx - downX, my - downY) > 6) dragged = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (dragFrom < 0) return super.mouseReleased(mx, my, b);
        updateHover(mx, my);
        int from = dragFrom;
        dragFrom = -1;
        if (dragged && hovered >= 0 && hovered != from) {
            WheelOrder.swap(from, hovered);
            order = WheelOrder.get();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_PLACE, 1.2f, 0.8f));
            return true;
        }
        if (!dragged) selectAndClose();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Keys.WHEEL.matches(key, scan)) {
            // only a fresh press after letting go closes it (holding X sends repeats; those are ignored)
            if (sticky && released) selectAndClose();
            return true;
        }
        // 1-9 pick that place on the wheel directly
        if (key >= 49 && key <= 57) {
            int i = key - 49;
            if (at(i) != null) { hovered = i; selectAndClose(); }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void updateHover(double mx, double my) {
        float dx = (float) (mx - width / 2.0), dy = (float) (my - height / 2.0);
        int prev = hovered;
        if (Mth.sqrt(dx * dx + dy * dy) < INNER * 0.45f) hovered = -1;
        else {
            float ang = (float) Math.atan2(dx, -dy);
            if (ang < 0) ang += Mth.TWO_PI;
            lastAngle = ang;
            hovered = Math.round(ang / (Mth.TWO_PI / N)) % N;
        }
        if (hovered != prev && hovered >= 0)
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f, 0.2f));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        updateHover(mouseX, mouseY);
        float open = Math.min(1f, (age + pt) / 5f), scale = Gfx.easeOutBack(open);
        float cx = width / 2f, cy = height / 2f;
        g.fillGradient(0, 0, width, height, Gfx.argb(0.4f * open, 0x05020C), Gfx.argb(0.65f * open, 0x05020C));
        Matrix4f m = g.pose().last().pose();
        float time = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + pt;
        float seg = Mth.TWO_PI / N, gap = 0.035f;

        // orbit: a faint ring with motes drifting round it, like spores
        Gfx.ring(m, cx, cy, (OUTER + 12) * scale, (OUTER + 13) * scale, Gfx.argb(0.3f * open, 0xB8A0FF));
        for (int i = 0; i < 24; i++) {
            float a = i * Mth.TWO_PI / 24 + time * 0.006f * (i % 2 == 0 ? 1 : -1.4f);
            float r = (OUTER + 12.5f + Mth.sin(time * 0.05f + i) * 2.5f) * scale;
            Gfx.arc(m, cx, cy, r - 1.4f, r + 1.4f, a - 0.012f, a + 0.012f, Gfx.argb(0.8f * open, i % 3 == 0 ? 0xFFFFFF : 0x9B6BFF));
        }
        for (int i = 0; i < N; i++) {
            Ability ab = at(i);
            float h = hoverAnim[i], mid = i * seg, a0 = mid - seg / 2 + gap, a1 = mid + seg / 2 - gap;
            float inner = (INNER + h * 2) * scale, outer = (OUTER + h * 9) * scale;
            int cd = ClientState.cd(ab.ordinal());
            boolean dropTarget = dragFrom >= 0 && dragged && i == hovered && i != dragFrom;
            Gfx.arc(m, cx, cy, inner, outer, a0, a1, Gfx.argb((0.78f + 0.18f * h) * open, Gfx.mix(0x0A0614, dropTarget ? 0xFFFFFF : ab.color, dropTarget ? 0.25f : 0.14f + 0.45f * h)));
            if (cd > 0) Gfx.arc(m, cx, cy, inner, inner + (outer - inner) * Math.min(1, cd / (ab.cooldownSec * 20f)), a0, a1, Gfx.argb(0.55f * open, 0x000000));
            boolean sel = ab.ordinal() == ClientState.selected() || ab.ordinal() == ClientState.form();
            float rim = 1.5f + h * 2 + (sel ? 1.5f : 0);
            Gfx.arc(m, cx, cy, outer - rim, outer, a0, a1, Gfx.argb((0.55f + 0.45f * Math.max(h, sel ? 1 : 0)) * open, ab.color));
            Gfx.arc(m, cx, cy, inner, inner + 1, a0, a1, Gfx.argb(0.35f * open, ab.color));
            if (ab == Ability.NEGATION && ClientState.negation()) {
                float pulse = 0.5f + 0.5f * Mth.sin(time * 0.3f);
                Gfx.arc(m, cx, cy, outer + 2, outer + 4 + pulse * 2, a0, a1, Gfx.argb(0.8f * open, ab.color));
            }
        }
        // the core: a little singularity with a hand pointing at the mouse
        Gfx.ring(m, cx, cy, 0, (INNER - 6) * scale, Gfx.argb(0.92f * open, 0x05030A));
        Gfx.ring(m, cx, cy, (INNER - 7) * scale, (INNER - 6) * scale, Gfx.argb(0.7f * open, 0xB8A0FF));
        Gfx.ring(m, cx, cy, (INNER - 15) * scale, (INNER - 14.3f) * scale, Gfx.argb(0.4f * open, 0x9B6BFF));
        if (hovered >= 0) Gfx.arc(m, cx, cy, 3 * scale, (INNER - 10) * scale, lastAngle - 0.035f, lastAngle + 0.035f, Gfx.argb(open, at(hovered).color));

        for (int i = 0; i < N; i++) {
            Ability ab = at(i);
            if (i == dragFrom && dragged) continue;
            float h = hoverAnim[i], mid = i * seg, r = (ICON_R + h * 4) * scale;
            float ix = cx + Mth.sin(mid) * r, iy = cy - Mth.cos(mid) * r;
            Gfx.icon(g, Icons.of(ab), ix, iy, (int) ((34 + h * 8) * scale), ClientState.cd(ab.ordinal()) > 0 ? 0.45f : 1f, ab.color, 0, open);
            float nr = (OUTER - 8) * scale;
            g.pose().pushPose();
            g.pose().translate(cx + Mth.sin(mid) * nr, cy - Mth.cos(mid) * nr - 3, 0);
            g.pose().scale(0.7f, 0.7f, 1);
            g.drawCenteredString(font, String.valueOf(i + 1), 0, 0, Gfx.argb(0.6f + 0.4f * h, 0xD8C8FF));
            g.pose().popPose();
        }
        if (dragFrom >= 0 && dragged) Gfx.icon(g, Icons.of(at(dragFrom)), mouseX, mouseY, 40, 1, 0xFFFFFF, 0, 0.9f);
        RenderSystem_reset();
        Ability show = hovered >= 0 ? at(hovered) : Ability.byIndex(ClientState.selected());
        if (show != null && open > 0.6f) {
            int i = show.ordinal();
            Component state = show == Ability.NEGATION ? Component.literal(ClientState.negation() ? "ON" : "off").withStyle(ClientState.negation() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY)
                    : ClientState.cd(i) > 0 ? Component.literal(String.format("%.1fs", ClientState.cd(i) / 20f)).withStyle(ChatFormatting.RED)
                    : Component.literal("READY").withStyle(ChatFormatting.LIGHT_PURPLE);
            g.drawCenteredString(font, show.title, (int) cx, (int) cy - 12, show.color);
            g.drawCenteredString(font, state, (int) cx, (int) cy, 0xFFFFFF);
            // the details sit beside the wheel (never under it, where the hotbar is)
            int px = (int) (cx + OUTER * scale + 26), pw = Math.min(230, width - px - 8);
            boolean side = pw >= 150;
            if (!side) { px = (int) cx - 115; pw = 230; }
            List<FormattedCharSequence> lines = font.split(Component.literal(show.desc), pw);
            int boxH = lines.size() * 10 + (show.sub != null ? 4 * 15 + 6 : 0) + 16;
            int y = side ? (int) cy - boxH / 2 : (int) (cy + (OUTER + 16) * scale);
            g.fill(px - 6, y - 6, px + pw + 6, y + boxH, 0xA0080412);
            g.fill(px - 6, y - 6, px - 5, y + boxH, 0xFF000000 | show.color);
            g.drawString(font, show.title, px, y, 0xFF000000 | show.color, false);
            y += 12;
            for (FormattedCharSequence l : lines) { g.drawString(font, l, px, y, 0xFFC8C0D8, false); y += 10; }
            if (show.sub != null) {
                y += 4;
                for (int k = 0; k < 4; k++) {
                    int yy = y + k * 15;
                    Gfx.icon(g, Icons.mouse(k), px + 6, yy + 6, 12, 1, 0xFFFFFF, 0, 1);
                    Gfx.icon(g, Icons.sub(show, k), px + 20, yy + 6, 14, 1, show.color, 0, 1);
                    g.drawString(font, show.sub[k], px + 30, yy + 2, 0xFFE0D4FF, false);
                }
                RenderSystem_reset();
                y += 4 * 15 + 2;
            }
            g.drawCenteredString(font, Component.literal(sticky ? "Click to select · drag to swap · 1-9 to pick" : "Release to select · drag to swap · 1-9 to pick").withStyle(ChatFormatting.DARK_GRAY), (int) cx, (int) (cy + (OUTER + 12) * scale), 0xFFFFFF);
        }
    }

    private static void RenderSystem_reset() { com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1); }
}
