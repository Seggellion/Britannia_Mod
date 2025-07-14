package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.DoubleBedBlockEntity;
import com.seggellion.britannia_mod.block.nudgeable.INudgeable;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos;

public class DoubleBedBlock extends Block implements EntityBlock, INudgeable {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BedPart> PART = EnumProperty.create("part", BedPart.class);
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    public static final BooleanProperty OCCUPIED_LEFT = BooleanProperty.create("occupied_left");
    public static final BooleanProperty OCCUPIED_RIGHT = BooleanProperty.create("occupied_right");

    public enum BedPart implements StringRepresentable {
        HEAD_LEFT("head_left"),
        HEAD_RIGHT("head_right"),
        FOOT_LEFT("foot_left"),
        FOOT_RIGHT("foot_right");

        private final String name;
        BedPart(String name) { this.name = name; }
        public String getSerializedName() { return name; }
    }

    protected static final VoxelShape BASE = Block.box(0.0, 3.0, 0.0, 16.0, 8.5, 16.0);
    protected static final VoxelShape FULL_SHAPE = Shapes.or(BASE);



    public DoubleBedBlock(Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, BedPart.FOOT_LEFT)
                .setValue(OCCUPIED, false)
                .setValue(OCCUPIED_LEFT, false)
                .setValue(OCCUPIED_RIGHT, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoubleBedBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED, OCCUPIED_LEFT, OCCUPIED_RIGHT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return FULL_SHAPE;
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        Direction facing = ctx.getHorizontalDirection();

        BlockPos footLeft = pos;
        BlockPos footRight = pos.relative(facing.getClockWise());
        BlockPos headLeft = pos.relative(facing);
        BlockPos headRight = pos.relative(facing).relative(facing.getClockWise());

        if (!level.getBlockState(footRight).canBeReplaced(ctx) ||
                !level.getBlockState(headLeft).canBeReplaced(ctx) ||
                !level.getBlockState(headRight).canBeReplaced(ctx)) {
            return null;
        }

        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART, BedPart.FOOT_LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();

            level.setBlock(pos.relative(right),
                    state.setValue(PART, BedPart.FOOT_RIGHT), 3);
            level.setBlock(pos.relative(facing),
                    state.setValue(PART, BedPart.HEAD_LEFT), 3);
            level.setBlock(pos.relative(facing).relative(right),
                    state.setValue(PART, BedPart.HEAD_RIGHT), 3);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.CONSUME;
        }

        DoubleBedBlockEntity bedEntity = (DoubleBedBlockEntity) level.getBlockEntity(pos);
        if (bedEntity == null) return InteractionResult.FAIL;

        return bedEntity.onPlayerInteract(player, pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {

        if (!level.isClientSide()
                && stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get())
                && player.isCreative()
                && !player.isSpectator()) {

            // ONLY allow nudging from HEAD_LEFT (master block)
            DoubleBedBlock.BedPart part = state.getValue(PART);
            if (part != DoubleBedBlock.BedPart.HEAD_LEFT) {
                return ItemInteractionResult.FAIL; // Block interaction on non-master blocks
            }

            // This IS the master block, proceed normally
            DoubleBedBlockEntity bedEntity = (DoubleBedBlockEntity) level.getBlockEntity(pos);
            if (bedEntity == null) return ItemInteractionResult.FAIL;

            if (hand == InteractionHand.MAIN_HAND) {
                // Rotate
                Direction currentFacing = state.getValue(FACING);
                Direction newFacing = currentFacing.getClockWise();
                rotateBed(level, pos, currentFacing, newFacing);
            } else {
                // Nudge
                Direction nudgeDirection = getNudgeDirection(hit, player);
                bedEntity.nudge(nudgeDirection);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private void rotateBed(Level level, BlockPos headLeftPos, Direction oldFacing, Direction newFacing) {
        Direction oldRight = oldFacing.getClockWise();
        Direction newRight = newFacing.getClockWise();

        // Calculate positions from HEAD_LEFT (master)
        BlockPos[] oldPositions = {
                headLeftPos.relative(oldFacing.getOpposite()),                              // FOOT_LEFT
                headLeftPos.relative(oldFacing.getOpposite()).relative(oldRight),           // FOOT_RIGHT
                headLeftPos,                                                                 // HEAD_LEFT (master)
                headLeftPos.relative(oldRight)                                               // HEAD_RIGHT
        };

        BlockPos[] newPositions = {
                headLeftPos.relative(newFacing.getOpposite()),                              // FOOT_LEFT
                headLeftPos.relative(newFacing.getOpposite()).relative(newRight),           // FOOT_RIGHT
                headLeftPos,                                                                 // HEAD_LEFT (master stays)
                headLeftPos.relative(newRight)                                               // HEAD_RIGHT
        };

        DoubleBedBlock.BedPart[] parts = {
                DoubleBedBlock.BedPart.FOOT_LEFT,
                DoubleBedBlock.BedPart.FOOT_RIGHT,
                DoubleBedBlock.BedPart.HEAD_LEFT,
                DoubleBedBlock.BedPart.HEAD_RIGHT
        };

        for (BlockPos pos : oldPositions) {
            level.removeBlock(pos, false);
        }

        for (int i = 0; i < newPositions.length; i++) {
            BlockState newState = defaultBlockState()
                    .setValue(FACING, newFacing)
                    .setValue(PART, parts[i]);
            level.setBlock(newPositions[i], newState, 3);
        }
    }

    private Direction getNudgeDirection(BlockHitResult hit, Player player) {
        double hitX = hit.getLocation().x - Math.floor(hit.getLocation().x);
        double hitZ = hit.getLocation().z - Math.floor(hit.getLocation().z);

        if (hitX < 0.25) return Direction.WEST;
        if (hitX > 0.75) return Direction.EAST;
        if (hitZ < 0.25) return Direction.NORTH;
        if (hitZ > 0.75) return Direction.SOUTH;

        return player.getDirection().getOpposite();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            breakWholeStructure(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void breakWholeStructure(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BedPart part = state.getValue(PART);

        // Find HEAD_LEFT position (master position)
        BlockPos headLeft = switch (part) {
            case HEAD_LEFT -> pos;
            case HEAD_RIGHT -> pos.relative(facing.getCounterClockWise());
            case FOOT_LEFT -> pos.relative(facing);
            case FOOT_RIGHT -> pos.relative(facing).relative(facing.getCounterClockWise());
        };

        Direction right = facing.getClockWise();

        level.destroyBlock(headLeft.relative(facing.getOpposite()), true); // FOOT_LEFT
        level.destroyBlock(headLeft.relative(facing.getOpposite()).relative(right), false); // FOOT_RIGHT
        level.destroyBlock(headLeft, false); // HEAD_LEFT
        level.destroyBlock(headLeft.relative(right), false); // HEAD_RIGHT
    }
}