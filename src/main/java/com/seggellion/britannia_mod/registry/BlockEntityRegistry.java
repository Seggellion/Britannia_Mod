package com.seggellion.britannia_mod.registry;


import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import com.seggellion.britannia_mod.block.entity.CarpetTeleporterBlockEntity;
import com.seggellion.britannia_mod.block.entity.DoubleBedBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.CandelabraBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.ChairBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.RotatableFurnitureBlockEntity;
import com.seggellion.britannia_mod.structure.HouseSignBlockEntity;
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

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarpetTeleporterBlockEntity>> CARPET_TELEPORTER_BLOCK_ENTITY_TYPE =
    BLOCK_ENTITIES.register("carpet_teleporter_block_entity", () ->
        BlockEntityType.Builder.of(CarpetTeleporterBlockEntity::new,
                                   BlockRegistry.CARPET_TELEPORTER_BLOCK.get())
                               .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HouseSignBlockEntity>> HOUSE_SIGN =
        BLOCK_ENTITIES.register("house_sign", () ->
            BlockEntityType.Builder.of(HouseSignBlockEntity::new, BlockRegistry.HOUSE_SIGN_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CandelabraBlockEntity>> CANDELABRA =
            BLOCK_ENTITIES.register("candelabra", () ->
                    BlockEntityType.Builder.of(CandelabraBlockEntity::new,
                            BlockRegistry.CANDELABRA_SMALL.get(),
                            BlockRegistry.CANDELABRA_TALL.get(),
                            BlockRegistry.LAMP_POST_REGULAR.get(),
                            BlockRegistry.LAMP_POST_FANCY.get(),
                            BlockRegistry.TORCH_WALL.get(),
                            BlockRegistry.TORCH_STANDING.get(),
                            BlockRegistry.BRAZIER_SMALL.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RotatableFurnitureBlockEntity>> ROTATABLE_FURNITURE =
            BLOCK_ENTITIES.register("rotatable_furniture", () ->
                    BlockEntityType.Builder.of(RotatableFurnitureBlockEntity::new,
                            BlockRegistry.YEW_TABLE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChairBlockEntity>> CHAIR =
            BLOCK_ENTITIES.register("chair", () ->
                    BlockEntityType.Builder.of(ChairBlockEntity::new,
                            BlockRegistry.MAGINCIA_STYLE_THRONE.get(),
                            BlockRegistry.LORD_BRITISH_THRONE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<DoubleBedBlockEntity>> DOUBLE_BED =
            BLOCK_ENTITIES.register("double_bed",
                    () -> BlockEntityType.Builder
                            .of(DoubleBedBlockEntity::new, BlockRegistry.DOUBLE_BED.get())
                            .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}