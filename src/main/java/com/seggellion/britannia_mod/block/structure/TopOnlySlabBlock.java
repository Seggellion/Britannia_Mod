package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;


import javax.annotation.Nullable;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;


public class TopOnlySlabBlock extends SlabBlock implements EntityBlock {
    public static final BooleanProperty SUPPORTS_LANTERN =
            BooleanProperty.create("supports_lantern");

    private static final Logger LOGGER = LogUtils.getLogger();


    public TopOnlySlabBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(TYPE, SlabType.TOP)
            .setValue(WATERLOGGED, false)
            .setValue(SUPPORTS_LANTERN, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return placementStateForFluid(fluidState);
    }

    BlockState placementStateForFluid(FluidState fluidState) {
        return this.defaultBlockState()
                .setValue(TYPE, SlabType.TOP)
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdaptiveRoofBlockEntity(pos, state);
    }


@Override
public InteractionResult useWithoutItem(BlockState state,
                                        Level level,
                                        BlockPos pos,
                                        Player player,
                                        BlockHitResult hit) {

    ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (isLantern(held)) return InteractionResult.PASS; // let vanilla handle it

    ResourceLocation texture = getTextureFromItem(held.getItem());
            LOGGER.info("[AdaptiveRoof] Player used item: {}", held);
            LOGGER.info("[AdaptiveRoof] texture: {}", texture);

    if (texture == null)                      // unsupported item
        return InteractionResult.PASS;

    if (level.getBlockEntity(pos) instanceof AdaptiveRoofBlockEntity be) {

        // The server owns stored decoration; its update packet refreshes client model data.
        if (!level.isClientSide) {
            be.setBottomTexture(texture);
        }
        return InteractionResult.SUCCESS;
    }
    return InteractionResult.PASS;
}


@Override
public VoxelShape getBlockSupportShape(BlockState state,
                                       BlockGetter level,
                                       BlockPos pos) {
    return state.getValue(SUPPORTS_LANTERN) ? Shapes.block()
                                            : super.getBlockSupportShape(state, level, pos);
}

    @Override
    public VoxelShape getOcclusionShape(BlockState state,
                                        BlockGetter level,
                                        BlockPos pos) {
        // SUPPORTS_LANTERN mirrors whether the acquired lower half exists. The terrain
        // renderer uses this state-only shape to decide which directional baked-quad
        // buckets to request and where light/AO may be occluded.
        return state.getValue(SUPPORTS_LANTERN) ? Shapes.block()
                                                : super.getOcclusionShape(state, level, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state,
                               BlockGetter level,
                               BlockPos pos,
                               CollisionContext context) {
        return state.getValue(SUPPORTS_LANTERN) ? Shapes.block()
                                                : super.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state,
                                        BlockGetter level,
                                        BlockPos pos,
                                        CollisionContext ctx) {
        return state.getValue(SUPPORTS_LANTERN) ? Shapes.block()
                                                : super.getCollisionShape(state, level, pos, ctx);
    }

private static boolean isLantern(ItemStack stack) {
        return stack.is(Items.LANTERN) || stack.is(Items.SOUL_LANTERN);
    }

@Override
protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(SUPPORTS_LANTERN); // ✅ REQUIRED
}


    @Nullable
    private ResourceLocation getTextureFromItem(Item item) {
          if (item == Items.WOODEN_AXE) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "block/air");
    }

        if (item == Items.STONE) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/stone");
        if (item == Items.OAK_LOG) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/oak_log");
        if (item == Items.DARK_OAK_LOG) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/dark_oak_log");

        if (item == Items.SPRUCE_LOG) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/spruce_log");
        if (item == Items.JUNGLE_LOG) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/jungle_log");

        if (item == Items.OAK_PLANKS) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/oak_planks");
        if (item == Items.SPRUCE_PLANKS) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/spruce_planks");

        if (item == Item.byBlock(BlockRegistry.CUSTOM_SANDSTONE_BRICK.get())) {
            // Match the registered block item's canonical model (variant 0, bottom row).
            return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/sandstone/custom_sandstone_brick_0");
        }
     
             if (item == Item.byBlock(BlockRegistry.THATCH_ROOF.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/roof/thatch_roof_flat");
        }

        if (item == Item.byBlock(BlockRegistry.OAK_WALL_BOTTOM.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/oak_wall_bottom");
        }
        if (item == Item.byBlock(BlockRegistry.OAK_WALL_TOP.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/oak_wall_top");
        }
        if (item == Item.byBlock(BlockRegistry.BIRCH_WALL.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/birch_wall");
        }
        if (item == Item.byBlock(BlockRegistry.BRICK_WALL_BOTTOM.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/brick_wall_bottom");
        }
        if (item == Item.byBlock(BlockRegistry.BRICK_WALL_TOP.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/brick_wall_top");
        }
        if (item == Item.byBlock(BlockRegistry.PLASTER_STONE_FOUNDATION.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/plaster_stone_foundation");
        }
        if (item == Item.byBlock(BlockRegistry.COBBLESTONE_WALL_BOTTOM.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/cobblestone_wall_bottom");
        }
        if (item == Item.byBlock(BlockRegistry.COBBLESTONE_WALL_TOP.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/cobblestone_wall_top");
        }
        if (item == Item.byBlock(BlockRegistry.COBBLESTONE_FOUNDATION.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/cobblestone_foundation");
        }
        if (item == Item.byBlock(BlockRegistry.PLASTER_WOOD_WALL_BOTTOM.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/plaster_wood_wall_bottom");
        }
        if (item == Item.byBlock(BlockRegistry.PLASTER_WOOD_WALL_TOP.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/plaster_wood_wall_top");
        }
        if (item == Item.byBlock(BlockRegistry.LOG_WALL.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/log_wall");
        }
        if (item == Item.byBlock(BlockRegistry.DARK_STONE_WALL_BOTTOM.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/dark_stone_wall_bottom1");
        }
        if (item == Item.byBlock(BlockRegistry.DARK_STONE_WALL.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/dark_stone_wall_bottom2");
        }
        if (item == Item.byBlock(BlockRegistry.CORRAL_WALL.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/corral_texture");
        }
        if (item == Item.byBlock(BlockRegistry.PLASTER_WOOD_FOUNDATION.get())) {
                return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/structure/plaster_wood_foundation");
        }
        return null;
    }
}
