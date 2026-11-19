package com.andy.abyss.cp;

import com.andy.abyss.Config;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.entity.AbyssBody;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.FlyingMob;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Combat Power: one number for how dangerous any living thing is, read from what the game knows about it — health,
 * armor, damage, ranged and explosive attacks, mobility, effects, bosses, modded attributes and the biggest hit it has
 * actually landed. Works for any mod's mobs; datapacks (data/&lt;ns&gt;/abyss_cp/*.json), commands and other mods
 * ({@link #registerProvider}) can adjust it. A zombie is about 100.
 */
public final class CombatPower {
    public record Breakdown(double survival, double offence, double mobility, double powers, double multiplier, int total, List<String> traits) {}

    private static final Map<Integer, Breakdown> CACHE = new HashMap<>();
    private static final Map<Integer, Long> CACHED_AT = new HashMap<>();
    private static final Map<UUID, Float> PEAK = new HashMap<>();
    private static final Map<EntityType<?>, Float> PEAK_BY_TYPE = new HashMap<>();
    /** Which projectile each kind of shooter fires, learned when it fires. Beelzebub copies these. */
    public static final Map<EntityType<?>, ResourceLocation> SHOOTS = new HashMap<>();
    private static final List<ToDoubleFunction<LivingEntity>> PROVIDERS = new ArrayList<>();
    private static long now;

    /** Other mods: return a multiplier (1 = no change) for an entity. */
    public static void registerProvider(ToDoubleFunction<LivingEntity> p) { PROVIDERS.add(p); }

    public static void tick(long gameTime) {
        now = gameTime;
        if (gameTime % 1200 == 0) { CACHE.clear(); CACHED_AT.clear(); }
    }

    public static int of(LivingEntity e) { return get(e).total(); }

    public static Breakdown get(LivingEntity e) {
        Long at = CACHED_AT.get(e.getId());
        Breakdown b = CACHE.get(e.getId());
        if (b != null && at != null && now - at < 40) return b;
        b = compute(e);
        CACHE.put(e.getId(), b);
        CACHED_AT.put(e.getId(), now);
        return b;
    }

    public static void invalidate(Entity e) { CACHE.remove(e.getId()); }

    /** Called from LivingHurtEvent: remember the biggest single hit each attacker (and its kind) has landed. */
    public static void recordHit(Entity attacker, float amount) {
        if (attacker == null || amount <= 0 || !Float.isFinite(amount)) return;
        amount = Math.min(amount, 100000);
        PEAK.merge(attacker.getUUID(), amount, Math::max);
        PEAK_BY_TYPE.merge(attacker.getType(), amount * 0.7f, Math::max);
    }

    public static Breakdown compute(LivingEntity e) {
        List<String> traits = new ArrayList<>();
        // ---- survival
        double hp = Math.max(1, e.getMaxHealth());
        double armor = attr(e, Attributes.ARMOR), tough = attr(e, Attributes.ARMOR_TOUGHNESS);
        double ref = 8;
        double red = Math.min(20, Math.max(armor / 5, armor - 4 * ref / (tough + 8))) / 25.0;
        double survival = hp / Math.max(0.12, 1 - red);
        if (e.fireImmune()) { survival *= 1.12; traits.add("fire_immune"); }
        if (e.getMobType() == MobType.UNDEAD) { survival *= 1.05; traits.add("undead"); }
        double kb = attr(e, Attributes.KNOCKBACK_RESISTANCE);
        survival *= 1 + 0.15 * Math.min(1, kb);
        boolean boss = e instanceof EnderDragon || e instanceof WitherBoss || !e.canChangeDimensions();
        if (boss) { survival *= 1.5; traits.add("boss"); }

        // ---- offence
        double melee = e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? attr(e, Attributes.ATTACK_DAMAGE) : 0;
        if (e instanceof Player p) {
            melee = attr(p, Attributes.ATTACK_DAMAGE);
            double spd = attr(p, Attributes.ATTACK_SPEED);
            melee *= Math.min(2.5, Math.max(0.6, spd / 1.6));
        }
        double ranged = 0, explosive = 0;
        boolean flying = e instanceof FlyingMob || e instanceof FlyingAnimal || e instanceof EnderDragon || e instanceof WitherBoss;
        boolean teleports = false;
        if (e instanceof RangedAttackMob) ranged = Math.max(ranged, 5);
        if (e instanceof Creeper c) { explosive = 7 * 2 * 3 * (c.isPowered() ? 2 : 1) * 0.6; traits.add("explosive"); }
        if (e instanceof Mob m) {
            if (m.getNavigation() instanceof FlyingPathNavigation) flying = true;
            try {
                for (WrappedGoal g : m.goalSelector.getAvailableGoals()) {
                    String n = g.getGoal().getClass().getSimpleName().toLowerCase(Locale.ROOT);
                    if (n.contains("ranged") || n.contains("shoot") || n.contains("breath") || n.contains("fireball") || n.contains("projectile") || n.contains("spit")) ranged = Math.max(ranged, 6);
                    if (n.contains("teleport") || n.contains("blink")) teleports = true;
                    if (n.contains("explode") || n.contains("swell")) { explosive = Math.max(explosive, 25); if (!traits.contains("explosive")) traits.add("explosive"); }
                    if (n.contains("summon") || n.contains("spell")) ranged = Math.max(ranged, 8);
                }
            } catch (Exception ignored) {}
        }
        ResourceLocation shot = SHOOTS.get(e.getType());
        if (shot != null) { ranged = Math.max(ranged, 6); traits.add("ranged:" + shot); }
        else if (ranged > 0) traits.add("ranged");
        double peak = Math.max(PEAK.getOrDefault(e.getUUID(), 0f), PEAK_BY_TYPE.getOrDefault(e.getType(), 0f));
        double offence = Math.max(1, Math.max(Math.max(melee, ranged), Math.max(explosive, peak * 0.8)) + 0.25 * (melee + ranged));
        if (e instanceof EnderDragon) offence = Math.max(offence, 15);
        if (e instanceof WitherBoss) offence = Math.max(offence, 14);

        // ---- mobility
        double speed = attr(e, Attributes.MOVEMENT_SPEED);
        double base = e instanceof Player ? 0.1 : 0.25;
        double mobility = 0.75 + 0.25 * Math.min(4, Math.max(0.2, speed / base));
        if (e.getAttributes().hasAttribute(Attributes.FLYING_SPEED) || flying) { mobility *= 1.3; traits.add("flight"); }
        if (teleports) { mobility *= 1.2; traits.add("teleport"); }
        if (e.canBreatheUnderwater()) traits.add("water_breathing");

        // ---- powers
        double powers = 1;
        for (MobEffectInstance mi : e.getActiveEffects())
            if (mi.getEffect().getCategory() == MobEffectCategory.BENEFICIAL) powers += 0.06 * (mi.getAmplifier() + 1);
        double size = e.getBbWidth() * e.getBbHeight();
        powers *= Math.pow(Math.max(0.25, size / 1.08), 0.08);
        // modded attributes that are above their default are powers this formula doesn't know about
        int extra = 0;
        try {
            for (Attribute a : ForgeRegistries.ATTRIBUTES.getValues()) {
                ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(a);
                if (id == null || id.getNamespace().equals("minecraft") || id.getNamespace().equals("forge")) continue;
                AttributeInstance ai = e.getAttribute(a);
                if (ai != null && ai.getValue() > a.getDefaultValue() + 1e-6) extra++;
            }
        } catch (Exception ignored) {}
        powers *= 1 + Math.min(0.6, extra * 0.04);

        double mult = 1;
        if (e instanceof Player p) {
            AbyssData d = AbyssData.get(p);
            if (d.awakened) { mult *= 1 + d.outputCap() / 150.0; traits.add("abyss"); }
        }
        if (e instanceof AbyssBody body) { mult *= body.cpMultiplier(); traits.add("abyss"); }
        mult *= CpOverrides.multiplier(e);
        double manual = e.getPersistentData().getDouble("abyss_cp_mult");
        if (manual > 0) mult *= manual;
        for (var p : PROVIDERS) { try { double v = p.applyAsDouble(e); if (v > 0 && Double.isFinite(v)) mult *= v; } catch (Exception ignored) {} }

        double raw = 10 * Math.sqrt(survival * offence) * mobility * powers * mult + CpOverrides.add(e);
        double set = CpOverrides.set(e);
        int total = (int) Math.max(1, Math.min(Integer.MAX_VALUE - 1, set > 0 ? set : raw));
        return new Breakdown(survival, offence, mobility, powers, mult, total, traits);
    }

    private static double attr(LivingEntity e, Attribute a) {
        AttributeInstance i = e.getAttribute(a);
        return i == null ? 0 : i.getValue();
    }

    /** log-ratio advantage of a over b, clamped to [-1, 1]: 0 when equal, 1 when a is 10x stronger. */
    public static double advantage(int a, int b) {
        return Math.max(-1, Math.min(1, Math.log10(Math.max(1, a) / (double) Math.max(1, b))));
    }

    public static String typeId(Entity e) { var k = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()); return k.toString(); }

    private CombatPower() {}
}
