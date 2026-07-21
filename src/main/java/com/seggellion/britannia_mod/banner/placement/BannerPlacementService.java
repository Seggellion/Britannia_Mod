package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.registry.BannerBlockRegistry;
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

/** Live server adapter for the testable planner and one-cell transaction executor. */
public final class BannerPlacementService {
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
        BannerBlock block = BannerBlockRegistry.BANNER.get();
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
            public boolean canAcceptState(BannerInstanceState state) {
                return state != null;
            }
        };

        BannerPlacementPlanningResult planning = BannerPlacementPlanner.plan(
                item, stack, BannerDataRegistries.current(), BannerDataRegistries.isAvailable(),
                context.getClickedPos(), context.getClickedFace(), block, world);
        if (!planning.successful()) {
            feedback(player, planning.failure());
            return InteractionResult.FAIL;
        }
        BannerPlacementPlan plan = planning.plan().orElseThrow();
        BannerPlacementMutation mutation = mutation(level, player);
        BannerPlacementFailure result;
        try {
            result = BannerPlacementExecutor.execute(plan, mutation, stack, player.hasInfiniteMaterials());
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

    private static BannerPlacementMutation mutation(ServerLevel level, Player player) {
        return new BannerPlacementMutation() {
            @Override
            public boolean placeBanner(BannerPlacementPlan plan) {
                return level.setBlock(plan.targetPos(), plan.bannerBlockState(), Block.UPDATE_ALL_IMMEDIATE);
            }

            @Override
            public Optional<StateTarget> bannerBlockEntity(BannerPlacementPlan plan) {
                BlockEntity blockEntity = level.getBlockEntity(plan.targetPos());
                if (!(blockEntity instanceof BannerBlockEntity banner)) return Optional.empty();
                return Optional.of(new StateTarget() {
                    @Override
                    public boolean assign(BannerInstanceState state) {
                        return banner.setBannerState(state);
                    }

                    @Override
                    public Optional<BannerInstanceState> currentState() {
                        return banner.bannerState();
                    }
                });
            }

            @Override
            public boolean rollback(BannerPlacementPlan plan) {
                int flags = Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS;
                boolean restored = level.setBlock(plan.targetPos(), plan.originalTargetState(), flags);
                return restored && level.getBlockState(plan.targetPos()).equals(plan.originalTargetState())
                        && !(level.getBlockEntity(plan.targetPos()) instanceof BannerBlockEntity);
            }

            @Override
            public void afterSuccess(BannerPlacementPlan plan) {
                var sound = plan.bannerBlockState().getSoundType(level, plan.targetPos(), player);
                level.playSound(player, plan.targetPos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                level.gameEvent(GameEvent.BLOCK_PLACE, plan.targetPos(),
                        GameEvent.Context.of(player, plan.bannerBlockState()));
            }
        };
    }

    private static void feedback(Player player, BannerPlacementFailure failure) {
        String suffix = switch (failure) {
            case UNCONFIGURED_BANNER -> "unconfigured";
            case REGISTRY_UNAVAILABLE, DEFINITION_MISSING, DEFINITION_DISABLED,
                    MATERIAL_MISSING, MATERIAL_DISABLED, PALETTE_MISSING, COLOUR_MISSING,
                    MOUNT_MISSING, MOUNT_DISABLED, UNSUPPORTED_MOUNT -> "data_unavailable";
            case MULTI_BLOCK_PLACEMENT_DEFERRED -> "multi_block_deferred";
            case UNSUPPORTED_ORIENTATION -> "unsupported_orientation";
            case INVALID_CLICKED_FACE -> "horizontal_face_required";
            case TARGET_OCCUPIED -> "target_occupied";
            case INVALID_WALL_SUPPORT -> "invalid_support";
            case PROTECTED_PLACEMENT -> "protected";
            default -> "failed_safely";
        };
        player.displayClientMessage(Component.translatable("message.britannia_mod.banner.placement." + suffix), true);
    }
}
