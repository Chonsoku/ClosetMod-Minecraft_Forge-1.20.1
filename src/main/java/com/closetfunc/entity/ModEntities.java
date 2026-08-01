package com.closetfunc.entity;

import com.closetfunc.MainCloset;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MainCloset.MOD_ID);

    public static final RegistryObject<EntityType<SlanderEntity>> SLANDER = ENTITY_TYPES.register("slander",
            () -> EntityType.Builder.of(SlanderEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 3.3F)
                    .build("slander"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SLANDER.get(), SlanderEntity.createAttributes().build());
    }
}
