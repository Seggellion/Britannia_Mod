package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One authoritative anchor, one generic occupied-part block, and one anchor-only block entity. */
public final class BannerBlockRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, BritanniaMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BritanniaMod.MODID);

    public static final DeferredHolder<Block, BannerBlock> BANNER = BLOCKS.register("banner", () ->
            new BannerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<Block, BannerPartBlock> BANNER_PART = BLOCKS.register("banner_part", () ->
            new BannerPartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BannerBlockEntity>> BANNER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("banner", () -> BlockEntityType.Builder.of(
                    BannerBlockEntity::new, BANNER.get()).build(null));

    private BannerBlockRegistry() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
