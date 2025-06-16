package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.InvisibleInAdventureMode;
import com.seggellion.britannia_mod.block.entity.FishSpawnBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * A block that can spawn a FishMerchant when city food supply >= 200 stones.
 * Right-click with a custom-named Name Tag to set the city name.
 */

public class FishSpawnBlock extends Block implements EntityBlock, InvisibleInAdventureMode  {
    private static final Logger LOGGER = LogManager.getLogger();

    public FishSpawnBlock() {
        // Provide some default properties (e.g., let's use a generic "strength(1.5F)" and "noOcclusion()").
        super(BlockBehaviour.Properties.of()
              .strength(1.5F));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;

        if (player != null && player.isCreative()) {
            return RenderShape.MODEL; // Visible in creative mode
        }
        return RenderShape.INVISIBLE; // Hidden for all other players
    }

        @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block(); // Critical for preventing "see through world"
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // No collision shape, so players can walk through it
    }


    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FishSpawnBlockEntity(pos, state);
    }

    /**
     * If your MC version supports getTicker exactly, keep it. Otherwise remove @Override or rename.
     */
    //@Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState blockState, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, blockPos, bs, blockEntity) -> {
            if (blockEntity instanceof FishSpawnBlockEntity FishSpawnBE) {
                FishSpawnBE.tick();
            }
        };
    }

}
