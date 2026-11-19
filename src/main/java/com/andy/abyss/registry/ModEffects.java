package com.andy.abyss.registry;

import com.andy.abyss.Abyss;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Abyss.MODID);

    /** Abyss matter eating the body: armor wears away, max HP erodes per stack, healing stops at high stacks. Ticked in Corrosion. */
    public static final RegistryObject<MobEffect> CORROSION = EFFECTS.register("corrosion", () -> new Plain(MobEffectCategory.HARMFUL, 0x2A0F45)
            .addAttributeModifier(Attributes.MAX_HEALTH, "6f3a0c2e-4c1d-4a8e-9a51-0b7d1c3e5a01", -2.0, AttributeModifier.Operation.ADDITION)
            .addAttributeModifier(Attributes.ARMOR, "6f3a0c2e-4c1d-4a8e-9a51-0b7d1c3e5a02", -1.0, AttributeModifier.Operation.ADDITION));
    /** Lost a Triangle: every hit lands harder, defences (and negation) are broken. */
    public static final RegistryObject<MobEffect> SHATTERED = EFFECTS.register("shattered", () -> new Plain(MobEffectCategory.HARMFUL, 0xB8B8D8));
    /** Pinned by the abyss: cannot move or jump. */
    public static final RegistryObject<MobEffect> STUNNED = EFFECTS.register("stunned", () -> new Plain(MobEffectCategory.HARMFUL, 0x101018)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, "6f3a0c2e-4c1d-4a8e-9a51-0b7d1c3e5a03", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));

    public static final class Plain extends MobEffect {
        public Plain(MobEffectCategory c, int color) { super(c, color); }
    }
    private ModEffects() {}
}
