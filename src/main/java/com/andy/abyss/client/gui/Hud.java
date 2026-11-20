package com.andy.abyss.client.gui;

import com.andy.abyss.Config;
import com.andy.abyss.client.ClientEvents;
import com.andy.abyss.client.ClientState;
import com.andy.abyss.client.Keys;
import com.andy.abyss.client.SporeClient;
import com.andy.abyss.client.WheelOrder;
import com.andy.abyss.client.render.FxManager;
import com.andy.abyss.combat.Move;
import com.andy.abyss.combat.Moves;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.Payload;
import com.andy.abyss.net.S2CCp;
import com.andy.abyss.net.S2CWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;

/**
 * Ronova-style HUD. Bottom right: the selected skill as a big medallion (its neighbours on the wheel beside it), the
 * output bar under it. When a form is open: four hints by the crosshair (mouse button, the sub-action's medallion,
 * its name; they change with context: shift, airborne, counter ready, in the Realm). Arsenal adds a weapon medallion
 * wrapped in the resonance gauge, the chain pips and the next move; Spores the payload ring; the Realm a banner.
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
        if (ClientState.awakened() && mc.player.isSpectator() && !ClientState.inRealm() && ClientState.form() == com.andy.abyss.data.Ability.AUTHORITY.ordinal()) {
            // looking through the eye: say how to get back, plainly
            g.drawCenteredString(f, "§8◉ §fTHE EYE IS OPEN §8◉", w / 2, h - 62, 0xFFFFFFFF);
            g.drawCenteredString(f, "§7LMB §fclose the eye §8·  §7RMB §fstep out here §8·  §7R §fback to the Realm", w / 2, h - 50, 0xFFFFFFFF);
            return;
        }
        if (!ClientState.awakened() || mc.player.isSpectator() && !ClientState.inRealm()) { if (ClientState.inRealm()) realm(g, f, w, h, time); return; }
        Matrix4f m = g.pose().last().pose();
        dock(g, f, m, w, h, time, pt);
        Ability form = ClientState.openForm();
        if (form != null) hints(g, f, m, w, h, form, time);
        if (form == Ability.ARSENAL) arsenal(g, f, m, w, h, time);
        if (form == Ability.SPORES || SporeClient.lens) payloadRing(g, f, m, w, h);
        if (ClientState.inRealm()) realm(g, f, w, h, time);
        bodies(g, f, m, w, h, w - 150);
        domainChooser(g, f, m, w, h);
    }

    // ------------------------------------------------------------------------------------------------- the dock
    private static void dock(GuiGraphics g, Font f, Matrix4f m, int w, int h, float time, float pt) {
        Ability sel = Ability.byIndex(ClientState.selected());
        if (sel == null) sel = Ability.DOMAIN;
        ClientState.selectAnim++;
        float pop = ClientState.selectAnim < 8 ? 1 + 0.25f * (1 - (ClientState.selectAnim + pt) / 8f) : 1;
        float cx = w - 34, cy = h - 40;
        boolean open = ClientState.openForm() == sel;
        // neighbours on the wheel, fading out to the left
        List<Ability> order = WheelOrder.get();
        int idx = Math.max(0, order.indexOf(sel));
        for (int k = 1; k <= 3; k++) {
            Ability a = order.get(Math.floorMod(idx + k, order.size()));
            float x = cx - 26 - k * 20;
            Gfx.icon(g, Icons.of(a), x, cy + 6, 18, 0.9f, a.color, 0, 0.75f - k * 0.17f);
        }
        if (open) {
            float spin = time * 0.05f;
            for (int i = 0; i < 3; i++) Gfx.arc(m, cx, cy, 25, 26.5f, spin + i * Mth.TWO_PI / 3, spin + i * Mth.TWO_PI / 3 + 1.2f, Gfx.argb(0.9f, sel.color));
        }
        int cd = ClientState.cd(sel.ordinal());
        Gfx.icon(g, Icons.of(sel), cx, cy, (int) (46 * pop), cd > 0 ? 0.45f : 1f, sel.color, 0, 1);
        if (cd > 0 && sel.cooldownSec > 0) {
            Gfx.arc(m, cx, cy, 0, 18, 0, Mth.TWO_PI * Math.min(1, cd / (sel.cooldownSec * 20f)), Gfx.argb(0.55f, 0x000000));
            g.drawCenteredString(f, String.valueOf((cd + 19) / 20), (int) cx, (int) cy - 4, 0xFFFFFFFF);
        }
        if (ClientState.me.getInt("chargeAbility") >= 0) {
            float c = Math.min(1, ClientState.me.getFloat("charge") / (ClientState.infinite() ? 2000f : 600f));
            Gfx.arc(m, cx, cy, 24, 26.5f, 0, Mth.TWO_PI * c, Gfx.argb(0.95f, 0xFFFFFF));
        }
        // name and state
        String name = sel.title.toUpperCase(Locale.ROOT);
        String state = open ? "§fOPEN §8· " + Keys.name(Keys.CAST) + " to close" : sel.isForm() ? "§7FORM §8· " + Keys.name(Keys.CAST) + " to open"
                : sel == Ability.NEGATION ? (ClientState.negation() ? "§fON" : "§7OFF") : "§8" + Keys.name(Keys.CAST) + " to use";
        g.pose().pushPose();
        g.pose().translate(cx + 22, cy - 34, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(f, name, -f.width(name), 0, Gfx.argb(1, Gfx.mix(0xFFFFFF, sel.color, 0.35f)), true);
        g.drawString(f, state, -f.width(state), 10, 0xFFFFFFFF, true);
        g.pose().popPose();
        // output bar
        boolean inf = ClientState.infinite();
        float frac = inf ? 1 : Mth.clamp(ClientState.output() / ClientState.cap(), 0, 1);
        int bw = 110, bh = 3, bx = w - 8 - bw, by = h - 9;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xC0050309);
        int fw = Math.round(bw * frac);
        if (fw > 0) {
            g.fillGradient(bx, by, bx + fw, by + bh, inf ? 0xFFFFFFFF : 0xFFB48CFF, inf ? 0xFFC8B0FF : 0xFF5A2AA8);
            int sh = (int) ((time * 1.6f) % (bw + 30)) - 15;
            if (sh > 0 && sh < fw) g.fill(bx + Math.max(0, sh - 3), by, bx + Math.min(fw, sh + 3), by + bh, 0x70FFFFFF);
        }
        String out = (inf ? "∞" : String.valueOf((int) ClientState.output())) + "  §8" + ClientState.me.getInt("spores") + " spores" + (ClientState.me.getInt("negDown") > 0 ? " §c· negation broken" : "");
        g.pose().pushPose();
        g.pose().translate(bx, by - 8, 0);
        g.pose().scale(0.7f, 0.7f, 1);
        g.drawString(f, out, 0, 0, 0xFFD8C8FF, true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------------------------------------- crosshair hints
    private static void hints(GuiGraphics g, Font f, Matrix4f m, int w, int h, Ability form, float time) {
        if (form.sub == null) return;
        // left of the crosshair, right-aligned (the right side belongs to the target panel)
        float x = w / 2f - 22, y = h / 2f - 26;
        for (int i = 0; i < 4; i++) {
            String[] lab = label(form, i);
            if (lab == null) continue;
            boolean hot = lab.length > 1 && "hot".equals(lab[1]);
            float yy = y + i * 14;
            float a = hot ? 0.75f + 0.25f * Mth.sin(time * 0.5f) : 0.92f;
            Gfx.icon(g, Icons.mouse(i), x - 5, yy + 6, 12, 1, 0xFFFFFF, 0, a);
            Gfx.icon(g, Icons.sub(form, i), x - 19, yy + 6, 13, 1, form.color, 0, a);
            g.pose().pushPose();
            g.pose().translate(x - 28, yy + 3, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(f, lab[0], -f.width(lab[0]), 0, Gfx.argb(a, hot ? 0xFFFFFF : 0xE0D4FF), true);
            g.pose().popPose();
        }
    }

    /** The label for a sub-action right now; {text, "hot"} when it deserves attention. */
    private static String[] label(Ability form, int i) {
        boolean shift = Screen.hasShiftDown();
        var mc = Minecraft.getInstance();
        boolean air = mc.player != null && !mc.player.onGround() && !mc.player.getAbilities().flying;
        switch (form) {
            case SPORES -> {
                int sel = SporeClient.SELECTED.size() + SporeClient.SELECTED_HOSTS.size();
                return switch (i) {
                    case 0 -> new String[]{shift ? "Seeker spore" : "Spread"};
                    case 1 -> new String[]{shift ? "Plant" : ClientState.painting ? "§fPainting… " + sel : "Paint (hold)"};
                    case 2 -> sel > 0 ? new String[]{"Unleash ×" + sel, "hot"} : new String[]{"Unleash §8(everything ahead)"};
                    default -> new String[]{Payload.byIndex(ClientState.payload).title + " §8(" + Payload.byIndex(ClientState.payload).cost + ")"};
                };
            }
            case ARSENAL -> {
                Weapon wpn = Weapon.byIndex(ClientState.weapon());
                Moves.Set set = Moves.ALL.get(wpn);
                return switch (i) {
                    case 0 -> ClientState.counterReady() ? new String[]{"§f" + set.counter().name(), "hot"} : air ? new String[]{set.plunge().name()}
                            : new String[]{nextBasic(set).name() + " §8· hold: " + set.heavy().name()};
                    case 1 -> new String[]{"Dodge"};
                    case 2 -> new String[]{set.skill().name()};
                    default -> ClientState.gauge() >= 100 || ClientState.infinite() ? new String[]{"§f" + set.liberation().name(), "hot"}
                            : new String[]{"§8" + set.liberation().name() + " " + (int) ClientState.gauge() + "%"};
                };
            }
            case AUTHORITY -> {
                if (ClientState.inRealm()) return switch (i) {
                    case 0 -> new String[]{"Peek through (hold)"};
                    case 1 -> new String[]{"Leave the Realm", "hot"};
                    case 2 -> new String[]{"Rift"};
                    default -> new String[]{"Exit into: §f" + shortWorld(ClientState.me.getString("realmTarget"))};
                };
            }
            case BEELZEBUB -> { if (i == 3 && ClientState.me.getBoolean("inhaling")) return new String[]{"Inhaling…", "hot"}; }
            case BODIES -> { if (i == 1 && shift) return new String[]{"Previous body"}; }
            default -> {}
        }
        return new String[]{form.sub[i]};
    }

    private static Move nextBasic(Moves.Set set) {
        int step = ClientState.me.getLong("chainLeft") > 0 ? ClientState.me.getInt("chain") : 0;
        return set.basic().get(Math.floorMod(step, set.basic().size()));
    }

    static String shortWorld(String id) {
        if (id == null || id.isEmpty()) return "where you came from";
        String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        s = s.replace('_', ' ');
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------------------------------------- arsenal
    private static void arsenal(GuiGraphics g, Font f, Matrix4f m, int w, int h, float time) {
        Weapon wpn = Weapon.byIndex(ClientState.weapon());
        Moves.Set set = Moves.ALL.get(wpn);
        float cx = w - 34, cy = h / 2f + 34;
        float gauge = ClientState.infinite() ? 100 : Mth.clamp(ClientState.gauge(), 0, 100);
        boolean full = gauge >= 100;
        Gfx.ring(m, cx, cy, 15, 19, Gfx.argb(0.7f, 0x0A0614));
        Gfx.arc(m, cx, cy, 15.5f, 18.5f, 0, Mth.TWO_PI * gauge / 100f, Gfx.argb(0.95f, full ? 0xFFFFFF : 0xB48CFF));
        if (full) for (int i = 0; i < 4; i++) { float a = time * 0.08f + i * Mth.HALF_PI; Gfx.arc(m, cx, cy, 20, 21.5f, a, a + 0.6f, Gfx.argb(0.8f, 0xE8DCFF)); }
        Gfx.icon(g, Icons.of(wpn), cx, cy, 30, 1, 0xB48CFF, 0, 1);
        g.pose().pushPose();
        g.pose().translate(cx - 24, cy - 8, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        String t = wpn.title.toUpperCase(Locale.ROOT);
        g.drawString(f, t, -f.width(t), 0, 0xFFE8DCFF, true);
        String sub = full ? "§fLIBERATION READY" : "§8scroll to change weapon";
        g.drawString(f, sub, -f.width(sub), 10, 0xFFFFFFFF, true);
        g.pose().popPose();
        // chain pips
        int n = set.basic().size();
        boolean live = ClientState.me.getLong("chainLeft") > 0;
        int step = live ? ClientState.me.getInt("chain") : 0;
        float px = cx - 24 - n * 8, py = cy + 12;
        for (int i = 0; i < n; i++) {
            boolean lit = live && (i < step || step == 0 && i == n - 1);
            float x = px + i * 8 + 4;
            Gfx.arc(m, x, py, 0, lit ? 3 : 2.2f, 0, Mth.TWO_PI, Gfx.argb(lit ? 0.95f : 0.35f, lit ? (i == n - 1 ? 0xFFFFFF : 0xB48CFF) : 0x3A2A5A));
        }
        if (ClientState.counterReady()) {
            float a = 0.6f + 0.4f * Mth.sin(time * 0.7f);
            g.drawCenteredString(f, "§fCOUNTER", w / 2, h / 2 + 22, Gfx.argb(a, 0xFFFFFF));
            Gfx.arc(m, w / 2f, h / 2f, 9, 10.5f, 0, Mth.TWO_PI, Gfx.argb(a, 0xFFFFFF));
        }
        if (ClientState.me.getBoolean("guarding")) g.drawCenteredString(f, "§7GUARD", w / 2, h / 2 + 22, 0xFFFFFFFF);
        if (ClientState.heavyHeld > 8 && wpn != Weapon.BULWARK) {
            float c = Math.min(1, (ClientState.heavyHeld - 8) / 12f);
            Gfx.arc(m, w / 2f, h / 2f, 12, 14, 0, Mth.TWO_PI * c, Gfx.argb(0.9f, c >= 1 ? 0xFFFFFF : 0xB48CFF));
        }
    }

    // ------------------------------------------------------------------------------------------------- spores
    private static void payloadRing(GuiGraphics g, Font f, Matrix4f m, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        int n = Payload.values().length;
        float seg = Mth.TWO_PI / n;
        for (Payload p : Payload.values()) {
            int i = p.ordinal();
            boolean on = i == ClientState.payload;
            float mid = i * seg;
            Gfx.arc(m, cx, cy, on ? 40 : 42, on ? 52 : 49, mid - seg / 2 + 0.05f, mid + seg / 2 - 0.05f, Gfx.argb(on ? 0.75f : 0.3f, on ? 0x2A1A44 : 0x05030A));
            if (on) Gfx.arc(m, cx, cy, 51, 52.5f, mid - seg / 2 + 0.05f, mid + seg / 2 - 0.05f, Gfx.argb(1, 0xFFFFFF));
        }
        for (Payload p : Payload.values()) {
            int i = p.ordinal();
            float mid = i * seg;
            boolean on = i == ClientState.payload;
            Gfx.icon(g, Icons.of(p), cx + Mth.sin(mid) * 46, cy - Mth.cos(mid) * 46, on ? 12 : 8, on ? 1 : 0.55f, 0xB48CFF, 0.2f, on ? 1 : 0.7f);
        }
        int selected = SporeClient.SELECTED.size() + SporeClient.SELECTED_HOSTS.size();
        if (selected > 0) g.drawCenteredString(f, "§f" + selected + " §7selected", (int) cx, (int) cy + 58, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------------------------------------- the realm
    private static void realm(GuiGraphics g, Font f, int w, int h, float time) {
        float a = 0.75f + 0.25f * Mth.sin(time * 0.05f);
        g.pose().pushPose();
        g.pose().translate(w / 2f, 12, 0);
        g.pose().scale(1.4f, 1.4f, 1);
        g.drawCenteredString(f, "T H E   A B Y S S", 0, 0, Gfx.argb(a, 0xD8C8FF));
        g.pose().popPose();
        String sub = "§7reflecting §f" + shortWorld(ClientState.soulWorld) + " §8· " + ClientState.soulNames.length + " souls · exit into §f" + shortWorld(ClientState.me.getString("realmTarget"));
        g.pose().pushPose();
        g.pose().translate(w / 2f, 27, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawCenteredString(f, sub, 0, 0, 0xFFFFFFFF);
        g.pose().popPose();
        if (Minecraft.getInstance().player.isSpectator()) g.drawCenteredString(f, "§fTHE EYE IS OPEN §8· LMB close · RMB step out", w / 2, h - 50, 0xFFFFFFFF);
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
        long frozen = ClientState.freezeUntil - ClientState.ticks;
        if (frozen > -6) {
            // time has stopped: the world goes cold and grey, the edges close in
            float a = frozen > 0 ? 1 : (frozen + 6) / 6f;
            g.fill(0, 0, w, h, ((int) (a * 92) << 24) | 0x5A6070);
            for (int i = 0; i < 24; i++) {
                int col = (Mth.clamp((int) (a * 140 * (24 - i) / 24), 0, 255) << 24) | 0x05060A;
                g.fill(0, i * 2, w, i * 2 + 2, col);
                g.fill(0, h - i * 2 - 2, w, h - i * 2, col);
                g.fill(i * 3, 0, i * 3 + 3, h, col);
                g.fill(w - i * 3 - 3, 0, w - i * 3, h, col);
            }
        }
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
