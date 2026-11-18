package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.data.Absorbed;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Beelzebub: the abyss eats things and keeps what made them strong. Devoured creatures add a share of their health,
 * damage, armor and speed to the user, grant their immunities (fire, drowning, flight, undead resistances), teach their
 * projectiles to Mimic Volley, and can be released again as abyss puppets (Brood).
 */
public final class Beelzebub {
    private static final UUID HP = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3a01"), DMG = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3a02"),
            ARM = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3a03"), SPD = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3a04");
    // the vessel's own body
    private static final UUID V_HP = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3b01"), V_DMG = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3b02"),
            V_ARM = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3b03"), V_TGH = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3b04"),
            V_KB = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3b05"), V_SPD = UUID.fromString("a1b5e7c0-3e2a-4f61-8d0a-6c0f1e2b3b06");
    public static final java.util.Set<UUID> OURS = java.util.Set.of(HP, DMG, ARM, SPD, V_HP, V_DMG, V_ARM, V_TGH, V_KB, V_SPD);

    /** Devour one target. by = the body doing it (player or clone). */
    public static void devour(ServerPlayer p, Entity by, LivingEntity t) {
        if (Util.friend(p.getUUID(), t) || !t.isAlive()) return;
        ServerLevel l = (ServerLevel) t.level();
        AbyssData d = AbyssData.get(p);
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Util.fx(l, S2CFx.DEVOUR, c, Math.max(1.2f, Math.max(t.getBbWidth(), t.getBbHeight()) * 0.9f), 1, 0, t.getId(), by.getId());
        Util.sound(l, c, ModSounds.DEVOUR.get(), 2f, 0.7f);
        int mine = CombatPower.of(p), theirs = CombatPower.of(t);
        boolean whole = !(t instanceof Player) && (theirs <= mine * Config.beelzebubCpRatio.get() || t.getHealth() < t.getMaxHealth() * 0.3f || Domains.sureHit(p.getUUID(), t));
        if (t instanceof Player tp) {
            // players are bitten, not swallowed: a chunk of their health and every buff they carry
            for (MobEffectInstance mi : new ArrayList<>(tp.getActiveEffects()))
                if (mi.getEffect().isBeneficial()) { tp.removeEffect(mi.getEffect()); p.addEffect(new MobEffectInstance(mi)); }
            Util.hurt(p.getUUID(), by, tp, tp.getMaxHealth() * 0.35f, "devoured");
            record(p, t, theirs);
            return;
        }
        if (!whole) {
            Util.hurt(p.getUUID(), by, t, t.getMaxHealth() * 0.3f, "devoured");
            Corrosion.add(t, 2, 200);
            return;
        }
        record(p, t, theirs);
        float heal = Math.min(20, t.getMaxHealth() * 0.25f);
        p.heal(heal);
        t.getPersistentData().putBoolean("abyss_no_drops", true);
        Util.sureHitInFlight = true;
        t.invulnerableTime = 0;
        try { t.hurt(Util.src(l, "devoured", by), t.getHealth() + t.getAbsorptionAmount() + 10000); } finally { Util.sureHitInFlight = false; }
        if (t.isAlive() && t.canChangeDimensions() && !(t instanceof Player)) t.discard();
        Actions.mastery(p, 0.3f);
    }

    /** A mouth opening at a point: devours creatures nearby, swallows projectiles (and learns them) and bites out blocks. */
    public static void mouth(ServerPlayer p, ServerLevel l, Vec3 at) {
        Util.fx(l, S2CFx.DEVOUR, at, 2.5f, 0, 0);
        Util.sound(l, at, ModSounds.DEVOUR.get(), 2f, 0.6f);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3), x -> x.isAlive() && !Util.friend(p.getUUID(), x))) devour(p, p, e);
        for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(5))) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(pr.getType());
            Absorbed a = new Absorbed();
            a.type = id.toString(); a.name = pr.getType().getDescription().getString(); a.flags.add("ranged:" + id);
            merge(AbyssData.get(p), a);
            pr.discard();
        }
        Payloads.consumeBlocks(l, at, 2.2, null);
        sync(p);
    }

    static void record(ServerPlayer p, LivingEntity t, int cp) {
        AbyssData d = AbyssData.get(p);
        Absorbed a = new Absorbed();
        a.type = t instanceof Player ? "minecraft:player" : BuiltInRegistries.ENTITY_TYPE.getKey(t.getType()).toString();
        a.name = t.getType().getDescription().getString();
        a.cp = cp;
        a.health = t.getMaxHealth();
        a.damage = (float) (t.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? t.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0);
        a.armor = (float) t.getAttributeValue(Attributes.ARMOR);
        a.speed = (float) t.getAttributeValue(Attributes.MOVEMENT_SPEED);
        a.flags.addAll(CombatPower.get(t).traits());
        a.flags.remove("boss"); a.flags.remove("abyss");
        ResourceLocation shot = CombatPower.SHOOTS.get(t.getType());
        if (shot != null) a.flags.add("ranged:" + shot);
        merge(d, a);
        applyPassives(p);
        p.displayClientMessage(Component.literal("§5Beelzebub took §d" + a.name + "§5 into the abyss."), true);
        sync(p);
    }

    static void merge(AbyssData d, Absorbed a) {
        for (Absorbed x : d.absorbed) {
            if (x.type.equals(a.type)) {
                x.count++; x.cp = Math.max(x.cp, a.cp); x.health = Math.max(x.health, a.health); x.damage = Math.max(x.damage, a.damage);
                x.armor = Math.max(x.armor, a.armor); x.speed = Math.max(x.speed, a.speed); x.flags.addAll(a.flags);
                d.absorbed.remove(x); d.absorbed.add(x);
                return;
            }
        }
        d.absorbed.add(a);
        if (d.absorbed.size() > 64) d.absorbed.sort(Comparator.comparingInt(x -> -x.cp));
        while (d.absorbed.size() > 64) d.absorbed.remove(d.absorbed.size() - 1);
    }

    private static void sync(ServerPlayer p) { AbyssData.get(p).dirty = true; CombatPower.invalidate(p); }

    /** Permanent stat share from everything devoured. */
    public static void applyPassives(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        set(p, Attributes.MAX_HEALTH, HP, d.awakened ? d.absorbedBonus("health") : 0, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.ATTACK_DAMAGE, DMG, d.awakened ? d.absorbedBonus("damage") : 0, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.ARMOR, ARM, d.awakened ? d.absorbedBonus("armor") : 0, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.MOVEMENT_SPEED, SPD, d.awakened ? d.absorbedBonus("speed") : 0, AttributeModifier.Operation.MULTIPLY_BASE);
        double k = d.awakened ? Config.vesselStrength.get() : 0;
        set(p, Attributes.MAX_HEALTH, V_HP, 20 * k, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.ATTACK_DAMAGE, V_DMG, 6 * k, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.ARMOR, V_ARM, 10 * k, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.ARMOR_TOUGHNESS, V_TGH, 6 * k, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.KNOCKBACK_RESISTANCE, V_KB, Math.min(0.8, 0.6 * k), AttributeModifier.Operation.ADDITION);
        set(p, Attributes.MOVEMENT_SPEED, V_SPD, 0.15 * k, AttributeModifier.Operation.MULTIPLY_BASE);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        boolean fly = d.awakened && d.has("flight");
        if (fly && !p.getAbilities().mayfly) { p.getAbilities().mayfly = true; p.onUpdateAbilities(); }
        if (!fly && p.getAbilities().mayfly && !p.isCreative() && !p.isSpectator() && p.getPersistentData().getBoolean("abyss_gave_flight")) {
            p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities();
        }
        p.getPersistentData().putBoolean("abyss_gave_flight", fly);
    }

    private static void set(ServerPlayer p, net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, double v, AttributeModifier.Operation op) {
        AttributeInstance i = p.getAttribute(a);
        if (i == null) return;
        i.removeModifier(id);
        if (v != 0) i.addPermanentModifier(new AttributeModifier(id, "abyss_beelzebub", v, op));
    }

    /** Transport: drag something into the Abyss. It comes back after a while, if it survives. */
    public static void transport(ServerPlayer p, LivingEntity t) {
        if (Util.friend(p.getUUID(), t) || !t.isAlive()) return;
        ServerLevel abyss = AbyssDim.level(p.server);
        if (abyss == null || t.level() == abyss) return;
        ServerLevel from = (ServerLevel) t.level();
        Vec3 back = t.position();
        Util.fx(from, S2CFx.TRANSPORT, back.add(0, t.getBbHeight() / 2, 0), Math.max(1, t.getBbHeight()), 1, 0, t.getId(), -1);
        Util.sound(from, back, ModSounds.RIFT.get(), 1.5f, 0.5f);
        Vec3 cell = AbyssDim.prison(abyss, Math.floorMod(t.getUUID().hashCode(), 32));
        Entity moved = Util.teleport(t, abyss, cell, t.getYRot(), t.getXRot(), false);
        UUID id = moved.getUUID();
        Scheduler.later(Config.transportSeconds.get() * 20, () -> {
            Entity e = abyss.getEntity(id);
            if (e == null) { ServerPlayer sp = p.server.getPlayerList().getPlayer(id); if (sp != null && sp.level() == abyss) e = sp; }
            if (e != null && e.isAlive() && e.level() == abyss) {
                Util.teleport(e, from, back, e.getYRot(), e.getXRot(), false);
                Util.fx(from, S2CFx.STEP, back.add(0, 1, 0), 1, 0, 0);
            }
        });
        Actions.mastery(p, 0.2f);
    }

    // ---------------- Inhale ----------------

    /** The mouth inhales for three seconds. */
    public static void inhale(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        d.inhaleUntil = p.level().getGameTime() + 60;
        Util.fx(p.serverLevel(), S2CFx.INHALE, maw(p), 60, 0, 0, p.getId(), -1);
        Util.sound(p.serverLevel(), p.position(), ModSounds.DEVOUR.get(), 2f, 0.4f);
    }

    static Vec3 maw(ServerPlayer p) { return p.getEyePosition().add(p.getLookAngle().scale(2.2)).subtract(0, 0.2, 0); }

    public static void inhaleTick(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 m = maw(p);
        int mine = CombatPower.of(p);
        for (Entity e : l.getEntities(p, new AABB(m, m).inflate(12), x -> x.isAlive() && !(x instanceof com.andy.abyss.entity.Rift))) {
            if (Util.friend(p.getUUID(), e)) continue;
            Vec3 in = m.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double dist = in.length();
            if (dist > 12) continue;
            if (dist < 1.6) {
                if (e instanceof Projectile pr) {
                    ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(pr.getType());
                    Absorbed a = new Absorbed();
                    a.type = id.toString(); a.name = pr.getType().getDescription().getString(); a.flags.add("ranged:" + id);
                    merge(AbyssData.get(p), a);
                    pr.discard();
                } else if (e instanceof net.minecraft.world.entity.item.ItemEntity it) {
                    it.discard();
                } else if (e instanceof LivingEntity le && !(le instanceof Player) && (CombatPower.of(le) <= mine * Config.beelzebubCpRatio.get() || le.getHealth() < le.getMaxHealth() * 0.3f)) {
                    devour(p, p, le);
                }
                continue;
            }
            double pull = e instanceof LivingEntity ? 0.12 : 0.3;
            if (e instanceof LivingEntity le) pull *= 1 - le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) * 0.6;
            e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(in.normalize().scale(pull * (1.4 - dist / 12))));
            e.hurtMarked = true;
        }
    }

    // ---------------- Brood: puppets ----------------

    public static void brood(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        List<Absorbed> spawnable = new ArrayList<>();
        for (Absorbed a : d.absorbed) {
            if (a.type.equals("minecraft:player")) continue;
            EntityType<?> t = EntityType.byString(a.type).orElse(null);
            if (t != null && t.canSummon() && t.getCategory() != MobCategory.MISC) spawnable.add(a);
        }
        if (spawnable.isEmpty()) { p.displayClientMessage(Component.literal("§5Beelzebub has devoured no creature to release."), true); return; }
        int pick = p.getPersistentData().getInt("abyss_brood_i") % spawnable.size();
        p.getPersistentData().putInt("abyss_brood_i", pick + 1);
        Absorbed a = spawnable.get(spawnable.size() - 1 - pick);
        // cap
        ServerLevel l = p.serverLevel();
        List<Mob> mine = puppets(l, p.getUUID());
        if (mine.size() >= Config.maxPuppets.get()) { Mob old = mine.get(0); Util.fx(l, S2CFx.CLONE_DEATH, old.position().add(0, 1, 0), 0.6f, 0, 0); old.discard(); }
        Entity e = EntityType.byString(a.type).get().create(l);
        if (!(e instanceof Mob m)) { if (e != null) e.discard(); return; }
        Vec3 at = Util.aim(p, 12).point();
        Vec3 pos = Rifts.safe(l, at.x, at.y, at.z);
        m.moveTo(pos.x, pos.y, pos.z, p.getYRot(), 0);
        try { m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null); } catch (Exception ignored) {}
        m.getPersistentData().putUUID("abyss_puppet_owner", p.getUUID());
        m.getPersistentData().putLong("abyss_puppet_until", l.getGameTime() + Config.puppetSeconds.get() * 20L);
        m.getPersistentData().putBoolean("abyss_no_drops", true);
        m.setPersistenceRequired();
        m.setCustomName(Component.literal("§5Abyssal " + a.name));
        l.addFreshEntity(m);
        Util.fx(l, S2CFx.CLONE_SPAWN, pos.add(0, m.getBbHeight() / 2, 0), Math.max(1, m.getBbHeight()), 1, 0, m.getId(), -1);
        Util.sound(l, pos, ModSounds.RIFT.get(), 1f, 0.7f);
    }

    public static List<Mob> puppets(ServerLevel l, UUID owner) {
        List<Mob> out = new ArrayList<>();
        for (Entity e : l.getAllEntities())
            if (e instanceof Mob m && e.getPersistentData().hasUUID("abyss_puppet_owner") && owner.equals(e.getPersistentData().getUUID("abyss_puppet_owner"))) out.add(m);
        return out;
    }

    /** Puppet brains: fight for the owner, never against them; dissolve when their time is up. */
    public static void tickPuppet(Mob m) {
        var tag = m.getPersistentData();
        ServerLevel l = (ServerLevel) m.level();
        if (l.getGameTime() > tag.getLong("abyss_puppet_until")) {
            Util.fx(l, S2CFx.CLONE_DEATH, m.position().add(0, m.getBbHeight() / 2, 0), 0.8f, 0, 0);
            m.discard();
            return;
        }
        UUID o = tag.getUUID("abyss_puppet_owner");
        ServerPlayer p = Util.player(o);
        LivingEntity t = m.getTarget();
        if (t != null && (Util.friend(o, t) || !t.isAlive())) { m.setTarget(null); t = null; }
        if (t == null && p != null && p.level() == l) {
            LivingEntity want = p.getLastHurtMob() != null && p.getLastHurtMob().isAlive() ? p.getLastHurtMob() : p.getLastHurtByMob();
            if (want == null || !want.isAlive() || Util.friend(o, want)) want = Bodies.nearestEnemy(m, 20);
            if (want != null) m.setTarget(want);
            else if (m.distanceToSqr(p) > 100) m.getNavigation().moveTo(p, 1.2);
        }
    }

    // ---------------- Mimic Volley ----------------

    public static void volley(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        LinkedHashSet<String> kinds = new LinkedHashSet<>();
        for (int i = d.absorbed.size() - 1; i >= 0 && kinds.size() < 4; i--)
            for (String f : d.absorbed.get(i).flags) if (f.startsWith("ranged:")) kinds.add(f.substring(7));
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        if (kinds.isEmpty()) {
            // nothing learned yet: fire seeker spores instead
            for (int i = 0; i < 5; i++) {
                Vec3 dir = look.add(p.getRandom().nextGaussian() * 0.06, p.getRandom().nextGaussian() * 0.06, p.getRandom().nextGaussian() * 0.06);
                com.andy.abyss.spore.SporeManager.shoot(l, p.getUUID(), p.getId(), eye, dir);
            }
            Util.fx(l, S2CFx.VOLLEY, eye.add(look), 0, 0, 0, p.getId(), -1);
            return;
        }
        int n = 0;
        for (String k : kinds) {
            EntityType<?> t = EntityType.byString(k).orElse(null);
            if (t == null) continue;
            for (int i = 0; i < 3; i++) {
                Entity e = t.create(l);
                if (!(e instanceof Projectile pr)) { if (e != null) e.discard(); break; }
                Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale((n % 5 - 2) * 0.35);
                pr.moveTo(eye.x + side.x + look.x, eye.y - 0.1 + look.y, eye.z + side.z + look.z, p.getYRot(), p.getXRot());
                pr.setOwner(p);
                Vec3 dir = look.add(p.getRandom().nextGaussian() * 0.04, p.getRandom().nextGaussian() * 0.04, p.getRandom().nextGaussian() * 0.04);
                if (pr instanceof AbstractHurtingProjectile hp) {
                    Vec3 a = dir.normalize().scale(0.12);
                    hp.xPower = a.x; hp.yPower = a.y; hp.zPower = a.z;
                    hp.setDeltaMovement(dir.normalize().scale(1.2));
                } else pr.shoot(dir.x, dir.y, dir.z, 2.6f, 1.5f);
                l.addFreshEntity(pr);
                n++;
            }
        }
        Util.fx(l, S2CFx.VOLLEY, eye.add(look), n, 0, 0, p.getId(), -1);
    }

    private Beelzebub() {}
}
