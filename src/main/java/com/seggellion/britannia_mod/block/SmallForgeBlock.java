package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
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

        int purity = purityOre.getPurity(stack);
        String fullOreType = purityOre.getOreType(stack).toLowerCase(); // e.g. "valorite ore"
        
        // Strip out " ore" to get the raw metal name (e.g. "valorite")
        String metalName = fullOreType.replace(" ore", "");

        LOGGER.info("Used The Small Forge");
        LOGGER.info("Ore Type: {}, Metal: {}", fullOreType, metalName);
        LOGGER.info("Purity: {}", purity);

        // Ask our Enum for the matching material
        UOMetalToolMaterial metal = UOMetalToolMaterial.getMaterialByName(metalName);
        
        if (metal == null) {
            LOGGER.warn("Unknown ore/metal type: {}", metalName);
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            // Pass the ingot supplier safely through
            forgeEntity.addPurity(fullOreType, purity, metal.getIngotSupplier());
            stack.shrink(1);
            if (stack.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        }

        return ItemInteractionResult.SUCCESS;
    }
}