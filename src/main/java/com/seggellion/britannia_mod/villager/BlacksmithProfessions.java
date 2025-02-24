package com.seggellion.britannia_mod.villager;

import com.google.common.collect.ImmutableSet;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class BlacksmithProfessions {
        private static final Logger LOGGER = LogManager.getLogger();

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, BritanniaMod.MODID);

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, BritanniaMod.MODID);

    // Register the POI type for the Blacksmith
    public static final DeferredHolder<PoiType, PoiType> JOURNEYMAN_BLACKSMITH_POI = POI_TYPES.register(
        "journeyman_blacksmith_poi",
        () -> new PoiType(
            ImmutableSet.copyOf(BlockRegistry.BLACKSMITH_SPAWN_BLOCK.get().getStateDefinition().getPossibleStates()),
            1, // Max tickets
            1
        )
    );

    // Register the Villager Profession
    public static final DeferredHolder<VillagerProfession, VillagerProfession> JOURNEYMAN_BLACKSMITH =
            PROFESSIONS.register("journeyman_blacksmith",
                () -> new VillagerProfession(
                    BritanniaMod.MODID + ":journeyman_blacksmith",
                    (poiTypeHolder) -> poiTypeHolder.is(ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, JOURNEYMAN_BLACKSMITH_POI.getId())),  // ✅ Fixed
                    (poiTypeHolder) -> poiTypeHolder.is(ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, JOURNEYMAN_BLACKSMITH_POI.getId())),  // ✅ Fixed
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_ARMORER // Or another fitting work sound
                )
            );

    public static void registerAll(IEventBus modEventBus) {

        POI_TYPES.register(modEventBus);
        PROFESSIONS.register(modEventBus);
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == JOURNEYMAN_BLACKSMITH.get()) {

            Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
            Holder<Item> goldCoinHolder = BuiltInRegistries.ITEM.wrapAsHolder(ItemRegistry.GOLD_COIN.get());

            // Level 1 Trade
            trades.computeIfAbsent(1, level -> new java.util.ArrayList<>()).add((merchant, random) -> {
                ItemCost cost = new ItemCost(goldCoinHolder, 10, null, ItemStack.EMPTY);
                ItemStack result = new ItemStack(new QualitySwordItem(UOMetalToolMaterial.IRON.getTier(),
                        new net.minecraft.world.item.Item.Properties().stacksTo(1)));
                return new MerchantOffer(cost, result, 10, 1, 0.05F);
            });

            // Level 2 Trade
            trades.computeIfAbsent(2, level -> new java.util.ArrayList<>()).add((merchant, random) -> {
                ItemCost cost = new ItemCost(goldCoinHolder, 50, null, ItemStack.EMPTY);
                ItemStack result = new ItemStack(new QualitySwordItem(UOMetalToolMaterial.VALORITE.getTier(),
                        new net.minecraft.world.item.Item.Properties().stacksTo(1)));

                return new MerchantOffer(cost, result, 5, 2, 0.05F);
            });

            LOGGER.info("Successfully registered trades for Journeyman Blacksmith.");
        }
    }
}
