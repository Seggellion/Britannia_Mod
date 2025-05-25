package com.seggellion.britannia_mod.network;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.structure.HouseSignBlock;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;

public class UpdateSignStyleHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void handle(UpdateSignStylePayload payload, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos signPos = payload.pos();
        BlockPos lotPos = signPos.below();

        LOGGER.info("🎨 UpdateSignStyleHandler triggered by player: {}", player.getName().getString());
        LOGGER.info("🔧 sign_type: {}, holder_type: {}", payload.signType(), payload.holderType());

        BlockEntity lotEntity = level.getBlockEntity(lotPos);
        if (!(lotEntity instanceof HouseLotBlockEntity house)) {
            LOGGER.warn("❌ Block below sign is not a HouseLotBlockEntity.");
            player.sendSystemMessage(Component.literal("Could not find house lot below sign."));
            return;
        }

        if (!house.getOwner().equals(player.getName().getString())) {
            LOGGER.warn("⛔ Ownership mismatch! Player = {}, Owner = {}", player.getName().getString(), house.getOwner());
            player.sendSystemMessage(Component.literal("You are not the owner of this house."));
            return;
        }

        BlockState state = level.getBlockState(signPos);
        if (!(state.getBlock() instanceof HouseSignBlock)) {
            LOGGER.warn("❌ Target block is not a HouseSignBlock.");
            return;
        }

        BlockState newState = state
            .setValue(HouseSignBlock.SIGN_TYPE, payload.signType())
            .setValue(HouseSignBlock.HOLDER_TYPE, payload.holderType());

        level.setBlock(signPos, newState, Block.UPDATE_ALL_IMMEDIATE);
        LOGGER.info("✅ Applied new sign style at {}", signPos);

        player.sendSystemMessage(Component.literal("Sign style updated."));
    }
}
