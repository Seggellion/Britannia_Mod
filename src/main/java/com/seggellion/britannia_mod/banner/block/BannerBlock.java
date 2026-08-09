package com.seggellion.britannia_mod.banner.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.placement.BannerBlockItemTransfer;
import com.seggellion.britannia_mod.banner.structure.BannerRemovalCause;
import com.seggellion.britannia_mod.banner.structure.BannerStructureIntegrity;
import com.seggellion.britannia_mod.banner.structure.BannerStructureLifecycle;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import java.util.function.BiConsumer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** One thin diagnostic banner anchor. FACING points away from its supporting wall. */
public final class BannerBlock extends BaseEntityBlock {
    public static final MapCodec<BannerBlock> CODEC = simpleCodec(BannerBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BannerOrientation> ORIENTATION =
            EnumProperty.create("orientation", BannerOrientation.class);

    public BannerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ORIENTATION, BannerOrientation.WALL_PARALLEL));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BannerBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // The authoritative anchor block entity owns the complete normal and fallback presentation.
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BannerStructureTransform.cellShape(
                state.getValue(ORIENTATION), state.getValue(FACING), true);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level instanceof ServerLevel server) {
            BannerStructureIntegrity.checkAnchor(server, pos);
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return BannerBlockItemTransfer.fromBlockEntity(
                blockEntity instanceof BannerBlockEntity banner ? banner : null);
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
            Level level, Player player, BlockPos pos, BlockState state, BlockEntity entity, ItemStack tool) {
        // The lifecycle service emitted the only permitted configured item before vanilla reaches this callback.
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
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof BannerBlockEntity banner && !BannerStructureLifecycle.isGuarded(server, pos)) {
                BannerStructureLifecycle.removeExternalAnchor(server, pos, banner);
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
        builder.add(FACING, ORIENTATION);
    }
}
