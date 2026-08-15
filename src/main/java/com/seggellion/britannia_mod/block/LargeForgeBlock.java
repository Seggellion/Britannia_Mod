package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.ItemRegistry;

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
import net.minecraft.world.item.Item;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ItemInteractionResult;
import java.util.Map;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.function.Supplier;

public class LargeForgeBlock extends Block implements EntityBlock {


    private static final Logger LOGGER = LogUtils.getLogger();
// Which ores are smeltable, and into what, is UOMetalToolMaterial's job -- a second table here
// could only drift from it. Bronze is absent from both on purpose: it is alloyed, never mined.


    public LargeForgeBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .lightLevel(state -> 14) 
            .requiresCorrectToolForDrops()
        );
    }


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LargeForgeBlockEntity(pos, state);
    }

@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
    if (!(stack.getItem() instanceof PurityOreItem purityOre)) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    LOGGER.info("Used The Large Forge");

    BlockEntity blockEntity = level.getBlockEntity(pos);
    if (!(blockEntity instanceof LargeForgeBlockEntity forgeEntity)) {
        return ItemInteractionResult.FAIL;
    }

    // ✅ Extract data from the item
    int purity = purityOre.getPurity(stack); // 1–5
    String oreType = purityOre.getOreType(stack).toLowerCase();

    LOGGER.info("Ore Type: {}", oreType);
    LOGGER.info("Purity: {}", purity);

    // One metal registry decides what is smeltable, rather than a second table here.
    if (com.seggellion.britannia_mod.item.UOMetalToolMaterial.getMaterialByName(
            com.seggellion.britannia_mod.block.ForgeSmelting.metalName(oreType)) == null) {
        LOGGER.warn("Unknown ore type: {}", oreType);
        return ItemInteractionResult.FAIL;
    }
    // ✅ Add the purity to the forge
    if (!level.isClientSide) {
        SmallForgeBlock.announceAlloy(player, forgeEntity.addPurity(oreType, purity));
        stack.shrink(1);

        if (stack.isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
    }

    return ItemInteractionResult.SUCCESS;
}

}
