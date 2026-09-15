package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.TripleMetalDoorBlock;
import com.seggellion.britannia_mod.block.TripleBlockPart;
import com.seggellion.britannia_mod.placement.CreativeDecorationPolicy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;

/** Validate and place the complete three-cell door before BlockItem can consume its item. */
public final class TripleMetalDoorItem extends BlockItem {
    public TripleMetalDoorItem(Block block, Properties properties) { super(block,properties); }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel();
        var root = context.getClickedPos();
        var originals = new ArrayList<BlockState>();
        var states = new ArrayList<BlockState>();
        var parts = new TripleBlockPart[]{TripleBlockPart.LOWER,TripleBlockPart.MIDDLE,TripleBlockPart.UPPER};
        for(int i=0;i<3;i++) {
            var planned = state.setValue(TripleMetalDoorBlock.TRIPLE_PART,parts[i]);
            if (!CreativeDecorationPolicy.canOccupy(context,root.above(i),planned)) return false;
            originals.add(level.getBlockState(root.above(i))); states.add(planned);
        }
        int placed = 0;
        try {
            for(int i=0;i<3;i++) {
                if (!level.setBlock(root.above(i),states.get(i),Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                        && level.getBlockState(root.above(i)) != states.get(i)) throw new IllegalStateException("door cell refused");
                placed++;
            }
            return true;
        } catch(RuntimeException failure) {
            for(int i=placed-1;i>=0;i--) if(level.getBlockState(root.above(i)) == states.get(i))
                level.setBlock(root.above(i),originals.get(i),Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            return false;
        }
    }
}
