package com.andy.abyss.server;

import com.andy.abyss.Config;
import com.andy.abyss.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Abyssal Corrosion: the abyss eating whatever it touches. Stacks 1-10. Each stack takes 2 max HP and 1 armor; every
 * second it wears armor down; from 2 stacks it burns; from 5 stacks the body stops healing.
 */
public final class Corrosion {
    public static int stacks(LivingEntity e) {
        MobEffectInstance i = e.getEffect(ModEffects.CORROSION.get());
        return i == null ? 0 : i.getAmplifier() + 1;
    }

    /** Add stacks (max 10) and refresh duration. */
    public static void add(LivingEntity e, int add, int ticks) {
        if (Config.corrosionStrength.get() <= 0 || add <= 0) return;
        int s = Math.min(10, stacks(e) + add);
        apply(e, s, ticks);
    }

    /** Make sure the entity has at least this many stacks. */
    public static void atLeast(LivingEntity e, int s, int ticks) {
        if (Config.corrosionStrength.get() <= 0 || s <= 0) return;
        MobEffectInstance cur = e.getEffect(ModEffects.CORROSION.get());
        if (cur != null && cur.getAmplifier() + 1 >= s && cur.getDuration() > ticks / 2) return;
        apply(e, Math.max(s, stacks(e)), ticks);
    }

    private static void apply(LivingEntity e, int s, int ticks) {
        float hpBefore = e.getHealth();
        e.removeEffect(ModEffects.CORROSION.get());
        e.addEffect(new MobEffectInstance(ModEffects.CORROSION.get(), ticks, Math.min(9, s - 1), false, true, true));
        if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
        else if (hpBefore < e.getHealth()) e.setHealth(hpBefore);
    }

    /** Once a second for every corroded entity. */
    public static void tick(LivingEntity e) {
        int s = stacks(e);
        if (s == 0) return;
        float k = Config.corrosionStrength.get().floatValue();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = e.getItemBySlot(slot);
            if (!st.isEmpty() && st.isDamageableItem()) st.hurtAndBreak(Math.max(1, (int) (s * k)), e, x -> x.broadcastBreakEvent(slot));
        }
        if (s >= 2) Util.hurt(null, null, e, 0.4f * (s - 1) * k, "corrosion");
    }

    public static boolean blocksHealing(LivingEntity e) { return stacks(e) >= 5; }
    private Corrosion() {}
}
