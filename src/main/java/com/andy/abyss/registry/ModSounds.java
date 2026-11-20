package com.andy.abyss.registry;

import com.andy.abyss.Abyss;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Abyss.MODID);
    public static final RegistryObject<SoundEvent> SPREAD = reg("spread"), RIFT = reg("rift"), DEVOUR = reg("devour"), NUKE = reg("nuke"),
            DOMAIN = reg("domain"), SWAP = reg("swap"), HEARTBEAT = reg("heartbeat"), COLLAPSE = reg("collapse"), NEGATE = reg("negate"),
            TRIANGLE = reg("triangle"), IMPACT = reg("impact"), CHARGE = reg("charge"),
            SWING = reg("arsenal_swing"), SWING_HEAVY = reg("arsenal_swing_heavy"), TEAR = reg("arsenal_tear"), HIT = reg("arsenal_hit"),
            HIT_HEAVY = reg("arsenal_hit_heavy"), SUMMON = reg("arsenal_summon"), SHATTER = reg("arsenal_shatter"), LIBERATION = reg("arsenal_liberation"),
            DODGE = reg("arsenal_dodge"), PERFECT = reg("arsenal_perfect"), CHARGE_UP = reg("arsenal_charge"),
            DOMAIN_SPEARS = reg("domain_spears"), DOMAIN_PULSE = reg("domain_pulse"), DOMAIN_COLLAPSE = reg("domain_collapse"), FORM_OPEN = reg("form_open"),
            PUNCH = reg("punch"), PUNCH_HEAVY = reg("punch_heavy"), PUNCH_LAYER = reg("punch_layer"), PUNCH_HEAVY_LAYER = reg("punch_heavy_layer"), PUNCH_BOOM = reg("punch_boom"), CRATER = reg("crater"), QUAKE = reg("quake"), FLICK = reg("flick"), FREEZE = reg("freeze"), RAGE = reg("rage");

    private static RegistryObject<SoundEvent> reg(String n) { return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(Abyss.id(n))); }
    private ModSounds() {}
}
