package com.andy.abyss.client.gui;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientEvents;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.Keys;
import com.andy.abyss.client.SporeClient;
import com.andy.abyss.client.render.FxManager;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.Payload;
import com.andy.abyss.entity.AbyssBody;
import com.andy.abyss.net.S2CCp;
import com.andy.abyss.net.S2CWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/** Output bar, spores, bodies, selected ability, Angel Eyes, Spore Lens, Triangle, and the swap fade. */
public final class Hud {
    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font f = mc.font;
        clash(g, f, w, h);
        overlays(g, w, h, pt);
        if (!ClientState.awakened()) { angelEyes(g, f, w, h); return; }
        int bw = 180, x = w - bw - 8, y = h - 58;
        float out = ClientState.output(), cap = ClientState.cap();
        g.fill(x - 2, y - 2, x + bw + 2, y + 8, 0xC0050309);
        int fill = (int) (bw * Math.min(1, out / cap));
        for (int i = 0; i < fill; i++) {
            float k = i / (float) bw;
            int r = (int) (60 + 90 * k), gg = (int) (25 + 40 * k), b = (int) (140 + 100 * k);
            g.fill(x + i, y, x + i + 1, y + 6, 0xFF000000 | r << 16 | gg << 8 | b);
        }
        float charge = ClientState.me.getFloat("charge");
        if (ClientState.me.getInt("chargeAbility") >= 0 && charge > 0) {
            int cw = (int) Math.min(bw, charge / 600f * bw);
            g.fill(x, y + 6, x + cw, y + 8, 0xFFFFFFFF);
        }
        g.drawString(f, String.format(Locale.ROOT, "OUTPUT %d / %d", (int) out, (int) cap), x, y - 11, 0xFFB9A2FF, true);
        String cp = String.format(Locale.ROOT, "CP %,d", ClientState.me.getInt("cp"));
        g.drawString(f, cp, x + bw - f.width(cp), y - 11, 0xFFE8E0FF, true);
        Ability a = Ability.byIndex(ClientState.selected());
        if (a != null) {
            int cd = ClientState.cd(a.ordinal());
            g.drawString(f, "§8[" + Keys.CAST.getTranslatedKeyMessage().getString() + "] §r" + a.title + (cd > 0 ? " §7" + (cd + 19) / 20 + "s" : ""), x, y + 12, a.color | 0xFF000000, true);
        }
        String[] modes = {"Spread", "Plant", "Tag"};
        g.drawString(f, "§8[" + Keys.SPORES.getTranslatedKeyMessage().getString() + "] §dSpores §f" + ClientState.me.getInt("spores") + " §7· " + ClientState.me.getInt("latched") + " in · §5" + modes[Math.max(0, Math.min(2, ClientState.sporeMode()))], x, y + 23, 0xFFFFFFFF, true);
        boolean negDown = ClientState.me.getInt("negDown") > 0;
        g.drawString(f, ClientState.negation() ? (negDown ? "§cNegation broken" : "§fNegation §7active") : "§8Negation off", x, y + 34, 0xFFFFFFFF, true);
        bodies(g, f, w, h);
        angelEyes(g, f, w, h);
        if (SporeClient.lens) lens(g, f, w, h);
    }

    private static void bodies(GuiGraphics g, Font f, int w, int h) {
        var list = ClientState.bodies();
        if (list.size() <= 1 && ClientState.me.getBoolean("inMain")) return;
        int x = w - 188, y = h - 92;
        int i = 0;
        for (Tag t : list) {
            CompoundTag b = (CompoundTag) t;
            boolean you = b.getBoolean("you"), main = b.getBoolean("main");
            int bx = x + i * 26;
            g.fill(bx, y, bx + 24, y + 14, you ? 0xE0E8E0FF : 0xA0100820);
            g.drawCenteredString(f, main ? "◆" : String.valueOf(b.getInt("slot")), bx + 12, y + 3, you ? 0xFF10061C : 0xFFC8B0FF);
            float hp = b.getFloat("hp") / Math.max(1, b.getFloat("max"));
            g.fill(bx, y + 14, bx + (int) (24 * hp), y + 16, 0xFF9B6BFF);
            if (++i >= 5) break;
        }
        g.drawString(f, "§8[ ] \\ '", x, y - 10, 0xFFFFFFFF, true);
    }

    private static void angelEyes(GuiGraphics g, Font f, int w, int h) {
        if (!Config.angelEyesHud.get()) return;
        var mc = Minecraft.getInstance();
        Entity e = ClientEvents.looked;
        if (e == null) return;
        S2CCp c = ClientState.CP.get(e.getId());
        if (c == null) return;
        int mine = ClientState.me.getInt("cp");
        int x = w / 2 + 14, y = h / 2 + 6;
        int col = mine <= 0 ? 0xFFFFFFFF : c.cp() > mine * 2 ? 0xFFFF6060 : c.cp() > mine ? 0xFFFFC060 : 0xFFB0FFB0;
        g.drawString(f, e.getDisplayName().getString(), x, y, 0xFFE8E0FF, true);
        g.drawString(f, String.format(Locale.ROOT, "CP %,d", c.cp()), x, y + 10, col, true);
        g.drawString(f, String.format(Locale.ROOT, "§7HP §f%.0f/%.0f §7AR §f%.0f §7ATK §f%.1f", c.hp(), c.maxHp(), c.armor(), c.damage()), x, y + 20, 0xFFFFFFFF, true);
        if (c.spores() > 0) g.drawString(f, "§d" + c.spores() + " spores inside", x, y + 30, 0xFFFFFFFF, true);
        if (!c.traits().isEmpty()) g.drawString(f, "§8" + c.traits().replace("ranged:", "fires ").replace(",", " · "), x, y + (c.spores() > 0 ? 40 : 30), 0xFFFFFFFF, true);
    }

    private static void lens(GuiGraphics g, Font f, int w, int h) {
        g.fill(0, 0, w, 14, 0x90000000);
        g.drawCenteredString(f, "§5SPORE LENS §7· click select · right-click sweep · scroll cycle · §f" + (SporeClient.SELECTED.size() + SporeClient.SELECTED_HOSTS.size()) + " selected", w / 2, 3, 0xFFFFFFFF);
        int y = 20;
        for (Payload p : Payload.values()) {
            g.drawString(f, "§f" + (p.ordinal() + 1) + " §d" + p.title + " §8" + p.cost, 8, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }

    private static void clash(GuiGraphics g, Font f, int w, int h) {
        S2CWorld.Clash c = ClientState.myClash();
        if (c == null) return;
        int bw = 220, x = w / 2 - bw / 2, y = h / 2 - 50;
        g.fill(x - 2, y - 2, x + bw + 2, y + 10, 0xD0000000);
        float m = (c.meter() + 100) / 200f;
        int mid = x + (int) (bw * m);
        boolean owner = c.you() == 0;
        g.fill(x, y, mid, y + 8, owner ? 0xFF9B6BFF : 0xFFFF6060);
        g.fill(mid, y, x + bw, y + 8, owner ? 0xFFFF6060 : 0xFF9B6BFF);
        g.fill(mid - 1, y - 3, mid + 1, y + 11, 0xFFFFFFFF);
        String key = owner ? Keys.CAST.getTranslatedKeyMessage().getString() : Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage().getString();
        boolean blink = (ClientState.ticks / 4) % 2 == 0;
        g.drawCenteredString(f, "§fTRIANGLE  " + (blink ? "§e" : "§6") + "MASH [" + key.toUpperCase(Locale.ROOT) + "]", w / 2, y - 13, 0xFFFFFFFF);
    }

    private static void overlays(GuiGraphics g, int w, int h, float pt) {
        // swap: fold into black, then out
        long since = ClientState.ticks - ClientState.swapFadeAt;
        if (since >= 0 && since < 14) {
            float t = (since + pt) / 14f;
            float a = t < 0.35f ? t / 0.35f : 1 - (t - 0.35f) / 0.65f;
            g.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0x05020A);
            int lines = 6;
            for (int i = 0; i < lines; i++) {
                int yy = (int) (h * (i + 0.5f) / lines + Math.sin(i * 7 + since) * 20);
                g.fill(0, yy, w, yy + 1, ((int) (a * 160) << 24) | 0x9B6BFF);
            }
        }
        if (FxManager.flash > 0.02f) g.fill(0, 0, w, h, ((int) (Math.min(1, FxManager.flash) * 200) << 24) | (FxManager.flashColor & 0xFFFFFF));
        if (ClientEvents.insideDomain > 0) {
            int a = (int) (ClientEvents.insideDomain * 90);
            for (int i = 0; i < 24; i++) {
                int edge = 24 - i;
                int col = ((a * edge / 24) << 24) | 0x0A0414;
                g.fill(0, i * 3, w, i * 3 + 3, col);
                g.fill(0, h - i * 3 - 3, w, h - i * 3, col);
            }
        }
    }
    private Hud() {}
}
