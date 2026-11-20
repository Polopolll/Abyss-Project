package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.cp.CombatPower;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.spore.SporeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class Commands {
    public static void register(CommandDispatcher<CommandSourceStack> disp) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("abyss");
        root.then(literal("cp")
                .executes(c -> cpLook(c))
                .then(argument("targets", EntityArgument.entities()).executes(c -> {
                    for (Entity e : EntityArgument.getEntities(c, "targets")) if (e instanceof LivingEntity le) cpReport(c.getSource(), le);
                    return 1;
                }))
                .then(literal("set").requires(s -> s.hasPermission(2)).then(argument("targets", EntityArgument.entities()).then(argument("multiplier", DoubleArgumentType.doubleArg(0, 1e9)).executes(c -> {
                    double m = DoubleArgumentType.getDouble(c, "multiplier");
                    for (Entity e : EntityArgument.getEntities(c, "targets")) { e.getPersistentData().putDouble("abyss_cp_mult", m); CombatPower.invalidate(e); }
                    c.getSource().sendSuccess(() -> Component.literal("Combat Power multiplier set to " + m), true);
                    return 1;
                })))));
        root.then(literal("awaken").requires(s -> s.hasPermission(2)).executes(c -> awaken(c, c.getSource().getPlayerOrException()))
                .then(argument("player", EntityArgument.player()).executes(c -> awaken(c, EntityArgument.getPlayer(c, "player")))));
        root.then(literal("revoke").requires(s -> s.hasPermission(2)).executes(c -> { Awakening.revoke(c.getSource().getPlayerOrException()); return 1; })
                .then(argument("player", EntityArgument.player()).executes(c -> { Awakening.revoke(EntityArgument.getPlayer(c, "player")); return 1; })));
        root.then(literal("mastery").requires(s -> s.hasPermission(2)).then(argument("player", EntityArgument.player()).then(argument("value", FloatArgumentType.floatArg(0, 100)).executes(c -> {
            ServerPlayer p = EntityArgument.getPlayer(c, "player");
            AbyssData.get(p).mastery = FloatArgumentType.getFloat(c, "value");
            AbyssData.get(p).dirty = true;
            c.getSource().sendSuccess(() -> Component.literal("Mastery set."), true);
            return 1;
        }))));
        root.then(literal("refill").requires(s -> s.hasPermission(2)).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            AbyssData d = AbyssData.get(p);
            d.output = d.outputCap(); d.cloneReadyAt = 0; Arrays.fill(d.cooldownUntil, 0); d.negationDownUntil = 0; d.dirty = true;
            return 1;
        }));
        root.then(literal("cast").requires(s -> s.hasPermission(2)).then(argument("ability", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(Arrays.stream(Ability.values()).map(a -> a.name().toLowerCase(Locale.ROOT)), java.util.stream.Stream.of("domain_open", "domain_closed")), b))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String name = StringArgumentType.getString(c, "ability").toUpperCase(Locale.ROOT);
                    AbyssData d = AbyssData.get(p);
                    if (name.startsWith("DOMAIN")) { d.domainVariant = name.endsWith("CLOSED") ? 1 : 0; name = "DOMAIN"; }
                    Ability a = Ability.valueOf(name);
                    if (a.charged()) d.charge = 120;
                    Actions.cast(p, a);
                    d.charge = 0;
                    return 1;
                })));
        root.then(literal("spores")
                .then(literal("clear").requires(s -> s.hasPermission(2)).executes(c -> { SporeManager.clear(null); return 1; }))
                .then(literal("burst").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    SporeManager.burst(p.serverLevel(), p.getUUID(), p, Config.sporesPerBurst.get());
                    return 1;
                }))
                .then(literal("count").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> Component.literal(SporeManager.count(p.getUUID()) + " spores, " + SporeManager.latched(p.getUUID()) + " latched"), false);
                    return 1;
                })));
        root.then(literal("domain").then(literal("end").requires(s -> s.hasPermission(2)).executes(c -> { Domains.clearAll(); return 1; })));
        root.then(literal("clones")
                .then(literal("recall").executes(c -> { Bodies.recall(c.getSource().getPlayerOrException(), -1); return 1; }))
                .then(literal("list").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (var t : Bodies.rosterTag(p)) c.getSource().sendSuccess(() -> Component.literal(t.toString()), false);
                    return 1;
                })));
        root.then(literal("demo").requires(s -> s.hasPermission(2))
                .then(literal("move").then(argument("weapon", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 6)).then(argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 20)).executes(c -> {
                    Arsenal.demo(c.getSource().getPlayerOrException(), com.andy.abyss.combat.Weapon.byIndex(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "weapon")), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "index"));
                    return 1;
                }))))
                .then(literal("form").then(argument("ability", StringArgumentType.word()).executes(c -> {
                    var a = com.andy.abyss.data.Ability.valueOf(StringArgumentType.getString(c, "ability").toUpperCase(java.util.Locale.ROOT));
                    Forms.toggle(c.getSource().getPlayerOrException(), a);
                    return 1;
                })))
                .then(literal("press").then(argument("button", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).then(argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 400)).executes(c -> {
                    // press a form button and hold it for some ticks (screenshots without a mouse)
                    var pl = c.getSource().getPlayerOrException();
                    int b = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "button"), t = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "ticks");
                    Forms.input(pl, b, com.andy.abyss.net.C2SInput.PRESS, 0, 0);
                    if (t >= 8) Scheduler.later(8, () -> Forms.input(pl, b, com.andy.abyss.net.C2SInput.HOLD, 0, 8));
                    Scheduler.later(Math.max(1, t), () -> Forms.input(pl, b, com.andy.abyss.net.C2SInput.RELEASE, 0, t));
                    return 1;
                }))))
                .then(literal("triangle").executes(c -> {
                    var pl = c.getSource().getPlayerOrException();
                    Triangle.start(pl, Util.aim(pl, 32).entity());
                    return 1;
                }))
                .then(literal("crater").then(argument("radius", FloatArgumentType.floatArg(1, 30)).executes(c -> {
                    var pl = c.getSource().getPlayerOrException();
                    Util.Aim a = Util.aim(pl, 64);
                    Destruction.crater(pl.serverLevel(), a.point(), FloatArgumentType.getFloat(c, "radius"), FloatArgumentType.getFloat(c, "radius") * 0.5f, 50, 0.8f);
                    return 1;
                })))
                .then(literal("fx").then(argument("type", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 64)).then(argument("a", FloatArgumentType.floatArg()).executes(c -> {
                    var pl = c.getSource().getPlayerOrException();
                    Util.fx(pl.serverLevel(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "type"), pl.position().add(pl.getLookAngle().multiply(1, 0, 1).normalize().scale(4)).add(0, 1, 0),
                            FloatArgumentType.getFloat(c, "a"), 0, 0, pl.getId(), -1);
                    return 1;
                })))));
        root.then(literal("selftest").requires(s -> s.hasPermission(2)).executes(c -> SelfTest.run(c.getSource())));
        // config
        LiteralArgumentBuilder<CommandSourceStack> cfg = literal("config");
        cfg.requires(s -> s.hasPermission(2));
        cfg.then(literal("list").executes(c -> { for (var en : Config.ALL.entrySet()) c.getSource().sendSuccess(() -> Component.literal("§d" + en.getKey() + "§7 = §f" + en.getValue().get()), false); return 1; }));
        cfg.then(argument("key", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Config.ALL.keySet(), b))
                .executes(c -> {
                    var v = Config.ALL.get(StringArgumentType.getString(c, "key"));
                    if (v == null) return 0;
                    c.getSource().sendSuccess(() -> Component.literal(StringArgumentType.getString(c, "key") + " = " + v.get()), false);
                    return 1;
                })
                .then(argument("value", StringArgumentType.greedyString()).executes(c -> setConfig(c))));
        root.then(cfg);
        disp.register(root);
    }

    private static int awaken(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        String err = Awakening.awaken(p, true);
        c.getSource().sendSuccess(() -> Component.literal(err == null ? p.getGameProfile().getName() + " is now the Abyss." : err), true);
        return 1;
    }

    private static int cpLook(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        LivingEntity t = Util.aim(p, 64).entity();
        cpReport(c.getSource(), t != null ? t : p);
        return 1;
    }

    private static void cpReport(CommandSourceStack s, LivingEntity e) {
        CombatPower.invalidate(e);
        var b = CombatPower.get(e);
        s.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "§d%s§7: §fCP %,d §8(survival %.1f · offence %.1f · mobility %.2f · powers %.2f · x%.2f) §7%s",
                e.getName().getString(), b.total(), b.survival(), b.offence(), b.mobility(), b.powers(), b.multiplier(), String.join(", ", b.traits()))), false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int setConfig(CommandContext<CommandSourceStack> c) {
        String key = StringArgumentType.getString(c, "key"), val = StringArgumentType.getString(c, "value");
        ForgeConfigSpec.ConfigValue v = Config.ALL.get(key);
        if (v == null) { c.getSource().sendFailure(Component.literal("No such key")); return 0; }
        try {
            Object cur = v.get();
            Object n = cur instanceof Boolean ? Boolean.parseBoolean(val) : cur instanceof Integer ? Integer.parseInt(val) : cur instanceof Double ? Double.parseDouble(val) : val;
            v.set(n);
            c.getSource().sendSuccess(() -> Component.literal(key + " = " + n), true);
            return 1;
        } catch (Exception ex) { c.getSource().sendFailure(Component.literal("Bad value")); return 0; }
    }
    private Commands() {}
}
