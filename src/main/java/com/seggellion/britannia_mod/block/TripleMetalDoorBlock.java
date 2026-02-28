package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.BritanniaBlockSetTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TripleMetalDoorBlock extends DoorBlock {
    public static final EnumProperty<TripleBlockPart> TRIPLE_PART = EnumProperty.create("part", TripleBlockPart.class);

    public TripleMetalDoorBlock() {
        super(BritanniaBlockSetTypes.METAL_DOOR, 
            Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                .strength(5.0F)
                .noOcclusion()
                .sound(SoundType.METAL)
        );
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(OPEN, false)
            .setValue(HINGE, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT)
            .setValue(POWERED, false)
            .setValue(TRIPLE_PART, TripleBlockPart.LOWER)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)); 
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HINGE, POWERED, TRIPLE_PART, BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    private net.minecraft.world.level.block.state.properties.DoorHingeSide getHinge(BlockPlaceContext context) {
        net.minecraft.world.phys.Vec3 clickLoc = context.getClickLocation();
        double x = clickLoc.x - (double)context.getClickedPos().getX();
        double z = clickLoc.z - (double)context.getClickedPos().getZ();
        Direction direction = context.getHorizontalDirection();
        
        return switch (direction) {
            case SOUTH -> x < 0.5 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT : net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT;
            case WEST -> z < 0.5 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT : net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT;
            case EAST -> z > 0.5 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT : net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT;
            default -> x > 0.5 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT : net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 2 && 
            level.getBlockState(pos.above()).canBeReplaced(context) && 
            level.getBlockState(pos.above(2)).canBeReplaced(context)) {
            
            return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(HINGE, this.getHinge(context))
                .setValue(TRIPLE_PART, TripleBlockPart.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(TRIPLE_PART, TripleBlockPart.MIDDLE), 3);
        level.setBlock(pos.above(2), state.setValue(TRIPLE_PART, TripleBlockPart.UPPER), 3);
    }

    // --- NEW: Override canSurvive so it checks for our 3 pieces instead of the vanilla 2 pieces ---
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        TripleBlockPart part = state.getValue(TRIPLE_PART);
        BlockPos blockBelow = pos.below();
        BlockState stateBelow = level.getBlockState(blockBelow);
        
        if (part == TripleBlockPart.LOWER) {
            // Lower piece needs a solid floor
            return stateBelow.isFaceSturdy(level, blockBelow, Direction.UP);
        } else {
            // Middle and Upper pieces just need the door below them
            return stateBelow.is(this);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        TripleBlockPart part = state.getValue(TRIPLE_PART);
        
        if (direction.getAxis() == Direction.Axis.Y) {
            boolean shouldBreak = false;
            if (part == TripleBlockPart.LOWER && direction == Direction.UP) {
                shouldBreak = !neighborState.is(this) || neighborState.getValue(TRIPLE_PART) != TripleBlockPart.MIDDLE;
            } else if (part == TripleBlockPart.MIDDLE) {
                if (direction == Direction.DOWN) shouldBreak = !neighborState.is(this) || neighborState.getValue(TRIPLE_PART) != TripleBlockPart.LOWER;
                if (direction == Direction.UP) shouldBreak = !neighborState.is(this) || neighborState.getValue(TRIPLE_PART) != TripleBlockPart.UPPER;
            } else if (part == TripleBlockPart.UPPER && direction == Direction.DOWN) {
                shouldBreak = !neighborState.is(this) || neighborState.getValue(TRIPLE_PART) != TripleBlockPart.MIDDLE;
            }

            if (shouldBreak) return Blocks.AIR.defaultBlockState();
        }
        
        // --- CHANGED: Do NOT call super.updateShape(), use our own survival check! ---
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        
        return state; 
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!this.type().canOpenByHand()) {
            return InteractionResult.PASS;
        }

        boolean isOpen = !state.getValue(OPEN);
        TripleBlockPart part = state.getValue(TRIPLE_PART);
        
        // Find the absolute bottom of the door structure
        BlockPos bottomPos = switch (part) {
            case MIDDLE -> pos.below();
            case UPPER -> pos.below(2);
            default -> pos; // LOWER
        };

        // Update all three blocks to the new OPEN state
        for (int i = 0; i < 3; i++) {
            BlockPos targetPos = bottomPos.above(i);
            BlockState targetState = level.getBlockState(targetPos);
            
            // Verify it's actually our door block before modifying it
            if (targetState.is(this) && targetState.getValue(OPEN) != isOpen) {
                // 10 is the flag for block update (2) + send to client (8)
                level.setBlock(targetPos, targetState.setValue(OPEN, isOpen), 10);
            }
        }
        
        // Play sound directly to the level using the BlockSetType sounds
        level.playSound(
            player, 
            pos, 
            isOpen ? this.type().doorOpen() : this.type().doorClose(), 
            net.minecraft.sounds.SoundSource.BLOCKS, 
            1.0F, 
            level.getRandom().nextFloat() * 0.1F + 0.9F
        );
        
        // Fire the game event for Sculk Sensors/Wardens
        level.gameEvent(player, isOpen ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE, pos);

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

}