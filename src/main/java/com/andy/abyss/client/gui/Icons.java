package com.andy.abyss.client.gui;

import com.andy.abyss.Abyss;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.Payload;
import net.minecraft.resources.ResourceLocation;

public final class Icons {
    private static final String[] AB = {"domain", "rift", "beelzebub", "triangle", "send_clone", "brood", "volley", "negation", "recall", "descend"};
    private static final String[] PL = {"p_nuke", "p_domain", "p_beelzebub", "p_transport", "p_disintegrate", "p_rift", "p_clone", "p_step"};
    public static ResourceLocation of(Ability a) { return Abyss.id("textures/gui/icon/" + AB[a.ordinal()] + ".png"); }
    public static ResourceLocation of(Payload p) { return Abyss.id("textures/gui/icon/" + PL[p.ordinal()] + ".png"); }
    private Icons() {}
}
