package com.andy.abyss.client;

import com.andy.abyss.client.render.Draw;
import com.andy.abyss.client.render.Rt;
import com.andy.abyss.net.S2CSpores;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Client copies of nearby spores, interpolated between server snapshots, plus the Spore Lens (picking and selection). */
public final class SporeClient {
    public static final class S {
        public int id, owner, host;
        public boolean mine, planted;
        double px, py, pz, cx, cy, cz; // previous and current snapshot (world pos, or offset when latched)
        long at;
        Vec3 trail;
    }
    public static final Map<Integer, S> ALL = new HashMap<>();
    // lens
    public static boolean lens;
    public static final Set<Integer> SELECTED = new LinkedHashSet<>();
    public static final Set<Integer> SELECTED_HOSTS = new LinkedHashSet<>();
    public static int hoverSpore = -1, hoverHost = -1, cycle;
    private static long snapAt;

    public static void snapshot(S2CSpores p) {
        long now = ClientState.ticks;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < p.ids().length; i++) {
            int id = p.ids()[i];
            seen.add(id);
            S s = ALL.get(id);
            boolean fresh = s == null;
            if (fresh) { s = new S(); s.id = id; ALL.put(id, s); }
            int host = p.hosts()[i];
            double x = p.xyz()[i * 3], y = p.xyz()[i * 3 + 1], z = p.xyz()[i * 3 + 2];
            if (fresh || host != s.host) { s.px = x; s.py = y; s.pz = z; }
            else { s.px = s.cx; s.py = s.cy; s.pz = s.cz; }
            s.cx = x; s.cy = y; s.cz = z;
            s.host = host; s.owner = p.owners()[i];
            s.mine = (p.flags()[i] & S2CSpores.MINE) != 0;
            s.planted = (p.flags()[i] & S2CSpores.PLANTED) != 0;
            s.at = now;
        }
        ALL.keySet().removeIf(id -> !seen.contains(id));
        SELECTED.removeIf(id -> !ALL.containsKey(id));
        snapAt = now;
    }

    public static Vec3 pos(S s, float pt) {
        float f = Math.min(1f, (ClientState.ticks - s.at + pt) / 3f);
        double x = s.px + (s.cx - s.px) * f, y = s.py + (s.cy - s.py) * f, z = s.cz == s.pz ? s.cz : s.pz + (s.cz - s.pz) * f;
        if (s.host >= 0) {
            Entity h = Minecraft.getInstance().level.getEntity(s.host);
            if (h == null) return null;
            Vec3 hp = h.getPosition(pt);
            return new Vec3(hp.x + s.cx, hp.y + s.cy, hp.z + s.cz);
        }
        return new Vec3(x, y, z);
    }

    public static void tick() {
        if (ClientState.ticks - snapAt > 60) ALL.clear();
    }

    public static void render(Draw d) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || ALL.isEmpty()) return;
        float density = com.andy.abyss.Config.fxDensity.get().floatValue();
        Map<Integer, Integer> hostCount = new HashMap<>();
        for (S s : ALL.values()) {
            Vec3 p = pos(s, d.pt);
            if (p == null) continue;
            double dist = p.distanceTo(d.cam);
            if (dist > 120) continue;
            float near = d.near(p, 1.2f);
            boolean sel = SELECTED.contains(s.id) || (s.host >= 0 && SELECTED_HOSTS.contains(s.host));
            boolean hov = s.id == hoverSpore || (s.host >= 0 && s.host == hoverHost);
            float pulse = 0.8f + 0.2f * (float) Math.sin(d.time * 4 + s.id);
            if (s.host >= 0) {
                hostCount.merge(s.host, 1, Integer::sum);
                // a black crystal barnacle with purple veins creeping out over the host
                Entity h = mc.level.getEntity(s.host);
                Vec3 hc = h != null ? h.getPosition(d.pt).add(0, h.getBbHeight() * 0.5, 0) : p;
                Vec3 out = p.subtract(hc).normalize();
                d.voidShard(p, out, 0.28f, 0.09f, 0.95f * near);
                d.billboard(Rt.GLOW, true, p, 0.22f, 0x9A55FF, 0.55f * pulse * near, 0);
                if (dist < 32) {
                    Random r = new Random(s.id * 31L);
                    for (int i = 0; i < 3; i++) {
                        Vec3 tip = p.add((r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.5);
                        d.beam(Rt.STREAK, true, p, tip, 0.035f, 0x7B3BEA, 0.6f * near);
                    }
                }
            } else {
                float a = s.mine ? 1f : 0.85f;
                // a dark core with a violet halo: the alpha halo reads in daylight, the additive one at night
                // a seed of the abyss: a tiny turning black hole
                d.blackHole(p, 0.42f, false, a * near, (float) d.time * -1.6f + s.id);
                d.billboard(Rt.GLOW, true, p, 0.3f, 0x6A4BFF, 0.25f * pulse * a * near * d.near(p, 2.5f), 0);
                if (dist < 48 && density > 0.3f) {
                    Vec3 prev = new Vec3(s.px, s.py, s.pz);
                    if (prev.distanceToSqr(p) > 0.04 && prev.distanceToSqr(p) < 400) d.beam(Rt.STREAK, true, prev, p, 0.07f, 0x6A3BD8, 0.45f * a);
                }
                if (s.planted) d.flat(Rt.RING, true, p, new Vec3(0, 1, 0), 0.45f, 0x9A70FF, 0.35f * pulse, (float) d.time);
            }
            if (lens && s.mine) {
                Long fl = SELECT_FLASH.get(s.host >= 0 ? -s.host - 1 : s.id);
                float pop = fl == null ? 0 : Math.max(0, 1 - (ClientState.ticks - fl + d.pt) / 8f);
                float r = (hov ? 0.55f : sel ? 0.42f : 0.26f) + pop * 0.5f;
                int col = hov ? 0xFFFFFF : sel ? 0xE8D0FF : 0x9A6BFF;
                d.billboard(Rt.RING, true, p, r, col, hov || sel ? 0.95f : 0.35f, 0);
            }
            if (UNLEASHED.contains(s.host >= 0 ? -s.host - 1 : s.id)) {
                float u = (ClientState.ticks - unleashedAt + d.pt) / 10f;
                if (u < 1) d.billboard(Rt.GLOW, true, p, 0.4f + u * 1.4f, 0xFFFFFF, (1 - u) * near, 0);
            }
        }
        if (lens) {
            for (var en : hostCount.entrySet()) {
                Entity h = mc.level.getEntity(en.getKey());
                if (h == null) continue;
                boolean hov = h.getId() == hoverHost, sel = SELECTED_HOSTS.contains(h.getId());
                AABB bb = h.getBoundingBox().move(h.getPosition(d.pt).subtract(h.position())).inflate(0.15);
                bracket(d, bb, hov ? 0xFFFFFF : sel ? 0xE8D0FF : 0x9A6BFF, hov || sel ? 0.9f : 0.5f);
            }
        }
    }

    private static void bracket(Draw d, AABB b, int col, float a) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        double lx = (b.maxX - b.minX) * 0.25, ly = (b.maxY - b.minY) * 0.2, lz = (b.maxZ - b.minZ) * 0.25;
        for (double x : xs) for (double y : ys) for (double z : zs) {
            Vec3 c = new Vec3(x, y, z);
            d.beam(Rt.WHITE, true, c, c.add(x == b.minX ? lx : -lx, 0, 0), 0.04f, col, a);
            d.beam(Rt.WHITE, true, c, c.add(0, y == b.minY ? ly : -ly, 0), 0.04f, col, a);
            d.beam(Rt.WHITE, true, c, c.add(0, 0, z == b.minZ ? lz : -lz), 0.04f, col, a);
        }
    }

    /** Lens picking: the spore or host nearest the crosshair (scroll cycles through near candidates). */
    public static void pick(float pt) {
        var mc = Minecraft.getInstance();
        hoverSpore = -1; hoverHost = -1;
        if (!lens || mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition(pt), look = mc.player.getViewVector(pt);
        record Cand(double ang, int spore, int host) {}
        List<Cand> c = new ArrayList<>();
        Set<Integer> hosts = new HashSet<>();
        for (S s : ALL.values()) {
            if (!s.mine) continue;
            Vec3 p = pos(s, pt);
            if (p == null) continue;
            if (s.host >= 0) { hosts.add(s.host); continue; }
            Vec3 to = p.subtract(eye);
            double dist = to.length();
            double ang = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look))));
            if (ang < Math.toRadians(4) + 0.25 / Math.max(1, dist)) c.add(new Cand(ang, s.id, -1));
        }
        for (int hid : hosts) {
            Entity h = mc.level.getEntity(hid);
            if (!(h instanceof LivingEntity)) continue;
            Vec3 to = h.getPosition(pt).add(0, h.getBbHeight() / 2, 0).subtract(eye);
            double dist = to.length();
            double size = Math.atan(Math.max(h.getBbWidth(), h.getBbHeight()) * 0.6 / Math.max(0.5, dist));
            double ang = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look))));
            if (ang < size + Math.toRadians(3)) c.add(new Cand(ang * 0.6, -1, hid));
        }
        if (c.isEmpty()) return;
        c.sort(Comparator.comparingDouble(Cand::ang));
        Cand pick = c.get(Math.floorMod(cycle, c.size()));
        hoverSpore = pick.spore; hoverHost = pick.host;
    }

    /** Spore form, RMB held: everything of yours under the crosshair (a small brush) joins the selection. */
    public static void paintStart() { ClientState.painting = true; }

    public static void paint(float pt) { selectCone(pt, 6, 128); }

    /** Select every spore and infected host of yours within a cone (degrees) around the crosshair. */
    public static void selectCone(float pt, double deg, double range) {
        var mc = Minecraft.getInstance();
        Vec3 eye = mc.player.getEyePosition(pt), look = mc.player.getViewVector(pt);
        double cos = Math.cos(Math.toRadians(deg));
        for (S s : ALL.values()) {
            if (!s.mine) continue;
            Vec3 p = pos(s, pt);
            if (p == null) continue;
            Vec3 to = p.subtract(eye);
            double d = to.length();
            if (d > range || d < 0.01) continue;
            double brush = deg + Math.toDegrees(Math.atan(0.4 / d));
            if (to.normalize().dot(look) < Math.cos(Math.toRadians(brush))) continue;
            boolean added = s.host >= 0 ? SELECTED_HOSTS.add(s.host) : SELECTED.add(s.id);
            if (added) SELECT_FLASH.put(s.host >= 0 ? -s.host - 1 : s.id, ClientState.ticks);
        }
    }

    /** Recently selected (for the little pop when the brush picks something up). Key: spore id, or -(host+1). */
    public static final java.util.Map<Integer, Long> SELECT_FLASH = new java.util.HashMap<>();
    /** The moment a selection is unleashed: every selected spore flares. */
    public static long unleashedAt = -100;
    public static final java.util.Set<Integer> UNLEASHED = new java.util.HashSet<>();

    public static void flashSelection() {
        unleashedAt = ClientState.ticks;
        UNLEASHED.clear();
        UNLEASHED.addAll(SELECTED);
        for (int h : SELECTED_HOSTS) UNLEASHED.add(-h - 1);
    }

    public static int mineCount() { int n = 0; for (S s : ALL.values()) if (s.mine) n++; return n; }

    public static void clearSelection() { SELECTED.clear(); SELECTED_HOSTS.clear(); cycle = 0; }
    private SporeClient() {}
}
