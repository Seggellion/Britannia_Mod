package com.seggellion.britannia_mod.banner.placement;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureCell;
import com.seggellion.britannia_mod.banner.structure.BannerStructureLifecycle;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.registry.BannerBlockRegistry;
import com.seggellion.britannia_mod.network.payload.banner.S2CBannerPlacementOrientationPayload;
import java.util.Optional;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/** Live server adapter for the complete multi-cell planner and transactional executor. */
public final class BannerPlacementService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private BannerPlacementService() {
    }

    public static InteractionResult place(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)
                || context.getPlayer() == null
                || !(context.getItemInHand().getItem() instanceof BannerItem item)) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            return cycle(player, stack, item);
        }
        BannerBlock block = BannerBlockRegistry.BANNER.get();
        BannerPartBlock partBlock = BannerBlockRegistry.BANNER_PART.get();
        BlockPlaceContext placeContext = new BlockPlaceContext(context);

        BannerPlacementWorld world = new BannerPlacementWorld() {
            @Override
            public BlockState blockState(BlockPos pos) {
                return level.getBlockState(pos);
            }

            @Override
            public boolean targetReplaceable(BlockPos pos) {
                return level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced(placeContext);
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
            public boolean unrelatedBannerCell(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.getBlock() instanceof BannerBlock || state.getBlock() instanceof BannerPartBlock;
            }

            @Override
            public boolean validWallSupport(BlockPos supportPos, Direction outwardFacing) {
                return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, outwardFacing);
            }

            @Override
            public boolean placementAllowed(BlockPos targetPos, Direction outwardFacing, ItemStack heldStack) {
                return level.mayInteract(player, targetPos)
                        && player.mayUseItemAt(targetPos, outwardFacing, heldStack);
            }

            @Override
            public boolean canCreateBannerBlockEntity(BlockState bannerState) {
                return BannerBlockRegistry.BANNER_BLOCK_ENTITY.get().isValid(bannerState);
            }

            @Override
            public boolean canEncodePart(BlockState partState) {
                return partState.getBlock() instanceof BannerPartBlock
                        && partState.hasProperty(BannerPartBlock.FACING)
                        && partState.hasProperty(BannerPartBlock.HORIZONTAL_OFFSET)
                        && partState.hasProperty(BannerPartBlock.VERTICAL_OFFSET);
            }

            @Override
            public boolean canAcceptState(BannerInstanceState state) {
                return state != null;
            }
        };

        BannerOrientation selectedOrientation = selectedOrientation(player, item, stack);
        BannerPlacementPlanningResult planning = BannerPlacementPlanner.plan(
                item, stack, BannerDataRegistries.current(), BannerDataRegistries.isAvailable(),
                selectedOrientation,
                context.getClickedPos(), context.getClickedFace(), block, partBlock, world);
        if (!planning.successful()) {
            feedback(player, planning.failure(), selectedOrientation);
            return InteractionResult.FAIL;
        }
        BannerPlacementPlan plan = planning.plan().orElseThrow();
        BannerPlacementMutation mutation = mutation(level, player);
        BannerPlacementFailure result;
        try {
            result = BannerStructureLifecycle.duringPlacement(level, plan.anchorPos(), () ->
                    BannerPlacementExecutor.execute(plan, mutation, stack, player.hasInfiniteMaterials()));
        } catch (RuntimeException exception) {
            result = mutation.rollback(plan)
                    ? BannerPlacementFailure.STATE_TRANSFER_FAILURE
                    : BannerPlacementFailure.UNEXPECTED_ROLLBACK_FAILURE;
        }
        if (result != BannerPlacementFailure.NONE) {
            feedback(player, result);
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    private static BannerOrientation selectedOrientation(Player player, BannerItem item, ItemStack stack) {
        if (!BannerDataRegistries.isAvailable()) {
            return BannerOrientation.WALL_PARALLEL;
        }
        return item.stateAccess().read(stack)
                .flatMap(state -> BannerDataRegistries.current().banners().find(state.bannerDefinitionId()))
                .map(definition -> BannerOrientationPreferenceService.currentNormalized(
                        player.getUUID(), definition.supportedOrientations()))
                .orElse(BannerOrientation.WALL_PARALLEL);
    }

    private static InteractionResult cycle(Player player, ItemStack stack, BannerItem item) {
        if (!BannerDataRegistries.isAvailable()) {
            feedback(player, BannerPlacementFailure.REGISTRY_UNAVAILABLE);
            return InteractionResult.FAIL;
        }
        BannerInstanceState state = item.stateAccess().read(stack).orElse(null);
        if (state == null) {
            feedback(player, BannerPlacementFailure.UNCONFIGURED_BANNER);
            return InteractionResult.FAIL;
        }
        var definition = BannerDataRegistries.current().banners().find(state.bannerDefinitionId()).orElse(null);
        if (definition == null) {
            feedback(player, BannerPlacementFailure.DEFINITION_MISSING);
            return InteractionResult.FAIL;
        }
        BannerOrientationPreferenceService.CycleResult result =
                BannerOrientationPreferenceService.cycle(player.getUUID(), definition.supportedOrientations());
        // Display-only mirror: skip connections that negotiated no britannia channels
        // (gametest mock players) instead of letting NeoForge throw, matching BannerRenderDataSync.
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.connection.hasChannel(S2CBannerPlacementOrientationPayload.TYPE)) {
            serverPlayer.connection.send(new ClientboundCustomPayloadPacket(
                    new S2CBannerPlacementOrientationPayload(result.orientation())));
        }
        String orientationKey = "message.britannia_mod.banner.orientation."
                + (result.orientation() == BannerOrientation.WALL_PARALLEL ? "parallel" : "perpendicular");
        String key = result.onlySupportedMode()
                ? "message.britannia_mod.banner.orientation.only"
                : "message.britannia_mod.banner.orientation.changed";
        player.displayClientMessage(Component.translatable(key, Component.translatable(orientationKey)), true);
        return InteractionResult.SUCCESS;
    }

    private static BannerPlacementMutation mutation(ServerLevel level, Player player) {
        return new BannerPlacementMutation() {
            @Override
            public boolean placeCell(BannerPlacementPlan plan, BannerStructureCell cell) {
                int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
                return level.setBlock(cell.worldPosition(), cell.placedState(), flags)
                        || level.getBlockState(cell.worldPosition()).equals(cell.placedState());
            }

            @Override
            public Optional<StateTarget> bannerBlockEntity(BannerPlacementPlan plan) {
                BlockEntity blockEntity = level.getBlockEntity(plan.anchorPos());
                if (!(blockEntity instanceof BannerBlockEntity banner)) return Optional.empty();
                return Optional.of(new StateTarget() {
                    @Override
                    public boolean assign(BannerInstanceState state, BannerPlacedStructure structure) {
                        return banner.setPlacedState(state, structure);
                    }

                    @Override
                    public Optional<BannerInstanceState> currentState() {
                        return banner.bannerState();
                    }

                    @Override
                    public Optional<BannerPlacedStructure> currentStructure() {
                        return banner.placedStructure();
                    }

                    @Override
                    public boolean synchronize() {
                        banner.synchronize();
                        return true;
                    }
                });
            }

            @Override
            public boolean verifyCell(BannerPlacementPlan plan, BannerStructureCell cell) {
                if (!level.getBlockState(cell.worldPosition()).equals(cell.placedState())) {
                    return false;
                }
                if (cell.offset().isAnchor()) {
                    BlockEntity entity = level.getBlockEntity(cell.worldPosition());
                    return entity instanceof BannerBlockEntity banner
                            && banner.bannerState().equals(Optional.of(plan.bannerState()))
                            && banner.placedStructure().equals(Optional.of(plan.placedStructure()));
                }
                return level.getBlockEntity(cell.worldPosition()) == null;
            }

            @Override
            public boolean rollback(BannerPlacementPlan plan) {
                int flags = Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS;
                boolean restored = true;
                for (int index = plan.cells().size() - 1; index >= 0; index--) {
                    BannerStructureCell cell = plan.cells().get(index);
                    boolean set = level.setBlock(cell.worldPosition(), cell.originalState(), flags);
                    restored &= set || level.getBlockState(cell.worldPosition()).equals(cell.originalState());
                }
                for (BannerStructureCell cell : plan.cells()) {
                    restored &= level.getBlockState(cell.worldPosition()).equals(cell.originalState());
                }
                restored &= !(level.getBlockEntity(plan.anchorPos()) instanceof BannerBlockEntity);
                if (!restored) {
                    LOGGER.error("Banner rollback failed for positions {}",
                            plan.cells().stream().map(BannerStructureCell::worldPosition).toList());
                }
                return restored;
            }

            @Override
            public void afterSuccess(BannerPlacementPlan plan) {
                for (BannerStructureCell cell : plan.cells()) {
                    level.updateNeighborsAt(cell.worldPosition(), cell.placedState().getBlock());
                }
                var sound = plan.anchorBlockState().getSoundType(level, plan.anchorPos(), player);
                level.playSound(player, plan.anchorPos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                level.gameEvent(GameEvent.BLOCK_PLACE, plan.anchorPos(),
                        GameEvent.Context.of(player, plan.anchorBlockState()));
            }
        };
    }

    private static void feedback(Player player, BannerPlacementFailure failure) {
        String suffix = switch (failure) {
            case UNCONFIGURED_BANNER -> "unconfigured";
            case REGISTRY_UNAVAILABLE, DEFINITION_MISSING, DEFINITION_DISABLED,
                    MATERIAL_MISSING, MATERIAL_DISABLED, PALETTE_MISSING, COLOUR_MISSING,
                    PIGMENT_MISSING, PIGMENT_DISABLED,
                    MOUNT_MISSING, MOUNT_DISABLED, UNSUPPORTED_MOUNT -> "data_unavailable";
            case UNSUPPORTED_WIDTH, UNSUPPORTED_HEIGHT, MALFORMED_FOOTPRINT,
                    UNSUPPORTED_PLACEMENT_PROFILE, PART_STATE_ENCODING_FAILURE -> "invalid_footprint";
            case UNSUPPORTED_ORIENTATION -> "unsupported_orientation";
            case INVALID_CLICKED_FACE -> "horizontal_face_required";
            case TARGET_OCCUPIED, UNRELATED_BANNER_CELL -> "target_occupied";
            case REQUIRED_CHUNK_UNLOADED -> "chunk_unloaded";
            case INVALID_WALL_SUPPORT -> "invalid_support";
            case PROTECTED_PLACEMENT -> "protected";
            default -> "failed_safely";
        };
        player.displayClientMessage(Component.translatable("message.britannia_mod.banner.placement." + suffix), true);
    }

    private static void feedback(
            Player player, BannerPlacementFailure failure, BannerOrientation orientation) {
        if (failure == BannerPlacementFailure.INVALID_WALL_SUPPORT) {
            String suffix = orientation == BannerOrientation.WALL_PARALLEL
                    ? "parallel_support" : "perpendicular_support";
            player.displayClientMessage(
                    Component.translatable("message.britannia_mod.banner.placement." + suffix), true);
            return;
        }
        feedback(player, failure);
    }
}
