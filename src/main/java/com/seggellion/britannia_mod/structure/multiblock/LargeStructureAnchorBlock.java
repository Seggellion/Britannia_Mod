package com.seggellion.britannia_mod.structure.multiblock;

import com.seggellion.britannia_mod.structure.lifecycle.ShrineIntegrityService;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineRemovalCause;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible diagnostic anchor with one cell-bounded solid shape. */
public final class LargeStructureAnchorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape CELL_SHAPE = Shapes.block();

    public LargeStructureAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LargeStructureAnchorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CELL_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CELL_SHAPE;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ShrineLifecycleService.pick(level, pos, state);
    }

    @Override
    public boolean onDestroyedByPlayer(
            BlockState state, Level level, BlockPos pos, Player player,
            boolean willHarvest, FluidState fluid) {
        if (level instanceof ServerLevel server) {
            ShrineRemovalCause cause = player.hasInfiniteMaterials()
                    ? ShrineRemovalCause.CREATIVE_PLAYER : ShrineRemovalCause.SURVIVAL_PLAYER;
            return ShrineLifecycleService.removeFrom(server, pos, state, cause).claimed();
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void playerDestroy(
            Level level, Player player, BlockPos pos, BlockState state,
            BlockEntity entity, ItemStack tool) {
        // onDestroyedByPlayer and the central lifecycle emitted the only permitted configured drop.
    }

    @Override
    protected void onExplosionHit(
            BlockState state, Level level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (level instanceof ServerLevel server) {
            ShrineLifecycleService.removeFrom(server, pos, state, ShrineRemovalCause.EXPLOSION);
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (level instanceof ServerLevel server) {
            ShrineLifecycleService.removeFrom(server, pos, state, ShrineRemovalCause.EXPLOSION);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel server && !state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LargeStructureAnchorBlockEntity anchor
                    && !ShrineLifecycleService.isGuarded(server, pos)) {
                ShrineLifecycleService.removeExternalAnchor(server, pos, anchor);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos,
            Block neighbor, BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level instanceof ServerLevel server && !ShrineLifecycleService.isGuarded(server, pos)) {
            ShrineIntegrityService.checkAnchor(server, pos);
        }
    }
}
