package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.HouseSignBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import com.seggellion.britannia_mod.util.HouseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;


public class HouseSignBlock extends Block implements EntityBlock {

    public HouseSignBlock() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0f, 3.0f));
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HouseSignBlockEntity(pos, state);
    }
    
    @Override
protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (level.isClientSide()) return InteractionResult.SUCCESS;

    ServerLevel serverLevel = (ServerLevel) level;
    ServerPlayer serverPlayer = (ServerPlayer) player;

    // 🔍 Look up the real ownership from nearby lot block
    HouseLotBlockEntity lot = HouseUtil.findNearbyLot(serverLevel, pos);
    if (lot == null) {
        player.sendSystemMessage(Component.literal("Could not find the house controller."));
        return InteractionResult.FAIL;
    }

    if (!lot.getOwner().equals(player.getName().getString())) {
        player.sendSystemMessage(Component.literal("You are not the owner of this house."));
        return InteractionResult.FAIL;
    }

    // ✅ Success: open the house management GUI
    HouseManagementScreenPayload.send(serverPlayer, pos);
    return InteractionResult.SUCCESS;
}


}
