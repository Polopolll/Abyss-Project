package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.C2SInput;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.net.S2CVisual;
import com.andy.abyss.registry.ModSounds;
import com.andy.abyss.spore.SporeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Umbrella forms (Ronova-style). R opens a form; while it is open the mouse buttons are its sub-abilities. Only one
 * form is open at a time. The Domain becomes the open form by itself while your domain stands.
 */
public final class Forms {
    public static Ability open(AbyssData d) { return d.form < 0 ? null : Ability.byIndex(d.form); }

    public static void toggle(ServerPlayer p, Ability a) {
        AbyssData d = AbyssData.get(p);
        if (d.form == a.ordinal()) { close(p); return; }
        if (d.form >= 0) close(p);
        d.form = a.ordinal();
        d.dirty = true;
        Util.fx(p.serverLevel(), S2CFx.FORM_OPEN, p.position().add(0, 1, 0), a.ordinal(), 1, d.weapon, p.getId(), -1);
        Util.sound(p.serverLevel(), p.position(), a == Ability.AUTHORITY ? ModSounds.RIFT.get() : ModSounds.FORM_OPEN.get(), 1f, a == Ability.BEELZEBUB ? 0.7f : 1f);
        Net.around(p, new S2CVisual(p.getId(), true, d.form, d.weapon));
    }

    public static void close(ServerPlayer p) {
        AbyssData d = AbyssData.get(p);
        if (d.form < 0) return;
        Ability was = Ability.byIndex(d.form);
        if (d.peeking) Realm.endPeek(p);
        Actions.stopSpreading(p.getUUID());
        d.guardSince = -1; d.pending = -1;
        d.form = -1;
        d.dirty = true;
        Util.fx(p.serverLevel(), S2CFx.FORM_OPEN, p.position().add(0, 1, 0), was == null ? 0 : was.ordinal(), 0, d.weapon, p.getId(), -1);
        Net.around(p, new S2CVisual(p.getId(), true, -1, d.weapon));
    }

    public static void input(ServerPlayer p, int button, int phase, int flags, int arg) {
        AbyssData d = AbyssData.get(p);
        if (!d.awakened || p.isSpectator() && !d.peeking) return;
        Ability f = open(d);
        if (f == null) return;
        boolean press = phase == C2SInput.PRESS, shift = (flags & C2SInput.SHIFT) != 0;
        ServerLevel l = p.serverLevel();
        switch (f) {
            case ARSENAL -> Arsenal.input(p, button, phase, flags, arg);
            case SPORES -> {
                if (button == C2SInput.LMB) {
                    if (press && shift) { if (d.spend(3)) { SporeManager.shoot(l, p.getUUID(), p.getId(), p.getEyePosition().add(p.getLookAngle().scale(0.5)), p.getLookAngle()); Util.fx(l, S2CFx.VOLLEY, p.getEyePosition().add(p.getLookAngle()), 0, 1, 0, p.getId(), -1); } }
                    else if (press) { d.sporeMode = AbyssData.SPREAD; Actions.handle(p, com.andy.abyss.net.C2SAction.SPREAD_START, 0); }
                    else if (phase == C2SInput.RELEASE) Actions.stopSpreading(p.getUUID());
                } else if (button == C2SInput.RMB && press && shift) {
                    if (d.spend(3)) { Util.Aim aim = Util.aim(p, 48); SporeManager.plant(l, p.getUUID(), p.getId(), aim.point(), aim.entity()); }
                }
                // paint-select, unleash and payload choice are client side (they send C2SPayload)
            }
            case AUTHORITY -> {
                if (!press) { if (button == C2SInput.LMB && phase == C2SInput.RELEASE && d.peeking) Realm.endPeek(p); return; }
                boolean inRealm = AbyssDim.is(p.level());
                switch (button) {
                    case C2SInput.LMB -> {
                        if (inRealm) { Realm.startPeek(p); return; }
                        LivingEntity t = Util.aim(p, 32).entity();
                        if (t == null) { p.displayClientMessage(Component.literal("§7Look at something to drag through."), true); return; }
                        if (!d.spend(30)) { Actions.noOutput(p); return; }
                        Beelzebub.transport(p, t);
                    }
                    case C2SInput.RMB -> { if (inRealm) Realm.leave(p); else Realm.enter(p); }
                    case C2SInput.M4 -> { } // the client opens the rift screen
                    case C2SInput.M5 -> {
                        if (inRealm) return; // the client opens the world list
                        if (!d.spend(10)) { Actions.noOutput(p); return; }
                        Vec3 at = Util.aim(p, 48).point();
                        Rifts.step(p, l, Rifts.safe(l, at.x, at.y, at.z));
                    }
                }
            }
            case DOMAIN -> {
                if (!press) return;
                Domains.Domain dom = Domains.ownedBy(p.getUUID());
                if (dom == null) { close(p); return; }
                switch (button) {
                    case C2SInput.LMB -> Domains.spears(dom, p);
                    case C2SInput.RMB -> Domains.pulseNow(dom, p);
                    case C2SInput.M4 -> Domains.collapse(dom, p);
                    case C2SInput.M5 -> { Domains.end(dom); close(p); }
                }
            }
            case BEELZEBUB -> {
                if (!press) return;
                switch (button) {
                    case C2SInput.LMB -> Actions.castDevour(p);
                    case C2SInput.RMB -> { if (d.spend(50)) Beelzebub.brood(p); else Actions.noOutput(p); }
                    case C2SInput.M4 -> { if (d.spend(15)) Beelzebub.volley(p); else Actions.noOutput(p); }
                    case C2SInput.M5 -> { if (d.spend(40)) Beelzebub.inhale(p); else Actions.noOutput(p); }
                }
            }
            case BODIES -> {
                if (!press) return;
                switch (button) {
                    case C2SInput.LMB -> Actions.sendClone(p);
                    case C2SInput.RMB -> Bodies.cycle(p, shift ? -1 : 1);
                    case C2SInput.M4 -> Bodies.recall(p, -1);
                    case C2SInput.M5 -> Bodies.descend(p);
                    case C2SInput.SCROLL -> { }
                }
            }
            default -> {}
        }
    }
    private Forms() {}
}
