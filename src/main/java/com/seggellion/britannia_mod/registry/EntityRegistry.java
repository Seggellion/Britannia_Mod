// EntityRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.entity.HorseSellerNPC;
import com.seggellion.britannia_mod.entity.MongbatEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE, "britannia_mod");

    public static final DeferredHolder<EntityType<?>, EntityType<HorseSellerNPC>> HORSE_SELLER_NPC = ENTITIES.register(
            "horse_seller_npc",
            () -> EntityType.Builder.of(HorseSellerNPC::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("britannia_mod:horse_seller_npc")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<MongbatEntity>> MONGBAT_ENTITY = ENTITIES.register(
            "mongbat",
            () -> EntityType.Builder.of(MongbatEntity::new, MobCategory.MONSTER)
                    .sized(0.5F, 0.9F)
                    .build("britannia_mod:mongbat")
    );

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        // Register attributes without redundant checks
        event.put(MONGBAT_ENTITY.get(), MongbatEntity.createAttributes().build());
        event.put(HORSE_SELLER_NPC.get(), HorseSellerNPC.createAttributes().build());
    }
}
