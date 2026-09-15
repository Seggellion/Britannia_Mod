package com.seggellion.britannia_mod.structure.interaction;

import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.item.InteriorDecoratorToolItem;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** One MAIN-phase transaction, resolved from either case cell and the actual tool-bearing hand. */
public final class DisplayCaseDecoratorService {
    private DisplayCaseDecoratorService() {}

    public static boolean matches(Level level, Player player, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DisplayCaseBlock
                && (decorator(player.getMainHandItem()) || decorator(player.getOffhandItem()));
    }

    public static InteractionResult interact(Level level, Player player, InteractionHand phase, BlockHitResult hit) {
        if (!matches(level, player, hit.getBlockPos())) return InteractionResult.PASS;
        // Consuming the other callback also prevents item placement and generic one-cell rotation.
        if (phase != InteractionHand.MAIN_HAND || level.isClientSide) return InteractionResult.SUCCESS;
        boolean rotate = decorator(player.getMainHandItem());
        ItemStack tool = rotate ? player.getMainHandItem() : player.getOffhandItem();
        BlockState clicked = level.getBlockState(hit.getBlockPos());
        DisplayCaseBlock block = (DisplayCaseBlock) clicked.getBlock();
        if (!block.hasValidPart(clicked) || player.isSpectator()) return InteractionResult.FAIL;
        BlockPos root = block.anchorPosition(hit.getBlockPos(), clicked);
        if (!valid(level, player, block, root, clicked.getValue(DisplayCaseBlock.FACING), tool, hit.getDirection()))
            return InteractionResult.FAIL;
        DisplayCaseBlockEntity display = (DisplayCaseBlockEntity) level.getBlockEntity(root);
        boolean applied = rotate
                ? rotate(level, player, block, root, tool, hit.getDirection(),
                        (pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE))
                : eject(level, player, block, display, root, tool, hit);
        return applied ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    private static boolean decorator(ItemStack stack) {
        return stack.getItem() instanceof InteriorDecoratorToolItem;
    }

    private static boolean valid(Level level, Player player, DisplayCaseBlock block, BlockPos root,
                                 Direction facing, ItemStack tool, Direction face) {
        if (player.isSpectator() || !decorator(tool)) return false;
        for (var cell : block.cells()) {
            BlockPos pos = block.worldPosition(root, facing, cell);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, face, tool)) return false;
            BlockState state = level.getBlockState(pos);
            if (!state.is(block) || state.getValue(DisplayCaseBlock.PART) != cell.part()
                    || state.getValue(DisplayCaseBlock.FACING) != facing) return false;
        }
        return level.getBlockEntity(root) instanceof DisplayCaseBlockEntity display && !display.isRemoved();
    }

    private static boolean eject(Level level, Player player, DisplayCaseBlock block, DisplayCaseBlockEntity display,
                                 BlockPos root, ItemStack tool, BlockHitResult hit) {
        if (!display.hasDisplayedItem()) return false;
        Direction facing = level.getBlockState(root).getValue(DisplayCaseBlock.FACING);
        ItemEntity entity = new ItemEntity(level, 0, 0, 0, display.displayedItem().copy());
        // Prefer the hit face, then the other adjacent sides; never put merchandise inside the case.
        Direction[] faces = {hit.getDirection(), Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        boolean located = false;
        for (Direction face : faces) {
            Vec3 point = switch (face) {
                case UP -> new Vec3(root.getX() + .5, root.getY() + 2.15, root.getZ() + .5);
                case DOWN -> new Vec3(root.getX() + .5, root.getY() - .4, root.getZ() + .5);
                default -> new Vec3(root.getX() + .5 + face.getStepX() * .8,
                        root.getY() + 1.1, root.getZ() + .5 + face.getStepZ() * .8);
            };
            entity.setPos(point);
            if (level.hasChunkAt(entity.blockPosition()) && level.getWorldBorder().isWithinBounds(entity.getBoundingBox())
                    && level.noCollision(entity, entity.getBoundingBox())) {
                located = true;
                break;
            }
        }
        if (!located) return false;
        entity.setDefaultPickUpDelay();
        entity.setDeltaMovement(Vec3.ZERO);
        return display.eject(stack -> {
            if (level.getBlockEntity(root) != display) return false;
            entity.setItem(stack);
            return level.addFreshEntity(entity) && !entity.isRemoved()
                    && ItemStack.matches(entity.getItem(), display.displayedItem())
                    && level.getBlockEntity(root) == display && player.getOffhandItem() == tool
                    && valid(level, player, block, root, facing, tool, hit.getDirection());
        }, entity::discard);
    }

    /** Setter is the commit seam; restoration always uses the world's ordinary setter. */
    public static boolean rotate(Level level, Player player, DisplayCaseBlock block, BlockPos root,
                                 ItemStack tool, Direction face, BiPredicate<BlockPos, BlockState> setter) {
        if (level.isClientSide || !(level.getBlockEntity(root) instanceof DisplayCaseBlockEntity display)) return false;
        BlockState lower = level.getBlockState(root);
        if (!lower.is(block) || !block.isRoot(lower)) return false;
        Direction facing = lower.getValue(DisplayCaseBlock.FACING);
        if (!valid(level, player, block, root, facing, tool, face)) return false;
        BlockState upper = level.getBlockState(root.above());
        return display.withContentsLocked(() -> {
            boolean committed = block.duringMutation(() -> {
                boolean complete = false;
                try {
                    BlockState nextLower = lower.setValue(DisplayCaseBlock.FACING, facing.getClockWise());
                    BlockState nextUpper = upper.setValue(DisplayCaseBlock.FACING, facing.getClockWise());
                    complete = setter.test(root, nextLower) && setter.test(root.above(), nextUpper)
                            && level.getBlockState(root).equals(nextLower)
                            && level.getBlockState(root.above()).equals(nextUpper)
                            && level.getBlockEntity(root) == display;
                    return complete;
                } catch (RuntimeException failure) {
                    com.mojang.logging.LogUtils.getLogger().warn("Display-case rotation refused at {}", root, failure);
                    return false;
                } finally {
                    if (!complete) {
                        level.setBlock(root, lower, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        level.setBlock(root.above(), upper, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            });
            // Neither our parts nor neighbors may inspect an intermediate facing pair.
            level.updateNeighborsAt(root, block);
            level.updateNeighborsAt(root.above(), block);
            return committed;
        });
    }
}
