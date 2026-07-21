package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankAccountOpenedS2CPayloadTest {
    @Test
    void roundTripsWithACityDisplayName() {
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", 42, "Britain", 250, 12.5, 3, 47, 92, List.of()
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void roundTripsWithNoCityDisplayNameForGlobalMode() {
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", 42, null, 250, 0.0, 0, 0, 0, List.of()
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        BankAccountOpenedS2CPayload decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer);
        assertEquals(payload, decoded);
        assertEquals(null, decoded.cityDisplayName());
    }

    @Test
    void roundTripsWithARealBankItemsList() {
        BankItemSummary first = new BankItemSummary(UUID.randomUUID(), 2.5);
        BankItemSummary second = new BankItemSummary(UUID.randomUUID(), 0.0);
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(first, second)
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        BankAccountOpenedS2CPayload decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer);
        assertEquals(payload, decoded);
        assertEquals(List.of(first, second), decoded.bankItems());
        assertEquals(42, decoded.entityId());
    }
}
