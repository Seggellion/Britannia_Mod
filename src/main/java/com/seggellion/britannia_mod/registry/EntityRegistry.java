// EntityRegistry.java
package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.entity.EntityHorseMerchant;
import com.seggellion.britannia_mod.entity.LayEntity;
import com.seggellion.britannia_mod.entity.LivingSeatEntity;
import com.seggellion.britannia_mod.entity.MongbatEntity;
import com.seggellion.britannia_mod.entity.OrcEntity;
import com.seggellion.britannia_mod.entity.OrcClubEntity;
import com.seggellion.britannia_mod.entity.HarpyEntity;
import com.seggellion.britannia_mod.entity.TrollEntity;
import com.seggellion.britannia_mod.entity.OgreEntity;
import com.seggellion.britannia_mod.entity.OgreArcticEntity;
import com.seggellion.britannia_mod.entity.OgreLordEntity;
import com.seggellion.britannia_mod.entity.OgreLordArcticEntity;
import com.seggellion.britannia_mod.entity.SerpentDeepSeaEntity;
import com.seggellion.britannia_mod.entity.SerpentCrystalEntity;
import com.seggellion.britannia_mod.entity.SerpentGiantEntity;
import com.seggellion.britannia_mod.entity.SerpentSeaEntity;
import com.seggellion.britannia_mod.entity.SerpentSilverEntity;
import com.seggellion.britannia_mod.entity.SerpentLavaEntity;
import com.seggellion.britannia_mod.entity.RatmanEntity;
import com.seggellion.britannia_mod.entity.HeadlessEntity;
import com.seggellion.britannia_mod.entity.RatmanArcherEntity;
import com.seggellion.britannia_mod.entity.RatmanAssassinEntity;
import com.seggellion.britannia_mod.entity.GiantRatEntity;
import com.seggellion.britannia_mod.entity.GargoyleEntity;
import com.seggellion.britannia_mod.entity.GargoyleEnforcerEntity;
import com.seggellion.britannia_mod.entity.GargoyleDestroyerEntity;
import com.seggellion.britannia_mod.entity.GargoyleStoneEntity;

import com.seggellion.britannia_mod.entity.GorillaEntity;
import com.seggellion.britannia_mod.entity.TurkeyEntity;
import com.seggellion.britannia_mod.entity.HindEntity;
import com.seggellion.britannia_mod.entity.GreatHartEntity;
import com.seggellion.britannia_mod.entity.BearBlackEntity;
import com.seggellion.britannia_mod.entity.BearBrownEntity;
import com.seggellion.britannia_mod.entity.BearPolarEntity;
import com.seggellion.britannia_mod.entity.BearGrizzlyEntity;


import com.seggellion.britannia_mod.entity.EttinEntity;
import com.seggellion.britannia_mod.entity.AlligatorEntity;
import com.seggellion.britannia_mod.entity.ScorpionEntity;
import com.seggellion.britannia_mod.entity.AcidElementalEntity;
import com.seggellion.britannia_mod.entity.AirElementalEntity;
import com.seggellion.britannia_mod.entity.DullCopperElementalEntity;
import com.seggellion.britannia_mod.entity.FireElementalEntity;
import com.seggellion.britannia_mod.entity.BloodElementalEntity;
import com.seggellion.britannia_mod.entity.WaterElementalEntity;
import com.seggellion.britannia_mod.entity.PoisonElementalEntity;
import com.seggellion.britannia_mod.entity.LizardmanEntity;
import com.seggellion.britannia_mod.entity.SerpentIceEntity;


