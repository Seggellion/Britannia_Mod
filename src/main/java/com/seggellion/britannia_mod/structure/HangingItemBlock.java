package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;


import com.seggellion.britannia_mod.item.InteriorDecoratorToolItem;
import com.seggellion.britannia_mod.structure.HouseSignBlock.HolderType;
import net.minecraft.world.item.context.BlockPlaceContext;



public class HangingItemBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<HolderType> HOLDER_TYPE = EnumProperty.create("holder_type", HolderType.class);

    public HangingItemBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(HOLDER_TYPE, HolderType.WOOD_1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HOLDER_TYPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

 @Override
public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (level.isClientSide()) return InteractionResult.SUCCESS;

    // Only allow Survival or Creative players to use the tool
    if (player instanceof ServerPlayer serverPlayer) {
        GameType gameType = serverPlayer.gameMode.getGameModeForPlayer();
        if (gameType == GameType.ADVENTURE) {
            return InteractionResult.PASS;
        }
    }

    if (player.getMainHandItem().getItem() instanceof InteriorDecoratorToolItem) {
        level.setBlock(pos, state.cycle(HOLDER_TYPE), 3);
        return InteractionResult.SUCCESS;
    }

    return InteractionResult.PASS;
}

}
