package com.seggellion.britannia_mod.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankAccountOpenedS2CPayloadTest {
    @Test
    void roundTripsWithACityDisplayName() {
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "Britain", 250, 12.5, 3, 47, 92
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void roundTripsWithNoCityDisplayNameForGlobalMode() {
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", null, 250, 0.0, 0, 0, 0
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        BankAccountOpenedS2CPayload decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer);
        assertEquals(payload, decoded);
        assertEquals(null, decoded.cityDisplayName());
    }
}
