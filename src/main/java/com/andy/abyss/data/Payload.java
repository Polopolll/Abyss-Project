package com.andy.abyss.data;

/** What a selected spore can become (Spore Lens, keys 1-8). */
public enum Payload {
    NUKE("Nuke", "Localized abyssal detonation. In a host it goes off from the inside out.", 25),
    DOMAIN("Small Domain", "A pocket domain around the spore. Sure hit inside.", 40),
    BEELZEBUB("Beelzebub", "The spore opens into a mouth and devours.", 30),
    TRANSPORT("Transport", "Drag the host (or whatever is near) into the abyss.", 30),
    DISINTEGRATE("Disintegrate", "Unmake it. No drops, no remains.", 20),
    RIFT("Rift", "Open a rift between you and the spore.", 15),
    CLONE("Clone Seed", "Grow a clone from the spore.", 60),
    STEP("Rift-step", "Step to the spore.", 8);

    public final String title, desc;
    public final int cost;
    Payload(String t, String d, int c) { title = t; desc = d; cost = c; }
    public static Payload byIndex(int i) { Payload[] v = values(); return i >= 0 && i < v.length ? v[i] : null; }
}
