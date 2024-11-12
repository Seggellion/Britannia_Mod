// EntityRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.entity.HorseSellerNPC;
import com.seggellion.britannia_mod.entity.MongbatEntity;
import com.seggellion.britannia_mod.entity.LichEntity;
import com.seggellion.britannia_mod.entity.WraithEntity;
import com.seggellion.britannia_mod.entity.GhoulEntity;
import com.seggellion.britannia_mod.entity.ShadeEntity;
import com.seggellion.britannia_mod.entity.DaemonEntity;
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

    public static final DeferredHolder<EntityType<?>, EntityType<DaemonEntity>> DAEMON_ENTITY = ENTITIES.register(
            "daemon",
            () -> EntityType.Builder.of(DaemonEntity::new, MobCategory.MONSTER)
                    .sized(2.5F, 5.5F)
                    .build("britannia_mod:daemon")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<LichEntity>> LICH_ENTITY = ENTITIES.register(
            "lich",
            () -> EntityType.Builder.of(LichEntity::new, MobCategory.MONSTER)
                    .sized(1F, 2F)
                    .build("britannia_mod:lich")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<WraithEntity>> WRAITH_ENTITY = ENTITIES.register(
            "wraith",
            () -> EntityType.Builder.of(WraithEntity::new, MobCategory.MONSTER)
                    .sized(1F, 3F)
                    .build("britannia_mod:wraith")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<GhoulEntity>> GHOUL_ENTITY = ENTITIES.register(
            "ghoul",
            () -> EntityType.Builder.of(GhoulEntity::new, MobCategory.MONSTER)
                    .sized(1F, 1F)
                    .build("britannia_mod:ghoul")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<ShadeEntity>> SHADE_ENTITY = ENTITIES.register(
            "shade",
            () -> EntityType.Builder.of(ShadeEntity::new, MobCategory.MONSTER)
                    .sized(1F, 1F)
                    .build("britannia_mod:shade")
    );

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        // Register attributes without redundant checks
        event.put(MONGBAT_ENTITY.get(), MongbatEntity.createAttributes().build());
        event.put(WRAITH_ENTITY.get(), WraithEntity.createAttributes().build());
        event.put(GHOUL_ENTITY.get(), GhoulEntity.createAttributes().build());
        event.put(SHADE_ENTITY.get(), ShadeEntity.createAttributes().build());
        event.put(DAEMON_ENTITY.get(), DaemonEntity.createAttributes().build());
         event.put(LICH_ENTITY.get(), LichEntity.createAttributes().build());
        event.put(HORSE_SELLER_NPC.get(), HorseSellerNPC.createAttributes().build());
    }
}
