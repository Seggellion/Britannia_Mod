package com.seggellion.britannia_mod.structure.multiblock;

import com.seggellion.britannia_mod.structure.lifecycle.ShrineIntegrityService;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineRemovalCause;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureTransform;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.HorizontalFacing;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.WorldPosition;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Lightweight occupied cell encoding only facing and its local offset from the anchor. */
public final class LargeStructurePartBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty LOCAL_X = IntegerProperty.create(
            "local_x", 0, StructureGeometry.MAX_LOCAL_X);
    public static final IntegerProperty LOCAL_Y = IntegerProperty.create(
            "local_y", 0, StructureGeometry.MAX_LOCAL_Y);
    public static final IntegerProperty LOCAL_Z = IntegerProperty.create(
            "local_z", 0, StructureGeometry.MAX_LOCAL_Z);
    private static final VoxelShape CELL_SHAPE = Shapes.block();

    public LargeStructurePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LOCAL_X, 0)
                .setValue(LOCAL_Y, 0)
                .setValue(LOCAL_Z, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOCAL_X, LOCAL_Y, LOCAL_Z);
    }

    public BlockState stateFor(Direction facing, LocalOffset offset) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Part facing must be horizontal");
        }
        if (offset.equals(LocalOffset.ANCHOR)
                || offset.x() < 0 || offset.x() > StructureGeometry.MAX_LOCAL_X
                || offset.y() < 0 || offset.y() > StructureGeometry.MAX_LOCAL_Y
                || offset.z() < 0 || offset.z() > StructureGeometry.MAX_LOCAL_Z) {
            throw new IllegalArgumentException("Offset cannot be encoded as a large-structure part: " + offset);
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(LOCAL_X, offset.x())
                .setValue(LOCAL_Y, offset.y())
                .setValue(LOCAL_Z, offset.z());
    }

    public static LocalOffset localOffset(BlockState state) {
        return new LocalOffset(
                state.getValue(LOCAL_X), state.getValue(LOCAL_Y), state.getValue(LOCAL_Z));
    }

    public static BlockPos anchorPosition(BlockPos partPosition, BlockState state) {
        Direction facing = state.getValue(FACING);
        WorldPosition anchor = StructureTransform.anchorPosition(
                worldPosition(partPosition), horizontalFacing(facing), localOffset(state));
        return new BlockPos(anchor.x(), anchor.y(), anchor.z());
    }

    public static HorizontalFacing horizontalFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> HorizontalFacing.NORTH;
            case EAST -> HorizontalFacing.EAST;
            case SOUTH -> HorizontalFacing.SOUTH;
            case WEST -> HorizontalFacing.WEST;
            default -> throw new IllegalArgumentException("Facing must be horizontal: " + facing);
        };
    }

    public static WorldPosition worldPosition(BlockPos pos) {
        return new WorldPosition(pos.getX(), pos.getY(), pos.getZ());
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
            net.minecraft.world.level.block.entity.BlockEntity entity, ItemStack tool) {
        // The central lifecycle already decided the sole configured drop.
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
            BlockPos anchor = anchorPosition(pos, state);
            if (!ShrineLifecycleService.isGuarded(server, anchor)) {
                ShrineLifecycleService.removeExternalPart(server, pos, state);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos,
            Block neighbor, BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level instanceof ServerLevel server) {
            ShrineIntegrityService.checkPart(server, pos, state);
        }
    }
}
