package com.andy.abyss.data;

/** Wheel abilities (cast with the cast key). Spores and their payloads have their own keys. */
public enum Ability {
    DOMAIN("Domain Expansion", "Hold to invest output, scroll (or right-click) to choose Open or Closed, release to expand. Open pours an infinite dimension into the world; Closed tears the area into the abyss. Every ability inside is a sure hit.", 0x9B6BFF, 30, true),
    RIFT("Rift", "Open a two-way rift to any place in any dimension. Sneak-cast to step straight through.", 0x7FA8FF, 3, false),
    BEELZEBUB("Beelzebub", "The abyss opens its mouth on what you look at and devours it, taking its properties.", 0xFF4A9A, 4, false),
    TRIANGLE("Triangle", "Lock a target in a tug of war of Combat Power. Mash the cast key to push.", 0xFFFFFF, 20, false),
    SEND_CLONE("Send Clone", "Send a body out of the abyss to where you look.", 0x8C8CFF, 2, false),
    BROOD("Brood", "Release the last creature you devoured as an abyss puppet that fights for you.", 0xD06AFF, 8, false),
    VOLLEY("Mimic Volley", "Fire the projectiles of the creatures you devoured.", 0xFFA06A, 2, false),
    NEGATION("Negation", "Toggle Percentage Negation: damage and harmful effects are cancelled while your output lasts.", 0xE0E0FF, 0, false),
    RECALL("Recall", "Pull every clone back into the abyss.", 0x606080, 2, false),
    DESCEND("Descend", "Move your main body into the abyss (you carry on as a clone), or bring it back out.", 0x3A2A6A, 5, false);

    public final String title, desc;
    public final int color, cooldownSec;
    public final boolean charged;

    Ability(String title, String desc, int color, int cd, boolean charged) {
        this.title = title; this.desc = desc; this.color = color; this.cooldownSec = cd; this.charged = charged;
    }

    public static Ability byIndex(int i) { Ability[] v = values(); return i >= 0 && i < v.length ? v[i] : null; }
}
