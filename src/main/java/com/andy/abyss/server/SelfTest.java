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
        check(out, "inside-out nuke", !z.isAlive() || z.getHealth() < hp);
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
