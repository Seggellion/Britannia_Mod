package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import java.util.Properties;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.annotation.Nullable;

public class AdaptiveRoofBlock extends Block implements EntityBlock {

    public AdaptiveRoofBlock(Properties properties) {
        super(properties);
    }

    
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdaptiveRoofBlockEntity(pos, state);
    }

@Override
public InteractionResult useWithoutItem(BlockState state,
                                        Level level,
                                        BlockPos pos,
                                        Player player,
                                        BlockHitResult hit) {

    if (!level.isClientSide) {
        // pick the hand you care about – here we use the main hand
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        ResourceLocation texture = getTextureFromItem(held.getItem());

        if (texture != null && level.getBlockEntity(pos) instanceof AdaptiveRoofBlockEntity be) {
            be.setBottomTexture(texture);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            return InteractionResult.SUCCESS;
        }
    }

    return InteractionResult.PASS;
}


    @Nullable
    private ResourceLocation getTextureFromItem(Item item) {
        // You define this mapping logic:
if (item == Items.STONE) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/stone");
if (item == Items.OAK_PLANKS) return ResourceLocation.fromNamespaceAndPath("minecraft", "block/oak_planks");
        return null;
    }
}
