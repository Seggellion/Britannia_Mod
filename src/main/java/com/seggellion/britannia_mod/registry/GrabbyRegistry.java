package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.grabbyhands.block.GrabbyPlacedItemBlock;
import com.seggellion.britannia_mod.grabbyhands.blockentity.GrabbyPlacedItemBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
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

/**
 * The one block Grabby Hands owns outright: a host for items that have no block form.
 *
 * <p>Follows {@code BannerBlockRegistry}'s convention of a feature keeping its own deferred
 * registers rather than adding to the shared ones.
 */
public final class GrabbyRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, BritanniaMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BritanniaMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, BritanniaMod.MODID);

    public static final DeferredHolder<Block, GrabbyPlacedItemBlock> PLACED_ITEM =
            BLOCKS.register("grabby_placed_item", () -> new GrabbyPlacedItemBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .strength(0.2F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            // Never piston-movable: a placed possession is not cargo.
                            .pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrabbyPlacedItemBlockEntity>>
            PLACED_ITEM_BLOCK_ENTITY = BLOCK_ENTITIES.register("grabby_placed_item", () ->
                    BlockEntityType.Builder.of(GrabbyPlacedItemBlockEntity::new, PLACED_ITEM.get())
                            .build(null));

    /**
     * The host's block item.
     *
     * <p>Never given to players and never added to a creative tab. It exists so placement can run
     * through {@code BlockItem.place}, which is what supplies facing, {@code canSurvive}, the
     * unobstructed check and the native place sound. Building a bespoke placement routine instead
     * would mean reimplementing all of that, badly.
     */
    public static final DeferredHolder<Item, BlockItem> PLACED_ITEM_BLOCK_ITEM =
            ITEMS.register("grabby_placed_item", () ->
                    new BlockItem(PLACED_ITEM.get(), new Item.Properties().stacksTo(1)));

    private GrabbyRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
