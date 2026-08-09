package com.seggellion.britannia_mod.banner.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.placement.BannerBlockItemTransfer;
import com.seggellion.britannia_mod.banner.structure.BannerLocalOffset;
import com.seggellion.britannia_mod.banner.structure.BannerRemovalCause;
import com.seggellion.britannia_mod.banner.structure.BannerStructureIntegrity;
import com.seggellion.britannia_mod.banner.structure.BannerStructureLifecycle;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Lightweight occupied cell. Facing and local offset are its entire authority reference. */
public final class BannerPartBlock extends Block {
    public static final MapCodec<BannerPartBlock> CODEC = simpleCodec(BannerPartBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BannerOrientation> ORIENTATION =
            EnumProperty.create("orientation", BannerOrientation.class);
    public static final IntegerProperty HORIZONTAL_OFFSET = IntegerProperty.create("horizontal", 0, 2);
    public static final IntegerProperty VERTICAL_OFFSET = IntegerProperty.create("vertical", 0, 1);
    public BannerPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ORIENTATION, BannerOrientation.WALL_PARALLEL)
                .setValue(HORIZONTAL_OFFSET, 0)
                .setValue(VERTICAL_OFFSET, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public BlockState stateFor(Direction facing, BannerLocalOffset offset) {
        return stateFor(facing, BannerOrientation.WALL_PARALLEL, offset);
    }

    public BlockState stateFor(
            Direction facing, BannerOrientation orientation, BannerLocalOffset offset) {
        if (offset.isAnchor()) {
            throw new IllegalArgumentException("The anchor cannot be encoded as a banner part");
        }
        return defaultBlockState().setValue(FACING, facing)
                .setValue(ORIENTATION, orientation)
                .setValue(HORIZONTAL_OFFSET, offset.horizontal())
                .setValue(VERTICAL_OFFSET, offset.vertical());
    }

    public static BannerLocalOffset localOffset(BlockState state) {
        return new BannerLocalOffset(state.getValue(HORIZONTAL_OFFSET), state.getValue(VERTICAL_OFFSET));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Parts retain selection/collision shapes but never emit cloth, mount, or fallback geometry.
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BannerStructureTransform.cellShape(
                state.getValue(ORIENTATION), state.getValue(FACING), false);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return true;
        }
        var resolution = BannerStructureLifecycle.resolve(server, pos, state);
        return resolution.status() == BannerStructureLifecycle.ResolutionStatus.ANCHOR_CHUNK_UNLOADED
                || resolution.valid();
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level instanceof ServerLevel server) {
            BannerStructureIntegrity.checkPart(server, pos, state);
        }
    }

    @Override
    public boolean onDestroyedByPlayer(
            BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (level instanceof ServerLevel server) {
            BannerRemovalCause cause = player.hasInfiniteMaterials()
                    ? BannerRemovalCause.CREATIVE_PLAYER : BannerRemovalCause.SURVIVAL_PLAYER;
            return BannerStructureLifecycle.removeFrom(server, pos, state, cause, player).claimed();
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void playerDestroy(
            Level level, Player player, BlockPos pos, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity entity, ItemStack tool) {
        // The lifecycle service has already decided and emitted the sole structure drop.
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BannerOrientation orientation = state.getValue(ORIENTATION);
        BannerLocalOffset offset = localOffset(state);
        BlockPos anchorPos = BannerStructureTransform.anchorPosition(pos, facing, orientation, offset);
        if (level.hasChunkAt(anchorPos)
                && level.getBlockState(anchorPos).getBlock() instanceof BannerBlock
                && level.getBlockState(anchorPos).getValue(BannerBlock.FACING) == facing
                && level.getBlockState(anchorPos).getValue(BannerBlock.ORIENTATION) == orientation
                && level.getBlockEntity(anchorPos) instanceof BannerBlockEntity anchor
                && anchor.placedStructure().filter(structure -> structure.orientation() == orientation
                        && structure.contains(offset)).isPresent()) {
            return BannerBlockItemTransfer.fromBlockEntity(anchor);
        }
        return BannerBlockItemTransfer.fromBlockEntity(null);
    }

    @Override
    protected void onExplosionHit(
            BlockState state, Level level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (level instanceof ServerLevel server) {
            BannerStructureLifecycle.removeFrom(server, pos, state, BannerRemovalCause.EXPLOSION, null);
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (level instanceof ServerLevel server) {
            BannerStructureLifecycle.removeFrom(server, pos, state, BannerRemovalCause.EXPLOSION, null);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel server && !state.is(newState.getBlock())) {
            var resolution = BannerStructureLifecycle.resolve(server, pos, state);
            if (!BannerStructureLifecycle.isGuarded(server, resolution.anchorPos())) {
                BannerStructureLifecycle.removeExternalPart(server, pos, state);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ORIENTATION, HORIZONTAL_OFFSET, VERTICAL_OFFSET);
    }
}
