package com.seggellion.britannia_mod.block;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;

public class BrickFoundationBlock extends Block {
    private static final Logger LOGGER = LogUtils.getLogger();

    public BrickFoundationBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.0F, 6.0F)
                .sound(SoundType.STONE));
    }

@Override
public void neighborChanged(BlockState state,
                            Level level,
                            BlockPos pos,
                            Block neighbourBlock,
                            BlockPos neighbourPos,
                            boolean moved) {

    if (neighbourPos.distManhattan(pos) == 1 &&
        neighbourPos.getY() == pos.getY()) {

        LOGGER.info("Neighbour update at {} ({}): sending BlockUpdated", pos, neighbourBlock);

        if (!level.isClientSide()) {                 // ① server‑side only!
            level.sendBlockUpdated(pos, state, state,
                    Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }

    super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, moved);
}

}
