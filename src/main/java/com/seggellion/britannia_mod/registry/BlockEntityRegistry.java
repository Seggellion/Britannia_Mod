package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseSignBlockEntity;
import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockEntityRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "britannia_mod");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HouseLotBlockEntity>> HOUSE_LOT =
        BLOCK_ENTITIES.register("house_lot", () ->
            BlockEntityType.Builder.of(HouseLotBlockEntity::new, BlockRegistry.HOUSE_LOT_BLOCK.get()).build(null));

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdaptiveRoofBlockEntity>> ADAPTIVE_ROOF =
    BLOCK_ENTITIES.register("adaptive_roof", () ->
        BlockEntityType.Builder.of(
            AdaptiveRoofBlockEntity::new,
            BlockRegistry.TILE_ROOF_FLAT.get(),
            BlockRegistry.CEDAR_ROOF_FLAT.get(),
            BlockRegistry.SLATE_ROOF_FLAT.get(),
            BlockRegistry.SLATE_ROOF_1_FLAT.get(),
            BlockRegistry.SLATE_ROOF_2_FLAT.get(),
            BlockRegistry.THATCH_ROOF_FLAT.get()
        ).build(null)
    );


    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HouseSignBlockEntity>> HOUSE_SIGN =
        BLOCK_ENTITIES.register("house_sign", () ->
            BlockEntityType.Builder.of(HouseSignBlockEntity::new, BlockRegistry.HOUSE_SIGN_BLOCK.get()).build(null));


    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
