package com.andy.abyss;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server config (balance, rules) and client config (visuals). Every value is also a command: /abyss config. */
public final class Config {
    public static final ForgeConfigSpec SPEC, CLIENT_SPEC;
    public static final Map<String, ForgeConfigSpec.ConfigValue<?>> ALL = new LinkedHashMap<>(), CLIENT = new LinkedHashMap<>();

    // output
    public static ForgeConfigSpec.DoubleValue outputBase, outputPerMastery, outputRefillPerSecond, masteryRate, damageMultiplier, pvpMultiplier;
    public static ForgeConfigSpec.BooleanValue singleUser, griefing, cooldownsEnabled, allowInfiniteOutput;
    public static ForgeConfigSpec.DoubleValue cooldownMultiplier;
    public static ForgeConfigSpec.BooleanValue sovereignty, vesselImmunities, beamCarves;
    public static ForgeConfigSpec.DoubleValue vesselStrength, comboDamage;
    public static ForgeConfigSpec.IntValue reformSeconds;
    public static ForgeConfigSpec.BooleanValue escapeIntoRealm;
    public static ForgeConfigSpec.BooleanValue domainDecay, domainScarsPermanent, domainDissolveItems;
    public static ForgeConfigSpec.IntValue domainDecayPerTick;
    public static ForgeConfigSpec.IntValue blockBudgetPerTick;
    public static ForgeConfigSpec.DoubleValue maxBreakHardness;
    // spores
    public static ForgeConfigSpec.IntValue sporeLifeSeconds, sporesPerBurst, maxSporesPerUser, maxSporesPerHost, infectSeconds;
    public static ForgeConfigSpec.DoubleValue sporeRange, sporeLatchChance, sporeJumpChance;
    // payloads
    public static ForgeConfigSpec.DoubleValue nukeDamage, nukeRadius, disintegrateDamage, beelzebubCpRatio;
    public static ForgeConfigSpec.IntValue transportSeconds, riftSeconds;
    // domain
    public static ForgeConfigSpec.DoubleValue domainExpansionRate, domainMaxRadius, closedMaxRadius, domainCrush;
    public static ForgeConfigSpec.IntValue domainSeconds, closedSeconds;
    // defence
    public static ForgeConfigSpec.DoubleValue negationCost, soulResistance;
    public static ForgeConfigSpec.BooleanValue negateVoidAndKill;
    // corrosion
    public static ForgeConfigSpec.DoubleValue corrosionStrength;
    public static ForgeConfigSpec.BooleanValue spareAllies;
    // clones
    public static ForgeConfigSpec.IntValue maxClones, cloneRespawnSeconds, maxPuppets, puppetSeconds;
    public static ForgeConfigSpec.DoubleValue cloneStatScale;
    // triangle
    public static ForgeConfigSpec.IntValue triangleTicks;

