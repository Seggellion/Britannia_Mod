package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.RenderShape;
import com.seggellion.britannia_mod.item.PurityOreItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ItemInteractionResult;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class SmallForgeBlock extends Block implements EntityBlock {


    private static final Logger LOGGER = LogUtils.getLogger();


    public SmallForgeBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .lightLevel(state -> 14) 
            .requiresCorrectToolForDrops()
            .noOcclusion()
        );
    }


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmallForgeBlockEntity(pos, state);
    }

@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
    if (!(stack.getItem() instanceof PurityOreItem purityOre)) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    BlockEntity blockEntity = level.getBlockEntity(pos);
    if (!(blockEntity instanceof SmallForgeBlockEntity forgeEntity)) {
        return ItemInteractionResult.FAIL;
    }

    int customModelData = purityOre.getCustomModelData(stack);
    int purity = customModelData % 100; // Extract purity level
    int oreBase = customModelData - purity; // Extract ore type

    // Determine ore type
    if (oreBase != 100) {
        return ItemInteractionResult.FAIL; 
    }

    if (!level.isClientSide) {
        forgeEntity.addPurity(purity);
        stack.shrink(1);
        if (stack.isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        }


    }

    return ItemInteractionResult.SUCCESS;
}

}
