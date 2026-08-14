package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (entity instanceof LivingEntity living && living.horizontalCollision) {
            Vec3 movement = living.getDeltaMovement();
            living.setDeltaMovement(movement.x, Math.max(movement.y, 0.2D), movement.z);
            living.resetFallDistance();
        }
    }
}