    // client
    public static ForgeConfigSpec.DoubleValue fxDensity, screenShake;
    public static ForgeConfigSpec.BooleanValue crystalSkin, angelEyesHud, domainFog, damageNumbers, slotCastsImmediately;
    public static ForgeConfigSpec.ConfigValue<String> wheelOrder;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("output");
        outputBase = put(b.comment("Abyss Output per second at mastery 0. The reserve is infinite; this is how much can be pushed out").defineInRange("outputBase", 100.0, 1, 1e6), "outputBase");
        outputPerMastery = put(b.comment("Extra output per point of mastery (0-100)").defineInRange("outputPerMastery", 4.0, 0, 1e5), "outputPerMastery");
        outputRefillPerSecond = put(b.comment("Fraction of the output cap that refills every second").defineInRange("outputRefillPerSecond", 1.0, 0.01, 100), "outputRefillPerSecond");
        masteryRate = put(b.defineInRange("masteryRate", 1.0, 0, 100), "masteryRate");
        damageMultiplier = put(b.defineInRange("damageMultiplier", 1.0, 0, 1000), "damageMultiplier");
        pvpMultiplier = put(b.defineInRange("pvpMultiplier", 1.0, 0, 1000), "pvpMultiplier");
        cooldownsEnabled = put(b.comment("Ability cooldowns (and the clone re-forming time). Off = cast as fast as your output allows").define("cooldownsEnabled", false), "cooldownsEnabled");
        cooldownMultiplier = put(b.comment("Scales every cooldown when cooldowns are on").defineInRange("cooldownMultiplier", 1.0, 0, 100), "cooldownMultiplier");
        allowInfiniteOutput = put(b.comment("The creative-only Abyssal Upgrade can grant infinite output").define("allowInfiniteOutput", true), "allowInfiniteOutput");
        singleUser = put(b.comment("Only one Abyss user per world").define("singleUser", true), "singleUser");
        griefing = put(b.comment("Abyss payloads consume blocks (also needs mobGriefing)").define("griefing", true), "griefing");
        blockBudgetPerTick = put(b.defineInRange("blockBudgetPerTick", 4000, 0, 100000), "blockBudgetPerTick");
        maxBreakHardness = put(b.comment("Blocks harder than this are never consumed (-1 hardness blocks like bedrock never are)").defineInRange("maxBreakHardness", 100.0, 0, 1e9), "maxBreakHardness");
        b.pop().push("vessel");
        vesselStrength = put(b.comment("Scales the vessel's raw body: +20 HP, +6 attack, +10 armor, +6 toughness, knockback resistance, +15% speed at 1.0").defineInRange("vesselStrength", 1.0, 0, 100), "vesselStrength");
        vesselImmunities = put(b.comment("The vessel ignores falling, drowning, suffocation, freezing, fire and lava, starvation, poison and wither").define("vesselImmunities", true), "vesselImmunities");
        sovereignty = put(b.comment("The vessel cannot die: a lethal blow dissolves the body into the abyss and it re-forms. Raw setHealth(0), die() and removal by other mods are refused; max health cannot be lowered").define("sovereignty", true), "sovereignty");
        reformSeconds = put(b.comment("A second lethal blow within this time is simply held at 1 HP instead of re-forming again").defineInRange("reformSeconds", 4, 0, 600), "reformSeconds");
        escapeIntoRealm = put(b.comment("A lethal blow pulls the vessel through a rift into the Abyss Realm as it re-forms (Ronova's escape)").define("escapeIntoRealm", true), "escapeIntoRealm");
        comboDamage = put(b.comment("Scales the abyssal combo (combat mode)").defineInRange("comboDamage", 1.0, 0, 1000), "comboDamage");
        beamCarves = put(b.comment("The heavy Abyssal Beam erases soft blocks along its path").define("beamCarves", true), "beamCarves");
        b.pop().push("spores");
        sporeLifeSeconds = put(b.defineInRange("sporeLifeSeconds", 300, 5, 36000), "sporeLifeSeconds");
        sporesPerBurst = put(b.defineInRange("sporesPerBurst", 64, 1, 2048), "sporesPerBurst");
        maxSporesPerUser = put(b.comment("Performance cap. Oldest spores fade when exceeded").defineInRange("maxSporesPerUser", 512, 16, 8192), "maxSporesPerUser");
        maxSporesPerHost = put(b.defineInRange("maxSporesPerHost", 12, 1, 256), "maxSporesPerHost");
        infectSeconds = put(b.comment("Seconds for a latched spore to multiply inside its host").defineInRange("infectSeconds", 8, 1, 3600), "infectSeconds");
        sporeRange = put(b.comment("How far a burst throws spores before they slow to a drift").defineInRange("sporeRange", 28.0, 2, 512), "sporeRange");
        sporeLatchChance = put(b.comment("Base chance a spore latches onto an entity it passes (scaled by Combat Power)").defineInRange("sporeLatchChance", 0.4, 0, 1), "sporeLatchChance");
        sporeJumpChance = put(b.comment("Chance per second that a spore jumps from a host to something touching it").defineInRange("sporeJumpChance", 0.3, 0, 1), "sporeJumpChance");
        b.pop().push("payloads");
        nukeDamage = put(b.defineInRange("nukeDamage", 40.0, 0, 1e6), "nukeDamage");
        nukeRadius = put(b.defineInRange("nukeRadius", 7.0, 1, 64), "nukeRadius");
        disintegrateDamage = put(b.defineInRange("disintegrateDamage", 30.0, 0, 1e6), "disintegrateDamage");
        beelzebubCpRatio = put(b.comment("Beelzebub devours anything whose Combat Power is below yours x this (or under 30% HP)").defineInRange("beelzebubCpRatio", 1.5, 0, 1000), "beelzebubCpRatio");
        transportSeconds = put(b.defineInRange("transportSeconds", 45, 1, 36000), "transportSeconds");
        riftSeconds = put(b.defineInRange("riftSeconds", 30, 1, 36000), "riftSeconds");
        b.pop().push("domain");
        domainExpansionRate = put(b.comment("Blocks per tick the open domain grows (the abyss spreads at the speed of light; this is the game's version)").defineInRange("domainExpansionRate", 6.0, 0.1, 512), "domainExpansionRate");
        domainMaxRadius = put(b.defineInRange("domainMaxRadius", 96.0, 4, 1024), "domainMaxRadius");
        closedMaxRadius = put(b.defineInRange("closedMaxRadius", 20.0, 4, 64), "closedMaxRadius");
        domainCrush = put(b.comment("Damage per second of the infinite weight, scaled by Combat Power").defineInRange("domainCrush", 3.0, 0, 1e5), "domainCrush");
        domainDecay = put(b.comment("Open domains rot the world inside them into abyss (also needs griefing + mobGriefing)").define("domainDecay", true), "domainDecay");
        domainDecayPerTick = put(b.comment("Blocks the domain corrupts per tick").defineInRange("domainDecayPerTick", 220, 0, 20000), "domainDecayPerTick");
        domainScarsPermanent = put(b.comment("false: the world heals once the domain ends. true: the abyss rot stays").define("domainScarsPermanent", false), "domainScarsPermanent");
        domainDissolveItems = put(b.comment("Dropped items inside a domain dissolve (except the user's own)").define("domainDissolveItems", true), "domainDissolveItems");
        domainSeconds = put(b.defineInRange("domainSeconds", 25, 1, 3600), "domainSeconds");
        closedSeconds = put(b.defineInRange("closedSeconds", 35, 1, 3600), "closedSeconds");
        b.pop().push("defence");
        negationCost = put(b.comment("Output spent per point of damage negated. Damage beyond your remaining output leaks through").defineInRange("negationCost", 1.0, 0, 1000), "negationCost");
        negateVoidAndKill = put(b.comment("Negation also stops /kill and the void").define("negateVoidAndKill", false), "negateVoidAndKill");
        soulResistance = put(b.comment("Fraction of soul damage resisted").defineInRange("soulResistance", 0.9, 0, 1), "soulResistance");
        corrosionStrength = put(b.defineInRange("corrosionStrength", 1.0, 0, 100), "corrosionStrength");
        spareAllies = put(b.comment("Teammates of the Abyss user are not corroded").define("spareAllies", true), "spareAllies");
        b.pop().push("bodies");
        maxClones = put(b.defineInRange("maxClones", 4, 0, 32), "maxClones");
        cloneStatScale = put(b.comment("Clones are this fraction of the main body").defineInRange("cloneStatScale", 0.85, 0.05, 1.0), "cloneStatScale");
        cloneRespawnSeconds = put(b.defineInRange("cloneRespawnSeconds", 20, 0, 3600), "cloneRespawnSeconds");
        maxPuppets = put(b.defineInRange("maxPuppets", 4, 0, 64), "maxPuppets");
        puppetSeconds = put(b.defineInRange("puppetSeconds", 120, 5, 36000), "puppetSeconds");
        triangleTicks = put(b.comment("Longest a Triangle clash can last").defineInRange("triangleTicks", 120, 20, 2400), "triangleTicks");
        b.pop();
        SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        fxDensity = putC(c.defineInRange("fxDensity", 1.0, 0.1, 3.0), "fxDensity");
        screenShake = putC(c.defineInRange("screenShake", 1.0, 0.0, 3.0), "screenShake");
        crystalSkin = putC(c.comment("Show Abyss users as the crystalline void body").define("crystalSkin", true), "crystalSkin");
        angelEyesHud = putC(c.comment("Show Combat Power and stats of what you look at").define("angelEyesHud", true), "angelEyesHud");
        domainFog = putC(c.define("domainFog", true), "domainFog");
        damageNumbers = putC(c.comment("Floating damage numbers on Arsenal hits").define("damageNumbers", true), "damageNumbers");
        slotCastsImmediately = putC(c.comment("X + 1-8: use the skill in that place right away (false: only select it)").define("slotCastsImmediately", true), "slotCastsImmediately");
        wheelOrder = putC(c.comment("Order of the skills on the wheel (drag them in the wheel to change it)").define("wheelOrder", ""), "wheelOrder");
        CLIENT_SPEC = c.build();
    }

    private static <T extends ForgeConfigSpec.ConfigValue<?>> T put(T v, String k) { ALL.put(k, v); return v; }
    private static <T extends ForgeConfigSpec.ConfigValue<?>> T putC(T v, String k) { CLIENT.put(k, v); return v; }
    private Config() {}
}
