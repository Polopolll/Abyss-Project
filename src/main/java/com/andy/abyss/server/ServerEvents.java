package com.andy.abyss.server;

import com.andy.abyss.Abyss;
import com.andy.abyss.Config;
import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.cp.CpOverrides;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.entity.AbyssBody;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CVisual;
import com.andy.abyss.registry.ModEffects;
import com.andy.abyss.registry.ModSounds;
import com.andy.abyss.spore.SporeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Abyss.MODID)
public final class ServerEvents {
    private static final TagKey<DamageType> SOUL = TagKey.create(Registries.DAMAGE_TYPE, Abyss.id("soul_attacks"));
    /** Partial negation carried from the attack event to the hurt event: entity id -> fraction negated. */
    private static final Map<Integer, Float> PARTIAL = new HashMap<>();
    private static final Map<UUID, Long> LAST_NEGATE_FX = new HashMap<>();

    @Mod.EventBusSubscriber(modid = Abyss.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent public static void caps(RegisterCapabilitiesEvent e) { e.register(AbyssData.class); }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player) e.addCapability(Abyss.id("data"), new AbyssData.Provider());
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        AbyssData.get(e.getEntity()).copyFrom(AbyssData.get(e.getOriginal()));
        e.getOriginal().invalidateCaps();
        AbyssData d = AbyssData.get(e.getEntity());
        if (e.isWasDeath()) { d.inMain = true; d.slot = 0; d.mainBody = null; d.clones.clear(); }
        d.dirty = true;
    }

    /** Bulwark guard turns projectiles back at whoever fired them. */
    @SubscribeEvent
    public static void projectile(net.minecraftforge.event.entity.ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult eh) || !(eh.getEntity() instanceof ServerPlayer p)) return;
        AbyssData d = AbyssData.get(p);
        if (d.guardSince < 0 || d.form != com.andy.abyss.data.Ability.ARSENAL.ordinal()) return;
        Projectile pr = e.getProjectile();
        net.minecraft.world.phys.Vec3 to = pr.position().subtract(p.position()).multiply(1, 0, 1).normalize();
        if (to.dot(net.minecraft.world.phys.Vec3.directionFromRotation(0, p.getYRot())) < 0.2) return;
        e.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        pr.setDeltaMovement(pr.getDeltaMovement().scale(-1.2));
        pr.setOwner(p);
        pr.hurtMarked = true;
        Util.fx(p.serverLevel(), S2CFx.GUARD, p.position().add(0, 1, 0), 2, 0, 0, p.getId(), -1);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Realm.restoreOnLogin(p);
        Bodies.loadFrom(p);
        Beelzebub.applyPassives(p);
        Actions.sync(p);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (AbyssData.get(o).awakened) Net.to(p, visual(o));
        if (AbyssData.get(p).awakened) Net.around(p, visual(p));
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) { Infection.forget(p); Bodies.saveTo(p); Actions.stopSpreading(p.getUUID()); } }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) { Beelzebub.applyPassives(p); Actions.sync(p); Net.around(p, visual(p)); } }
    @SubscribeEvent public static void dim(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) { Actions.sync(p); Net.around(p, visual(p)); } }

    @SubscribeEvent
    public static void track(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer viewer && AbyssData.get(t).awakened)
            Net.to(viewer, visual(t));
    }

    public static S2CVisual visual(ServerPlayer p) { AbyssData d = AbyssData.get(p); return new S2CVisual(p.getId(), d.awakened, d.form, d.weapon); }

    @SubscribeEvent public static void reload(AddReloadListenerEvent e) { e.addListener(new CpOverrides()); }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) { Commands.register(e.getDispatcher()); }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) Bodies.saveTo(p);
        Domains.clearAll();
        Triangle.ALL.clear();
        SporeManager.clear(null);
        Scheduler.clear();
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload e) { if (e.getLevel() instanceof ServerLevel l) SporeManager.onLevelUnload(l); }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var server = e.getServer();
        Scheduler.tick();
        CombatPower.tick(server.overworld().getGameTime());
        for (ServerLevel l : server.getAllLevels()) {
            SporeManager.tick(l);
            SporeManager.sync(l);
        }
        Domains.tick(server);
        Triangle.tick();
        // the Abyss itself is hostile to everything that does not belong to it
        ServerLevel abyss = AbyssDim.level(server);
        if (abyss != null && server.getTickCount() % 60 == 0) {
            for (Entity en : abyss.getAllEntities()) {
                if (!(en instanceof LivingEntity le) || !le.isAlive() || le instanceof AbyssBody) continue;
                if (le instanceof Player pl && (AbyssData.get(pl).awakened || pl.isCreative() || pl.isSpectator())) continue;
                if (le.getPersistentData().hasUUID("abyss_puppet_owner")) continue;
                if (Corrosion.stacks(le) < 4) Corrosion.add(le, 1, 200);
            }
        }
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) Actions.tickPlayer(p);
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide) return;
        if ((le.tickCount + le.getId()) % 20 == 0 && le.hasEffect(ModEffects.CORROSION.get())) Corrosion.tick(le);
        if (le instanceof Mob m && (le.tickCount + le.getId()) % 10 == 0 && le.getPersistentData().hasUUID("abyss_puppet_owner")) Beelzebub.tickPuppet(m);
    }

    // ------------------------------------------------------------------ defence

    private static UUID abyssOwner(LivingEntity e) {
        if (e instanceof Player p && AbyssData.get(p).awakened) return p.getUUID();
        if (e instanceof AbyssBody b) return b.ownerId();
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        DamageSource src = e.getSource();
        if (src.getEntity() instanceof LivingEntity a && a != t && Triangle.of(t) != null && Triangle.of(t) == Triangle.of(a)) {
            // nothing lands during a clash between the two of them
            e.setCanceled(true);
            return;
        }
        UUID owner = abyssOwner(t);
        if (owner == null) return;
        ServerPlayer p = Util.player(owner);
        if (p == null) return;
        AbyssData d = AbyssData.get(p);
        if (t == p && !src.is(DamageTypes.FELL_OUT_OF_WORLD) && !src.is(DamageTypes.GENERIC_KILL)) {
            if (Arsenal.dodged(p, src.getEntity() != null)) { e.setCanceled(true); return; }
            if (Arsenal.guarded(p, src, e.getAmount())) { e.setCanceled(true); return; }
        }
        if (src.getEntity() != null && Util.friend(owner, src.getEntity()) && src.getEntity() != t) { e.setCanceled(true); return; }
        if (d.has("fire_immune") && src.is(DamageTypeTags.IS_FIRE)) { e.setCanceled(true); return; }
        if (Config.vesselImmunities.get() && (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_DROWNING)
                || src.is(DamageTypeTags.IS_FREEZING) || src.is(DamageTypes.IN_WALL) || src.is(DamageTypes.STARVE) || src.is(DamageTypes.WITHER)
                || src.is(DamageTypes.CRAMMING) || src.is(DamageTypes.FLY_INTO_WALL))) { e.setCanceled(true); if (t.isOnFire()) t.clearFire(); return; }
        if (!d.negation || t.level().getGameTime() < d.negationDownUntil || t.hasEffect(ModEffects.SHATTERED.get())) return;
        if (!Config.negateVoidAndKill.get() && (src.is(DamageTypes.FELL_OUT_OF_WORLD) || src.is(DamageTypes.GENERIC_KILL))) return;
        if (t instanceof Player pl && pl.isCreative()) return;
        float cost = e.getAmount() * Config.negationCost.get().floatValue();
        if (cost <= 0) return;
        if (d.output >= cost) {
            d.spend(cost);
            e.setCanceled(true);
            negateFx(t);
        } else if (d.output > 1) {
            PARTIAL.put(t.getId(), d.output / cost);
            d.output = 0;
            d.dirty = true;
            negateFx(t);
        }
    }

    private static void negateFx(LivingEntity t) {
        long now = t.level().getGameTime();
        Long last = LAST_NEGATE_FX.get(t.getUUID());
        if (last != null && now - last < 6) return;
        LAST_NEGATE_FX.put(t.getUUID(), now);
        Util.fx((ServerLevel) t.level(), S2CFx.NEGATE, t.position().add(0, t.getBbHeight() * 0.6, 0), 1, 0, 0, t.getId(), -1);
        Util.sound((ServerLevel) t.level(), t.position(), ModSounds.NEGATE.get(), 0.6f, 1.4f);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hurt(LivingHurtEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        float amt = e.getAmount();
        Float part = PARTIAL.remove(t.getId());
        if (part != null) amt *= Math.max(0, 1 - part);
        UUID owner = abyssOwner(t);
        DamageSource src = e.getSource();
        if (owner != null) {
            ServerPlayer p = Util.player(owner);
            String id = src.typeHolder().unwrapKey().map(k -> k.location().getPath()).orElse("");
            if (src.is(SOUL) || id.contains("soul")) amt *= 1 - Config.soulResistance.get().floatValue();
            if (p != null && AbyssData.get(p).has("explosive") && src.is(DamageTypeTags.IS_EXPLOSION)) amt *= 0.5f;
        }
        if (t.hasEffect(ModEffects.SHATTERED.get())) amt *= 1.5f;
        e.setAmount(amt);
        // a hit only counts as much as the target could actually lose (an execution is not a 'peak hit')
        if (!Util.sureHitInFlight) CombatPower.recordHit(src.getEntity(), Math.min(amt, t.getMaxHealth()));
    }

    /** Sure hit: an abyss blow inside the owner's domain (or on a shattered target) lands even if another mod cancelled it. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void sureAttack(LivingAttackEvent e) { if (Util.sureHitInFlight && e.isCanceled()) e.setCanceled(false); }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void sureHurt(LivingHurtEvent e) { if (Util.sureHitInFlight && e.isCanceled()) e.setCanceled(false); }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void sureDamage(LivingDamageEvent e) { if (Util.sureHitInFlight && e.isCanceled()) e.setCanceled(false); }

    @SubscribeEvent
    public static void effect(MobEffectEvent.Applicable e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        MobEffect eff = e.getEffectInstance().getEffect();
        if (BuiltInRegistries.MOB_EFFECT.getKey(eff) != null && Abyss.MODID.equals(BuiltInRegistries.MOB_EFFECT.getKey(eff).getNamespace())) {
            if (abyssOwner(t) != null && eff == ModEffects.CORROSION.get()) e.setResult(Event.Result.DENY);
            return;
        }
        UUID owner = abyssOwner(t);
        if (owner == null || eff.isBeneficial()) return;
        ServerPlayer p = Util.player(owner);
        if (p == null) return;
        AbyssData d = AbyssData.get(p);
        if ((d.has("undead") || Config.vesselImmunities.get()) && (eff == MobEffects.POISON || eff == MobEffects.WITHER)) { e.setResult(Event.Result.DENY); return; }
        // mental negation: harmful effects (debuffs, mind control) are refused while output lasts
        if (d.negation && t.level().getGameTime() >= d.negationDownUntil && !t.hasEffect(ModEffects.SHATTERED.get()) && d.spend(10)) e.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void heal(LivingHealEvent e) { if (!e.getEntity().level().isClientSide && Corrosion.blocksHealing(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Bodies.onPlayerDeath(p)) { e.setCanceled(true); return; }
        // killed by the abyss's own force: the body is unmade into abyssal ash (Ronova's ash, in the abyss's terms)
        var key = e.getSource().typeHolder().unwrapKey().orElse(null);
        if (key != null && key.location().getNamespace().equals(com.andy.abyss.Abyss.MODID) && e.getEntity().level() instanceof net.minecraft.server.level.ServerLevel l) {
            String t = key.location().getPath();
            if (t.equals("abyss") || t.equals("inside_out")) {
                LivingEntity v = e.getEntity();
                Util.fx(l, com.andy.abyss.net.S2CFx.ASH, v.position(), v.getBbWidth(), v.getBbHeight(), 0, v.getId(), -1);
            }
        }
    }

    @SubscribeEvent public static void drops(LivingDropsEvent e) { if (e.getEntity().getPersistentData().getBoolean("abyss_no_drops")) e.setCanceled(true); }
    @SubscribeEvent public static void xp(LivingExperienceDropEvent e) { if (e.getEntity().getPersistentData().getBoolean("abyss_no_drops")) e.setCanceled(true); }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent e) {
        LivingEntity me = e.getEntity(), t = e.getNewTarget();
        if (t == null) return;
        if (me.getPersistentData().hasUUID("abyss_puppet_owner") && Util.friend(me.getPersistentData().getUUID("abyss_puppet_owner"), t)) e.setCanceled(true);
        if (me instanceof AbyssBody b && Util.friend(b.ownerId(), t)) e.setCanceled(true);
    }

    /** Learn which projectile each kind of creature fires (Beelzebub copies them). */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof Projectile pr)) return;
        Entity o = pr.getOwner();
        if (o instanceof LivingEntity && !(o instanceof Player)) CombatPower.SHOOTS.putIfAbsent(o.getType(), BuiltInRegistries.ENTITY_TYPE.getKey(pr.getType()));
    }

    private ServerEvents() {}
}
