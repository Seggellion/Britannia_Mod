package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.block.entity.ChessBoardBlockEntity;
import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public record ChessBoardMoveC2SPayload(BlockPos pos, int fromX, int fromY, int toX, int toY, boolean reset)
        implements CustomPacketPayload {
    public static final Type<ChessBoardMoveC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "chess_board_move"));

    public static final StreamCodec<FriendlyByteBuf, ChessBoardMoveC2SPayload> STREAM_CODEC = StreamCodec.of(
            ChessBoardMoveC2SPayload::encode,
            ChessBoardMoveC2SPayload::decode
    );

    private static ChessBoardMoveC2SPayload decode(FriendlyByteBuf buf) {
        return new ChessBoardMoveC2SPayload(
                buf.readBlockPos(),
                buf.readByte(),
                buf.readByte(),
                buf.readByte(),
                buf.readByte(),
                buf.readBoolean()
        );
    }

    private static void encode(FriendlyByteBuf buf, ChessBoardMoveC2SPayload payload) {
        buf.writeBlockPos(payload.pos());
        buf.writeByte(payload.fromX());
        buf.writeByte(payload.fromY());
        buf.writeByte(payload.toX());
        buf.writeByte(payload.toY());
        buf.writeBoolean(payload.reset());
    }

    public static void handle(ChessBoardMoveC2SPayload payload, ServerPlayer player) {
        if (player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) {
            return;
        }

        BlockEntity blockEntity = player.serverLevel().getBlockEntity(payload.pos());
        if (!(blockEntity instanceof ChessBoardBlockEntity chessBoard)) {
            return;
        }

        if (payload.reset()) {
            chessBoard.resetGame();
        } else {
            chessBoard.move(payload.fromX(), payload.fromY(), payload.toX(), payload.toY());
        }
        ChessBoardScreenS2CPayload.send(player, payload.pos(), chessBoard.gameState());
    }

    public static void send(BlockPos pos, int fromX, int fromY, int toX, int toY) {
        NetworkHandler.sendToServer(new ChessBoardMoveC2SPayload(pos, fromX, fromY, toX, toY, false));
    }

    public static void sendReset(BlockPos pos) {
        NetworkHandler.sendToServer(new ChessBoardMoveC2SPayload(pos, 0, 0, 0, 0, true));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
