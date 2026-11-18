package com.andy.abyss.client.gui;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.Keys;
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
 * Hold the wheel key, point at an ability, release to select it (tap instead to keep the wheel open until you click).
 * Dark glass segments in each ability's colour around a galaxy core; orbiting motes instead of clock ticks.
 */
public class WheelScreen extends Screen {
    private static final float INNER = 38, OUTER = 98, ICON_R = 68;
    private static final int N = Ability.values().length;
    private int age, hovered = -1;
    private boolean sticky;
    private final float[] hoverAnim = new float[N];
    private float lastAngle;

    public WheelScreen() { super(Component.literal("Abyss")); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        age++;
        for (int i = 0; i < N; i++) hoverAnim[i] += ((i == hovered ? 1 : 0) - hoverAnim[i]) * 0.5f;
        if (!sticky && !Keys.down(Keys.WHEEL)) {
            if (age < 4 && hovered < 0) sticky = true;
            else selectAndClose();
        }
    }

    private void selectAndClose() {
        if (hovered >= 0) {
            Net.toServer(new C2SAction(C2SAction.SELECT, hovered));
            ClientState.me.putInt("selected", hovered);
            ClientState.selectAnim = 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.0f));
        }
        onClose();
    }

    @Override public boolean mouseClicked(double mx, double my, int b) { updateHover(mx, my); selectAndClose(); return true; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Keys.WHEEL.matches(key, scan)) { selectAndClose(); return true; }
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
        for (Ability ab : Ability.values()) {
            int i = ab.ordinal();
            float h = hoverAnim[i], mid = i * seg, a0 = mid - seg / 2 + gap, a1 = mid + seg / 2 - gap;
            float inner = (INNER + h * 2) * scale, outer = (OUTER + h * 9) * scale;
            int cd = ClientState.cd(i);
            Gfx.arc(m, cx, cy, inner, outer, a0, a1, Gfx.argb((0.78f + 0.18f * h) * open, Gfx.mix(0x0A0614, ab.color, 0.14f + 0.45f * h)));
            if (cd > 0) Gfx.arc(m, cx, cy, inner, inner + (outer - inner) * Math.min(1, cd / (ab.cooldownSec * 20f)), a0, a1, Gfx.argb(0.55f * open, 0x000000));
            boolean sel = i == ClientState.selected();
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
        if (hovered >= 0) Gfx.arc(m, cx, cy, 3 * scale, (INNER - 10) * scale, lastAngle - 0.035f, lastAngle + 0.035f, Gfx.argb(open, Ability.byIndex(hovered).color));

        for (Ability ab : Ability.values()) {
            int i = ab.ordinal();
            float h = hoverAnim[i], mid = i * seg, r = (ICON_R + h * 4) * scale;
            float ix = cx + Mth.sin(mid) * r, iy = cy - Mth.cos(mid) * r;
            Gfx.icon(g, Icons.of(ab), ix, iy, (int) ((22 + h * 6) * scale), ClientState.cd(i) > 0 ? 0.45f : 1f, ab.color, h > 0.5f ? 0 : 0.35f, open);
        }
        RenderSystem_reset();
        Ability show = hovered >= 0 ? Ability.byIndex(hovered) : Ability.byIndex(ClientState.selected());
        if (show != null && open > 0.6f) {
            int i = show.ordinal();
            Component state = show == Ability.NEGATION ? Component.literal(ClientState.negation() ? "ON" : "off").withStyle(ClientState.negation() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY)
                    : ClientState.cd(i) > 0 ? Component.literal(String.format("%.1fs", ClientState.cd(i) / 20f)).withStyle(ChatFormatting.RED)
                    : Component.literal("READY").withStyle(ChatFormatting.LIGHT_PURPLE);
            g.drawCenteredString(font, show.title, (int) cx, (int) cy - 12, show.color);
            g.drawCenteredString(font, state, (int) cx, (int) cy, 0xFFFFFF);
            List<FormattedCharSequence> lines = font.split(Component.literal(show.desc), 220);
            int y = (int) (cy + (OUTER + 24) * scale);
            for (FormattedCharSequence l : lines) { g.drawCenteredString(font, l, (int) cx, y, 0xFFC8C0D8); y += 10; }
            g.drawCenteredString(font, Component.literal(sticky ? "Click to select" : "Release to select · [" + Keys.name(Keys.CAST) + "] casts").withStyle(ChatFormatting.DARK_GRAY), (int) cx, y + 4, 0xFFFFFF);
        }
    }

    private static void RenderSystem_reset() { com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1); }
}
