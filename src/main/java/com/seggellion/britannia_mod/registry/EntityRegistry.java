// EntityRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.entity.HorseSellerNPC;
import com.seggellion.britannia_mod.entity.MongbatEntity;
import com.seggellion.britannia_mod.entity.LichEntity;
import com.seggellion.britannia_mod.entity.RatEntity;
import com.seggellion.britannia_mod.entity.CustomCatEntity;
import com.seggellion.britannia_mod.entity.WraithEntity;
import com.seggellion.britannia_mod.entity.GhoulEntity;
import com.seggellion.britannia_mod.entity.WispEntity;
import com.seggellion.britannia_mod.entity.ShadeEntity;
import com.seggellion.britannia_mod.entity.EarthElementalEntity;
import com.seggellion.britannia_mod.entity.GoldOreElementalEntity;
import com.seggellion.britannia_mod.entity.ShadowOreElementalEntity;
import com.seggellion.britannia_mod.entity.DaemonEntity;
import com.seggellion.britannia_mod.entity.EntityFishMerchant;
import com.seggellion.britannia_mod.entity.EntityWoodMerchant;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
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

    public static final DeferredHolder<EntityType<?>, EntityType<EntityFishMerchant>> FISH_MERCHANT_ENTITY = ENTITIES.register(
    "fish_merchant",
    () -> EntityType.Builder.of(EntityFishMerchant::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .build("britannia_mod:fish_merchant")
);

    public static final DeferredHolder<EntityType<?>, EntityType<EntityWoodMerchant>> WOOD_MERCHANT_ENTITY = ENTITIES.register(
    "wood_merchant",
    () -> EntityType.Builder.of(EntityWoodMerchant::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .build("britannia_mod:wood_merchant")
);

public static final DeferredHolder<EntityType<?>, EntityType<TownPersonEntity>> TOWN_PERSON_ENTITY = ENTITIES.register(
    "town_person",
    () -> EntityType.Builder.of(TownPersonEntity::new, MobCategory.MISC)
        .sized(0.6F, 1.95F)
        .build("britannia_mod:town_person")
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
                    .sized(1F, 2.5F)
                    .build("britannia_mod:lich")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<RatEntity>> RAT_ENTITY = ENTITIES.register(
            "rat",
            () -> EntityType.Builder.of(RatEntity::new, MobCategory.MONSTER)
                    .sized(0.3F, 0.3F)
                    .build("britannia_mod:rat")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<CustomCatEntity>> CUSTOM_CAT_ENTITY = ENTITIES.register(
            "custom_cat",
            () -> EntityType.Builder.of(CustomCatEntity::new, MobCategory.CREATURE)
                .sized(0.6F, 0.7F)
                    .build("britannia_mod:custom_cat")
    );


        public static final DeferredHolder<EntityType<?>, EntityType<WraithEntity>> WRAITH_ENTITY = ENTITIES.register(
            "wraith",
            () -> EntityType.Builder.of(WraithEntity::new, MobCategory.MONSTER)
                    .sized(1F, 2.5F)
                    .build("britannia_mod:wraith")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<GhoulEntity>> GHOUL_ENTITY = ENTITIES.register(
            "ghoul",
            () -> EntityType.Builder.of(GhoulEntity::new, MobCategory.MONSTER)
                    .sized(1F, 2.5F)
                    .build("britannia_mod:ghoul")
    );

                public static final DeferredHolder<EntityType<?>, EntityType<WispEntity>> WISP_ENTITY = ENTITIES.register(
            "wisp",
            () -> EntityType.Builder.of(WispEntity::new, MobCategory.MONSTER)
                    .sized(1F, 1F)
                    .build("britannia_mod:wisp")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<ShadeEntity>> SHADE_ENTITY = ENTITIES.register(
            "shade",
            () -> EntityType.Builder.of(ShadeEntity::new, MobCategory.MONSTER)
                    .sized(1F, 2.5F)
                    .build("britannia_mod:shade")
    );
        public static final DeferredHolder<EntityType<?>, EntityType<EarthElementalEntity>> EARTH_ELEMENTAL_ENTITY = ENTITIES.register(
            "earth_elemental",
            () -> EntityType.Builder.of(EarthElementalEntity::new, MobCategory.MONSTER)
                    .sized(2F, 3.5F)
                    .build("britannia_mod:earth_elemental")
    );


        public static final DeferredHolder<EntityType<?>, EntityType<ShadowOreElementalEntity>> SHADOW_ORE_ELEMENTAL_ENTITY = ENTITIES.register(
            "shadow_ore_elemental",
            () -> EntityType.Builder.of(ShadowOreElementalEntity::new, MobCategory.MONSTER)
                    .sized(1F, 3.5F)
                    .build("britannia_mod:shadow_ore_elemental")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<GoldOreElementalEntity>> GOLD_ORE_ELEMENTAL_ENTITY = ENTITIES.register(
            "gold_ore_elemental",
            () -> EntityType.Builder.of(GoldOreElementalEntity::new, MobCategory.MONSTER)
                    .sized(1F, 3.5F)
                    .build("britannia_mod:gold_ore_elemental")
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
        event.put(EARTH_ELEMENTAL_ENTITY.get(), EarthElementalEntity.createAttributes().build());
        event.put(SHADOW_ORE_ELEMENTAL_ENTITY.get(), ShadowOreElementalEntity.createAttributes().build());
        event.put(GOLD_ORE_ELEMENTAL_ENTITY.get(), GoldOreElementalEntity.createAttributes().build());
        event.put(CUSTOM_CAT_ENTITY.get(), CustomCatEntity.createAttributes().build());
        event.put(DAEMON_ENTITY.get(), DaemonEntity.createAttributes().build());
         event.put(LICH_ENTITY.get(), LichEntity.createAttributes().build());
        event.put(RAT_ENTITY.get(), RatEntity.createAttributes().build());
        event.put(WISP_ENTITY.get(), WispEntity.createAttributes().build());
        event.put(HORSE_SELLER_NPC.get(), HorseSellerNPC.createAttributes().build());
        event.put(FISH_MERCHANT_ENTITY.get(), EntityFishMerchant.createAttributes().build());
        event.put(WOOD_MERCHANT_ENTITY.get(), EntityWoodMerchant.createAttributes().build());
        event.put(TOWN_PERSON_ENTITY.get(), TownPersonEntity.createAttributes().build());

    }
}
