package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Decorative multiblock shell with one server-authoritative inventory on its root cell. */
public final class CrateBlock extends DecorativeMultiblockBlock implements EntityBlock {
    private final int slotCount;
    private final String containerTitleKey;

    public CrateBlock(
            Properties properties,
            int slotCount,
            String containerTitleKey,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            CellShapeFactory shapeFactory) {
        super(properties, minX, maxX, minY, maxY, minZ, maxZ, shapeFactory);
        if (slotCount != 9 && slotCount != 27 && slotCount != 54) {
            throw new IllegalArgumentException("Crates must use a vanilla 1-, 3-, or 6-row inventory");
        }
        this.slotCount = slotCount;
        this.containerTitleKey = Objects.requireNonNull(containerTitleKey, "containerTitleKey");
    }

    public int slotCount() {
        return slotCount;
    }

    public String containerTitleKey() {
        return containerTitleKey;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return hasValidPart(state) && isRoot(state) ? new CrateBlockEntity(pos, state) : null;
    }

    /**
     * Yields the click to the held crate so a crate can be stacked on a crate.
     *
     * <p>Minecraft runs the block interaction before the item's, and {@link #useWithoutItem} consumes
     * it by opening this crate. Without this the placement item is never reached at all, and a crate
     * could only ever be stacked by sneaking. {@code SKIP_DEFAULT_BLOCK_INTERACTION} is the one result
     * that neither consumes the action nor falls through to {@code useWithoutItem}, so the held crate's
     * own {@code useOn} runs and stays the single authority on whether the placement is legal.
     *
     * <p>Scoped to a crate on an upward face: every other item, face, and the empty hand still open
     * this crate exactly as before.
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (hit.getDirection() == Direction.UP
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof CrateBlock) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!hasValidPart(state)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos anchor = anchorPosition(pos, state);
        BlockState rootState = level.getBlockState(anchor);
        if (!rootState.is(this) || !isRoot(rootState)) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(anchor) instanceof CrateBlockEntity crate) {
            player.openMenu(crate);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void beforeDismantle(ServerLevel level, BlockPos anchor, Direction facing) {
        if (level.getBlockEntity(anchor) instanceof CrateBlockEntity crate) {
            Containers.dropContents(level, anchor, crate);
            level.updateNeighbourForOutputSignal(anchor, this);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return hasValidPart(state) && isRoot(state);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}
