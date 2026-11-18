package com.andy.abyss.client.gui;

import com.andy.abyss.client.ClientState;
import com.andy.abyss.data.Ability;
import com.andy.abyss.entity.AbyssBody;
import com.andy.abyss.net.C2SAction;
import com.andy.abyss.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Every body at a glance: move into one, give orders (to one or all), recall, or send a new clone. */
public class RosterScreen extends Screen {
    private int lastSize = -1;
    private long lastHash;

    public RosterScreen() { super(Component.literal("Bodies")); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        clearWidgets();
        int x = width / 2 - 170, y = 46;
        for (Tag t : ClientState.bodies()) {
            CompoundTag b = (CompoundTag) t;
            int slot = b.getInt("slot");
            boolean you = b.getBoolean("you"), main = b.getBoolean("main");
            if (!you) addRenderableWidget(Button.builder(Component.literal("Enter"), bt -> { Net.toServer(new C2SAction(C2SAction.SWAP_TO, slot)); onClose(); }).bounds(x + 220, y, 44, 18).build());
            if (!you && !main) {
                int order = b.getInt("order");
                addRenderableWidget(Button.builder(Component.literal(AbyssBody.ORDER_NAMES[Math.max(0, order) % AbyssBody.ORDER_NAMES.length]), bt -> {
                    Net.toServer(new C2SAction(C2SAction.ORDER, slot * 8 + (order + 1) % AbyssBody.ORDER_NAMES.length));
                }).bounds(x + 268, y, 50, 18).build());
                addRenderableWidget(Button.builder(Component.literal("✕"), bt -> Net.toServer(new C2SAction(C2SAction.RECALL_ONE, slot))).bounds(x + 322, y, 18, 18).build());
            }
            y += 24;
        }
        y += 8;
        int bx = width / 2 - 170;
        for (int o = 0; o < AbyssBody.ORDER_NAMES.length; o++) {
            int ord = o;
            addRenderableWidget(Button.builder(Component.literal("All: " + AbyssBody.ORDER_NAMES[o]), bt -> Net.toServer(new C2SAction(C2SAction.ORDER_ALL, ord))).bounds(bx + o * 68, y, 64, 18).build());
        }
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Send clone where I look"), bt -> {
            int prev = ClientState.selected();
            Net.toServer(new C2SAction(C2SAction.SELECT, Ability.SEND_CLONE.ordinal()));
            Net.toServer(new C2SAction(C2SAction.CAST_START, 0));
            Net.toServer(new C2SAction(C2SAction.SELECT, prev));
            onClose();
        }).bounds(width / 2 - 170, y, 166, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Recall all"), bt -> Net.toServer(new C2SAction(C2SAction.RECALL_ONE, -1))).bounds(width / 2 + 4, y, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal(ClientState.me.getBoolean("inMain") ? "Descend" : "Go home"), bt -> {
            int prev = ClientState.selected();
            Net.toServer(new C2SAction(C2SAction.SELECT, Ability.DESCEND.ordinal()));
            Net.toServer(new C2SAction(C2SAction.CAST_START, 0));
            Net.toServer(new C2SAction(C2SAction.SELECT, prev));
            onClose();
        }).bounds(width / 2 + 88, y, 82, 18).build());
        lastSize = ClientState.bodies().size();
        lastHash = hash();
    }

    private long hash() {
        long h = 0;
        for (Tag t : ClientState.bodies()) { CompoundTag b = (CompoundTag) t; h = h * 31 + b.getInt("slot") * 7L + b.getInt("order") + (b.getBoolean("you") ? 1000 : 0); }
        return h;
    }

    @Override
    public void tick() { if (ClientState.bodies().size() != lastSize || hash() != lastHash) init(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "§5BODIES §8· " + (ClientState.bodies().size() - 1) + " / " + ClientState.me.getInt("maxClones") + " clones out", width / 2, 20, 0xFFFFFFFF);
        int cloneCd = ClientState.me.getInt("cloneCd");
        if (cloneCd > 0) g.drawCenteredString(font, "§7Re-forming a body: " + (cloneCd + 19) / 20 + "s", width / 2, 31, 0xFFFFFFFF);
        int x = width / 2 - 170, y = 46;
        for (Tag t : ClientState.bodies()) {
            CompoundTag b = (CompoundTag) t;
            boolean you = b.getBoolean("you"), main = b.getBoolean("main");
            g.fill(x, y - 2, x + 340, y + 20, you ? 0x60E8E0FF : 0x60100820);
            g.drawString(font, (main ? "§d◆ Main body" : "§b◇ Clone " + b.getInt("slot")) + (you ? " §f(you)" : ""), x + 4, y + 1, 0xFFFFFFFF, true);
            String dim = b.getString("dim");
            dim = dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
            g.drawString(font, String.format(Locale.ROOT, "§8%s %.0f %.0f %.0f", dim, b.getDouble("x"), b.getDouble("y"), b.getDouble("z")), x + 4, y + 11, 0xFFFFFFFF, false);
            float hp = b.getFloat("hp") / Math.max(1, b.getFloat("max"));
            g.fill(x + 130, y + 4, x + 210, y + 10, 0xFF1A0E2E);
            g.fill(x + 130, y + 4, x + 130 + (int) (80 * hp), y + 10, 0xFF9B6BFF);
            y += 24;
        }
        super.render(g, mx, my, pt);
    }
}
