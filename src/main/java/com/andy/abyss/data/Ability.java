package com.andy.abyss.data;

/**
 * The skills on the wheel (Ronova-style). X: tap = next skill, hold = wheel, X + 1-8 = that place, X + scroll = flip.
 * R uses the selected skill. A FORM is an umbrella: R opens it, and while it is open the mouse buttons are its
 * sub-abilities (LMB, RMB, mouse 4, mouse 5), with hints next to the crosshair. R again closes it.
 */
public enum Ability {
    DOMAIN("Domain Expansion", Type.CHARGE, 0x9B6BFF, 30,
            "Hold R to pour output in, scroll to choose Open or Closed, release to expand. While your domain stands, the mouse commands it.",
            new String[]{"Sure-Hit Spears", "Pressure Pulse", "Collapse", "Dismiss"},
            new String[]{"Spears of crystal erupt under every enemy inside — they cannot miss.", "The domain breathes in: everything inside is slammed down and dragged inward.",
                    "The finale: everything inside is pulled to the centre and the domain implodes on it.", "Let the domain go."}),
    SPORES("Spore Form", Type.FORM, 0xD06AFF, 0,
            "Spores swirl around you. Spread them, paint over the ones you want, and turn them all into one payload at once.",
            new String[]{"Spread", "Paint (hold)", "Unleash", "Next payload"},
            new String[]{"360° burst (hold to keep spreading). Shift: a seeker spore that always latches.", "Hold and sweep the crosshair: every spore and infected creature it passes over is selected. Shift: plant a spore where you look.",
                    "Every selected spore becomes the payload. Nothing selected: everything of yours in front of you — one press nukes a whole crowd.", "Cycle the payload (scroll works too). Shift: previous."}),
    ARSENAL("Abyssal Arsenal", Type.FORM, 0xB48CFF, 0,
            "Forge a weapon of crystal and void. Scroll to change weapon. Every weapon has its own chain, heavy, plunge, counter, skill and liberation.",
            new String[]{"Attack (hold: heavy)", "Dodge", "Weapon Skill", "Liberation"},
            new String[]{"Click to chain the basic attacks; hold and release for the heavy; in the air for a plunge; right after a perfect dodge for a counter.",
                    "Dash out of the way. Dodge at the last moment for a perfect dodge: time slows and your next click is a counter.",
                    "The weapon's skill (costs output).", "The weapon's ultimate, when the resonance gauge is full."}),
    AUTHORITY("Authority of the Abyss", Type.FORM, 0x7FA8FF, 0,
            "A great rift tears open behind you. Through it: the Abyss Realm, where the world is reflected at the same place.",
            new String[]{"Transport", "The Realm", "Rift", "Rift-step"},
            new String[]{"Drag what you look at through the rift into the Abyss.", "Step through the rift into the Abyss Realm (again: leave, at the same place, into the world you chose).",
                    "Open a two-way rift to any place in any dimension.", "Step to where you look. In the Realm: choose which world to exit into."}),
    BEELZEBUB("Beelzebub", Type.FORM, 0xFF4A9A, 0,
            "The abyss opens its mouth behind you.",
            new String[]{"Devour", "Brood", "Mimic Volley", "Inhale"},
            new String[]{"Devour what you look at and keep what made it strong.", "Release a devoured creature as a puppet that fights for you.",
                    "Fire the projectiles of the creatures you devoured.", "The mouth inhales for three seconds: projectiles, items and the weak are pulled in and swallowed."}),
    BODIES("Bodies", Type.FORM, 0x8C8CFF, 0,
            "Your clones and your main body.",
            new String[]{"Send clone", "Next body", "Recall all", "Descend"},
            new String[]{"A clone climbs out of a rift where you look.", "Move into your next body (Shift: previous).", "Pull every clone back into the abyss.",
                    "Main body down to its throne in the Abyss (you go on as a clone), or go home to it."}),
    TRIANGLE("Triangle", Type.INSTANT, 0xFFFFFF, 20,
            "Lock what you look at in a tug of war of Combat Power. Mash R.", null, null),
    NEGATION("Negation", Type.TOGGLE, 0xE0E0FF, 0,
            "Damage and harmful effects are cancelled while your output lasts.", null, null),
    GUIDE("Guide", Type.CLIENT, 0xC8C0D8, 0,
            "How everything works.", null, null);

    public enum Type { FORM, CHARGE, INSTANT, TOGGLE, CLIENT }
    public static final String[] BUTTONS = {"LMB", "RMB", "M4", "M5"};

    public final String title, desc;
    public final Type type;
    public final int color, cooldownSec;
    public final String[] sub, subDesc;

    Ability(String title, Type type, int color, int cd, String desc, String[] sub, String[] subDesc) {
        this.title = title; this.type = type; this.color = color; this.cooldownSec = cd; this.desc = desc; this.sub = sub; this.subDesc = subDesc;
    }

    public boolean isForm() { return type == Type.FORM || this == DOMAIN; }
    public boolean charged() { return type == Type.CHARGE; }
    public static Ability byIndex(int i) { Ability[] v = values(); return i >= 0 && i < v.length ? v[i] : null; }
}
