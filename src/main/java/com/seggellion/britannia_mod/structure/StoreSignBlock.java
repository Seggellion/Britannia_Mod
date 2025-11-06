package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.item.InteriorDecoratorToolItem;
import com.seggellion.britannia_mod.structure.HouseSignBlock.HolderType;
import com.seggellion.britannia_mod.structure.HouseSignBlock.SignType;
import com.seggellion.britannia_mod.block.entity.StoreSignBlockEntity;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.seggellion.britannia_mod.network.RenameStorePayload;
import com.seggellion.britannia_mod.network.StoreSignScreenPayload;

public class StoreSignBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<HolderType> HOLDER_TYPE = EnumProperty.create("holder_type", HolderType.class);

    private final SignType signType;

    public StoreSignBlock(SignType signType) {
        super(BlockBehaviour.Properties.of().noOcclusion().strength(3.0f, 3.0f));
        this.signType = signType;
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(HOLDER_TYPE, HolderType.WOOD_1)
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StoreSignBlockEntity(pos, state);
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

        if (player.getMainHandItem().getItem() instanceof InteriorDecoratorToolItem) {
            level.setBlock(pos, state.cycle(HOLDER_TYPE), 3);
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof StoreSignBlockEntity storeSign)) {
            player.sendSystemMessage(Component.literal("This sign is missing its data."));
            return InteractionResult.FAIL;
        }

        boolean isCreative = player.isCreative();

        StoreSignScreenPayload.send((ServerPlayer) player, pos, storeSign.getStoreName(), signType.getSerializedName(), isCreative);
        return InteractionResult.SUCCESS;
    }

    public SignType getSignType() {
        return this.signType;
    }
}
