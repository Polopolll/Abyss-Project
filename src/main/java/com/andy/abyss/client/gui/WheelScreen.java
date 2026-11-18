package com.andy.abyss.client.gui;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.Keys;
import com.andy.abyss.data.Ability;
import com.andy.abyss.net.C2SAction;
import com.andy.abyss.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Hold the wheel key, point at an ability, release to select it for the cast key. */
public class WheelScreen extends Screen {
    private int hovered = -1;
    private float open;

    public WheelScreen() { super(Component.literal("Abyss")); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        open = Math.min(1f, open + 0.25f);
        if (!Keys.down(Keys.WHEEL)) {
            if (hovered >= 0) Net.toServer(new C2SAction(C2SAction.SELECT, hovered));
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int cx = width / 2, cy = height / 2;
        float o = Math.min(1f, open + pt * 0.25f);
        float ease = 1 - (1 - o) * (1 - o);
        g.fill(0, 0, width, height, (int) (0x90 * ease) << 24 | 0x05020A);
        Ability[] all = Ability.values();
        int n = all.length;
        double dx = mx - cx, dy = my - cy;
        hovered = -1;
        if (Math.sqrt(dx * dx + dy * dy) > 30) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2;
            if (ang < 0) ang += Math.PI * 2;
            hovered = (int) Math.floor((ang + Math.PI / n) / (Math.PI * 2 / n)) % n;
        }
        float R = 104 * ease;
        for (int i = 0; i < n; i++) {
            Ability a = all[i];
            double ang = i * Math.PI * 2 / n - Math.PI / 2;
            int x = cx + (int) (Math.cos(ang) * R), y = cy + (int) (Math.sin(ang) * R);
            boolean hov = i == hovered, sel = ClientState.selected() == i;
            int s = hov ? 18 : 14;
            // a faceted crystal tile
            g.fill(x - s - 1, y - s - 1, x + s + 1, y + s + 1, hov ? 0xFFE8E0FF : 0xFF2A1A44);
            g.fill(x - s, y - s, x + s, y + s, hov ? 0xFF1A0E2E : 0xF0080510);
            g.fill(x - s, y - s, x - s + 3, y + s, a.color | 0xFF000000);
            if (sel) g.fill(x - s, y + s + 2, x + s, y + s + 4, 0xFFFFFFFF);
            String init = a.title.substring(0, 1) + (a.title.contains(":") ? a.title.substring(a.title.indexOf(':') + 2, a.title.indexOf(':') + 3) : "");
            g.drawCenteredString(font, init, x + 1, y - 4, a.color | 0xFF000000);
            int cd = ClientState.cd(i);
            if (cd > 0) g.drawCenteredString(font, "§7" + (cd + 19) / 20, x + 1, y + 6, 0xFFFFFFFF);
            if (a == Ability.NEGATION) g.drawCenteredString(font, ClientState.negation() ? "§aON" : "§8off", x + 1, y + 6, 0xFFFFFFFF);
        }
        Ability h = hovered >= 0 ? all[hovered] : Ability.byIndex(ClientState.selected());
        if (h != null) {
            g.drawCenteredString(font, h.title, cx, cy - 22, h.color | 0xFF000000);
            g.drawCenteredString(font, "§8cooldown " + h.cooldownSec + "s" + (h.charged ? " · hold to invest output" : ""), cx, cy - 11, 0xFFFFFFFF);
            List<FormattedCharSequence> lines = font.split(Component.literal(h.desc), 150);
            int y = cy + 2;
            for (FormattedCharSequence l : lines) { g.drawCenteredString(font, l, cx, y, 0xFFC8C0D8); y += 9; }
        }
        g.drawCenteredString(font, "Release to select · then [" + Keys.CAST.getTranslatedKeyMessage().getString() + "] to cast", cx, height - 24, 0xAAFFFFFF);
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        if (hovered >= 0) { Net.toServer(new C2SAction(C2SAction.SELECT, hovered)); onClose(); return true; }
        return super.mouseClicked(x, y, b);
    }
}
