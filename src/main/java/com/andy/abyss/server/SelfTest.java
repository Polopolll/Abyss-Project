package com.andy.abyss.server;

import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.data.Payload;
import com.andy.abyss.entity.AbyssBody;
import com.andy.abyss.spore.Spore;
import com.andy.abyss.spore.SporeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** /abyss selftest: exercises every system around the calling player and reports. */
public final class SelfTest {
    public static int errors;

    public static int run(CommandSourceStack src) {
        try { return run0(src); } catch (Throwable t) {
            com.andy.abyss.Abyss.LOG.error("selftest crashed", t);
            src.sendFailure(Component.literal("selftest crashed: " + t));
            return 0;
        }
    }

    private static int run0(CommandSourceStack src) {
        ServerPlayer p;
        try { p = src.getPlayerOrException(); } catch (Exception e) { return 0; }
        List<String> out = new ArrayList<>();
        int before = errors;
        ServerLevel l = p.serverLevel();
        AbyssData d = AbyssData.get(p);
        if (!d.awakened) Awakening.awaken(p, true);
        d.output = d.outputCap();
        check(out, "awakened", d.awakened);
        // CP
        Zombie z = EntityType.ZOMBIE.create(l);
        z.moveTo(p.getX() + 6, p.getY(), p.getZ());
        l.addFreshEntity(z);
        int zcp = CombatPower.of(z), pcp = CombatPower.of(p);
        check(out, "CP zombie=" + zcp + " you=" + pcp, zcp > 40 && zcp < 400 && pcp > zcp);
        // spores: tag the zombie directly
        Spore s = SporeManager.plant(l, p.getUUID(), p.getId(), z.position(), z);
        check(out, "spore latches", s.latched() && SporeManager.on(z, p.getUUID()).size() == 1);
        SporeManager.burst(l, p.getUUID(), p, 32);
        check(out, "burst", SporeManager.count(p.getUUID()) >= 33);
        // inside-out nuke
        float hp = z.getHealth();
        Payloads.request(p, Payload.NUKE.ordinal(), new int[0], new int[]{z.getId()});
        for (int i = 0; i < 20; i++) Scheduler.tick(); // the 18-tick build-up
        check(out, "inside-out nuke (after its build-up)", !z.isAlive() || z.getHealth() < hp);
        // domain
        Domains.open(p, 40);
        check(out, "open domain", Domains.ownedBy(p.getUUID()) != null);
        Zombie z2 = EntityType.ZOMBIE.create(l);
        z2.moveTo(p.getX() + 3, p.getY(), p.getZ());
        l.addFreshEntity(z2);
        Domains.tick(p.server);
        check(out, "sure hit inside domain", Domains.sureHit(p.getUUID(), z2));
        Domains.end(Domains.ownedBy(p.getUUID()));
        // clone + swap
        int clonesBefore = d.clones.size();
        d.cloneReadyAt = 0;
        boolean sent = Bodies.sendClone(p, l, p.position().add(4, 0, 0));
        check(out, "clone sent", sent && d.clones.size() == clonesBefore + 1);
        if (sent) {
            Vec3 start = p.position();
            boolean ok = Bodies.swap(p, d.clones.get(d.clones.size() - 1), true);
            check(out, "swap into clone", ok && !d.inMain && p.position().distanceTo(start) > 2);
            ok = Bodies.swap(p, d.mainBody, true);
            check(out, "swap back to main", ok && d.inMain);
            Bodies.recall(p, -1);
        }
        // triangle
        boolean tri = Triangle.start(p, z2);
        check(out, "triangle starts", tri);
        Triangle.ALL.clear();
        // beelzebub on the zombie
        Beelzebub.devour(p, p, z2);
        check(out, "beelzebub devours + absorbs", !z2.isAlive() && d.absorbed.stream().anyMatch(a -> a.type.equals("minecraft:zombie")));
        check(out, "abyss dimension", AbyssDim.level(p.server) != null);
        // arsenal: every weapon's chain lands
        Zombie z3 = EntityType.ZOMBIE.create(l);
        z3.moveTo(p.getX(), p.getY(), p.getZ() + 2.5);
        z3.setNoAi(true);
        z3.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000);
        z3.setHealth(2000);
        l.addFreshEntity(z3);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z3.getEyePosition());
        // clear leftovers from the earlier checks so the lock can only find the dummy
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), x -> x != p && x != z3 && !(x instanceof net.minecraft.world.entity.player.Player))) e.discard();
        if (d.form != com.andy.abyss.data.Ability.ARSENAL.ordinal()) Forms.toggle(p, com.andy.abyss.data.Ability.ARSENAL); // open it (a toggle would close it if already open)
        check(out, "arsenal form opens", d.form == com.andy.abyss.data.Ability.ARSENAL.ordinal());
        for (com.andy.abyss.combat.Weapon w : com.andy.abyss.combat.Weapon.values()) {
            d.weapon = w.ordinal();
            // put the dummy back in front (the previous weapon's knockback moved it)
            net.minecraft.world.phys.Vec3 front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
            z3.teleportTo(front.x, p.getY(), front.z);
            z3.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z3.getEyePosition());
            float hp0 = z3.getHealth();
            d.busyUntil = 0; d.chainUntil = 0; d.pending = -1;
            Arsenal.input(p, com.andy.abyss.net.C2SInput.LMB, com.andy.abyss.net.C2SInput.RELEASE, 0, 1);
            for (int i = 0; i < 40; i++) Scheduler.tick();
            LivingEntity lk = Arsenal.lock(p, w.reach * 2.4f + 4);
            check(out, w.title + " basic lands" + (z3.getHealth() < hp0 ? "" : " (lock=" + (lk == null ? "none" : lk.getType().toShortString() + (lk == z3 ? "*" : "")) + " d=" + String.format("%.1f", z3.distanceTo(p)) + " alive=" + z3.isAlive() + ")"), z3.getHealth() < hp0);
        }
        d.form = -1;
        // infinite output
        boolean wasInf = d.infinite;
        d.infinite = true;
        check(out, "infinite output", d.spend(1e9f));
        d.infinite = wasInf;
        // sovereignty
        boolean wasCreative = p.isCreative();
        if (wasCreative) p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        d.output = d.outputCap(); d.reformedAt = -10000;
        boolean esc = com.andy.abyss.Config.escapeIntoRealm.get();
        com.andy.abyss.Config.escapeIntoRealm.set(false); // the rest of the test happens here, not in the Realm
        p.setHealth(0);
        check(out, "sovereignty refuses setHealth(0)", p.isAlive() && p.getHealth() > 0);
        d.output = d.outputCap(); d.reformedAt = -10000;
        try { p.hurt(l.damageSources().magic(), 10000); } finally { com.andy.abyss.Config.escapeIntoRealm.set(esc); }
        check(out, "sovereignty re-forms after a lethal hit", p.isAlive());
        if (wasCreative) p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        // domain rot and healing
        Domains.open(p, 20);
        for (int i = 0; i < 30; i++) Domains.tick(p.server);
        check(out, "domain still up after 30 ticks (alive=" + p.isAlive() + " hp=" + p.getHealth() + ")", Domains.ownedBy(p.getUUID()) != null);
        int infected = Infection.count(p.serverLevel());
        check(out, "domain infects the ground (" + infected + " blocks in " + p.level().dimension().location() + ")", infected > 0 || !Util.griefing(p.serverLevel()));
        if (Domains.ownedBy(p.getUUID()) != null) Domains.end(Domains.ownedBy(p.getUUID()));
        for (int i = 0; i < 400; i++) Domains.tick(p.server);
        check(out, "the infection recedes afterwards (" + Infection.count(l) + " left)", Infection.count(l) == 0);
        SporeManager.clear(p.getUUID());
        check(out, "no task errors", errors == before);
        long fails = out.stream().filter(x -> x.startsWith("§c")).count();
        for (String x : out) src.sendSuccess(() -> Component.literal(x), false);
        src.sendSuccess(() -> Component.literal(fails == 0 ? "§aAbyss self-test passed." : "§c" + fails + " check(s) failed."), false);
        return fails == 0 ? 1 : 0;
    }

    private static void check(List<String> out, String what, boolean ok) { out.add((ok ? "§a✔ " : "§c✘ ") + what); }
    private SelfTest() {}
}
