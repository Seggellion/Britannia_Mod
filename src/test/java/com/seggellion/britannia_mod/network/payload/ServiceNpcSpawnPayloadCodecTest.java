package com.seggellion.britannia_mod.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceNpcSpawnPayloadCodecTest {
    @Test
    void configurationPayloadRoundTripsWithinBounds() {
        ServiceNpcSpawnConfigureC2SPayload payload = new ServiceNpcSpawnConfigureC2SPayload(
                4, new BlockPos(1, 64, 2), UUID.randomUUID(), 3L, UUID.randomUUID(), "bank_teller", false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ServiceNpcSpawnConfigureC2SPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, ServiceNpcSpawnConfigureC2SPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void oversizedTypeKeyCannotBeEncoded() {
        ServiceNpcSpawnConfigureC2SPayload payload = new ServiceNpcSpawnConfigureC2SPayload(
                4, BlockPos.ZERO, UUID.randomUUID(), 0L, UUID.randomUUID(), "a".repeat(65), true
        );
        assertThrows(IllegalArgumentException.class, () ->
                ServiceNpcSpawnConfigureC2SPayload.STREAM_CODEC.encode(
                        new FriendlyByteBuf(Unpooled.buffer()), payload));
    }
}
