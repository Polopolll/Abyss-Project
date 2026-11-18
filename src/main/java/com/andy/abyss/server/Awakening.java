package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CVisual;
import com.andy.abyss.registry.ModSounds;
import com.andy.abyss.spore.SporeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Becoming the Abyss. One user per world by default (config singleUser). */
public final class Awakening {
    public static class WorldData extends SavedData {
        public UUID user;
        public String userName = "";
        @Override public CompoundTag save(CompoundTag t) { if (user != null) t.putUUID("user", user); t.putString("name", userName); return t; }
        static WorldData load(CompoundTag t) { WorldData w = new WorldData(); if (t.hasUUID("user")) w.user = t.getUUID("user"); w.userName = t.getString("name"); return w; }
    }

    public static WorldData world(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(WorldData::load, WorldData::new, "abyss_world");
    }

    /** Returns null on success, or why it failed. */
    public static String awaken(ServerPlayer p, boolean force) {
        AbyssData d = AbyssData.get(p);
        if (d.awakened) return "Already the Abyss.";
        WorldData w = world(p.server);
        if (Config.singleUser.get() && w.user != null && !w.user.equals(p.getUUID())) {
            if (!force) return "The Abyss already has a vessel: " + w.userName + ".";
            ServerPlayer old = p.server.getPlayerList().getPlayer(w.user);
            if (old != null) revoke(old);
        }
        w.user = p.getUUID(); w.userName = p.getGameProfile().getName(); w.setDirty();
        d.awakened = true;
        d.output = d.outputCap();
        d.dirty = true;
        Beelzebub.applyPassives(p);
        Util.fx(p.serverLevel(), S2CFx.AWAKEN, p.position().add(0, 1, 0), 1, 0, 0, p.getId(), -1);
        Util.sound(p.serverLevel(), p.position(), ModSounds.DOMAIN.get(), 2f, 0.4f);
        Util.sound(p.serverLevel(), p.position(), ModSounds.HEARTBEAT.get(), 2f, 0.5f);
        Net.around(p, new S2CVisual(p.getId(), true));
        p.displayClientMessage(Component.literal("§5The abyss opens inside you. §7Hold §f[P]§7 for the wheel, §f[O]§7 spores, hold §f[Caps Lock]§7 Spore Lens, §f[']§7 bodies."), false);
        return null;
    }

    public static void revoke(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (!d.inMain && d.mainBody != null) Bodies.swap(p, d.mainBody, false);
        Bodies.collapseAll(p);
        SporeManager.clear(p.getUUID());
        d.awakened = false;
        d.inMain = true; d.slot = 0; d.mainBody = null;
        d.dirty = true;
        Beelzebub.applyPassives(p);
        WorldData w = world(p.server);
        if (p.getUUID().equals(w.user)) { w.user = null; w.userName = ""; w.setDirty(); }
        Net.around(p, new S2CVisual(p.getId(), false));
        Actions.sync(p);
    }
    private Awakening() {}
}
