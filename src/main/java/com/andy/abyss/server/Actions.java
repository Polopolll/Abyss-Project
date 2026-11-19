package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.C2SAction;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CCp;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CSync;
import com.andy.abyss.registry.ModSounds;
import com.andy.abyss.spore.SporeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Key presses from the client, casting, charging, output refill and the sync back to the client. */
public final class Actions {
    private static final Set<UUID> SPREADING = new HashSet<>();

    public static void handle(ServerPlayer p, int action, int arg) {
        AbyssData d = AbyssData.get(p);
        if (action == C2SAction.CLASH_PUSH) { Triangle.push(p); return; }
        if (action == C2SAction.CP_QUERY) { cpQuery(p, arg); return; }
        if (!d.awakened || p.isSpectator()) return;
        switch (action) {
            case C2SAction.CAST_START -> {
                if (Triangle.of(p) != null) { Triangle.push(p); return; }
                Ability a = Ability.byIndex(d.selected);
                if (a == null || a.type == Ability.Type.CLIENT || !ready(p, a)) return;
                if (a == Ability.DOMAIN && Domains.ownedBy(p.getUUID()) != null) { Forms.toggle(p, Ability.DOMAIN); return; }
                if (a.charged()) {
                    d.chargeAbility = a.ordinal(); d.charge = 0; d.dirty = true;
                    Util.sound(p.serverLevel(), p.position(), ModSounds.CHARGE.get(), 1.2f, 0.5f);
                } else cast(p, a);
            }
            case C2SAction.CAST_RELEASE -> { d.domainVariant = Math.max(0, Math.min(1, arg)); release(p); }
            case C2SAction.SELECT -> { if (Ability.byIndex(arg) != null) { d.selected = arg; d.dirty = true; } }
            case C2SAction.SPREAD_START -> {
                if (d.sporeMode == AbyssData.SPREAD) {
                    if (!d.spend(20)) { noOutput(p); return; }
                    SporeManager.burst(p.serverLevel(), p.getUUID(), p, Config.sporesPerBurst.get());
                    Util.fx(p.serverLevel(), S2CFx.SPREAD, p.position().add(0, 1.2, 0), 1, 0, 0, p.getId(), -1);
                    SPREADING.add(p.getUUID());
                    Bodies.mirror(p, "spread");
                    mastery(p, 0.03f);
                } else shootSpore(p);
            }
            case C2SAction.SPREAD_STOP -> SPREADING.remove(p.getUUID());
            case C2SAction.SPORE_MODE -> {
                d.sporeMode = (d.sporeMode + 1) % 3; d.dirty = true;
                p.displayClientMessage(Component.literal("§5Spores: §d" + new String[]{"Spread", "Plant", "Tag"}[d.sporeMode]), true);
            }
            case C2SAction.SHOOT_SPORE -> shootSpore(p);
            case C2SAction.SWAP_NEXT -> Bodies.cycle(p, 1);
            case C2SAction.SWAP_PREV -> Bodies.cycle(p, -1);
            case C2SAction.SWAP_MAIN -> { if (!d.inMain) Bodies.swap(p, d.mainBody, true); }
            case C2SAction.SWAP_TO -> Bodies.swapToSlot(p, arg);
            case C2SAction.ORDER -> Bodies.order(p, arg / 8, arg % 8);
            case C2SAction.ORDER_ALL -> Bodies.order(p, -1, arg);
            case C2SAction.RECALL_ONE -> Bodies.recall(p, arg);
            case C2SAction.SEND_CLONE -> sendClone(p);
            case C2SAction.DESCEND -> Bodies.descend(p);
            case C2SAction.CAST_SLOT -> { if (Ability.byIndex(arg) != null) { d.selected = arg; d.dirty = true; handle(p, C2SAction.CAST_START, 0); } }
            default -> {}
        }
    }

    private static void shootSpore(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (!d.spend(3)) { noOutput(p); return; }
        ServerLevel l = p.serverLevel();
        if (d.sporeMode == AbyssData.PLANT) {
            Util.Aim aim = Util.aim(p, 48);
            SporeManager.plant(l, p.getUUID(), p.getId(), aim.point(), aim.entity());
        } else {
            SporeManager.shoot(l, p.getUUID(), p.getId(), p.getEyePosition().add(p.getLookAngle().scale(0.5)), p.getLookAngle());
        }
        Util.fx(l, S2CFx.VOLLEY, p.getEyePosition().add(p.getLookAngle()), 0, 1, 0, p.getId(), -1);
    }

