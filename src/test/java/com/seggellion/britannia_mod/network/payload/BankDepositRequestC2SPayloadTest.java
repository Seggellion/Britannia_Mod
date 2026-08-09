package com.seggellion.britannia_mod.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BankDepositRequestC2SPayloadTest {
    @Test
    void roundTripsEntityIdAndSlotIndex() {
        BankDepositRequestC2SPayload payload = new BankDepositRequestC2SPayload(42, 7);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankDepositRequestC2SPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, BankDepositRequestC2SPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void carriesOnlyEntityIdAndSlotIndexNothingElse() {
        // Proof by construction, not just convention: the record's only components are the
        // minimal selection reference -- no fingerprint, weight, or payload field exists to
        // accidentally populate.
        assertEquals(2, BankDepositRequestC2SPayload.class.getRecordComponents().length);
    }

    @Test
    void decodeRejectsANegativeSlotIndex() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(42);
        buffer.writeVarInt(-1);
        assertThrows(IllegalArgumentException.class, () -> BankDepositRequestC2SPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void encodeRejectsANegativeSlotIndex() {
        BankDepositRequestC2SPayload payload = new BankDepositRequestC2SPayload(42, -1);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        assertThrows(IllegalArgumentException.class, () -> BankDepositRequestC2SPayload.STREAM_CODEC.encode(buffer, payload));
    }
}
