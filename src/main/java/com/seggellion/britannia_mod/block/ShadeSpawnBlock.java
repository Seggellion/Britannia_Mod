// ShadeSpawnBlock.java
package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.ShadeSpawnBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class ShadeSpawnBlock extends Block implements EntityBlock {
    private static final Logger LOGGER = LogManager.getLogger();


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // Default to invisible
    }

        @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
   //     LOGGER.info("getCollisionShape called - returning empty shape to allow pass-through at {}", pos);
        return Shapes.empty(); // No collision shape, so players can walk through it
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Player player = entityContext.getEntity() instanceof Player ? (Player) entityContext.getEntity() : null;
            if (player != null && !player.isCreative()) {
                return Shapes.empty(); // Return an empty shape if not in Creative mode
            } 
       } 
        return super.getShape(state, world, pos, context); // Otherwise, show the default shape
    }

    public ShadeSpawnBlock() {
        super(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion()); // noOcclusion prevents it from blocking sight
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {

        return new ShadeSpawnBlockEntity(pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ShadeSpawnBlockEntity entity) {
        // Check if the player placing this block is in Creative mode
        if (!level.isClientSide) {
            Player player = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 5.0, false); // Check nearby players
            if (player != null && !player.isCreative()) {
                level.removeBlock(pos, false); // Remove block if the player is not in Creative mode
            }
        }
    }


@Nullable
@Override
@SuppressWarnings("unchecked")
public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide ? null : (lvl, pos, stt, t) -> {
        if (t instanceof ShadeSpawnBlockEntity blockEntity) {
            blockEntity.tick(); // Call tick() without arguments                        

        }
    };
}
}
