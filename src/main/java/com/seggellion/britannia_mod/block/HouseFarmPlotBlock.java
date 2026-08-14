package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.HouseFarmPlotBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FlowerInteractionService;
import com.seggellion.britannia_mod.farming.FlowerPersistentState;
import com.seggellion.britannia_mod.farming.FlowerRegistry;
import com.seggellion.britannia_mod.item.GrapeSeedsItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A raised planting bed: a brick kerb around the outside with soil recessed inside it.
 *
 * <p>Plots placed next to each other merge. Each side carries a boolean saying whether a plot
 * adjoins it, and the blockstate leaves the kerb off on connected sides, so the soil of both blocks
 * runs together into one bed instead of every block being ringed by its own wall.
 */
public class HouseFarmPlotBlock extends FarmingBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final Map<Direction, BooleanProperty> SIDES = Map.of(
        Direction.NORTH, NORTH, Direction.SOUTH, SOUTH,
        Direction.EAST, EAST, Direction.WEST, WEST);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public HouseFarmPlotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(NORTH, false).setValue(SOUTH, false)
            .setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HouseFarmPlotBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction.getAxis().isVertical()) {
            return state;
        }
        return state.setValue(SIDES.get(direction), neighborState.getBlock() instanceof HouseFarmPlotBlock);
    }

    private BlockState connect(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            boolean joins = level.getBlockState(pos.relative(side.getKey()))
                                 .getBlock() instanceof HouseFarmPlotBlock;
            state = state.setValue(side.getValue(), joins);
        }
        return state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(NORTH, state.getValue(SIDES.get(rotation.rotate(Direction.NORTH))))
                    .setValue(EAST, state.getValue(SIDES.get(rotation.rotate(Direction.EAST))))
                    .setValue(SOUTH, state.getValue(SIDES.get(rotation.rotate(Direction.SOUTH))))
                    .setValue(WEST, state.getValue(SIDES.get(rotation.rotate(Direction.WEST))));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> state;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (!(level.getBlockEntity(pos) instanceof HouseFarmPlotBlockEntity plot)) {
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            plot.initializeDefaultPoppy(serverLevel);
            if (!mayManagePlot(serverLevel, pos, player)) {
                player.displayClientMessage(Component.literal("You may only tend a house farm plot you own."), true);
                return ItemInteractionResult.SUCCESS;
            }
        }

        // Let the existing FarmingHoeItem own the exclusive clear transaction.
        if (stack.is(ItemRegistry.FARMING_HOE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (plot.isAssigned() && isPlantingMaterial(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Clear this plot with a farming hoe before changing its plant."), true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (plot.isInitialized()) {
            if (FlowerInteractionService.isUprootingTool(stack)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("Only a farming hoe can clear a house farm plot."), true);
                }
                return ItemInteractionResult.SUCCESS;
            }
            ItemInteractionResult flowerResult = FlowerInteractionService.interact(
                    level, pos, state, player, hand, stack, plot
            );
            return flowerResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    ? super.useItemOn(stack, state, level, pos, player, hand, hitResult)
                    : flowerResult;
        }

        boolean wasCleared = plot.isCleared();
        ItemInteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (!level.isClientSide && wasCleared && plot.isCleared()) {
            FlowerPersistentState flower = plot.flowerState().orElse(null);
            if (flower != null) {
                plot.assignFlower(flower.speciesId());
            } else if (plot.hasCrop()) {
                CropRegistry.byId(plot.getPlantedCropId()).ifPresent(plot::assignCrop);
            }
        }
        return result;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && !mayManagePlot(serverLevel, pos, player)) {
            player.displayClientMessage(Component.literal("You may only harvest a house farm plot you own."), true);
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof HouseFarmPlotBlockEntity plot && plot.isInitialized()) {
            return InteractionResult.PASS;
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof HouseFarmPlotBlockEntity plot)) {
            return;
        }
        plot.initializeDefaultPoppy(level);
        if (plot.isInitialized()) {
            FlowerBlock.tickFlower(state, level, pos, random, plot);
            return;
        }
        super.randomTick(state, level, pos, random);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof HouseFarmPlotBlockEntity plot) {
            CropDefinition crop = CropRegistry.byId(plot.getPlantedCropId()).orElse(null);
            plot.cleanupTreeRoot(serverLevel, crop);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static boolean mayManagePlot(ServerLevel level, BlockPos pos, Player player) {
        if (level == null || pos == null || player == null || !level.mayInteract(player, pos)) {
            return false;
        }
        if (player.isCreative() || player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
            return true;
        }
        return StructureRegionManager.getStructuresInChunk(
                        SectionPos.blockToSectionCoord(pos.getX()),
                        SectionPos.blockToSectionCoord(pos.getZ())
                ).stream()
                .filter(record -> record.getFullBox().contains(pos.getCenter()))
                .map(StructureRecord::getOwnerUuid)
                .findFirst()
                .map(player.getUUID()::equals)
                .orElse(true);
    }

    private static boolean isPlantingMaterial(ItemStack stack) {
        return stack.is(ModTags.Items.FLOWER_SEEDS)
                || FlowerRegistry.initial().bySeedItemId(BuiltInRegistries.ITEM.getKey(stack.getItem())).isPresent()
                || CropRegistry.bySeed(stack.getItem()).isPresent()
                || stack.getItem() instanceof GrapeSeedsItem;
    }
}
