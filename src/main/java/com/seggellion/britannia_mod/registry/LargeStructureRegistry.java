package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Minimal Milestone 2 registration: one anchor, one part, one entity, and one family item. */
public final class LargeStructureRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, BritanniaMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BritanniaMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, BritanniaMod.MODID);

    public static final DeferredHolder<Block, LargeStructureAnchorBlock> LARGE_STRUCTURE_ANCHOR =
            BLOCKS.register("large_structure_anchor", () -> new LargeStructureAnchorBlock(properties()));

    public static final DeferredHolder<Block, LargeStructurePartBlock> LARGE_STRUCTURE_PART =
            BLOCKS.register("large_structure_part", () -> new LargeStructurePartBlock(properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeStructureAnchorBlockEntity>>
            LARGE_STRUCTURE = BLOCK_ENTITIES.register("large_structure", () ->
                    BlockEntityType.Builder.of(
                            LargeStructureAnchorBlockEntity::new, LARGE_STRUCTURE_ANCHOR.get()).build(null));

    public static final DeferredHolder<Item, ShrineItem> SHRINE = ITEMS.register(
            "shrine", () -> new ShrineItem(new Item.Properties().stacksTo(1)));

    private LargeStructureRegistry() {
    }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.0F, 6.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        ITEMS.register(eventBus);
    }
}
