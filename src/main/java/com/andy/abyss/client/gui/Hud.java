package com.andy.abyss.client.gui;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientEvents;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.Keys;
import com.andy.abyss.client.SporeClient;
import com.andy.abyss.client.render.FxManager;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.Payload;
import com.andy.abyss.net.S2CCp;
import com.andy.abyss.net.S2CWorld;
import com.andy.abyss.server.Combat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Right of the hotbar: a medallion for the selected ability, a row of every ability with cooldown sweeps, the output
 * bar. Above it: bodies. Right edge: the combo chain in combat mode. Around the crosshair: Domain Open/Closed while
 * charging, the payload ring in the Spore Lens, Angel Eyes.
 */
public final class Hud {
    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font f = mc.font;
        float time = (mc.level != null ? mc.level.getGameTime() : 0) + pt;
        overlays(g, w, h, pt);
        clash(g, f, w, h);
        angelEyes(g, f, w, h);
        if (!ClientState.awakened() || mc.player.isSpectator()) return;
        Matrix4f m = g.pose().last().pose();
        int x0 = w / 2 + 91 + 8;

        // medallion
        Ability sel = Ability.byIndex(ClientState.selected());
        if (sel == null) sel = Ability.DOMAIN;
        ClientState.selectAnim++;
        float pop = ClientState.selectAnim < 8 ? 1 + 0.25f * (1 - (ClientState.selectAnim + pt) / 8f) : 1;
        float mcx = x0 + 12, mcy = h - 12, rad = 11.5f * pop;
        Gfx.ring(m, mcx, mcy, 0, rad, Gfx.argb(0.85f, 0x05030A));
        int cd = ClientState.cd(sel.ordinal());
        if (cd > 0) Gfx.arc(m, mcx, mcy, 0, rad, 0, Mth.TWO_PI * Math.min(1, cd / (sel.cooldownSec * 20f)), Gfx.argb(0.6f, 0x000000));
        Gfx.ring(m, mcx, mcy, rad - 1.5f, rad, Gfx.argb(0.95f, sel.color));
        float orb = time * 0.06f;
        Gfx.arc(m, mcx, mcy, rad + 0.6f, rad + 2f, orb, orb + 0.5f, Gfx.argb(0.8f, 0xFFFFFF));
        if (ClientState.me.getInt("chargeAbility") >= 0) {
            float c = Math.min(1, ClientState.me.getFloat("charge") / (ClientState.infinite() ? 2000f : 600f));
            Gfx.arc(m, mcx, mcy, rad + 2.5f, rad + 4.5f, 0, Mth.TWO_PI * c, Gfx.argb(0.95f, 0xFFFFFF));
        }
        Gfx.icon(g, Icons.of(sel), mcx, mcy, (int) (15 * pop), cd > 0 ? 0.5f : 1f, sel.color, 0.25f, 1);

        // every ability, small
        int qx = x0 + 28, qy = h - 22;
        for (Ability a : Ability.values()) {
            int i = a.ordinal();
            float sx = qx + i * 13 + 6, sy = qy + 5;
            Gfx.ring(m, sx, sy, 0, 6f, Gfx.argb(0.7f, 0x05030A));
            int c = ClientState.cd(i);
            if (c > 0) Gfx.arc(m, sx, sy, 0, 6f, 0, Mth.TWO_PI * Math.min(1, c / (a.cooldownSec * 20f)), Gfx.argb(0.6f, 0x000000));
            boolean on = a == Ability.NEGATION ? ClientState.negation() : i == ClientState.selected();
            Gfx.ring(m, sx, sy, 5f, 6f, Gfx.argb(on ? 0.95f : 0.35f, a.color));
        }
        for (Ability a : Ability.values()) {
            int i = a.ordinal();
            Gfx.icon(g, Icons.of(a), qx + i * 13 + 6, qy + 5, 9, ClientState.cd(i) > 0 ? 0.4f : 0.95f, a.color, 0.4f, 1);
        }

