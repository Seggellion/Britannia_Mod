package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Three-cell ladder whose full 48-voxel model is rendered from the middle cell. */
public final class LadderMultiblockBlock extends DecorativeMultiblockBlock {
    private static final int VISUAL_PART = 1;

    public LadderMultiblockBlock(
            Properties properties,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            CellShapeFactory shapeFactory) {
        super(properties, minX, maxX, minY, maxY, minZ, maxZ, shapeFactory);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return hasValidPart(state) && state.getValue(PART) == VISUAL_PART
                ? RenderShape.MODEL
                : RenderShape.INVISIBLE;
    }
}
