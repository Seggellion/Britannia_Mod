package com.seggellion.britannia_mod.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankTransferResultS2CPayloadTest {
    @Test
    void roundTripsEveryOperationAndKindCombination() {
        for (BankTransferResultS2CPayload.Operation operation : BankTransferResultS2CPayload.Operation.values()) {
            for (BankTransferResultS2CPayload.Kind kind : BankTransferResultS2CPayload.Kind.values()) {
                BankTransferResultS2CPayload payload = new BankTransferResultS2CPayload(operation, kind);
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                BankTransferResultS2CPayload.STREAM_CODEC.encode(buffer, payload);
                assertEquals(payload, BankTransferResultS2CPayload.STREAM_CODEC.decode(buffer));
            }
        }
    }
}
