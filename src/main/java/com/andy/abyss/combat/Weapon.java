package com.andy.abyss.combat;

/** Abyssal constructs the vessel can forge in Arsenal form. Each has its own move set (see {@link Moves}). */
public enum Weapon {
    SWORD("Abyssal Edge", "A blade so thin it cuts space. Every slash leaves a rend in reality that bleeds galaxy, then severs.", 0xB48CFF, 3.6f, 1.0f),
    GREATSWORD("Event Cleaver", "A two-handed slab of crystal. Slow, wide arcs that tear the ground open.", 0x8C55FF, 4.6f, 1.6f),
    HAMMER("Singularity Maul", "Each blow is a little collapse: shock rings, craters, gravity wells.", 0x6A40C0, 3.4f, 1.8f),
    SCYTHE("Horizon Reaper", "Sweeping reaps that hook and drag everything inward.", 0xD06AFF, 4.2f, 1.2f),
    SPEAR("Rift Lance", "Long piercing thrusts; rifts spit more spears at whatever you point at.", 0x9B9BFF, 5.5f, 1.1f),
    BULWARK("Aegis & Shard", "A shard blade and a hex shield. Block with heavy, bash, and break guards.", 0xC8B8FF, 3.2f, 1.2f),
    GAUNTLETS("Hands of the Abyss", "Fists of crystal; void arms punch out of rifts around you.", 0xFF6AB4, 3.0f, 0.8f);

    public final String title, desc;
    public final int color;
    public final float reach, weight;

    Weapon(String t, String d, int c, float reach, float weight) { title = t; desc = d; color = c; this.reach = reach; this.weight = weight; }

    public static Weapon byIndex(int i) { Weapon[] v = values(); return v[Math.floorMod(i, v.length)]; }
}