    static boolean ready(ServerPlayer p, Ability a) {
        AbyssData d = AbyssData.get(p);
        long now = p.level().getGameTime();
        if (now < d.cooldownUntil[a.ordinal()]) {
            p.displayClientMessage(Component.literal("§5" + a.title + " §7" + (d.cooldownUntil[a.ordinal()] - now + 19) / 20 + "s"), true);
            return false;
        }
        return true;
    }

    static void cooldown(ServerPlayer p, Ability a) {
        AbyssData d = AbyssData.get(p);
        if (!Config.cooldownsEnabled.get()) { d.cooldownUntil[a.ordinal()] = 0; d.dirty = true; return; }
        d.cooldownUntil[a.ordinal()] = p.level().getGameTime() + (long) (a.cooldownSec * 20L * Config.cooldownMultiplier.get());
        d.dirty = true;
    }

    public static void cast(ServerPlayer p, Ability a) {
        AbyssData d = AbyssData.get(p);
        switch (a) {
            case DOMAIN -> {
                if (d.domainVariant == 1) Domains.closed(p, Math.max(d.charge, 20)); else Domains.open(p, Math.max(d.charge, 20));
                if (Domains.ownedBy(p.getUUID()) != null && d.form != Ability.DOMAIN.ordinal()) Forms.toggle(p, Ability.DOMAIN);
            }
            case TRIANGLE -> {
                LivingEntity t = Util.aim(p, 32).entity();
                if (t == null) t = domainTarget(p);
                if (t == null) { p.displayClientMessage(Component.literal("§7Look at a target."), true); return; }
                if (!d.spend(20)) { noOutput(p); return; }
                if (!Triangle.start(p, t)) return;
            }
            case NEGATION -> {
                d.negation = !d.negation;
                p.displayClientMessage(Component.literal("§5Negation " + (d.negation ? "§fON" : "§7off")), true);
            }
            case SPORES, ARSENAL, AUTHORITY, BEELZEBUB, BODIES -> { Forms.toggle(p, a); return; }
            default -> { return; }
        }
        cooldown(p, a);
        mastery(p, 0.05f);
        d.dirty = true;
    }

    public static void castDevour(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (!d.spend(30)) { noOutput(p); return; }
        Util.Aim aim = Util.aim(p, 24);
        LivingEntity t = aim.entity();
        if (t == null) t = domainTarget(p);
        if (t != null) Beelzebub.devour(p, p, t); else Beelzebub.mouth(p, p.serverLevel(), aim.point());
        Bodies.mirror(p, "beelzebub");
    }

    public static void sendClone(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (!d.spend(40)) { noOutput(p); return; }
        Vec3 at = Util.aim(p, 64).point();
        ServerLevel l = p.serverLevel();
        if (!Bodies.sendClone(p, l, Rifts.safe(l, at.x, at.y, at.z))) d.output += 40;
    }

