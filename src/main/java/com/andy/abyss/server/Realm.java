package com.andy.abyss.server;

import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CSouls;
import com.andy.abyss.registry.ModSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Abyss Realm (Authority form, RMB). The Abyss reflects a world at the same coordinates: you step through the rift
 * and are at the same place, in the galaxy. Here you fly, you heal, and you see the souls of everyone in the reflected
 * world through anything. RMB leaves (a rift opens in the chosen world at the same spot), LMB held peeks through an
 * eye of the abyss at that spot, mouse 5 chooses the world.
 */
public final class Realm {
    public static ServerLevel target(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        ServerLevel l = byName(p, d.realmTarget);
        if (l == null) l = byName(p, d.realmFrom);
        if (l == null || AbyssDim.is(l)) l = p.server.overworld();
        return l;
    }

    private static ServerLevel byName(ServerPlayer p, String s) {
        if (s == null || s.isEmpty()) return null;
        return p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(s)));
    }

    public static void setTarget(ServerPlayer p, ResourceLocation dim) {
        ServerLevel l = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (l == null || AbyssDim.is(l)) return;
        AbyssData d = AbyssData.get(p);
        d.realmTarget = dim.toString();
        d.dirty = true;
        p.displayClientMessage(Component.literal("§5The Realm now reflects §f" + dim.getPath().replace('_', ' ')), true);
    }

    public static void enter(ServerPlayer p) {
        ServerLevel abyss = AbyssDim.level(p.server);
        if (abyss == null || AbyssDim.is(p.level())) return;
        AbyssData d = AbyssData.get(p);
        if (!d.spend(25)) { Actions.noOutput(p); return; }
        ServerLevel from = p.serverLevel();
        d.realmFrom = from.dimension().location().toString();
        if (d.realmTarget.isEmpty()) d.realmTarget = d.realmFrom;
        Vec3 at = p.position();
        Util.fx(from, S2CFx.REALM_ENTER, at.add(0, 1, 0), 0, 0, 0, p.getId(), -1);
        Util.sound(from, at, ModSounds.RIFT.get(), 2f, 0.5f);
        double y = Mth.clamp(at.y, abyss.getMinBuildHeight() + 2, abyss.getMaxBuildHeight() - 4);
        Util.teleport(p, abyss, new Vec3(at.x, y, at.z), p.getYRot(), p.getXRot(), false);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        Util.fx(abyss, S2CFx.REALM_ENTER, p.position().add(0, 1, 0), 1, 0, 0, p.getId(), -1);
        d.dirty = true;
    }

    public static void leave(ServerPlayer p) {
        if (!AbyssDim.is(p.level())) return;
        AbyssData d = AbyssData.get(p);
        if (d.peeking) endPeek(p);
        ServerLevel to = target(p);
        Vec3 at = p.position();
        Vec3 dest = Rifts.safe(to, at.x, Mth.clamp(at.y, to.getMinBuildHeight() + 1, to.getMaxBuildHeight() - 3), at.z);
        Util.fx(p.serverLevel(), S2CFx.REALM_EXIT, at.add(0, 1, 0), 0, 0, 0, p.getId(), -1);
        Rifts.pair(p.getUUID(), p.serverLevel(), at.add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2)), p.getYRot() + 180, to, dest, p.getYRot());
        Util.teleport(p, to, dest, p.getYRot(), p.getXRot(), false);
        Util.fx(to, S2CFx.REALM_EXIT, dest.add(0, 1, 0), 1, 0, 0, p.getId(), -1);
        if (!p.isCreative() && !p.isSpectator() && !d.has("flight")) { p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities(); }
        d.dirty = true;
    }

    public static void startPeek(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (d.peeking || !AbyssDim.is(p.level())) return;
        ServerLevel to = target(p);
        d.peeking = true;
        d.peekX = p.getX(); d.peekY = p.getY(); d.peekZ = p.getZ();
        d.peekMode = p.gameMode.getGameModeForPlayer().getId();
        p.getPersistentData().putBoolean("abyss_peeking", true);
        p.getPersistentData().putInt("abyss_peek_mode", d.peekMode);
        p.setGameMode(GameType.SPECTATOR);
        Vec3 at = new Vec3(p.getX(), Mth.clamp(p.getY(), to.getMinBuildHeight() + 1, to.getMaxBuildHeight() - 2), p.getZ());
        Util.teleport(p, to, at, p.getYRot(), p.getXRot(), false);
        Util.fx(to, S2CFx.PEEK, at.add(0, 1.6, 0), 1, 0, 0, p.getId(), -1);
        Util.sound(to, at, ModSounds.HEARTBEAT.get(), 1f, 0.6f);
    }

    public static void endPeek(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (!d.peeking) return;
        d.peeking = false;
        p.getPersistentData().remove("abyss_peeking");
        Util.fx(p.serverLevel(), S2CFx.PEEK, p.position().add(0, 1.6, 0), 0, 0, 0, p.getId(), -1);
        ServerLevel abyss = AbyssDim.level(p.server);
        GameType back = GameType.byId(d.peekMode < 0 ? 0 : d.peekMode);
        if (abyss != null) Util.teleport(p, abyss, new Vec3(d.peekX, d.peekY, d.peekZ), p.getYRot(), p.getXRot(), false);
        p.setGameMode(back);
        p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
    }

    /** After a crash or logout mid-peek, put the player back. */
    public static void restoreOnLogin(ServerPlayer p) {
        if (!p.getPersistentData().getBoolean("abyss_peeking")) return;
        p.getPersistentData().remove("abyss_peeking");
        p.setGameMode(GameType.byId(p.getPersistentData().getInt("abyss_peek_mode")));
        ServerLevel abyss = AbyssDim.level(p.server);
        if (abyss != null) Util.teleport(p, abyss, p.position(), p.getYRot(), p.getXRot(), false);
    }

    public static void tick(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        long gt = p.level().getGameTime();
        if (d.peeking) {
            // the eye stays where it opened: you can look around, not walk away
            if (Math.abs(p.getX() - d.peekX) > 0.3 || Math.abs(p.getZ() - d.peekZ) > 0.3) p.teleportTo(d.peekX, p.getY(), d.peekZ);
            if (gt % 20 == 0) Util.fx(p.serverLevel(), S2CFx.PEEK, p.position().add(0, 1.6, 0), 2, 0, 0, p.getId(), -1);
            sendSouls(p, p.serverLevel());
            return;
        }
        if (!AbyssDim.is(p.level()) || !d.awakened) return;
        if (!p.getAbilities().mayfly) { p.getAbilities().mayfly = true; p.onUpdateAbilities(); }
        if (gt % 20 == 0) { p.heal(1); p.getFoodData().eat(1, 0.5f); }
        if (gt % 10 == 0) sendSouls(p, target(p));
    }

    private static void sendSouls(ServerPlayer p, ServerLevel world) {
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer o : world.players()) if (o != p && !o.isSpectator()) others.add(o);
        String[] names = new String[others.size()];
        float[] xyz = new float[others.size() * 3];
        for (int i = 0; i < others.size(); i++) {
            ServerPlayer o = others.get(i);
            names[i] = o.getGameProfile().getName();
            xyz[i * 3] = (float) o.getX(); xyz[i * 3 + 1] = (float) o.getY(); xyz[i * 3 + 2] = (float) o.getZ();
        }
        Net.to(p, new S2CSouls(world.dimension().location().toString(), names, xyz));
    }

    public static boolean is(Level l) { return AbyssDim.is(l); }
    private Realm() {}
}
