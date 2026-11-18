package com.andy.abyss.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;

/** One thing Beelzebub devoured: what it was, how strong, and the properties the abyss took from it. */
public class Absorbed {
    public String type = "";
    public String name = "";
    public int cp;
    public float health, damage, armor, speed;
    /** fire_immune, flight, water_breathing, undead, ranged:<projectile id>, explosive, teleport, effect:<id> ... */
    public final Set<String> flags = new HashSet<>();
    public int count = 1;

    public double bonus(String key) {
        double k = Math.min(3, count) / 3.0 + 0.5;
        return switch (key) {
            case "health" -> health * 0.05 * k;
            case "damage" -> damage * 0.06 * k;
            case "armor" -> armor * 0.08 * k;
            case "speed" -> Math.max(0, speed - 0.2) * 0.1 * k;
            case "output" -> cp * 0.02 * k;
            default -> 0;
        };
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type); t.putString("name", name); t.putInt("cp", cp); t.putInt("count", count);
        t.putFloat("health", health); t.putFloat("damage", damage); t.putFloat("armor", armor); t.putFloat("speed", speed);
        ListTag l = new ListTag();
        for (String f : flags) l.add(StringTag.valueOf(f));
        t.put("flags", l);
        return t;
    }

    public static Absorbed load(CompoundTag t) {
        Absorbed a = new Absorbed();
        a.type = t.getString("type"); a.name = t.getString("name"); a.cp = t.getInt("cp"); a.count = Math.max(1, t.getInt("count"));
        a.health = t.getFloat("health"); a.damage = t.getFloat("damage"); a.armor = t.getFloat("armor"); a.speed = t.getFloat("speed");
        for (Tag x : t.getList("flags", Tag.TAG_STRING)) a.flags.add(x.getAsString());
        return a;
    }
}