import com.seggellion.britannia_mod.entity.LichEntity;
import com.seggellion.britannia_mod.entity.RatEntity;
import com.seggellion.britannia_mod.entity.CustomCatEntity;
import com.seggellion.britannia_mod.entity.BritanniaCatEntity;
import com.seggellion.britannia_mod.entity.WraithEntity;
import com.seggellion.britannia_mod.entity.GhoulEntity;
import com.seggellion.britannia_mod.entity.WispEntity;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.entity.ShadeEntity;
import com.seggellion.britannia_mod.entity.EarthElementalEntity;
import com.seggellion.britannia_mod.entity.GoldOreElementalEntity;
import com.seggellion.britannia_mod.entity.ShadowOreElementalEntity;
import com.seggellion.britannia_mod.entity.DaemonEntity;
import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.entity.FishTraderEntity;
import com.seggellion.britannia_mod.entity.SalvageTraderEntity;
import com.seggellion.britannia_mod.entity.AlcoholTraderEntity;
import com.seggellion.britannia_mod.entity.EntityWoodMerchant;
import com.seggellion.britannia_mod.entity.EntityStoneMerchant;
import com.seggellion.britannia_mod.entity.EntityMetalMerchant;
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

    public static final DeferredHolder<EntityType<?>, EntityType<EntityHorseMerchant>> HORSE_MERCHANT_ENTITY = ENTITIES.register(
            "horse_merchant",
            () -> EntityType.Builder.of(EntityHorseMerchant::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("britannia_mod:horse_merchant")
    );

public static final DeferredHolder<EntityType<?>, EntityType<FishTraderEntity>> FISH_TRADER =
        ENTITIES.register("fish_trader",
            () -> EntityType.Builder.of(FishTraderEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("britannia_mod:fish_trader"));

public static final DeferredHolder<EntityType<?>, EntityType<QuestGiverEntity>> QUEST_GIVER =
        ENTITIES.register("quest_giver",
            () -> EntityType.Builder.of(QuestGiverEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("britannia_mod:quest_giver"));

public static final DeferredHolder<EntityType<?>, EntityType<SalvageTraderEntity>> SALVAGE_TRADER =
        ENTITIES.register("salvage_trader",
            () -> EntityType.Builder.of(SalvageTraderEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("britannia_mod:salvage_trader"));

public static final DeferredHolder<EntityType<?>, EntityType<AlcoholTraderEntity>> ALCOHOL_TRADER =
        ENTITIES.register("alcohol_trader",
            () -> EntityType.Builder.of(AlcoholTraderEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("britannia_mod:alcohol_trader"));

public static final DeferredHolder<EntityType<?>, EntityType<EntityMetalMerchant>> MEAT_TRADER =
        ENTITIES.register("meat_trader",
            () -> EntityType.Builder.of(EntityMetalMerchant::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .build("britannia_mod:meat_trader"));


    public static final DeferredHolder<EntityType<?>, EntityType<EntityWoodMerchant>> WOOD_MERCHANT_ENTITY = ENTITIES.register(
    "wood_merchant",
    () -> EntityType.Builder.of(EntityWoodMerchant::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .build("britannia_mod:wood_merchant")
);

public static final DeferredHolder<EntityType<?>, EntityType<ArchitectEntity>>
        ARCHITECT_ENTITY = ENTITIES.register(
    "architect",
    () -> EntityType.Builder                      
              .<ArchitectEntity>of(ArchitectEntity::create, MobCategory.MISC)
              .sized(0.6F, 1.95F)
              .build("britannia_mod:architect")
);



    public static final DeferredHolder<EntityType<?>, EntityType<EntityMetalMerchant>> METAL_MERCHANT_ENTITY = ENTITIES.register(
    "metal_merchant",
    () -> EntityType.Builder.of(EntityMetalMerchant::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .build("britannia_mod:metal_merchant")
);

    public static final DeferredHolder<EntityType<?>, EntityType<EntityStoneMerchant>> STONE_MERCHANT_ENTITY = ENTITIES.register(
    "stone_merchant",
    () -> EntityType.Builder.of(EntityStoneMerchant::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .build("britannia_mod:stone_merchant")
);

public static final DeferredHolder<EntityType<?>, EntityType<TownPersonEntity>> TOWNSPERSON = ENTITIES.register(
    "town_person",
    () -> EntityType.Builder.of(TownPersonEntity::new, MobCategory.MISC)
        .sized(0.6F, 1.95F)
        .build("britannia_mod:town_person")
);

    public static final DeferredHolder<EntityType<?>, EntityType<LayEntity>> LAY_ENTITY =
            ENTITIES.register("lay",
                    () -> EntityType.Builder.<LayEntity>of(LayEntity::new, MobCategory.MISC)
                            .sized(0.0F, 0.0F)         
                            .clientTrackingRange(256)   
                            .updateInterval(20)
                            .build("britannia_mod:lay"));


// --- ANIMAL ---


    // --- MONSTERS ---

    public static final DeferredHolder<EntityType<?>, EntityType<MongbatEntity>> MONGBAT_ENTITY = ENTITIES.register(
            "mongbat",
            () -> EntityType.Builder.of(MongbatEntity::new, MobCategory.MONSTER)
                    .sized(0.5F, 1.3F)
                    .build("britannia_mod:mongbat")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<AlligatorEntity>> ALLIGATOR_ENTITY = ENTITIES.register(
            "alligator",
            () -> EntityType.Builder.of(AlligatorEntity::new, MobCategory.MONSTER)
                    .sized(1.6F, 1.0F)
                    .build("britannia_mod:alligator")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<GiantRatEntity>> GIANT_RAT_ENTITY = ENTITIES.register(
            "giant_rat",
            () -> EntityType.Builder.of(GiantRatEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.6F)
                    .build("britannia_mod:giant_rat")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<EttinEntity>> ETTIN_ENTITY = ENTITIES.register(
            "ettin",
            () -> EntityType.Builder.of(EttinEntity::new, MobCategory.MONSTER)
                    .sized(1.8F, 5.3F) // Big boy
                    .build("britannia_mod:ettin")
    );

public static final DeferredHolder<EntityType<?>, EntityType<SerpentSeaEntity>> SERPENT_SEA_ENTITY = ENTITIES.register(
    "serpent_sea",
    () -> EntityType.Builder.of(SerpentSeaEntity::new, MobCategory.MONSTER)
            // Shrink the base hitbox. The multipart code will handle the length!
            .sized(0.8F, 0.6F) 
            .build("britannia_mod:serpent_sea")
);

    public static final DeferredHolder<EntityType<?>, EntityType<SerpentDeepSeaEntity>> SERPENT_DEEP_SEA_ENTITY = ENTITIES.register(
            "serpent_deep_sea",
            () -> EntityType.Builder.of(SerpentDeepSeaEntity::new, MobCategory.MONSTER)
               .sized(0.8F, 0.6F) 
                    .build("britannia_mod:serpent_deep_sea")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<SerpentSilverEntity>> SERPENT_SILVER_ENTITY = ENTITIES.register(
            "serpent_silver",
            () -> EntityType.Builder.of(SerpentSilverEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.3F)
                    .build("britannia_mod:serpent_silver")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<SerpentCrystalEntity>> SERPENT_CRYSTAL_SEA_ENTITY = ENTITIES.register(
            "serpent_crystal_sea",
            () -> EntityType.Builder.of(SerpentCrystalEntity::new, MobCategory.MONSTER)
                .sized(0.8F, 0.6F) 
                    .build("britannia_mod:serpent_crystal_sea")
    );

    
        public static final DeferredHolder<EntityType<?>, EntityType<SerpentIceEntity>> SERPENT_ICE_ENTITY = ENTITIES.register(
            "serpent_giant_ice",
            () -> EntityType.Builder.of(SerpentIceEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.3F)
                    .build("britannia_mod:serpent_giant_ice")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<SerpentGiantEntity>> SERPENT_GIANT_ENTITY = ENTITIES.register(
            "serpent_giant",
            () -> EntityType.Builder.of(SerpentGiantEntity::new, MobCategory.MONSTER)
                      .sized(0.8F, 0.6F) 
                    .build("britannia_mod:serpent_giant")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<SerpentLavaEntity>> SERPENT_LAVA_ENTITY = ENTITIES.register(
            "serpent_lava",
            () -> EntityType.Builder.of(SerpentLavaEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.3F)
                    .build("britannia_mod:serpent_lava")
    );


    public static final DeferredHolder<EntityType<?>, EntityType<ScorpionEntity>> SCORPION_ENTITY = ENTITIES.register(
            "scorpion",
            () -> EntityType.Builder.of(ScorpionEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 0.8F)
                    .build("britannia_mod:scorpion")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<LizardmanEntity>> LIZARDMAN_ENTITY = ENTITIES.register(
            "lizardman",
            () -> EntityType.Builder.of(LizardmanEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.2F)
                    .build("britannia_mod:lizardman")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<RatmanEntity>> RATMAN_ENTITY = ENTITIES.register(
            "ratman",
            () -> EntityType.Builder.of(RatmanEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.8F)
                    .build("britannia_mod:ratman")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<RatmanArcherEntity>> RATMAN_ARCHER_ENTITY = ENTITIES.register(
            "ratman_archer",
            () -> EntityType.Builder.of(RatmanArcherEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.8F)
                    .build("britannia_mod:ratman_archer")
    );
    
        public static final DeferredHolder<EntityType<?>, EntityType<RatmanAssassinEntity>> RATMAN_ASSASSIN_ENTITY = ENTITIES.register(
            "ratman_assassin",
            () -> EntityType.Builder.of(RatmanAssassinEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.8F)
                    .build("britannia_mod:ratman_assassin")
    );
    

    public static final DeferredHolder<EntityType<?>, EntityType<HarpyEntity>> HARPY_ENTITY = ENTITIES.register(
            "harpy",
            () -> EntityType.Builder.of(HarpyEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.5F)
                    .build("britannia_mod:harpy")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<HeadlessEntity>> HEADLESS_ENTITY = ENTITIES.register(
            "headless",
            () -> EntityType.Builder.of(HeadlessEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.5F) // Slightly shorter because, well, no head
                    .build("britannia_mod:headless")
    );


    public static final DeferredHolder<EntityType<?>, EntityType<OrcEntity>> ORC_ENTITY = ENTITIES.register(
            "orc",
            () -> EntityType.Builder.of(OrcEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.6F)
                    .build("britannia_mod:orc")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<OrcClubEntity>> ORC_CLUB_ENTITY = ENTITIES.register(
            "orc_club",
            () -> EntityType.Builder.of(OrcClubEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.6F)
                    .build("britannia_mod:orc_club")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<OgreEntity>> OGRE_ENTITY = ENTITIES.register(
            "ogre",
            () -> EntityType.Builder.of(OgreEntity::new, MobCategory.MONSTER)
                    .sized(1.8F, 4.3F)
                    .build("britannia_mod:ogre")
    );

public static final DeferredHolder<EntityType<?>, EntityType<OgreArcticEntity>> OGRE_ARCTIC_ENTITY = ENTITIES.register(
            "ogre_arctic",
            () -> EntityType.Builder.of(OgreArcticEntity::new, MobCategory.MONSTER)
                .sized(1.7F, 4.1F)
                    .build("britannia_mod:ogre_arctic")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<OgreLordEntity>> OGRE_LORD_ENTITY = ENTITIES.register(
            "ogre_lord",
            () -> EntityType.Builder.of(OgreLordEntity::new, MobCategory.MONSTER)
                      .sized(1.9F, 4.9F)
                    .build("britannia_mod:ogre_lord")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<OgreLordArcticEntity>> OGRE_LORD_ARCTIC_ENTITY = ENTITIES.register(
            "ogre_lord_arctic",
            () -> EntityType.Builder.of(OgreLordArcticEntity::new, MobCategory.MONSTER)
                      .sized(1.8F, 4.7F)
                    .build("britannia_mod:ogre_lord_arctic")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<TrollEntity>> TROLL_ENTITY = ENTITIES.register(
            "troll",
            () -> EntityType.Builder.of(TrollEntity::new, MobCategory.MONSTER)
                    .sized(1.4F, 3.9F)
                    .build("britannia_mod:troll")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<GargoyleEntity>> GARGOYLE_ENTITY = ENTITIES.register(
            "gargoyle",
            () -> EntityType.Builder.of(GargoyleEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.2F)
                    .build("britannia_mod:gargoyle")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<GargoyleDestroyerEntity>> GARGOYLE_DESTROYER_ENTITY = ENTITIES.register(
            "gargoyle_destroyer",
            () -> EntityType.Builder.of(GargoyleDestroyerEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.6F)
                    .build("britannia_mod:gargoyle_destroyer")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<GargoyleEnforcerEntity>> GARGOYLE_ENFORCER_ENTITY = ENTITIES.register(
            "gargoyle_enforcer",
            () -> EntityType.Builder.of(GargoyleEnforcerEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.6F)
                    .build("britannia_mod:gargoyle_enforcer")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<GargoyleStoneEntity>> GARGOYLE_STONE_ENTITY = ENTITIES.register(
            "gargoyle_stone",
            () -> EntityType.Builder.of(GargoyleStoneEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.0F)
                    .build("britannia_mod:gargoyle_stone")
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
                    .sized(1F, 2.0F)
                    .build("britannia_mod:lich")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<RatEntity>> RAT_ENTITY = ENTITIES.register(
            "rat",
            () -> EntityType.Builder.of(RatEntity::new, MobCategory.CREATURE)
                    .sized(0.4F, 0.4F)
                    .build("britannia_mod:rat")
    );
    

            public static final DeferredHolder<EntityType<?>, EntityType<CustomCatEntity>> CUSTOM_CAT_ENTITY = ENTITIES.register(
            "custom_cat",
            () -> EntityType.Builder.of(CustomCatEntity::new, MobCategory.CREATURE)
                .sized(0.6F, 0.7F)
                    .build("britannia_mod:custom_cat")
    );

                public static final DeferredHolder<EntityType<?>, EntityType<BritanniaCatEntity>> BRITANNIA_CAT_ENTITY = ENTITIES.register(
            "britannia_cat",
            () -> EntityType.Builder.of(BritanniaCatEntity::new, MobCategory.CREATURE)
                .sized(0.6F, 0.7F)
                    .build("britannia_mod:britannia_cat")
    );


public static final DeferredHolder<EntityType<?>, EntityType<TurkeyEntity>> TURKEY_ENTITY = ENTITIES.register(
            "turkey",
            () -> EntityType.Builder.of(TurkeyEntity::new, MobCategory.CREATURE)
                    .sized(1.1F, 1.2F)
                    .build("britannia_mod:turkey")
    );

public static final DeferredHolder<EntityType<?>, EntityType<HindEntity>> HIND_ENTITY = ENTITIES.register(
            "hind",
            () -> EntityType.Builder.of(HindEntity::new, MobCategory.CREATURE)
                    .sized(1.2F, 1.7F)
                    .build("britannia_mod:hind")
    );

    public static final DeferredHolder<EntityType<?>, EntityType<GreatHartEntity>> GREAT_HART_ENTITY = ENTITIES.register(
            "great_hart",
            () -> EntityType.Builder.of(GreatHartEntity::new, MobCategory.CREATURE)
                .sized(1.2F, 1.7F)
                    .build("britannia_mod:great_hart")
    );

  public static final DeferredHolder<EntityType<?>, EntityType<GorillaEntity>> GORILLA_ENTITY = ENTITIES.register(
            "gorilla",
            () -> EntityType.Builder.of(GorillaEntity::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.2F)
                    .build("britannia_mod:gorilla")
    );

      public static final DeferredHolder<EntityType<?>, EntityType<BearBrownEntity>> BEAR_BROWN_ENTITY = ENTITIES.register(
            "bear_brown",
            () -> EntityType.Builder.of(BearBrownEntity::new, MobCategory.CREATURE)
                    .sized(1.6F, 1.3F)
                    .build("britannia_mod:bear_brown")
    );

      public static final DeferredHolder<EntityType<?>, EntityType<BearBlackEntity>> BEAR_BLACK_ENTITY = ENTITIES.register(
            "bear_black",
            () -> EntityType.Builder.of(BearBlackEntity::new, MobCategory.CREATURE)
                    .sized(1.5F, 1.2F)
                    .build("britannia_mod:bear_black")
    );

      public static final DeferredHolder<EntityType<?>, EntityType<BearGrizzlyEntity>> BEAR_GRIZZLY_ENTITY = ENTITIES.register(
            "bear_grizzly",
            () -> EntityType.Builder.of(BearGrizzlyEntity::new, MobCategory.CREATURE)
                    .sized(1.7F, 1.4F)
                    .build("britannia_mod:bear_grizzly")
    );

     public static final DeferredHolder<EntityType<?>, EntityType<BearPolarEntity>> BEAR_POLAR_ENTITY = ENTITIES.register(
            "bear_polar",
            () -> EntityType.Builder.of(BearPolarEntity::new, MobCategory.CREATURE)
                    .sized(1.5F, 1.0F)
                    .build("britannia_mod:bear_polar")
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
            () -> EntityType.Builder.of(WispEntity::new, MobCategory.CREATURE)
                    .sized(1F, 1F)
                    .build("britannia_mod:wisp")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<ShadeEntity>> SHADE_ENTITY = ENTITIES.register(
            "shade",
            () -> EntityType.Builder.of(ShadeEntity::new, MobCategory.MONSTER)
                    .sized(1F, 2.5F)
                    .build("britannia_mod:shade")
    );


    public static final DeferredHolder<EntityType<?>, EntityType<AirElementalEntity>> AIR_ELEMENTAL_ENTITY = ENTITIES.register(
            "elemental_air",
            () -> EntityType.Builder.of(AirElementalEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.4F)
                    .build("britannia_mod:elemental_air")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<FireElementalEntity>> FIRE_ELEMENTAL_ENTITY = ENTITIES.register(
            "elemental_fire",
            () -> EntityType.Builder.of(FireElementalEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.4F)
                    .build("britannia_mod:elemental_fire")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<PoisonElementalEntity>> POISON_ELEMENTAL_ENTITY = ENTITIES.register(
            "elemental_poison",
            () -> EntityType.Builder.of(PoisonElementalEntity::new, MobCategory.MONSTER)
                     .sized(1.0F, 2.4F)
                    .build("britannia_mod:elemental_poison")
    );

    
        public static final DeferredHolder<EntityType<?>, EntityType<AcidElementalEntity>> ACID_ELEMENTAL_ENTITY = ENTITIES.register(
            "elemental_acid",
            () -> EntityType.Builder.of(AcidElementalEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.4F)
                    .build("britannia_mod:elemental_acid")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<WaterElementalEntity>> WATER_ELEMENTAL_ENTITY = ENTITIES.register(
            "elemental_water",
            () -> EntityType.Builder.of(WaterElementalEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.4F)
                    .build("britannia_mod:elemental_water")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<BloodElementalEntity>> BLOOD_ELEMENTAL_ENTITY = ENTITIES.register(
            "elemental_blood",
            () -> EntityType.Builder.of(BloodElementalEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.4F)
                    .build("britannia_mod:elemental_blood")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<DullCopperElementalEntity>> DULL_COPPER_ELEMENTAL_ENTITY = ENTITIES.register(
            "elemental_dull_copper",
            () -> EntityType.Builder.of(DullCopperElementalEntity::new, MobCategory.MONSTER)
                    .sized(2.2F, 3.8F)
                    .build("britannia_mod:elemental_dull_copper")
    );

        public static final DeferredHolder<EntityType<?>, EntityType<EarthElementalEntity>> EARTH_ELEMENTAL_ENTITY = ENTITIES.register(
            "earth_elemental",
            () -> EntityType.Builder.of(EarthElementalEntity::new, MobCategory.MONSTER)
                    .sized(2.2F, 3.8F)
                    .build("britannia_mod:earth_elemental")
    );


        public static final DeferredHolder<EntityType<?>, EntityType<ShadowOreElementalEntity>> SHADOW_ORE_ELEMENTAL_ENTITY = ENTITIES.register(
            "shadow_ore_elemental",
            () -> EntityType.Builder.of(ShadowOreElementalEntity::new, MobCategory.MONSTER)
                     .sized(2.2F, 3.8F)
                    .build("britannia_mod:shadow_ore_elemental")
    );

            public static final DeferredHolder<EntityType<?>, EntityType<GoldOreElementalEntity>> GOLD_ORE_ELEMENTAL_ENTITY = ENTITIES.register(
            "gold_ore_elemental",
            () -> EntityType.Builder.of(GoldOreElementalEntity::new, MobCategory.MONSTER)
                     .sized(2.2F, 3.8F)
                    .build("britannia_mod:gold_ore_elemental")
    );


public static final DeferredHolder<EntityType<?>, EntityType<LivingSeatEntity>> SEAT_ENTITY =
    ENTITIES.register("seat_entity",
        () -> EntityType.Builder.<LivingSeatEntity>of(LivingSeatEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.5f) // Required!
            .build("britannia_mod:seat_entity"));

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
        event.put(BRITANNIA_CAT_ENTITY.get(), BritanniaCatEntity.createAttributes().build());
        event.put(DAEMON_ENTITY.get(), DaemonEntity.createAttributes().build());
         event.put(LICH_ENTITY.get(), LichEntity.createAttributes().build());
        event.put(GORILLA_ENTITY.get(), GorillaEntity.createAttributes().build());
event.put(BEAR_BROWN_ENTITY.get(), BearBrownEntity.createAttributes().build());
        event.put(BEAR_BLACK_ENTITY.get(), BearBlackEntity.createAttributes().build());
        event.put(BEAR_POLAR_ENTITY.get(), BearPolarEntity.createAttributes().build());
        event.put(BEAR_GRIZZLY_ENTITY.get(), BearGrizzlyEntity.createAttributes().build());
        event.put(TURKEY_ENTITY.get(), TurkeyEntity.createAttributes().build());
        event.put(HIND_ENTITY.get(), HindEntity.createAttributes().build());
        event.put(GREAT_HART_ENTITY.get(), GreatHartEntity.createAttributes().build());

        event.put(RAT_ENTITY.get(), RatEntity.createAttributes().build());
        event.put(WISP_ENTITY.get(), WispEntity.createAttributes().build());
        event.put(HORSE_MERCHANT_ENTITY.get(), EntityHorseMerchant.createAttributes().build());
      //  event.put(FISH_MERCHANT_ENTITY.get(), FishTraderEntity.createAttributes().build());
        event.put(WOOD_MERCHANT_ENTITY.get(), EntityWoodMerchant.createAttributes().build());
        event.put(METAL_MERCHANT_ENTITY.get(), EntityMetalMerchant.createAttributes().build());
        event.put(STONE_MERCHANT_ENTITY.get(), EntityStoneMerchant.createAttributes().build());
        event.put(ARCHITECT_ENTITY.get(), ArchitectEntity.createAttributes().build());
        event.put(FISH_TRADER.get(), FishTraderEntity.createAttributes().build());

        event.put(QUEST_GIVER.get(), QuestGiverEntity.createAttributes().build());
        event.put(SALVAGE_TRADER.get(), SalvageTraderEntity.createAttributes().build());
        event.put(MEAT_TRADER.get(), EntityMetalMerchant.createAttributes().build());
        event.put(ALCOHOL_TRADER.get(), AlcoholTraderEntity.createAttributes().build());


        event.put(TOWNSPERSON.get(), TownPersonEntity.createAttributes().build());

        event.put(EntityRegistry.GIANT_RAT_ENTITY.get(), GiantRatEntity.createAttributes().build());
    event.put(EntityRegistry.ALLIGATOR_ENTITY.get(), AlligatorEntity.createAttributes().build());
    event.put(EntityRegistry.ETTIN_ENTITY.get(), EttinEntity.createAttributes().build());
    event.put(EntityRegistry.SERPENT_SILVER_ENTITY.get(), SerpentSilverEntity.createAttributes().build());
    event.put(EntityRegistry.SERPENT_SEA_ENTITY.get(), SerpentSeaEntity.createAttributes().build());
    event.put(EntityRegistry.SERPENT_DEEP_SEA_ENTITY.get(), SerpentDeepSeaEntity.createAttributes().build());
    event.put(EntityRegistry.SERPENT_LAVA_ENTITY.get(), SerpentLavaEntity.createAttributes().build());
    event.put(EntityRegistry.SERPENT_ICE_ENTITY.get(), SerpentIceEntity.createAttributes().build());
    event.put(EntityRegistry.SERPENT_CRYSTAL_SEA_ENTITY.get(), SerpentCrystalEntity.createAttributes().build());
    event.put(EntityRegistry.SERPENT_GIANT_ENTITY.get(), SerpentGiantEntity.createAttributes().build());

    event.put(EntityRegistry.SCORPION_ENTITY.get(), ScorpionEntity.createAttributes().build());
    event.put(EntityRegistry.LIZARDMAN_ENTITY.get(), LizardmanEntity.createAttributes().build());
    event.put(EntityRegistry.RATMAN_ENTITY.get(), RatmanEntity.createAttributes().build());
event.put(EntityRegistry.RATMAN_ARCHER_ENTITY.get(), RatmanArcherEntity.createAttributes().build());
event.put(EntityRegistry.RATMAN_ASSASSIN_ENTITY.get(), RatmanAssassinEntity.createAttributes().build());

    event.put(EntityRegistry.AIR_ELEMENTAL_ENTITY.get(), AirElementalEntity.createAttributes().build());
    event.put(EntityRegistry.FIRE_ELEMENTAL_ENTITY.get(), FireElementalEntity.createAttributes().build());
    event.put(EntityRegistry.WATER_ELEMENTAL_ENTITY.get(), WaterElementalEntity.createAttributes().build());
    event.put(EntityRegistry.POISON_ELEMENTAL_ENTITY.get(), PoisonElementalEntity.createAttributes().build());
    event.put(EntityRegistry.BLOOD_ELEMENTAL_ENTITY.get(), BloodElementalEntity.createAttributes().build());
    event.put(EntityRegistry.ACID_ELEMENTAL_ENTITY.get(), AcidElementalEntity.createAttributes().build());
    event.put(EntityRegistry.DULL_COPPER_ELEMENTAL_ENTITY.get(), DullCopperElementalEntity.createAttributes().build());


    event.put(EntityRegistry.HARPY_ENTITY.get(), HarpyEntity.createAttributes().build());
    event.put(EntityRegistry.HEADLESS_ENTITY.get(), HeadlessEntity.createAttributes().build());
        event.put(EntityRegistry.ORC_ENTITY.get(), OrcEntity.createAttributes().build());
        event.put(EntityRegistry.ORC_CLUB_ENTITY.get(), OrcClubEntity.createAttributes().build());

    event.put(EntityRegistry.OGRE_ENTITY.get(), OgreEntity.createAttributes().build());
        event.put(EntityRegistry.OGRE_LORD_ENTITY.get(), OgreLordEntity.createAttributes().build());
        event.put(EntityRegistry.OGRE_ARCTIC_ENTITY.get(), OgreArcticEntity.createAttributes().build());
        event.put(EntityRegistry.OGRE_LORD_ARCTIC_ENTITY.get(), OgreLordArcticEntity.createAttributes().build());


    event.put(EntityRegistry.TROLL_ENTITY.get(), TrollEntity.createAttributes().build());
    event.put(EntityRegistry.GARGOYLE_ENTITY.get(), GargoyleEntity.createAttributes().build());
event.put(EntityRegistry.GARGOYLE_ENFORCER_ENTITY.get(), GargoyleEnforcerEntity.createAttributes().build());
event.put(EntityRegistry.GARGOYLE_DESTROYER_ENTITY.get(), GargoyleDestroyerEntity.createAttributes().build());
event.put(EntityRegistry.GARGOYLE_STONE_ENTITY.get(), GargoyleStoneEntity.createAttributes().build());

    }
}
