package com.andy.abyss.combat;

/** Abyssal constructs the vessel can forge in Arsenal form. Each has its own move set (see {@link Moves}). */
public enum Weapon {
    SWORD("Abyssal Edge", "A blade so thin it cuts space. Quick, clean cuts that leave the abyss showing through the air.", 0xB48CFF, 3.6f, 1.0f),
    GAUNTLETS("Hands of the Abyss", "A brawler's fists. Every blow lands with your whole weight, and the heavy ones break the ground.", 0xFF6AB4, 3.0f, 0.8f);

    public final String title, desc;
    public final int color;
    public final float reach, weight;

    Weapon(String t, String d, int c, float reach, float weight) { title = t; desc = d; color = c; this.reach = reach; this.weight = weight; }

    public static Weapon byIndex(int i) { Weapon[] v = values(); return v[Math.floorMod(i, v.length)]; }
}
