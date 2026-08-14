package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.TrainingDummyBlock;
import com.seggellion.britannia_mod.event.TrainingDummyEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Adventure-mode-safe, server-validated training-dummy strike request. */
public record TrainingDummyHitC2SPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<TrainingDummyHitC2SPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "training_dummy_hit"));
    public static final StreamCodec<FriendlyByteBuf, TrainingDummyHitC2SPayload> STREAM_CODEC =
            StreamCodec.of(TrainingDummyHitC2SPayload::encode, TrainingDummyHitC2SPayload::decode);

    private static void encode(FriendlyByteBuf buffer, TrainingDummyHitC2SPayload payload) {
        buffer.writeBlockPos(payload.pos);
    }

    private static TrainingDummyHitC2SPayload decode(FriendlyByteBuf buffer) {
        return new TrainingDummyHitC2SPayload(buffer.readBlockPos());
    }

    public static void handle(TrainingDummyHitC2SPayload payload, ServerPlayer player) {
        if (player == null
                || player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE
                || player.isShiftKeyDown()
                || !player.canInteractWithBlock(payload.pos, 0.5D)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(payload.pos)
                || !(level.getBlockState(payload.pos).getBlock() instanceof TrainingDummyBlock)) {
            return;
        }

        TrainingDummyEventHandler.attemptStrike(player, level, payload.pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
