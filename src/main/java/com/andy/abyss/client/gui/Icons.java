package com.andy.abyss.client.gui;

import com.andy.abyss.Abyss;
import com.andy.abyss.combat.Weapon;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.Payload;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Ronova-style medallions: skills, each form's four sub-actions, weapons, payloads, and the mouse buttons. */
public final class Icons {
    private static final String[] PL = {"p_nuke", "p_domain", "p_beelzebub", "p_transport", "p_disintegrate", "p_rift", "p_clone", "p_step"};
    private static final String[] MOUSE = {"l", "r", "4", "5"};
    public static ResourceLocation of(Ability a) { return Abyss.id("textures/gui/skill/" + a.name().toLowerCase(Locale.ROOT) + ".png"); }
    /** i: 0 LMB, 1 RMB, 2 M4, 3 M5. */
    public static ResourceLocation sub(Ability a, int i) { return Abyss.id("textures/gui/sub/" + a.name().toLowerCase(Locale.ROOT) + "_" + i + ".png"); }
    public static ResourceLocation of(Weapon w) { return Abyss.id("textures/gui/weapon/" + w.name().toLowerCase(Locale.ROOT) + ".png"); }
    public static ResourceLocation of(Payload p) { return Abyss.id("textures/gui/icon/" + PL[p.ordinal()] + ".png"); }
    public static ResourceLocation mouse(int i) { return Abyss.id("textures/gui/mouse/" + MOUSE[i] + ".png"); }
    private Icons() {}
}
