package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.WoodSpawnBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A block that can spawn a WoodMerchant when city food supply >= 200 stones.
 * Right-click with a custom-named Name Tag to set the city name.
 */
public class WoodSpawnBlock extends Block implements EntityBlock {
    private static final Logger LOGGER = LogManager.getLogger();

    public WoodSpawnBlock() {
        // Provide some default properties (e.g., let's use a generic "strength(1.5F)" and "noOcclusion()").
        super(BlockBehaviour.Properties.of()
              .strength(1.5F));

        LOGGER.info("WoodSpawnBlock");

    }

/*
@Override
public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    // A full 1x1x1 cube shape ensures the game recognizes there's a block to click.
    return box(0, 0, 0, 16, 16, 16);
}
*/
    /**
     * If your version’s Block class does not have a matching getRenderShape signature, 
     * remove this method or rename it to your version’s method signature.
     */
    /*
    public RenderShape getRenderShape(BlockState state) {
        // If you want the block invisible.
        return RenderShape.INVISIBLE;
    }
*/



    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WoodSpawnBlockEntity(pos, state);
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
            if (blockEntity instanceof WoodSpawnBlockEntity woodSpawnBE) {
                woodSpawnBE.tick();
            }
        };
    }
}
