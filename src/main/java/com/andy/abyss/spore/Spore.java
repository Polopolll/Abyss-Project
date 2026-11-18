package com.andy.abyss.spore;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One abyssal spore. Free spores drift (and pass through blocks — the abyss isn't matter); latched ones ride and burrow into a host. */
public final class Spore {
    public final int id;
    public final UUID owner;
    public int ownerEntity;
    public double x, y, z, vx, vy, vz;
    public int age, life;
    public LivingEntity host;
    public double ox, oy, oz;
    public boolean planted, seeker;
    public final Set<Integer> passed = new HashSet<>();

    Spore(int id, UUID owner, int ownerEntity, Vec3 p, Vec3 v, int life) {
        this.id = id; this.owner = owner; this.ownerEntity = ownerEntity;
        x = p.x; y = p.y; z = p.z; vx = v.x; vy = v.y; vz = v.z; this.life = life;
    }

    public Vec3 pos() { return new Vec3(x, y, z); }
    public boolean latched() { return host != null; }
}
