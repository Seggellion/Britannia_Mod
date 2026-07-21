package com.seggellion.britannia_mod.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankWithdrawalRequestC2SPayloadTest {
    @Test
    void roundTripsEntityIdAndBankItemPublicId() {
        UUID bankItemId = UUID.randomUUID();
        BankWithdrawalRequestC2SPayload payload = new BankWithdrawalRequestC2SPayload(42, bankItemId);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankWithdrawalRequestC2SPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, BankWithdrawalRequestC2SPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void carriesOnlyEntityIdAndBankItemPublicIdNothingElse() {
        // Proof by construction: no fingerprint/weight/payload field exists on this record at all.
        assertEquals(2, BankWithdrawalRequestC2SPayload.class.getRecordComponents().length);
    }
}
