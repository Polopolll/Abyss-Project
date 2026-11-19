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
            TRIANGLE = reg("triangle"), IMPACT = reg("impact"), CHARGE = reg("charge");

    private static RegistryObject<SoundEvent> reg(String n) { return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(Abyss.id(n))); }
    private ModSounds() {}
}
