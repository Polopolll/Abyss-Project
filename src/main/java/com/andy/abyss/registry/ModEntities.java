package com.andy.abyss.registry;

import com.andy.abyss.Abyss;
import com.andy.abyss.entity.AbyssBody;
import com.andy.abyss.entity.Rift;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Abyss.MODID);
    public static final RegistryObject<EntityType<AbyssBody>> BODY = ENTITIES.register("abyss_body", () -> EntityType.Builder.<AbyssBody>of(AbyssBody::new, MobCategory.MISC)
            .sized(0.6f, 1.8f).clientTrackingRange(16).updateInterval(1).fireImmune().build("abyss:abyss_body"));
    public static final RegistryObject<EntityType<Rift>> RIFT = ENTITIES.register("rift", () -> EntityType.Builder.<Rift>of(Rift::new, MobCategory.MISC)
            .sized(1.6f, 2.6f).clientTrackingRange(16).updateInterval(5).fireImmune().noSave().build("abyss:rift"));

    public static void attributes(EntityAttributeCreationEvent e) { e.put(BODY.get(), AbyssBody.createAttributes().build()); }
    private ModEntities() {}
}