        // output
        boolean inf = ClientState.infinite();
        float frac = inf ? 1 : Mth.clamp(ClientState.output() / ClientState.cap(), 0, 1);
        int bx = qx, by = h - 8, bw = 128, bh = 4;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xC0050309);
        int fw = Math.round(bw * frac);
        if (fw > 0) {
            g.fillGradient(bx, by, bx + fw, by + bh, inf ? 0xFFFFFFFF : 0xFFB48CFF, inf ? 0xFFC8B0FF : 0xFF5A2AA8);
            int sh = (int) ((time * 1.6f) % (bw + 30)) - 15;
            if (sh > 0 && sh < fw) g.fill(bx + Math.max(0, sh - 3), by, bx + Math.min(fw, sh + 3), by + bh, 0x70FFFFFF);
        }
        for (int i = 1; i < 4; i++) g.fill(bx + bw * i / 4, by, bx + bw * i / 4 + 1, by + bh, 0x50000000);
        String out = inf ? "∞" : String.valueOf((int) ClientState.output());
        g.pose().pushPose();
        g.pose().translate(bx + bw + 4, by - 1, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(f, out, 0, 0, 0xD8C8FF, true);
        g.pose().popPose();

        // a little status line above: spores, mode, combat
        String[] modes = {"SPREAD", "PLANT", "TAG"};
        String status = "§d" + ClientState.me.getInt("spores") + " §8spores · §5" + modes[Mth.clamp(ClientState.sporeMode(), 0, 2)]
                + (ClientState.combat() ? " §8· §fCOMBAT" : "") + (ClientState.me.getInt("negDown") > 0 ? " §8· §cNEGATION BROKEN" : "");
        g.pose().pushPose();
        g.pose().translate(qx, qy - 9, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(f, status, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();

        bodies(g, f, m, w, h, x0);
        combo(g, f, m, w, h, time);
        domainChooser(g, f, m, w, h);
        if (SporeClient.lens) lens(g, f, m, w, h);
    }

    private static void bodies(GuiGraphics g, Font f, Matrix4f m, int w, int h, int x0) {
        var list = ClientState.bodies();
        if (list.size() <= 1 && ClientState.me.getBoolean("inMain")) return;
        int i = 0;
        for (Tag t : list) {
            CompoundTag b = (CompoundTag) t;
            boolean you = b.getBoolean("you"), main = b.getBoolean("main");
            float cx = x0 + 34 + i * 16, cy = h - 40;
            float hp = b.getFloat("hp") / Math.max(1, b.getFloat("max"));
            Gfx.ring(m, cx, cy, 0, 6.5f, Gfx.argb(0.8f, you ? 0x2A1A44 : 0x05030A));
            Gfx.arc(m, cx, cy, 5.2f, 6.5f, 0, Mth.TWO_PI * hp, Gfx.argb(0.95f, you ? 0xFFFFFF : 0x9B6BFF));
            g.drawCenteredString(f, main ? "◆" : String.valueOf(b.getInt("slot")), (int) cx, (int) cy - 4, you ? 0xFFFFFFFF : 0xFFB8A0FF);
            if (++i >= 6) break;
        }
    }

    /** Wuthering-Waves-style chain: five pips that light up, the move name and a fading hit counter. */
    private static void combo(GuiGraphics g, Font f, Matrix4f m, int w, int h, float time) {
        if (!ClientState.combat()) return;
        long since = ClientState.ticks - ClientState.comboAt;
        int step = since < 26 ? ClientState.comboStep : 0;
        int x = w - 92, y = h / 2 + 20;
        for (int i = 1; i <= 5; i++) {
            float cx = x + i * 14, cy = y;
            boolean lit = i <= step;
            Gfx.arc(m, cx, cy, 0, lit ? 5.5f : 4f, Mth.PI * 0.25f, Mth.PI * 2.25f, Gfx.argb(lit ? 0.95f : 0.35f, lit ? (i == 5 ? 0xFFFFFF : 0xB48CFF) : 0x3A2A5A));
        }
        if (step > 0 && since < 26) {
            float a = 1 - since / 26f;
            String name = step < Combat.NAMES.length ? Combat.NAMES[step] : "";
            g.pose().pushPose();
            g.pose().translate(x + 84, y - 22, 0);
            float s = 1.2f + (since < 4 ? (4 - since) * 0.08f : 0);
            g.pose().scale(s, s, 1);
            g.drawString(f, name, -f.width(name), 0, Gfx.argb(a, step == 5 ? 0xFFFFFF : 0xD8C0FF), true);
            g.pose().popPose();
        }
        if (ClientState.heavyHeld > 8) {
            float c = Math.min(1, (ClientState.heavyHeld - 8) / 30f);
            Gfx.arc(m, w / 2f, h / 2f, 12, 14, 0, Mth.TWO_PI * c, Gfx.argb(0.9f, 0xFFFFFF));
            g.drawCenteredString(f, "§fABYSSAL BEAM", w / 2, h / 2 + 18, 0xFFFFFFFF);
        }
        g.pose().pushPose();
        g.pose().translate(x + 14, y + 9, 0);
        g.pose().scale(0.7f, 0.7f, 1);
        g.drawString(f, "§8click chain · hold: beam · sprint: lunge · air: fold", 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static void domainChooser(GuiGraphics g, Font f, Matrix4f m, int w, int h) {
        if (ClientState.me.getInt("chargeAbility") != Ability.DOMAIN.ordinal()) return;
        float cx = w / 2f, cy = h / 2f;
        boolean closed = ClientState.domainVariant == 1;
        Gfx.arc(m, cx, cy, 18, 26, -Mth.HALF_PI - 0.9f, -Mth.HALF_PI + 0.9f, Gfx.argb(closed ? 0.3f : 0.9f, 0x9B6BFF));
        Gfx.arc(m, cx, cy, 18, 26, Mth.HALF_PI - 0.9f, Mth.HALF_PI + 0.9f, Gfx.argb(closed ? 0.9f : 0.3f, 0xC0A0FF));
        g.drawString(f, (closed ? "§7" : "§f") + "OPEN", (int) cx - 30 - f.width("OPEN"), (int) cy - 4, 0xFFFFFFFF, true);
        g.drawString(f, (closed ? "§f" : "§7") + "CLOSED", (int) cx + 30, (int) cy - 4, 0xFFFFFFFF, true);
        float charge = ClientState.me.getFloat("charge");
        float radius = closed ? Math.min(Config.closedMaxRadius.get().floatValue(), 8 + charge / 8f) : Math.min(Config.domainMaxRadius.get().floatValue(), 12 + charge / 2f);
        g.drawCenteredString(f, String.format(Locale.ROOT, "§dradius %.0f  §8scroll / right-click to switch", radius), (int) cx, (int) cy + 30, 0xFFFFFFFF);
    }

    private static void lens(GuiGraphics g, Font f, Matrix4f m, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        int n = Payload.values().length;
        float seg = Mth.TWO_PI / n;
        for (Payload p : Payload.values()) {
            int i = p.ordinal();
            boolean on = i == ClientState.payload;
            float mid = i * seg;
            Gfx.arc(m, cx, cy, on ? 26 : 28, on ? 40 : 37, mid - seg / 2 + 0.05f, mid + seg / 2 - 0.05f, Gfx.argb(on ? 0.85f : 0.45f, on ? 0x2A1A44 : 0x05030A));
            if (on) Gfx.arc(m, cx, cy, 39, 40.5f, mid - seg / 2 + 0.05f, mid + seg / 2 - 0.05f, Gfx.argb(1, 0xFFFFFF));
        }
        for (Payload p : Payload.values()) {
            int i = p.ordinal();
            float mid = i * seg;
            Gfx.icon(g, Icons.of(p), cx + Mth.sin(mid) * 33, cy - Mth.cos(mid) * 33, i == ClientState.payload ? 12 : 9, i == ClientState.payload ? 1 : 0.6f, 0xB48CFF, 0.2f, 1);
        }
        Payload cur = Payload.byIndex(ClientState.payload);
        int selected = SporeClient.SELECTED.size() + SporeClient.SELECTED_HOSTS.size();
        g.drawCenteredString(f, "§f" + cur.title + " §8(" + cur.cost + ")", (int) cx, (int) cy + 46, 0xFFFFFFFF);
        g.drawCenteredString(f, "§7" + (selected > 0 ? selected + " selected" : "aim at a spore or host") + " §8· scroll payload · click select · shift-click sweep · §fright-click fire", (int) cx, (int) cy + 57, 0xFFFFFFFF);
    }

    private static void angelEyes(GuiGraphics g, Font f, int w, int h) {
        if (!Config.angelEyesHud.get() || !ClientState.awakened()) return;
        Entity e = ClientEvents.looked;
        if (e == null) return;
        S2CCp c = ClientState.CP.get(e.getId());
        if (c == null) return;
        int mine = ClientState.me.getInt("cp");
        int x = w / 2 + 16, y = h / 2 + 8;
        int col = c.cp() > mine * 2 ? 0xFF6060 : c.cp() > mine ? 0xFFC060 : 0xB0FFB0;
        String name = e.getDisplayName().getString();
        String cp = String.format(Locale.ROOT, "CP %,d", c.cp());
        String stats = String.format(Locale.ROOT, "%.0f/%.0f HP · %.0f AR · %.1f ATK", c.hp(), c.maxHp(), c.armor(), c.damage());
        int bw = Math.max(f.width(name) + f.width(cp) + 8, (int) (f.width(stats) * 0.75f)) + 6;
        g.fill(x - 3, y - 3, x + bw, y + 22 + (c.spores() > 0 ? 8 : 0), 0x90050309);
        g.fill(x - 3, y - 3, x - 2, y + 22 + (c.spores() > 0 ? 8 : 0), 0xFF000000 | col);
        g.drawString(f, name, x, y, 0xFFE8E0FF, true);
        g.drawString(f, cp, x + bw - 4 - f.width(cp), y, 0xFF000000 | col, true);
        g.pose().pushPose();
        g.pose().translate(x, y + 11, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(f, "§7" + stats, 0, 0, 0xFFFFFFFF, true);
        if (c.spores() > 0) g.drawString(f, "§d" + c.spores() + " spores inside", 0, 11, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static void clash(GuiGraphics g, Font f, int w, int h) {
        S2CWorld.Clash c = ClientState.myClash();
        if (c == null) return;
        Matrix4f m = g.pose().last().pose();
        float cx = w / 2f, cy = h / 2f - 52;
        int bw = 220, x = (int) cx - bw / 2;
        g.fill(x - 2, (int) cy - 2, x + bw + 2, (int) cy + 8, 0xD0000000);
        float t = (c.meter() + 100) / 200f;
        int mid = x + (int) (bw * t);
        boolean owner = c.you() == 0;
        g.fillGradient(x, (int) cy, mid, (int) cy + 6, owner ? 0xFFC8A8FF : 0xFFFF7070, owner ? 0xFF6A3AC8 : 0xFFB03030);
        g.fillGradient(mid, (int) cy, x + bw, (int) cy + 6, owner ? 0xFFFF7070 : 0xFFC8A8FF, owner ? 0xFFB03030 : 0xFF6A3AC8);
        Gfx.ring(m, mid, cy + 3, 0, 6, Gfx.argb(1, 0xFFFFFF));
        String key = owner ? Keys.name(Keys.CAST) : Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        boolean blink = (ClientState.ticks / 4) % 2 == 0;
        g.drawCenteredString(f, "§fTRIANGLE  " + (blink ? "§e" : "§6") + "MASH [" + key + "]", (int) cx, (int) cy - 13, 0xFFFFFFFF);
    }

    private static void overlays(GuiGraphics g, int w, int h, float pt) {
        long since = ClientState.ticks - ClientState.swapFadeAt;
        if (since >= 0 && since < 14) {
            float t = (since + pt) / 14f;
            float a = t < 0.35f ? t / 0.35f : 1 - (t - 0.35f) / 0.65f;
            g.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0x05020A);
            for (int i = 0; i < 6; i++) {
                int yy = (int) (h * (i + 0.5f) / 6 + Math.sin(i * 7 + since) * 20);
                g.fill(0, yy, w, yy + 1, ((int) (a * 160) << 24) | 0x9B6BFF);
            }
        }
        if (FxManager.flash > 0.02f) g.fill(0, 0, w, h, ((int) (Math.min(1, FxManager.flash) * 200) << 24) | (FxManager.flashColor & 0xFFFFFF));
        if (ClientEvents.insideDomain > 0.02f) {
            // the pressure: a heavy dark rim that throbs with the domain's pulse
            float throb = 1 + 0.35f * FxManager.pulse;
            int a = (int) (ClientEvents.insideDomain * 150 * throb);
            for (int i = 0; i < 32; i++) {
                int col = (Mth.clamp(a * (32 - i) / 32, 0, 255) << 24) | 0x06020E;
                g.fill(0, i * 2, w, i * 2 + 2, col);
                g.fill(0, h - i * 2 - 2, w, h - i * 2, col);
                g.fill(i * 3, 0, i * 3 + 3, h, col);
                g.fill(w - i * 3 - 3, 0, w - i * 3, h, col);
            }
        }
    }
    private Hud() {}
}
