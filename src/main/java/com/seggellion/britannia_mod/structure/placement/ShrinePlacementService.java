package com.seggellion.britannia_mod.structure.placement;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.LargeStructureRegistry;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import com.seggellion.britannia_mod.structure.multiblock.StructureCellRole;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;

/** Live server adapter for diagnostic shrine preflight, transaction, and rollback. */
public final class ShrinePlacementService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ShrinePlacementService() {
    }

    public static InteractionResult place(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)
                || context.getPlayer() == null
                || !(context.getItemInHand().getItem() instanceof ShrineItem item)) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        Direction outwardFacing = placeContext.getHorizontalDirection().getOpposite();

        ShrinePlacementWorld world = new ShrinePlacementWorld() {
            @Override
            public BlockState blockState(BlockPos pos) {
                return level.getBlockState(pos);
            }

            @Override
            public boolean targetReplaceable(BlockPos pos) {
                return level.getBlockEntity(pos) == null
                        && level.getBlockState(pos).canBeReplaced(placeContext);
            }

            @Override
            public boolean inWorldBounds(BlockPos pos) {
                return level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos);
            }

            @Override
            public boolean chunkLoaded(BlockPos pos) {
                return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
            }

            @Override
            public boolean unrelatedLargeStructureCell(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.getBlock() == LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get()
                        || state.getBlock() == LargeStructureRegistry.LARGE_STRUCTURE_PART.get();
            }

            @Override
            public boolean placementAllowed(BlockPos pos, Direction facing, ItemStack heldStack) {
                return level.mayInteract(player, pos)
                        && player.mayUseItemAt(pos, Direction.UP, heldStack);
            }

            @Override
            public boolean canCreateAnchorBlockEntity(BlockState anchorState) {
                return LargeStructureRegistry.LARGE_STRUCTURE.get().isValid(anchorState);
            }

            @Override
            public boolean canInitializeAnchor(BlockState anchorState, PlacedStructureState state) {
                BlockEntity candidate = LargeStructureRegistry.LARGE_STRUCTURE.get()
                        .create(BlockPos.ZERO, anchorState);
                return candidate instanceof LargeStructureAnchorBlockEntity anchor
                        && anchor.initialize(state)
                        && anchor.placedState().equals(Optional.of(state));
            }

            @Override
            public boolean canEncodePart(BlockState partState) {
                return partState.getBlock() instanceof LargeStructurePartBlock
                        && partState.hasProperty(LargeStructurePartBlock.FACING)
                        && partState.hasProperty(LargeStructurePartBlock.LOCAL_X)
                        && partState.hasProperty(LargeStructurePartBlock.LOCAL_Y)
                        && partState.hasProperty(LargeStructurePartBlock.LOCAL_Z);
            }
        };

        ShrinePlacementPlanningResult planning = ShrinePlacementPlanner.plan(
                item,
                stack,
                context.getClickedPos(),
                context.getClickedFace(),
                outwardFacing,
                LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get(),
                LargeStructureRegistry.LARGE_STRUCTURE_PART.get(),
                world);
        if (!planning.successful()) {
            feedback(player, planning.failure());
            return InteractionResult.FAIL;
        }

        ShrinePlacementPlan plan = planning.plan().orElseThrow();
        ShrinePlacementFailure result = ShrineLifecycleService.duringPlacement(
                level, plan.anchorPosition(), () -> ShrinePlacementExecutor.execute(
                        plan, mutation(level, player), stack, player.hasInfiniteMaterials()));
        if (result != ShrinePlacementFailure.NONE) {
            feedback(player, result);
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    private static ShrinePlacementMutation mutation(ServerLevel level, Player player) {
        return new ShrinePlacementMutation() {
            @Override
            public boolean placeCell(ShrinePlacementPlan plan, StructureCell cell) {
                int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
                return level.setBlock(cell.worldPosition(), cell.placedState(), flags)
                        || level.getBlockState(cell.worldPosition()).equals(cell.placedState());
            }

            @Override
            public Optional<StateTarget> anchorBlockEntity(ShrinePlacementPlan plan) {
                BlockEntity blockEntity = level.getBlockEntity(plan.anchorPosition());
                if (!(blockEntity instanceof LargeStructureAnchorBlockEntity anchor)) {
                    return Optional.empty();
                }
                return Optional.of(new StateTarget() {
                    @Override
                    public boolean assign(PlacedStructureState state) {
                        return anchor.initialize(state);
                    }

                    @Override
                    public Optional<PlacedStructureState> currentState() {
                        return anchor.placedState();
                    }

                    @Override
                    public boolean synchronize() {
                        anchor.synchronize();
                        return true;
                    }
                });
            }

            @Override
            public boolean verifyCell(ShrinePlacementPlan plan, StructureCell cell) {
                if (!level.getBlockState(cell.worldPosition()).equals(cell.placedState())) {
                    return false;
                }
                if (cell.role() == StructureCellRole.ANCHOR) {
                    return level.getBlockEntity(cell.worldPosition()) instanceof LargeStructureAnchorBlockEntity anchor
                            && anchor.placedState().equals(Optional.of(plan.placedStructure()));
                }
                return level.getBlockEntity(cell.worldPosition()) == null
                        && LargeStructurePartBlock.anchorPosition(
                                cell.worldPosition(), cell.placedState()).equals(plan.anchorPosition());
            }

            @Override
            public boolean rollback(ShrinePlacementPlan plan) {
                int flags = Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS;
                boolean restored = true;
                for (int index = plan.cells().size() - 1; index >= 0; index--) {
                    StructureCell cell = plan.cells().get(index);
                    BlockState current = level.getBlockState(cell.worldPosition());
                    ShrineRollbackOwnership.Action action = ShrineRollbackOwnership.classify(
                            current, cell.originalState(), cell.placedState());
                    if (action == ShrineRollbackOwnership.Action.PRESERVE_UNRELATED) {
                        restored = false;
                    } else if (action == ShrineRollbackOwnership.Action.RESTORE_ORIGINAL) {
                        boolean set = level.setBlock(cell.worldPosition(), cell.originalState(), flags);
                        restored &= set || level.getBlockState(cell.worldPosition())
                                .equals(cell.originalState());
                    }
                }
                for (StructureCell cell : plan.cells()) {
                    restored &= level.getBlockState(cell.worldPosition()).equals(cell.originalState());
                }
                restored &= !(level.getBlockEntity(plan.anchorPosition())
                        instanceof LargeStructureAnchorBlockEntity);
                if (!restored) {
                    LOGGER.error("Shrine rollback failed safely for positions {}",
                            plan.cells().stream().map(StructureCell::worldPosition).toList());
                }
                return restored;
            }

            @Override
            public void afterSuccess(ShrinePlacementPlan plan) {
                for (StructureCell cell : plan.cells()) {
                    level.updateNeighborsAt(cell.worldPosition(), cell.placedState().getBlock());
                }
                var sound = plan.anchorBlockState().getSoundType(level, plan.anchorPosition(), player);
                level.playSound(player, plan.anchorPosition(), sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                level.gameEvent(GameEvent.BLOCK_PLACE, plan.anchorPosition(),
                        GameEvent.Context.of(player, plan.anchorBlockState()));
            }
        };
    }

    private static void feedback(Player player, ShrinePlacementFailure failure) {
        String suffix = switch (failure) {
            case INVALID_CLICKED_FACE, INVALID_FACING -> "floor_required";
            case WORLD_BOUND_FAILURE -> "out_of_bounds";
            case REQUIRED_CHUNK_UNLOADED -> "chunk_unloaded";
            case TARGET_OCCUPIED, UNRELATED_STRUCTURE_CELL -> "target_occupied";
            case PROTECTED_PLACEMENT -> "protected";
            case UNSUPPORTED_FAMILY, FAMILY_MISSING, VARIANT_MISSING, VARIANT_DISABLED,
                    INCOMPATIBLE_VARIANT, PART_STATE_ENCODING_FAILURE -> "invalid_configuration";
            default -> "failed_safely";
        };
        player.displayClientMessage(
                Component.translatable("message.britannia_mod.shrine.placement." + suffix), true);
    }
}
