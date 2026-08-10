package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock.Cell;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Transactional placement item for {@link DecorativeMultiblockBlock}. */
public final class DecorativeMultiblockItem extends BlockItem {
    public DecorativeMultiblockItem(DecorativeMultiblockBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP || context.getPlayer() == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        DecorativeMultiblockBlock block = (DecorativeMultiblockBlock) getBlock();
        Direction facing = placeContext.getHorizontalDirection().getOpposite();
        BlockPos minimumPosition = placeContext.getClickedPos();
        BlockPos anchor = block.anchorForMinimumPosition(minimumPosition, facing);

        List<PlacementCell> placement = new ArrayList<>(block.cells().size());
        for (Cell cell : block.cells()) {
            BlockPos position = block.worldPosition(anchor, facing, cell);
            BlockState placedState = block.stateFor(facing, cell);
            if (!level.isInWorldBounds(position)
                    || !level.getWorldBorder().isWithinBounds(position)
                    || !level.hasChunkAt(position)
                    || level.getBlockEntity(position) != null
                    || !level.getBlockState(position).canBeReplaced(placeContext)
                    || !level.mayInteract(player, position)
                    || !player.mayUseItemAt(position, Direction.UP, stack)) {
                return InteractionResult.FAIL;
            }
            if (cell.y() == block.minimumY()) {
                BlockPos supportPosition = position.below();
                if (!level.getBlockState(supportPosition)
                        .isFaceSturdy(level, supportPosition, Direction.UP)) {
                    return InteractionResult.FAIL;
                }
            }
            placement.add(new PlacementCell(position, level.getBlockState(position), placedState));
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.FAIL;
        }

        boolean placed = block.duringMutation(() -> placeOrRollback(server, placement));
        if (!placed) {
            return InteractionResult.FAIL;
        }
        for (PlacementCell cell : placement) {
            server.updateNeighborsAt(cell.position(), block);
        }
        BlockState rootState = server.getBlockState(anchor);
        var sound = rootState.getSoundType(server, anchor, player);
        server.playSound(player, anchor, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        server.gameEvent(GameEvent.BLOCK_PLACE, anchor, GameEvent.Context.of(player, rootState));
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean placeOrRollback(ServerLevel level, List<PlacementCell> placement) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        int placedCount = 0;
        for (PlacementCell cell : placement) {
            boolean set = level.setBlock(cell.position(), cell.placedState(), flags);
            if (!set && !level.getBlockState(cell.position()).equals(cell.placedState())) {
                rollback(level, placement, placedCount);
                return false;
            }
            placedCount++;
        }
        for (PlacementCell cell : placement) {
            if (!level.getBlockState(cell.position()).equals(cell.placedState())) {
                rollback(level, placement, placedCount);
                return false;
            }
        }
        return true;
    }

    private static void rollback(ServerLevel level, List<PlacementCell> placement, int placedCount) {
        int flags = Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS;
        for (int index = placedCount - 1; index >= 0; index--) {
            PlacementCell cell = placement.get(index);
            if (level.getBlockState(cell.position()).equals(cell.placedState())) {
                level.setBlock(cell.position(), cell.originalState(), flags);
            }
        }
    }

    private record PlacementCell(BlockPos position, BlockState originalState, BlockState placedState) {
    }
}
