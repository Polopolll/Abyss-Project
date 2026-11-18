package com.andy.abyss.client;

import com.andy.abyss.Config;
import com.andy.abyss.data.Ability;

import java.util.ArrayList;
import java.util.List;

/** The player's own order of skills on the wheel (drag to swap; saved in the client config). */
public final class WheelOrder {
    public static List<Ability> get() {
        List<Ability> out = new ArrayList<>();
        try {
            for (String s : Config.wheelOrder.get().split(",")) {
                if (s.isBlank()) continue;
                Ability a = Ability.valueOf(s.trim());
                if (!out.contains(a)) out.add(a);
            }
        } catch (Exception ignored) {}
        for (Ability a : Ability.values()) if (!out.contains(a)) out.add(a);
        return out;
    }

    public static void swap(int i, int j) {
        List<Ability> l = get();
        if (i < 0 || j < 0 || i >= l.size() || j >= l.size()) return;
        Ability t = l.get(i); l.set(i, l.get(j)); l.set(j, t);
        StringBuilder b = new StringBuilder();
        for (Ability a : l) { if (b.length() > 0) b.append(','); b.append(a.name()); }
        try { Config.wheelOrder.set(b.toString()); } catch (Exception ignored) {}
    }

    public static Ability next(Ability cur, int dir) {
        List<Ability> l = get();
        int i = Math.max(0, l.indexOf(cur));
        return l.get(Math.floorMod(i + dir, l.size()));
    }
    private WheelOrder() {}
}
