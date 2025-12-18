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

public class SmallForgeBlock extends Block implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Supplier<Item>> ORE_TYPE_TO_INGOT = Map.of(
        "iron ore", () -> Items.IRON_INGOT,
        "gold ore", () -> Items.GOLD_INGOT,
        "shadow iron ore", () -> ItemRegistry.SHADOW_IRON_INGOT.get(),
        "valorite ore", () -> ItemRegistry.VALORITE_INGOT.get(),
        "verite ore", () -> ItemRegistry.VERITE_INGOT.get(),
        "agapite ore", () -> ItemRegistry.AGAPITE_INGOT.get(),
        "copper ore", () -> ItemRegistry.COPPER_INGOT.get(),
        "silver ore", () -> ItemRegistry.SILVER_INGOT.get(),
        "tin ore", () -> ItemRegistry.TIN_INGOT.get()
    );

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
        String oreType = purityOre.getOreType(stack).toLowerCase();

        LOGGER.info("Used The Small Forge");
        LOGGER.info("Ore Type: {}", oreType);
        LOGGER.info("Purity: {}", purity);

        Supplier<Item> ingotSupplier = ORE_TYPE_TO_INGOT.get(oreType);
        if (ingotSupplier == null) {
            LOGGER.warn("Unknown ore type: {}", oreType);
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            forgeEntity.addPurity(oreType, purity, ingotSupplier);
            stack.shrink(1);
            if (stack.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        }

        return ItemInteractionResult.SUCCESS;
    }
}
