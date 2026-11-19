package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

/**
 * The vessel cannot simply be killed. A lethal blow (or another mod setting its health to 0, calling die() or removing
 * it) dissolves the body into the abyss and re-forms it at half health. A second lethal blow inside reformSeconds is
 * held where it is. /kill and the void still work unless negateVoidAndKill is on. Our own collapse (main body destroyed) is let through.
 */
public final class Sovereignty {
    /** Set while the Abyss itself kills its vessel (main body destroyed, revoke). */
    public static boolean allowDeath;

    public static boolean active(Player p) {
        if (!(p instanceof ServerPlayer) || allowDeath || !Config.sovereignty.get() || p.isCreative() || p.isSpectator()) return false;
        AbyssData d = AbyssData.get(p);
        return d.awakened && d.inMain;
    }

    private static boolean exempt(DamageSource s) {
        if (s == null || Config.negateVoidAndKill.get()) return false;
        return s.is(DamageTypes.FELL_OUT_OF_WORLD) || s.is(DamageTypes.GENERIC_KILL);
    }

    /** Health is about to drop to 0 (or below). Returns true if the death was prevented. */
    public static boolean intercept(ServerPlayer p) {
        if (!active(p) || exempt(p.getLastDamageSource())) return false;
        AbyssData d = AbyssData.get(p);
        long now = p.level().getGameTime();
        if (now - d.reformedAt < Config.reformSeconds.get() * 20L) return true; // held at its current health
        d.reformedAt = now;
        p.server.execute(() -> reform(p));
        return true;
    }

    private static void reform(ServerPlayer p) {
        if (!p.isAlive()) return;
        p.setHealth(Math.max(1, p.getMaxHealth() * 0.5f));
        for (MobEffectInstance mi : new ArrayList<>(p.getActiveEffects())) if (!mi.getEffect().isBeneficial()) p.removeEffect(mi.getEffect());
        p.clearFire();
        p.invulnerableTime = 40;
        Util.fx(p.serverLevel(), S2CFx.REFORM, p.position().add(0, 1, 0), 1, 0, 0, p.getId(), -1);
        Util.sound(p.serverLevel(), p.position(), ModSounds.COLLAPSE.get(), 1.5f, 0.6f);
        Util.sound(p.serverLevel(), p.position(), ModSounds.HEARTBEAT.get(), 1.5f, 0.8f);
        // the abyss takes its vessel out of harm's way: through a rift into the Realm
        if (Config.escapeIntoRealm.get() && !AbyssDim.is(p.level()) && !AbyssData.get(p).peeking) Realm.enter(p, true);
    }

    /** Nothing else may lower the vessel's max health. */
    public static void lockMaxHealth(ServerPlayer p) {
        if (!active(p)) return;
        AttributeInstance a = p.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return;
        for (AttributeModifier m : new ArrayList<>(a.getModifiers()))
            if (m.getAmount() < 0 && !Beelzebub.OURS.contains(m.getId())) a.removeModifier(m.getId());
        if (a.getBaseValue() < 20) a.setBaseValue(20);
    }
    private Sovereignty() {}
}