    private static LivingEntity domainTarget(ServerPlayer p) {
        Domains.Domain dom = Domains.ownedBy(p.getUUID());
        if (dom == null || !dom.contains(p)) return null;
        // sure hit: inside the domain, a missed aim still finds the nearest enemy
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(dom.radius), x -> x.isAlive() && dom.contains(x) && !Util.friend(p.getUUID(), x))) {
            double dd = e.distanceToSqr(p);
            if (dd < bd) { bd = dd; best = e; }
        }
        return best;
    }

    private static void release(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (d.chargeAbility < 0) return;
        Ability a = Ability.byIndex(d.chargeAbility);
        d.chargeAbility = -1;
        if (a != null) cast(p, a);
        d.charge = 0;
        d.dirty = true;
    }

    public static void tickPlayer(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (!d.awakened) return;
        long gt = p.level().getGameTime();
        float cap = d.outputCap();
        if (d.chargeAbility >= 0) {
            float invest = d.isInfinite() ? cap * 0.12f : Math.min(d.output, cap * 0.035f);
            if (!d.isInfinite()) d.output -= invest;
            d.charge = Math.min(d.isInfinite() ? 2000 : 600, d.charge + invest);
            if (gt % 5 == 0) Util.fx(p.serverLevel(), S2CFx.CHARGE, p.position().add(0, 1, 0), d.charge, d.chargeAbility, 0, p.getId(), -1);
            d.dirty = true;
        } else if (d.output < cap) {
            d.output = Math.min(cap, d.output + cap * Config.outputRefillPerSecond.get().floatValue() / 20f);
            if (gt % 4 == 0) d.dirty = true;
        } else if (d.output > cap) d.output = cap;
        if (SPREADING.contains(p.getUUID()) && gt % 6 == 0) {
            if (d.spend(6)) SporeManager.burst(p.serverLevel(), p.getUUID(), p, Math.max(4, Config.sporesPerBurst.get() / 4));
            else SPREADING.remove(p.getUUID());
        }
        if (d.has("water_breathing") && p.getAirSupply() < p.getMaxAirSupply()) p.setAirSupply(p.getMaxAirSupply());
        if (d.has("fire_immune") && p.isOnFire()) p.clearFire();
        if (gt % 40 == 0) Bodies.rememberOutside(p);
        Arsenal.tick(p);
        Realm.tick(p);
        if (d.inhaleUntil > gt) Beelzebub.inhaleTick(p);
        if (d.form == Ability.DOMAIN.ordinal() && Domains.ownedBy(p.getUUID()) == null) Forms.close(p);
        if (gt % 10 == 0) Sovereignty.lockMaxHealth(p);
        if (gt % 100 == 0) Bodies.saveTo(p);
        if (AbyssDim.is(p.level()) && p.getY() < -60) {
            Vec3 t = AbyssDim.throne(p.serverLevel(), p.getUUID());
            p.teleportTo(t.x, t.y, t.z); p.fallDistance = 0;
        }
        if (d.dirty && gt - d.lastSync >= 2 || gt - d.lastSync >= 20) sync(p);
    }

    public static void sync(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        CompoundTag t = d.save();
        t.putFloat("cap", d.outputCap());
        t.putBoolean("inf", d.isInfinite());
        t.putBoolean("cooldowns", Config.cooldownsEnabled.get());
        t.putLong("counterLeft", Math.max(0, d.counterUntil - p.level().getGameTime()));
        t.putBoolean("inRealm", AbyssDim.is(p.level()));
        t.putBoolean("guarding", d.guardSince >= 0);
        t.putInt("chain", d.comboStep);
        t.putLong("chainLeft", Math.max(0, d.chainUntil - p.level().getGameTime()));
        t.putBoolean("inhaling", d.inhaleUntil > p.level().getGameTime());
        t.putFloat("charge", d.charge);
        t.putInt("chargeAbility", d.chargeAbility);
        t.put("bodies", Bodies.rosterTag(p));
        t.putInt("spores", SporeManager.count(p.getUUID()));
        t.putInt("latched", SporeManager.latched(p.getUUID()));
        t.putInt("cp", CombatPower.of(p));
        t.putInt("maxClones", Config.maxClones.get());
        long now = p.level().getGameTime();
        int[] cd = new int[Ability.values().length];
        for (int i = 0; i < cd.length; i++) cd[i] = (int) Math.max(0, d.cooldownUntil[i] - now);
        t.putIntArray("cd", cd);
        t.putInt("cloneCd", (int) Math.max(0, d.cloneReadyAt - now));
        t.putInt("negDown", (int) Math.max(0, d.negationDownUntil - now));
        Net.to(p, new S2CSync(t));
        d.dirty = false;
        d.lastSync = now;
    }

    /** Angel Eyes: the client asks about what it is looking at. */
    private static void cpQuery(ServerPlayer p, int id) {
        Entity e = p.serverLevel().getEntity(id);
        if (!(e instanceof LivingEntity le) || e.distanceToSqr(p) > 128 * 128) return;
        var b = CombatPower.get(le);
        float dmg = (float) (le.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? le.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0);
        int sp = SporeManager.on(le, p.getUUID()).size();
        Net.to(p, new S2CCp(id, b.total(), le.getHealth(), le.getMaxHealth(), (float) le.getAttributeValue(Attributes.ARMOR), dmg, sp, String.join(",", b.traits())));
    }

    public static void mastery(ServerPlayer p, float amount) {
        AbyssData d = AbyssData.get(p);
        d.mastery = Math.min(100, d.mastery + amount * Config.masteryRate.get().floatValue());
        d.dirty = true;
    }

    public static void noOutput(ServerPlayer p) { p.displayClientMessage(Component.literal("§5Not enough output."), true); }

    public static void stopSpreading(UUID u) { SPREADING.remove(u); }
    private Actions() {}
}
