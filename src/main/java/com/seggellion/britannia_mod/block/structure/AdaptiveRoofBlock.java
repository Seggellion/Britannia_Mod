package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class AdaptiveRoofBlock extends Block implements EntityBlock {
    public static final BooleanProperty SUPPORTS_LANTERN = BooleanProperty.create("supports_lantern");

    public AdaptiveRoofBlock(Properties properties) {
        super(properties);
                registerDefaultState(defaultBlockState().setValue(SUPPORTS_LANTERN, false));

    }

  @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SUPPORTS_LANTERN);
    }
    
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdaptiveRoofBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(SUPPORTS_LANTERN) ? Shapes.block()
                                                : super.getBlockSupportShape(state, level, pos);
    }

@Override
public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    if (level.getBlockEntity(pos) instanceof AdaptiveRoofBlockEntity be) {
        if (be.getBottomTexture() != null) {
            // Return a full cube shape so `Block.canSupportCenter(...)` returns true
            return Shapes.block();
        }
    }

    // Default (empty or partial) shape
    return super.getShape(state, level, pos, context);
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
