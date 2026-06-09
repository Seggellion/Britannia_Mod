package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.chess.ChessGameState;
import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ChessBoardScreenS2CPayload(BlockPos pos, ChessGameState state) implements CustomPacketPayload {
    public static final Type<ChessBoardScreenS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "chess_board_screen"));

    public static final StreamCodec<FriendlyByteBuf, ChessBoardScreenS2CPayload> STREAM_CODEC = StreamCodec.of(
            ChessBoardScreenS2CPayload::encode,
            ChessBoardScreenS2CPayload::decode
    );

    private static ChessBoardScreenS2CPayload decode(FriendlyByteBuf buf) {
        return new ChessBoardScreenS2CPayload(buf.readBlockPos(), ChessGameState.read(buf));
    }

    private static void encode(FriendlyByteBuf buf, ChessBoardScreenS2CPayload payload) {
        buf.writeBlockPos(payload.pos());
        payload.state().write(buf);
    }

    public static void send(ServerPlayer player, BlockPos pos, ChessGameState state) {
        NetworkHandler.sendToPlayer(player, new ChessBoardScreenS2CPayload(pos, ChessGameState.copyOf(state)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
