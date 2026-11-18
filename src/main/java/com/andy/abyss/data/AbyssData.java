package com.andy.abyss.data;

import com.andy.abyss.Config;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything the Abyss stores on a player. The client copy is filled by S2CSync. */
public class AbyssData {
    public static final Capability<AbyssData> CAP = CapabilityManager.get(new CapabilityToken<>() {});
    private static final AbyssData DUMMY = new AbyssData();
    public static final int SPREAD = 0, PLANT = 1, TAG = 2;

    public boolean awakened;
    public float mastery;
    public float output = 100;
    public boolean negation = true;
    /** Abyssal Upgrade: output never runs out. */
    public boolean infinite;
    /** Combat mode: left click is the abyssal combo. */
    public boolean combat;
    /** 0 = Open, 1 = Closed (chosen while charging). */
    public int domainVariant;
    public int sporeMode = SPREAD;
    public int selected = 0;
    /** True while the player is standing in the main body. When false, the main body is an AbyssBody entity (usually in the Abyss). */
    public boolean inMain = true;
    public UUID mainBody;
    public final List<UUID> clones = new ArrayList<>();
    public final List<Absorbed> absorbed = new ArrayList<>();
    public long[] cooldownUntil = new long[Ability.values().length];
    public long cloneReadyAt, negationDownUntil;
    /** 0 = the main body; n = clone n (1-based) — the slot the player is currently in. */
    public int slot;

    // transient
    public float charge;
    public int comboStep;
    public long lastHit, reformedAt, nextHit;
    public int chargeAbility = -1;
    public boolean dirty = true;
    public long lastSync;

    public static AbyssData get(Player p) { return p.getCapability(CAP).orElse(DUMMY); }

    public float outputCap() {
        return (float) (Config.outputBase.get() + Config.outputPerMastery.get() * mastery + absorbedBonus("output"));
    }

    public double absorbedBonus(String key) {
        double s = 0;
        for (Absorbed a : absorbed) s += a.bonus(key);
        return switch (key) {
            case "health" -> Math.min(s, 60);
            case "damage" -> Math.min(s, 30);
            case "armor" -> Math.min(s, 12);
            case "speed" -> Math.min(s, 0.35);
            case "output" -> Math.min(s, 400);
            default -> s;
        };
    }

    public boolean has(String flag) {
        for (Absorbed a : absorbed) if (a.flags.contains(flag)) return true;
        return false;
    }

    public boolean isInfinite() { return infinite && Config.allowInfiniteOutput.get(); }

    public boolean spend(float amount) {
        if (infinite && Config.allowInfiniteOutput.get()) { dirty = true; return true; }
        if (output < amount) return false;
        output -= amount;
        dirty = true;
        return true;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("awakened", awakened);
        t.putFloat("mastery", mastery);
        t.putFloat("output", output);
        t.putBoolean("negation", negation);
        t.putBoolean("infinite", infinite);
        t.putBoolean("combat", combat);
        t.putInt("domainVariant", domainVariant);
        t.putInt("sporeMode", sporeMode);
        t.putInt("selected", selected);
        t.putBoolean("inMain", inMain);
        t.putInt("slot", slot);
        if (mainBody != null) t.putUUID("mainBody", mainBody);
        ListTag cl = new ListTag();
        for (UUID u : clones) { CompoundTag c = new CompoundTag(); c.putUUID("id", u); cl.add(c); }
        t.put("clones", cl);
        ListTag ab = new ListTag();
        for (Absorbed a : absorbed) ab.add(a.save());
        t.put("absorbed", ab);
        return t;
    }

    public void load(CompoundTag t) {
        awakened = t.getBoolean("awakened");
        mastery = t.getFloat("mastery");
        output = t.getFloat("output");
        negation = !t.contains("negation") || t.getBoolean("negation");
        sporeMode = t.getInt("sporeMode");
        infinite = t.getBoolean("infinite");
        combat = t.getBoolean("combat");
        domainVariant = t.getInt("domainVariant");
        selected = t.getInt("selected");
        inMain = !t.contains("inMain") || t.getBoolean("inMain");
        slot = t.getInt("slot");
        mainBody = t.hasUUID("mainBody") ? t.getUUID("mainBody") : null;
        clones.clear();
        for (Tag x : t.getList("clones", Tag.TAG_COMPOUND)) clones.add(((CompoundTag) x).getUUID("id"));
        absorbed.clear();
        for (Tag x : t.getList("absorbed", Tag.TAG_COMPOUND)) absorbed.add(Absorbed.load((CompoundTag) x));
        dirty = true;
    }

    public void copyFrom(AbyssData o) { load(o.save()); }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final AbyssData data = new AbyssData();
        private final LazyOptional<AbyssData> opt = LazyOptional.of(() -> data);
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) { return cap == CAP ? opt.cast() : LazyOptional.empty(); }
        @Override public CompoundTag serializeNBT() { return data.save(); }
        @Override public void deserializeNBT(CompoundTag t) { data.load(t); }
    }
}
