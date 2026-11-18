package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CWorld;
import com.andy.abyss.registry.ModEffects;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Triangle: a tug of war of Combat Power. A three-point sigil (the user, the target, and a point of the abyss above
 * them) locks both in place. Each tick the meter moves toward whoever has more power; each side adds to it by mashing
 * (the user's cast key, the target's jump key) and the user also pours output in. Reaching the end wins:
 * <ul>
 *   <li>User wins: the target is Shattered (defences broken, +50% damage taken, every hit on it lands), stunned 3 s,
 *   loses a quarter of its max HP and all its buffs.</li>
 *   <li>Target wins: the user is stunned 2 s and their negation is down for 10 s.</li>
 * </ul>
 */
public final class Triangle {
    public static final class Clash {
        public final ServerPlayer owner; public final LivingEntity target;
        public float meter; public int age; long lastOwnerPush, lastTargetPush;
        final Vec3 ownerAt, targetAt;
        Clash(ServerPlayer o, LivingEntity t) { owner = o; target = t; ownerAt = o.position(); targetAt = t.position(); }
    }
    public static final List<Clash> ALL = new ArrayList<>();

    public static Clash of(LivingEntity e) {
        for (Clash c : ALL) if (c.owner == e || c.target == e) return c;
        return null;
    }

    public static boolean start(ServerPlayer p, LivingEntity t) {
        if (t == null || of(p) != null || of(t) != null || Util.friend(p.getUUID(), t)) return false;
        ALL.add(new Clash(p, t));
        ServerLevel l = p.serverLevel();
        Util.sound(l, p.position(), ModSounds.TRIANGLE.get(), 2f, 0.8f);
        if (t instanceof ServerPlayer tp) tp.displayClientMessage(Component.literal("§fTRIANGLE — mash §e[Jump]§f to push back!"), true);
        return true;
    }

    /** A mash press. */
    public static void push(LivingEntity who) {
        Clash c = of(who);
        if (c == null) return;
        long now = who.level().getGameTime();
        int a = CombatPower.of(c.owner), b = CombatPower.of(c.target);
        if (who == c.owner) {
            if (now - c.lastOwnerPush < 2) return;
            c.lastOwnerPush = now;
            c.meter += 4f * (float) Math.pow(a / (double) Math.max(1, b), 0.15);
        } else {
            if (now - c.lastTargetPush < 2) return;
            c.lastTargetPush = now;
            c.meter -= 4f * (float) Math.pow(b / (double) Math.max(1, a), 0.15);
        }
    }

    public static void tick() {
        for (Clash c : new ArrayList<>(ALL)) {
            if (!c.owner.isAlive() || !c.target.isAlive() || c.owner.level() != c.target.level() || c.owner.distanceTo(c.target) > 64) { ALL.remove(c); continue; }
            c.age++;
            int a = CombatPower.of(c.owner), b = CombatPower.of(c.target);
            double adv = CombatPower.advantage(a, b);
            c.meter += (float) (adv * 1.4);
            AbyssData d = AbyssData.get(c.owner);
            float pour = d.outputCap() * 0.012f;
            if (d.spend(pour)) c.meter += 0.5f;
            if (!(c.target instanceof Player) && c.age % 6 == 0) c.meter -= c.target.getRandom().nextFloat() * 6f * (float) (1 - adv) * 0.5f;
            // pin both
            for (LivingEntity e : new LivingEntity[]{c.owner, c.target}) {
                Vec3 at = e == c.owner ? c.ownerAt : c.targetAt;
                if (e.position().distanceToSqr(at) > 0.04) e.teleportTo(at.x, at.y, at.z);
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(ModEffects.STUNNED.get(), 3, 0, false, false, false));
            }
            if (c.meter >= 100 || c.meter <= -100 || c.age >= Config.triangleTicks.get()) finish(c);
        }
    }

    private static void finish(Clash c) {
        ALL.remove(c);
        ServerLevel l = c.owner.serverLevel();
        boolean won = c.meter >= 0;
        Vec3 mid = c.owner.position().add(c.target.position()).scale(0.5).add(0, 1.5, 0);
        Util.fx(l, S2CFx.TRIANGLE_END, mid, won ? 1 : 0, 0, 0, c.owner.getId(), c.target.getId());
        Util.sound(l, mid, ModSounds.IMPACT.get(), 3f, won ? 0.7f : 1.3f);
        if (won) {
            LivingEntity t = c.target;
            for (MobEffectInstance mi : new ArrayList<>(t.getActiveEffects())) if (mi.getEffect().isBeneficial()) t.removeEffect(mi.getEffect());
            t.setAbsorptionAmount(0);
            t.addEffect(new MobEffectInstance(ModEffects.SHATTERED.get(), 15 * 20, 0));
            t.addEffect(new MobEffectInstance(ModEffects.STUNNED.get(), 60, 0));
            if (t instanceof Player tp) AbyssData.get(tp).negationDownUntil = l.getGameTime() + 300;
            Util.sureHitInFlight = true;
            t.invulnerableTime = 0;
            try { t.hurt(Util.src(l, "inside_out", c.owner), t.getMaxHealth() * 0.25f * Config.damageMultiplier.get().floatValue()); } finally { Util.sureHitInFlight = false; }
            Actions.mastery(c.owner, 1f);
            c.owner.displayClientMessage(Component.literal("§fTriangle won. §7" + t.getName().getString() + " is shattered."), true);
        } else {
            c.owner.addEffect(new MobEffectInstance(ModEffects.STUNNED.get(), 40, 0));
            AbyssData.get(c.owner).negationDownUntil = l.getGameTime() + 200;
            c.owner.displayClientMessage(Component.literal("§cTriangle lost. Negation is down."), true);
        }
    }

    public static List<S2CWorld.Clash> snapshot(ServerLevel l, ServerPlayer viewer) {
        List<S2CWorld.Clash> out = new ArrayList<>();
        for (Clash c : ALL) {
            if (c.owner.level() != l) continue;
            int you = c.owner == viewer ? 0 : c.target == viewer ? 1 : -1;
            out.add(new S2CWorld.Clash(c.owner.getId(), c.target.getId(), c.meter, c.age, you));
        }
        return out;
    }
    private Triangle() {}
}
