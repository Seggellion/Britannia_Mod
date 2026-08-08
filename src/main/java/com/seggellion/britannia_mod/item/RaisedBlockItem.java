package com.seggellion.britannia_mod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A {@link BlockItem} that places its block a fixed number of blocks higher than it otherwise would.
 *
 * <p>For lamp posts and tall candelabra. Minecraft rejects any model element outside -16..32, so a
 * 48-voxel post cannot start at y=0 - it has to be authored spanning {@code -16..32}, which puts a
 * third of the model in the block BELOW the one it occupies. Placed normally that third disappears
 * into the ground.
 *
 * <p>The lift has to be applied to the PLACEMENT position, not to the block that was clicked.
 * Nudging the hit position up simply moves the hit into the air block above, which
 * {@link BlockPlaceContext} then treats as replaceable and places into - landing on exactly the
 * same block as before. Overriding {@link BlockPlaceContext#getClickedPos()} is what actually
 * moves the block.
 */
public class RaisedBlockItem extends BlockItem {

    private final int lift;

    public RaisedBlockItem(Block block, int lift, Properties properties) {
        super(block, properties);
        this.lift = lift;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockHitResult hit = new BlockHitResult(
            context.getClickLocation(), context.getClickedFace(),
            context.getClickedPos(), context.isInside());

        return this.place(new LiftedPlaceContext(
            context.getLevel(), context.getPlayer(), context.getHand(),
            context.getItemInHand(), hit, this.lift));
    }

    /** A placement context whose target position is raised by a fixed number of blocks. */
    private static final class LiftedPlaceContext extends BlockPlaceContext {

        private final int lift;

        private LiftedPlaceContext(Level level, Player player, InteractionHand hand,
                                   ItemStack stack, BlockHitResult hit, int lift) {
            super(level, player, hand, stack, hit);
            this.lift = lift;
        }

        @Override
        public BlockPos getClickedPos() {
            return super.getClickedPos().above(this.lift);
        }

        @Override
        public boolean canPlace() {
            // The base implementation short-circuits to true whenever the clicked block was
            // replaceable, which says nothing about the raised position we are actually using.
            return this.getLevel().getBlockState(this.getClickedPos()).canBeReplaced(this);
        }
    }
}
